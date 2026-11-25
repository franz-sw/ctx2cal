package de.franzsw.ctx2cal.core.domain.routing

import de.franzsw.ctx2cal.core.domain.model.ExtractionResult
import de.franzsw.ctx2cal.core.domain.model.ModelStatus
import de.franzsw.ctx2cal.core.domain.ports.EventExtractor
import de.franzsw.ctx2cal.core.domain.ports.ModelCatalog

/**
 * Routes extraction to the user's selected active model (Nano or a specific downloaded LiteRT model),
 * falling back to the alternative ready backend if the primary fails at runtime.
 */
class ExtractorRouter(
    private val nano: EventExtractor,
    private val liteRt: EventExtractor,
    private val catalog: ModelCatalog,
) : EventExtractor {
    override val backendName: String
        get() = nano.backendName + "+" + liteRt.backendName

    override suspend fun extract(prompt: String): ExtractionResult {
        val snapshot = catalog.catalogSnapshot()
        val status = catalog.currentStatus()
        return when (status) {
            is ModelStatus.NanoAvailable -> {
                val nanoResult = nano.extract(prompt)
                if (nanoResult is ExtractionResult.Failure) {
                    val fallback = liteRt.extract(prompt)
                    if (fallback is ExtractionResult.Failure && fallback.isNoModel) nanoResult else fallback
                } else {
                    nanoResult
                }
            }
            is ModelStatus.LiteRtReady -> {
                val liteRtResult = liteRt.extract(prompt)
                if (liteRtResult is ExtractionResult.Failure && snapshot.nanoStatus is ModelStatus.NanoAvailable) {
                    val fallback = nano.extract(prompt)
                    if (fallback is ExtractionResult.Failure && fallback.isNoModel) liteRtResult else fallback
                } else {
                    liteRtResult
                }
            }
            is ModelStatus.Unsupported -> ExtractionResult.Failure(
                "On-device extraction is not supported on this device.",
                isNoModel = false,
            )
            else -> ExtractionResult.Failure(
                "No on-device model is available. Open Setup to download one.",
                isNoModel = true,
            )
        }
    }
}
