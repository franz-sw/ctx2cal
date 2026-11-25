package com.example.ctx2cal.data.llm

import com.example.ctx2cal.domain.model.ModelCandidate
import com.example.ctx2cal.domain.model.ModelStatus
import com.example.ctx2cal.domain.ports.ModelCatalog
import com.example.ctx2cal.domain.ports.ModelDownloader
import com.example.ctx2cal.domain.ports.SettingsRepository

/**
 * Selection order: Nano (system) > downloaded LiteRT > none.
 * Returns status without ever blocking the UI on an unavailable backend.
 */
class AndroidModelCatalog(
    private val nano: GeminiNanoExtractor,
    private val downloader: ModelDownloader,
    private val settings: SettingsRepository,
    var downloading: Boolean = false,
) : ModelCatalog {

    override suspend fun currentStatus(): ModelStatus {
        if (downloading) return ModelStatus.Downloading(null, null)
        val nanoStatus = nano.nanoStatus()
        if (nanoStatus == ModelStatus.NanoAvailable) return nanoStatus
        val path = settings.modelPath() ?: downloader.downloadedModelPath()
        if (path != null) return ModelStatus.LiteRtReady(path)
        return when (nanoStatus) {
            is ModelStatus.NanoDownloading -> nanoStatus
            is ModelStatus.NanoDownloadable -> nanoStatus
            else -> ModelStatus.None
        }
    }

    override suspend fun availableCandidate(): ModelCandidate? = ModelDownloaderAndroid.candidate()
}
