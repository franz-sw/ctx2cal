package de.franzsw.ctx2cal.core.data.llm

import de.franzsw.ctx2cal.core.domain.model.ModelCandidate
import de.franzsw.ctx2cal.core.domain.model.ModelStatus
import de.franzsw.ctx2cal.core.domain.ports.ModelCatalog
import de.franzsw.ctx2cal.core.domain.ports.ModelDownloader
import de.franzsw.ctx2cal.core.domain.repository.SettingsRepository

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
