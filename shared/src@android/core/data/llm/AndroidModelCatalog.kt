package de.franzsw.ctx2cal.core.data.llm

import de.franzsw.ctx2cal.core.domain.catalog.ModelSuitabilityEvaluator
import de.franzsw.ctx2cal.core.domain.model.CatalogModelState
import de.franzsw.ctx2cal.core.domain.model.ModelCandidate
import de.franzsw.ctx2cal.core.domain.model.ModelCatalogEntries
import de.franzsw.ctx2cal.core.domain.model.ModelCatalogSnapshot
import de.franzsw.ctx2cal.core.domain.model.ModelStatus
import de.franzsw.ctx2cal.core.domain.model.ModelSuitability
import de.franzsw.ctx2cal.core.domain.ports.DeviceCapabilitiesProvider
import de.franzsw.ctx2cal.core.domain.ports.ModelCatalog
import de.franzsw.ctx2cal.core.domain.ports.ModelDownloader
import de.franzsw.ctx2cal.core.domain.repository.SettingsRepository
import java.io.File

/**
 * Multi-model Android catalog reporting Gemini Nano status, per-model download & active state,
 * and pure-domain hardware suitability evaluations. Recovers cleanly from stale persisted paths.
 */
class AndroidModelCatalog(
    private val nano: GeminiNanoExtractor,
    private val downloader: ModelDownloader,
    private val settings: SettingsRepository,
    private val capabilitiesProvider: DeviceCapabilitiesProvider? = null,
    var downloading: Boolean = false,
) : ModelCatalog {

    override suspend fun currentStatus(): ModelStatus {
        if (downloading) {
            return ModelStatus.Downloading(null, null, settings.activeDownloadModelId())
        }
        val nanoStatus = nano.nanoStatus()
        val downloadedPaths = downloader.downloadedModelPaths()
        val activeId = resolveActiveModelId(nanoStatus, downloadedPaths)

        return when {
            activeId == ModelCatalogEntries.NANO_MODEL_ID && nanoStatus is ModelStatus.NanoAvailable -> {
                nanoStatus
            }
            activeId != null && downloadedPaths[activeId] != null -> {
                val candidate = ModelCatalogEntries.findById(activeId)
                ModelStatus.LiteRtReady(
                    modelPath = downloadedPaths.getValue(activeId),
                    modelId = activeId,
                    modelName = candidate?.name,
                )
            }
            nanoStatus is ModelStatus.NanoDownloading -> nanoStatus
            nanoStatus is ModelStatus.NanoDownloadable -> nanoStatus
            else -> ModelStatus.None
        }
    }

    override suspend fun availableCandidate(): ModelCandidate? {
        val caps = capabilitiesProvider?.getCapabilities()
        if (caps != null) {
            return ModelCatalogEntries.all.firstOrNull {
                ModelSuitabilityEvaluator.evaluate(it, caps) !is ModelSuitability.Incompatible
            }
        }
        return ModelCatalogEntries.QWEN3_0_6B_INT4
    }

    override suspend fun catalogSnapshot(): ModelCatalogSnapshot {
        val nanoStatus = nano.nanoStatus()
        val downloadedPaths = downloader.downloadedModelPaths()
        val activeId = resolveActiveModelId(nanoStatus, downloadedPaths)
        val caps = capabilitiesProvider?.getCapabilities()

        val models = ModelCatalogEntries.all.map { candidate ->
            val downloadedPath = downloadedPaths[candidate.id]
            val suitability = if (caps != null) {
                ModelSuitabilityEvaluator.evaluate(
                    candidate = candidate,
                    capabilities = caps,
                    isDownloaded = downloadedPath != null,
                )
            } else {
                ModelSuitability.Recommended
            }
            CatalogModelState(
                candidate = candidate,
                downloadedPath = downloadedPath,
                isActive = activeId == candidate.id,
                suitability = suitability,
            )
        }

        val showWarning = ModelSuitabilityEvaluator.shouldShowNoAcceleratedWarning(
            nanoStatus = nanoStatus,
            suitabilities = models.map { it.suitability },
        )

        return ModelCatalogSnapshot(
            nanoStatus = nanoStatus,
            models = models,
            activeModelId = activeId,
            showNoAcceleratedWarning = showWarning,
        )
    }

    override suspend fun selectActiveModel(modelId: String) {
        if (modelId == ModelCatalogEntries.NANO_MODEL_ID) {
            if (nano.nanoStatus() is ModelStatus.NanoAvailable) {
                settings.setActiveModelId(ModelCatalogEntries.NANO_MODEL_ID)
                settings.setPreferredBackend("gemini-nano")
            }
            return
        }
        val path = downloader.downloadedModelPath(modelId) ?: return
        settings.setActiveModelId(modelId)
        settings.setModelPath(path)
        settings.setPreferredBackend("litertlm")
    }

    private fun resolveActiveModelId(
        nanoStatus: ModelStatus,
        downloadedPaths: Map<String, String>,
    ): String? {
        // Clear stale settings.modelPath() if pointing to a missing or empty file.
        val savedPath = settings.modelPath()
        if (savedPath != null) {
            val file = File(savedPath)
            if (!file.isFile || file.length() <= 0L) {
                settings.setModelPath(null)
            }
        }

        val savedActiveId = settings.activeModelId()
        return when {
            savedActiveId == ModelCatalogEntries.NANO_MODEL_ID -> {
                if (nanoStatus is ModelStatus.NanoAvailable) {
                    ModelCatalogEntries.NANO_MODEL_ID
                } else {
                    fallbackFromStaleSelection(nanoStatus, downloadedPaths)
                }
            }
            savedActiveId != null -> {
                val validPath = downloadedPaths[savedActiveId]
                if (validPath != null) {
                    if (settings.modelPath() != validPath) {
                        settings.setModelPath(validPath)
                    }
                    savedActiveId
                } else {
                    fallbackFromStaleSelection(nanoStatus, downloadedPaths)
                }
            }
            else -> {
                when {
                    nanoStatus is ModelStatus.NanoAvailable -> ModelCatalogEntries.NANO_MODEL_ID
                    downloadedPaths.isNotEmpty() -> {
                        val firstId = downloadedPaths.keys.first()
                        settings.setActiveModelId(firstId)
                        settings.setModelPath(downloadedPaths.getValue(firstId))
                        firstId
                    }
                    else -> null
                }
            }
        }
    }

    private fun fallbackFromStaleSelection(
        nanoStatus: ModelStatus,
        downloadedPaths: Map<String, String>,
    ): String? {
        return when {
            nanoStatus is ModelStatus.NanoAvailable -> {
                settings.setActiveModelId(ModelCatalogEntries.NANO_MODEL_ID)
                settings.setModelPath(null)
                settings.setPreferredBackend("gemini-nano")
                ModelCatalogEntries.NANO_MODEL_ID
            }
            downloadedPaths.isNotEmpty() -> {
                val firstId = downloadedPaths.keys.first()
                val path = downloadedPaths.getValue(firstId)
                settings.setActiveModelId(firstId)
                settings.setModelPath(path)
                settings.setPreferredBackend("litertlm")
                firstId
            }
            else -> {
                settings.setActiveModelId(null)
                settings.setModelPath(null)
                settings.setPreferredBackend(null)
                null
            }
        }
    }
}
