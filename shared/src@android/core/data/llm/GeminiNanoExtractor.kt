package de.franzsw.ctx2cal.core.data.llm

import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Candidate
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.GenerateContentResponse
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import de.franzsw.ctx2cal.core.domain.llm.NanoStatusMapper
import de.franzsw.ctx2cal.core.domain.model.ExtractionResult
import de.franzsw.ctx2cal.core.domain.model.ModelStatus
import de.franzsw.ctx2cal.core.domain.ports.EventExtractor
import de.franzsw.ctx2cal.core.domain.ports.RawPromptResult
import de.franzsw.ctx2cal.core.domain.repository.SettingsRepository
import de.franzsw.ctx2cal.extract.domain.EventJsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Gemini Nano backend via ML Kit GenAI (AICore). Foreground-only: background-use
 * errors and per-attempt timeouts are mapped to a recoverable Failure without retrying.
 */
class GeminiNanoExtractor(
    private val settings: SettingsRepository? = null,
    private val attemptTimeoutMs: Long = DEFAULT_ATTEMPT_TIMEOUT_MS,
    override val backendName: String = "gemini-nano",
) : EventExtractor {

    suspend fun nanoStatus(): ModelStatus {
        val model = Generation.getClient()
        return try {
            val status = model.checkStatus()
            val baseName = if (
                status == FeatureStatus.AVAILABLE ||
                status == FeatureStatus.DOWNLOADABLE ||
                status == FeatureStatus.DOWNLOADING
            ) {
                runCatching { model.getBaseModelName() }.getOrNull()
            } else {
                null
            }
            NanoStatusMapper.mapNanoStatus(status, baseName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("GeminiNanoExtractor", "checkStatus failed", e)
            ModelStatus.NanoUnavailable
        } finally {
            model.close()
        }
    }

    override suspend fun extract(prompt: String): ExtractionResult {
        val model = Generation.getClient()
        try {
            val status = model.checkStatus()
            val baseName = if (status == FeatureStatus.DOWNLOADABLE || status == FeatureStatus.DOWNLOADING) {
                runCatching { model.getBaseModelName() }.getOrNull()
            } else {
                null
            }
            if (!NanoStatusMapper.isNanoReady(status, baseName)) {
                return ExtractionResult.Failure("Gemini Nano is not available on this device.", isNoModel = true)
            }
            val parts = EventJsonParser.splitForConversation(prompt, settings?.customSystemPrompt())
            return extractWithRetry(
                generate = { systemPrompt, userPrompt ->
                    val request = buildRequest(model, systemPrompt, userPrompt)
                    withTimeout(attemptTimeoutMs) { model.generateContent(request) }
                },
                systemPrompt = parts.systemInstruction,
                userPrompt = parts.userPrompt,
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

    suspend fun generateRaw(prompt: String): RawPromptResult =
        generateRaw(systemPrompt = "", userPrompt = prompt)

    suspend fun generateRaw(systemPrompt: String, userPrompt: String): RawPromptResult {
        val model = Generation.getClient()
        try {
            val status = model.checkStatus()
            val baseName = if (status == FeatureStatus.DOWNLOADABLE || status == FeatureStatus.DOWNLOADING) {
                runCatching { model.getBaseModelName() }.getOrNull()
            } else {
                null
            }
            if (!NanoStatusMapper.isNanoReady(status, baseName)) {
                return RawPromptResult.Failure("Gemini Nano is not available on this device.")
            }
            val startMs = System.currentTimeMillis()
            val request = buildRequest(model, systemPrompt, userPrompt)
            val response = withTimeout(attemptTimeoutMs) { model.generateContent(request) }
            val text = extractCandidateText(response)
            return RawPromptResult.Success(
                output = text,
                durationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L),
            )
        } catch (_: TimeoutCancellationException) {
            return RawPromptResult.Failure("Gemini Nano timed out after ${attemptTimeoutMs / 1000}s. Please retry.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: GenAiException) {
            return when (e.errorCode) {
                GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED ->
                    RawPromptResult.Failure("Inference runs in the foreground only — keep the app open and retry.")
                GenAiException.ErrorCode.NOT_AVAILABLE ->
                    RawPromptResult.Failure("Gemini Nano is not available on this device.")
                else -> {
                    val detail = e.message?.takeIf { it.isNotBlank() }
                    RawPromptResult.Failure(
                        if (detail != null) "Gemini Nano failed ($detail). Please retry."
                        else "Gemini Nano failed to run. Please retry."
                    )
                }
            }
        } catch (e: Exception) {
            val detail = e.message?.takeIf { it.isNotBlank() }
            return RawPromptResult.Failure(
                if (detail != null) "Gemini Nano couldn't process this prompt ($detail)."
                else "Gemini Nano couldn't process this prompt. Please try again."
            )
        } finally {
            model.close()
        }
    }

    private suspend fun extractWithRetry(
        generate: suspend (systemPrompt: String, userPrompt: String) -> GenerateContentResponse,
        systemPrompt: String,
        userPrompt: String,
    ): ExtractionResult {
        val first = extractCandidateText(generate(systemPrompt, userPrompt))
        return when (val parsed = EventJsonParser.parse(first)) {
            is EventJsonParser.Parsed.Event -> ExtractionResult.Success(parsed.draft, rawOutput = first)
            is EventJsonParser.Parsed.NoEvent -> ExtractionResult.NoEvent
            is EventJsonParser.Parsed.Invalid -> {
                val retryPrompt = EventJsonParser.buildRetryPrompt(
                    previousRaw = first,
                    reason = parsed.reason,
                    originalPrompt = userPrompt,
                )
                val second = extractCandidateText(generate(systemPrompt, retryPrompt))
                when (val retry = EventJsonParser.parse(second)) {
                    is EventJsonParser.Parsed.Event -> ExtractionResult.Success(retry.draft, rawOutput = second)
                    is EventJsonParser.Parsed.NoEvent -> ExtractionResult.NoEvent
                    is EventJsonParser.Parsed.Invalid -> ExtractionResult.Failure(
                        reason = "Couldn't extract a valid event from this text. Try simplifying the wording or adding a clearer date/time.",
                        rawOutput = second,
                    )
                }
            }
        }
    }

    private suspend fun buildRequest(
        model: GenerativeModel,
        systemPrompt: String,
        userPrompt: String,
    ): GenerateContentRequest {
        val supportsSystemPrompt = systemPrompt.isNotBlank() &&
            runCatching { @Suppress("DEPRECATION") model.isSystemPromptAvailable() }.getOrDefault(false)

        val builder = if (supportsSystemPrompt) {
            GenerateContentRequest.Builder(SystemInstruction(systemPrompt), TextPart(userPrompt))
        } else {
            val combined = if (systemPrompt.isNotBlank()) "$systemPrompt\n\n$userPrompt" else userPrompt
            GenerateContentRequest.Builder(TextPart(combined))
        }

        val tokenLimit = runCatching { model.getTokenLimit() }.getOrNull()
        val inputTokens = runCatching { model.countTokens(builder.build()).totalTokens }.getOrNull()
        val maxOutput = if (tokenLimit != null && inputTokens != null && tokenLimit > inputTokens) {
            minOf(MAX_OUTPUT_TOKENS, tokenLimit - inputTokens)
        } else {
            MAX_OUTPUT_TOKENS
        }

        builder.temperature = 0.0f
        builder.topK = 1
        builder.seed = 0
        builder.candidateCount = 1
        builder.maxOutputTokens = maxOutput
        builder.enableThinking = false
        return builder.build()
    }

    private fun extractCandidateText(response: GenerateContentResponse): String {
        val candidate = response.candidates.firstOrNull()
        val text = candidate?.text ?: ""
        val finishReason = candidate?.finishReason
        if (finishReason != null && finishReason != Candidate.FinishReason.STOP) {
            Log.w(
                "GeminiNanoExtractor",
                "Generation stopped with finishReason=$finishReason (outputChars=${text.length})",
            )
        }
        return text
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
        const val MAX_OUTPUT_TOKENS = 1024
    }
}
