package com.example.ctx2cal.domain.routing

import com.example.ctx2cal.domain.model.ExtractionResult
import com.example.ctx2cal.domain.model.ModelStatus
import com.example.ctx2cal.domain.ports.EventExtractor
import com.example.ctx2cal.domain.ports.ModelCatalog

/**
 * Routes extraction to Nano when available, else to the downloaded LiteRT model,
 * else returns the no-model error (which sends the user to the Setup screen).
 * Selection order: Nano > LiteRT > none.
 */
class ExtractorRouter(
    private val nano: EventExtractor,
    private val liteRt: EventExtractor,
    private val catalog: ModelCatalog,
) : EventExtractor {
    override val backendName: String
        get() = nano.backendName + "+" + liteRt.backendName

    override suspend fun extract(prompt: String): ExtractionResult = when (val status = catalog.currentStatus()) {
        is ModelStatus.NanoAvailable -> nano.extract(prompt)
        is ModelStatus.LiteRtReady -> liteRt.extract(prompt)
        else -> ExtractionResult.Failure(
            "No on-device model is available. Open Setup to download one.",
            isNoModel = true,
        )
    }
}
