package de.franzsw.ctx2cal.core.platform

import de.franzsw.ctx2cal.core.domain.model.CalendarEventDraft
import de.franzsw.ctx2cal.core.domain.model.ExtractionResult
import de.franzsw.ctx2cal.core.domain.model.ModelCandidate
import de.franzsw.ctx2cal.core.domain.model.ModelCatalogSnapshot
import de.franzsw.ctx2cal.core.domain.model.ModelStatus
import de.franzsw.ctx2cal.core.domain.ports.CalendarSharer
import de.franzsw.ctx2cal.core.domain.ports.DownloadProgress
import de.franzsw.ctx2cal.core.domain.ports.EventExtractor
import de.franzsw.ctx2cal.core.domain.ports.IcsExporter
import de.franzsw.ctx2cal.core.domain.ports.ModelCatalog
import de.franzsw.ctx2cal.core.domain.ports.ModelDownloader
import de.franzsw.ctx2cal.core.domain.ports.ModelTester
import de.franzsw.ctx2cal.core.domain.ports.RawPromptResult
import de.franzsw.ctx2cal.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** iOS has no on-device LLM backend in v1 — all ports degrade cleanly to unsupported states. */
class UnsupportedModelCatalog : ModelCatalog {
    override suspend fun currentStatus(): ModelStatus = ModelStatus.Unsupported
    override suspend fun availableCandidate(): ModelCandidate? = null
    override suspend fun catalogSnapshot(): ModelCatalogSnapshot = ModelCatalogSnapshot(
        nanoStatus = ModelStatus.Unsupported,
        models = emptyList(),
        activeModelId = null,
        showNoAcceleratedWarning = false,
    )
}

class UnsupportedDownloader : ModelDownloader {
    override fun download(candidate: ModelCandidate): Flow<DownloadProgress> =
        flowOf(DownloadProgress.Failed("Model download is not supported on iOS yet."))
    override fun downloadedModelPath(): String? = null
    override fun downloadedModelPath(modelId: String): String? = null
    override fun downloadedModelPaths(): Map<String, String> = emptyMap()
    override fun cancelDownload() = Unit
    override fun deleteModel(): Boolean = false
    override fun deleteModel(modelId: String): Boolean = false
}

class UnsupportedExtractor(override val backendName: String = "none") : EventExtractor {
    override suspend fun extract(prompt: String): ExtractionResult =
        ExtractionResult.Failure("On-device extraction is not supported on iOS yet.", isNoModel = false)
}

class UnsupportedCalendarSharer : CalendarSharer {
    override fun share(draft: CalendarEventDraft) = Unit
    override fun shareWithResult(draft: CalendarEventDraft): String =
        "Calendar sharing is not supported on iOS yet."
}

class UnsupportedIcsExporter : IcsExporter {
    override suspend fun export(draft: CalendarEventDraft): String? = null
}

class UnsupportedModelTester : ModelTester {
    override suspend fun runPrompt(
        modelId: String,
        systemPrompt: String,
        userPrompt: String,
    ): RawPromptResult = RawPromptResult.Failure("On-device model testing is not supported on iOS yet.")
}

class UnsupportedSettings : SettingsRepository {
    private var memory: String? = null
    private var activeId: String? = null
    private var dlId: Long? = null
    private var dlModelId: String? = null
    private var gpuUnsupported: Boolean = false
    private var customPrompt: String? = null
    override fun preferredBackend(): String? = memory
    override fun setPreferredBackend(name: String?) { memory = name }
    override fun modelPath(): String? = null
    override fun setModelPath(path: String?) = Unit
    override fun activeModelId(): String? = activeId
    override fun setActiveModelId(id: String?) { activeId = id }
    override fun activeDownloadId(): Long? = dlId
    override fun setActiveDownloadId(id: Long?) { dlId = id }
    override fun activeDownloadModelId(): String? = dlModelId
    override fun setActiveDownloadModelId(id: String?) { dlModelId = id }
    override fun isGpuUnsupported(): Boolean = gpuUnsupported
    override fun setGpuUnsupported(unsupported: Boolean) { gpuUnsupported = unsupported }
    override fun customSystemPrompt(): String? = customPrompt
    override fun setCustomSystemPrompt(prompt: String?) { customPrompt = prompt }
}
