package de.franzsw.ctx2cal.data.llm

import de.franzsw.ctx2cal.domain.extraction.EventJsonParser
import de.franzsw.ctx2cal.domain.model.ExtractionResult
import de.franzsw.ctx2cal.domain.model.ModelStatus
import de.franzsw.ctx2cal.domain.ports.EventExtractor
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation

/**
 * Gemini Nano backend via ML Kit GenAI (AICore). Foreground-only: background-use
 * errors are mapped to a recoverable Failure, never thrown.
 */
class GeminiNanoExtractor(override val backendName: String = "gemini-nano") : EventExtractor {

    suspend fun nanoStatus(): ModelStatus {
        val model = Generation.getClient()
        return try {
            when (model.checkStatus()) {
                FeatureStatus.AVAILABLE -> ModelStatus.NanoAvailable
                FeatureStatus.DOWNLOADABLE -> ModelStatus.NanoDownloadable
                FeatureStatus.DOWNLOADING -> ModelStatus.NanoDownloading
                else -> ModelStatus.NanoUnavailable
            }
        } catch (e: Exception) {
            ModelStatus.NanoUnavailable
        } finally {
            model.close()
        }
    }

    override suspend fun extract(prompt: String): ExtractionResult {
        val model = Generation.getClient()
        try {
            if (model.checkStatus() != FeatureStatus.AVAILABLE) {
                return ExtractionResult.Failure("Gemini Nano is not available on this device", isNoModel = true)
            }
            return extractWithRetry(model::generateContent, prompt)
        } catch (e: GenAiException) {
            return mapFailure(e)
        } catch (e: Exception) {
            return ExtractionResult.Failure("Gemini Nano failed: ${e.message}", isNoModel = false)
        } finally {
            model.close()
        }
    }

    private suspend fun extractWithRetry(
        generate: suspend (String) -> com.google.mlkit.genai.prompt.GenerateContentResponse,
        prompt: String,
    ): ExtractionResult {
        val first = generate(prompt).candidates.firstOrNull()?.text ?: ""
        return when (val parsed = EventJsonParser.parse(first)) {
            is EventJsonParser.Parsed.Event -> ExtractionResult.Success(parsed.draft)
            is EventJsonParser.Parsed.NoEvent -> ExtractionResult.NoEvent
            is EventJsonParser.Parsed.Invalid -> {
                val second = generate(EventJsonParser.buildRetryPrompt(first, parsed.reason))
                    .candidates.firstOrNull()?.text ?: ""
                when (val retry = EventJsonParser.parse(second)) {
                    is EventJsonParser.Parsed.Event -> ExtractionResult.Success(retry.draft)
                    is EventJsonParser.Parsed.NoEvent -> ExtractionResult.NoEvent
                    is EventJsonParser.Parsed.Invalid -> ExtractionResult.Failure(
                        "Model output was invalid after retry: ${retry.reason}"
                    )
                }
            }
        }
    }

    private fun mapFailure(e: GenAiException): ExtractionResult =
        if (e.errorCode == GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED) {
            ExtractionResult.Failure("Extraction runs in the foreground only — bring the app forward and retry.")
        } else if (e.errorCode == GenAiException.ErrorCode.NOT_AVAILABLE) {
            ExtractionResult.Failure("Gemini Nano is not available on this device", isNoModel = true)
        } else {
            ExtractionResult.Failure("Gemini Nano failed: ${e.message}")
        }
}
