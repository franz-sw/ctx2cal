package de.franzsw.ctx2cal.core.data.llm

import de.franzsw.ctx2cal.core.domain.model.ModelCatalogEntries
import de.franzsw.ctx2cal.core.domain.ports.ModelDownloader
import de.franzsw.ctx2cal.core.domain.ports.ModelTester
import de.franzsw.ctx2cal.core.domain.ports.RawPromptResult

/**
 * Dispatches raw single-turn test prompts directly to the selected model ([modelId])
 * without JSON validation, retries, or cross-model fallback.
 */
class AndroidModelTester(
    private val nano: GeminiNanoExtractor,
    private val liteRt: LiteRtExtractor,
    private val downloader: ModelDownloader,
) : ModelTester {

    override suspend fun runPrompt(
        modelId: String,
        systemPrompt: String,
        userPrompt: String,
    ): RawPromptResult {
        if (modelId == ModelCatalogEntries.NANO_MODEL_ID) {
            return nano.generateRaw(
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
            )
        }
        val modelPath = downloader.downloadedModelPath(modelId)
            ?: return RawPromptResult.Failure("Model \"$modelId\" is not downloaded.")
        return liteRt.generateRaw(
            modelPath = modelPath,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
        )
    }
}
