package de.franzsw.ctx2cal.core.data.llm

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.GenerateContentResponse
import com.google.mlkit.genai.prompt.Generation
import de.franzsw.ctx2cal.core.domain.model.ExtractionResult
import de.franzsw.ctx2cal.core.domain.model.ModelStatus
import de.franzsw.ctx2cal.core.domain.ports.EventExtractor
import de.franzsw.ctx2cal.extract.domain.EventJsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Gemini Nano backend via ML Kit GenAI (AICore). Foreground-only: background-use
 * errors and per-attempt timeouts are mapped to a recoverable Failure without retrying.
 */
class GeminiNanoExtractor(
    private val attemptTimeoutMs: Long = DEFAULT_ATTEMPT_TIMEOUT_MS,
    override val backendName: String = "gemini-nano",
) : EventExtractor {

    suspend fun nanoStatus(): ModelStatus {
        val model = Generation.getClient()
        return try {
            when (model.checkStatus()) {
                FeatureStatus.AVAILABLE -> ModelStatus.NanoAvailable
                FeatureStatus.DOWNLOADABLE -> ModelStatus.NanoDownloadable
                FeatureStatus.DOWNLOADING -> ModelStatus.NanoDownloading
                else -> ModelStatus.NanoUnavailable
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ModelStatus.NanoUnavailable
        } finally {
            model.close()
        }
    }

    override suspend fun extract(prompt: String): ExtractionResult {
        val model = Generation.getClient()
        try {
            if (model.checkStatus() != FeatureStatus.AVAILABLE) {
                return ExtractionResult.Failure("Gemini Nano is not available on this device.", isNoModel = true)
            }
            return extractWithRetry(
                generate = { input -> withTimeout(attemptTimeoutMs) { model.generateContent(input) } },
                prompt = prompt,
            )
        } catch (_: TimeoutCancellationException) {
            return ExtractionResult.Failure(
                "Gemini Nano timed out after ${attemptTimeoutMs / 1000}s. Please retry.",
                isNoModel = false,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: GenAiException) {
            return mapFailure(e)
        } catch (e: Exception) {
            val detail = e.message?.takeIf { it.isNotBlank() }
            return ExtractionResult.Failure(
                if (detail != null) "Gemini Nano couldn't process this text ($detail)."
                else "Gemini Nano couldn't process this text. Please try again.",
                isNoModel = false,
            )
        } finally {
            model.close()
        }
    }

    private suspend fun extractWithRetry(
        generate: suspend (String) -> GenerateContentResponse,
        prompt: String,
    ): ExtractionResult {
        val first = generate(prompt).candidates.firstOrNull()?.text ?: ""
        return when (val parsed = EventJsonParser.parse(first)) {
            is EventJsonParser.Parsed.Event -> ExtractionResult.Success(parsed.draft)
            is EventJsonParser.Parsed.NoEvent -> ExtractionResult.NoEvent
            is EventJsonParser.Parsed.Invalid -> {
                val retryPrompt = EventJsonParser.buildRetryPrompt(
                    previousRaw = first,
                    reason = parsed.reason,
                    originalPrompt = prompt,
                )
                val second = generate(retryPrompt).candidates.firstOrNull()?.text ?: ""
                when (val retry = EventJsonParser.parse(second)) {
                    is EventJsonParser.Parsed.Event -> ExtractionResult.Success(retry.draft)
                    is EventJsonParser.Parsed.NoEvent -> ExtractionResult.NoEvent
                    is EventJsonParser.Parsed.Invalid -> ExtractionResult.Failure(
                        "Couldn't extract a valid event from this text. Try simplifying the wording or adding a clearer date/time."
                    )
                }
            }
        }
    }

    private fun mapFailure(e: GenAiException): ExtractionResult = when (e.errorCode) {
        GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED ->
            ExtractionResult.Failure("Extraction runs in the foreground only — keep the app open and retry.")
        GenAiException.ErrorCode.NOT_AVAILABLE ->
            ExtractionResult.Failure("Gemini Nano is not available on this device.", isNoModel = true)
        else -> {
            val detail = e.message?.takeIf { it.isNotBlank() }
            ExtractionResult.Failure(
                if (detail != null) "Gemini Nano failed ($detail). Please retry."
                else "Gemini Nano failed to run. Please retry."
            )
        }
    }

    companion object {
        const val DEFAULT_ATTEMPT_TIMEOUT_MS = 45_000L
    }
}
