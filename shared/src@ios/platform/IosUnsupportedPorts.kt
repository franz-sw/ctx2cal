package com.example.ctx2cal.platform

import com.example.ctx2cal.domain.model.CalendarEventDraft
import com.example.ctx2cal.domain.model.ExtractionResult
import com.example.ctx2cal.domain.model.ModelStatus
import com.example.ctx2cal.domain.ports.CalendarSharer
import com.example.ctx2cal.domain.ports.DownloadProgress
import com.example.ctx2cal.domain.ports.EventExtractor
import com.example.ctx2cal.domain.ports.IcsExporter
import com.example.ctx2cal.domain.ports.ModelCandidate
import com.example.ctx2cal.domain.ports.ModelCatalog
import com.example.ctx2cal.domain.ports.ModelDownloader
import com.example.ctx2cal.domain.ports.SettingsRepository
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
