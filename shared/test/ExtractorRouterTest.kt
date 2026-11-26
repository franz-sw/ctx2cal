package de.franzsw.ctx2cal

import de.franzsw.ctx2cal.core.domain.model.CalendarEventDraft
import de.franzsw.ctx2cal.core.domain.model.CatalogModelState
import de.franzsw.ctx2cal.core.domain.model.ExtractionResult
import de.franzsw.ctx2cal.core.domain.model.ModelCandidate
import de.franzsw.ctx2cal.core.domain.model.ModelCatalogEntries
import de.franzsw.ctx2cal.core.domain.model.ModelCatalogSnapshot
import de.franzsw.ctx2cal.core.domain.model.ModelStatus
import de.franzsw.ctx2cal.core.domain.model.ModelSuitability
import de.franzsw.ctx2cal.core.domain.ports.EventExtractor
import de.franzsw.ctx2cal.core.domain.ports.ModelCatalog
import de.franzsw.ctx2cal.core.domain.routing.ExtractorRouter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class ExtractorRouterTest {

    private class FakeExtractor(
        override val backendName: String,
        var result: ExtractionResult,
    ) : EventExtractor {
        var calls: Int = 0

        override suspend fun extract(prompt: String): ExtractionResult {
            calls++
            return result
        }
    }

    private class FakeCatalog(
        var status: ModelStatus,
        var nanoStatusOverride: ModelStatus? = null,
    ) : ModelCatalog {
        override suspend fun currentStatus(): ModelStatus = status

        override suspend fun availableCandidate(): ModelCandidate? = null

        override suspend fun catalogSnapshot(): ModelCatalogSnapshot = ModelCatalogSnapshot(
            nanoStatus = nanoStatusOverride ?: status,
            models = listOf(
                CatalogModelState(
                    candidate = ModelCatalogEntries.QWEN3_0_6B_INT4,
                    downloadedPath = (status as? ModelStatus.LiteRtReady)?.modelPath,
                    isActive = status is ModelStatus.LiteRtReady,
                    suitability = ModelSuitability.Recommended,
                )
            ),
            activeModelId = when (status) {
                is ModelStatus.NanoAvailable -> ModelCatalogEntries.NANO_MODEL_ID
                is ModelStatus.LiteRtReady -> (status as? ModelStatus.LiteRtReady)?.modelId ?: ModelCatalogEntries.QWEN3_0_6B_INT4.id
                else -> null
            },
            showNoAcceleratedWarning = false,
        )
    }

    private val nanoDraft = CalendarEventDraft(
        title = "Nano event",
        startIso = "2026-11-25T10:00:00",
        endIso = "2026-11-25T11:00:00",
        allDay = false,
    )
    private val liteRtDraft = CalendarEventDraft(
        title = "LiteRT event",
        startIso = "2026-11-25T10:00:00",
        endIso = "2026-11-25T11:00:00",
        allDay = false,
    )

    @Test
    fun `backend name combines both backends`() {
        val router = ExtractorRouter(
            nano = FakeExtractor("nano", ExtractionResult.Success(nanoDraft)),
            liteRt = FakeExtractor("litertlm", ExtractionResult.Success(liteRtDraft)),
            catalog = FakeCatalog(ModelStatus.NanoAvailable()),
        )
        assertEquals("nano+litertlm", router.backendName)
    }

    @Test
    fun `nano preferred when available`() = runTest {
        val nano = FakeExtractor("nano", ExtractionResult.Success(nanoDraft))
        val liteRt = FakeExtractor("litertlm", ExtractionResult.Success(liteRtDraft))
        val router = ExtractorRouter(nano, liteRt, FakeCatalog(ModelStatus.NanoAvailable()))

        val result = router.extract("prompt")
        assertIs<ExtractionResult.Success>(result)
        assertEquals("Nano event", result.draft.title)
        assertEquals(1, nano.calls)
        assertEquals(0, liteRt.calls)
    }

    @Test
    fun `liteRT fallback when nano unavailable`() = runTest {
        val nano = FakeExtractor("nano", ExtractionResult.Success(nanoDraft))
        val liteRt = FakeExtractor("litertlm", ExtractionResult.Success(liteRtDraft))
        val router = ExtractorRouter(nano, liteRt, FakeCatalog(ModelStatus.LiteRtReady("/data/model.litertlm")))

        val result = router.extract("prompt")
        assertIs<ExtractionResult.Success>(result)
        assertEquals("LiteRT event", result.draft.title)
        assertEquals(0, nano.calls)
        assertEquals(1, liteRt.calls)
    }

    @Test
    fun `nano runtime failure falls back to liteRT success`() = runTest {
        val nano = FakeExtractor("nano", ExtractionResult.Failure("nano boom", isNoModel = false))
        val liteRt = FakeExtractor("litertlm", ExtractionResult.Success(liteRtDraft))
        val router = ExtractorRouter(nano, liteRt, FakeCatalog(ModelStatus.NanoAvailable()))

        val result = router.extract("prompt")
        assertIs<ExtractionResult.Success>(result)
        assertEquals("LiteRT event", result.draft.title)
        assertEquals(1, nano.calls)
        assertEquals(1, liteRt.calls)
    }

    @Test
    fun `nano failure with liteRT no-model failure returns original nano failure`() = runTest {
        val nanoFailure = ExtractionResult.Failure("nano boom", isNoModel = false)
        val nano = FakeExtractor("nano", nanoFailure)
        val liteRt = FakeExtractor("litertlm", ExtractionResult.Failure("no model", isNoModel = true))
        val router = ExtractorRouter(nano, liteRt, FakeCatalog(ModelStatus.NanoAvailable()))

        val result = router.extract("prompt")
        assertIs<ExtractionResult.Failure>(result)
        assertEquals("nano boom", result.reason)
    }

    @Test
    fun `nano failure with liteRT generic failure returns liteRT failure`() = runTest {
        val nano = FakeExtractor("nano", ExtractionResult.Failure("nano boom", isNoModel = false))
        val liteRt = FakeExtractor("litertlm", ExtractionResult.Failure("litertlm boom", isNoModel = false))
        val router = ExtractorRouter(nano, liteRt, FakeCatalog(ModelStatus.NanoAvailable()))

        val result = router.extract("prompt")
        assertIs<ExtractionResult.Failure>(result)
        assertEquals("litertlm boom", result.reason)
        assertEquals(false, result.isNoModel)
    }

    @Test
    fun `nano success is returned even if liteRT would fail`() = runTest {
        val nano = FakeExtractor("nano", ExtractionResult.Success(nanoDraft))
        val liteRt = FakeExtractor("litertlm", ExtractionResult.Failure("litertlm boom"))
        val router = ExtractorRouter(nano, liteRt, FakeCatalog(ModelStatus.NanoAvailable()))

        val result = router.extract("prompt")
        assertIs<ExtractionResult.Success>(result)
        assertEquals("Nano event", result.draft.title)
        assertEquals(1, nano.calls)
        assertEquals(0, liteRt.calls)
    }

    @Test
    fun `no model reports isNoModel true without calling extractors`() = runTest {
        for (status in listOf(
            ModelStatus.None,
            ModelStatus.LiteRtNotDownloaded,
            ModelStatus.NanoDownloadable,
            ModelStatus.NanoDownloading,
            ModelStatus.NanoUnavailable,
            ModelStatus.Downloading(10L, 100L),
        )) {
            val nano = FakeExtractor("nano", ExtractionResult.Success(nanoDraft))
            val liteRt = FakeExtractor("litertlm", ExtractionResult.Success(liteRtDraft))
            val router = ExtractorRouter(nano, liteRt, FakeCatalog(status))

            val result = router.extract("prompt")
            assertIs<ExtractionResult.Failure>(result)
            assertEquals(true, result.isNoModel)
            assertEquals(0, nano.calls)
            assertEquals(0, liteRt.calls)
        }
    }

    @Test
    fun `unsupported reports not no-model without calling extractors`() = runTest {
        val nano = FakeExtractor("nano", ExtractionResult.Success(nanoDraft))
        val liteRt = FakeExtractor("litertlm", ExtractionResult.Success(liteRtDraft))
        val router = ExtractorRouter(nano, liteRt, FakeCatalog(ModelStatus.Unsupported))

        val result = router.extract("prompt")
        assertIs<ExtractionResult.Failure>(result)
        assertEquals(false, result.isNoModel)
        assertEquals(0, nano.calls)
        assertEquals(0, liteRt.calls)
    }

    @Test
    fun `user-selected downloaded model is honored even when nano is available`() = runTest {
        val nano = FakeExtractor("nano", ExtractionResult.Success(nanoDraft))
        val liteRt = FakeExtractor("litertlm", ExtractionResult.Success(liteRtDraft))
        val catalog = FakeCatalog(
            status = ModelStatus.LiteRtReady("/data/qwen3-0.6b-int4.litertlm", "qwen3-0.6b-int4", "Qwen3 0.6B INT4"),
            nanoStatusOverride = ModelStatus.NanoAvailable(),
        )
        val router = ExtractorRouter(nano, liteRt, catalog)

        val result = router.extract("prompt")
        assertIs<ExtractionResult.Success>(result)
        assertEquals("LiteRT event", result.draft.title)
        assertEquals(0, nano.calls)
        assertEquals(1, liteRt.calls)
    }

    @Test
    fun `user-selected liteRT runtime failure falls back to available nano`() = runTest {
        val nano = FakeExtractor("nano", ExtractionResult.Success(nanoDraft))
        val liteRt = FakeExtractor("litertlm", ExtractionResult.Failure("litert boom", isNoModel = false))
        val catalog = FakeCatalog(
            status = ModelStatus.LiteRtReady("/data/qwen3-0.6b-int4.litertlm", "qwen3-0.6b-int4", "Qwen3 0.6B INT4"),
            nanoStatusOverride = ModelStatus.NanoAvailable(),
        )
        val router = ExtractorRouter(nano, liteRt, catalog)

        val result = router.extract("prompt")
        assertIs<ExtractionResult.Success>(result)
        assertEquals("Nano event", result.draft.title)
        assertEquals(1, liteRt.calls)
        assertEquals(1, nano.calls)
    }
}
