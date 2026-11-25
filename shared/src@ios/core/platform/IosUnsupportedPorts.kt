package de.franzsw.ctx2cal.core.platform

import de.franzsw.ctx2cal.core.domain.model.CalendarEventDraft
import de.franzsw.ctx2cal.core.domain.model.ExtractionResult
import de.franzsw.ctx2cal.core.domain.model.ModelStatus
import de.franzsw.ctx2cal.core.domain.ports.CalendarSharer
import de.franzsw.ctx2cal.core.domain.ports.DownloadProgress
import de.franzsw.ctx2cal.core.domain.ports.EventExtractor
import de.franzsw.ctx2cal.core.domain.ports.IcsExporter
import de.franzsw.ctx2cal.core.domain.model.ModelCandidate
import de.franzsw.ctx2cal.core.domain.ports.ModelCatalog
import de.franzsw.ctx2cal.core.domain.ports.ModelDownloader
import de.franzsw.ctx2cal.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** iOS has no on-device LLM backend in v1 — all ports degrade to unsupported errors. */
class UnsupportedModelCatalog : ModelCatalog {
    override suspend fun currentStatus(): ModelStatus = ModelStatus.None
    override suspend fun availableCandidate(): ModelCandidate? = null
}

class UnsupportedDownloader : ModelDownloader {
    override fun download(candidate: ModelCandidate): Flow<DownloadProgress> =
        flowOf(DownloadProgress.Failed("Model download is not supported on iOS in v1"))
    override fun downloadedModelPath(): String? = null
}

class UnsupportedExtractor(override val backendName: String = "none") : EventExtractor {
    override suspend fun extract(prompt: String): ExtractionResult =
        ExtractionResult.Failure("On-device extraction is not supported on iOS in v1.", isNoModel = true)
}

class UnsupportedCalendarSharer : CalendarSharer {
    override fun share(draft: CalendarEventDraft) = Unit
}

class UnsupportedIcsExporter : IcsExporter {
    override suspend fun export(draft: CalendarEventDraft): String? = null
}

class UnsupportedSettings : SettingsRepository {
    private var memory: String? = null
    override fun preferredBackend(): String? = memory
    override fun setPreferredBackend(name: String?) { memory = name }
    override fun modelPath(): String? = null
    override fun setModelPath(path: String?) = Unit
}
