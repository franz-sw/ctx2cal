package de.franzsw.ctx2cal

import de.franzsw.ctx2cal.core.domain.model.CalendarEventDraft
import de.franzsw.ctx2cal.core.domain.model.ExtractionResult
import de.franzsw.ctx2cal.core.domain.model.ModelCandidate
import de.franzsw.ctx2cal.core.domain.model.ModelStatus
import de.franzsw.ctx2cal.core.domain.ports.CalendarSharer
import de.franzsw.ctx2cal.core.domain.ports.EventExtractor
import de.franzsw.ctx2cal.core.domain.ports.IcsExporter
import de.franzsw.ctx2cal.core.domain.ports.ModelCatalog
import de.franzsw.ctx2cal.extract.presentation.ExtractUiState
import de.franzsw.ctx2cal.extract.presentation.ExtractViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ExtractViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeExtractor(
        var result: ExtractionResult,
        var gate: CompletableDeferred<Unit>? = null,
    ) : EventExtractor {
        override val backendName = "fake"
        var calls: Int = 0

        override suspend fun extract(prompt: String): ExtractionResult {
            calls++
            gate?.await()
            return result
        }
    }

    private class FakeSharer(var shareResult: String? = null) : CalendarSharer {
        var shareCalls: Int = 0

        override fun share(draft: CalendarEventDraft) {
            shareCalls++
        }

        override fun shareWithResult(draft: CalendarEventDraft): String? {
            shareCalls++
            return shareResult
        }
    }

    private class FakeExporter(
        var exportResult: String? = "event.ics",
        var gate: CompletableDeferred<Unit>? = null,
    ) : IcsExporter {
        var exportCalls: Int = 0

        override suspend fun export(draft: CalendarEventDraft): String? {
            exportCalls++
            gate?.await()
            return exportResult
        }
    }

    private class FakeCatalog(var status: ModelStatus) : ModelCatalog {
        var statusCalls: Int = 0

        override suspend fun currentStatus(): ModelStatus {
            statusCalls++
            return status
        }

        override suspend fun availableCandidate(): ModelCandidate? = null
    }

    private fun vm(
        extractor: FakeExtractor,
        sharer: FakeSharer = FakeSharer(),
        exporter: FakeExporter = FakeExporter(),
        catalog: FakeCatalog? = null,
    ): ExtractViewModel = ExtractViewModel(
        extractor = extractor,
        sharer = sharer,
        exporter = exporter,
        catalog = catalog,
    )

    private fun vm(result: ExtractionResult): ExtractViewModel = vm(FakeExtractor(result))

    private val draft = CalendarEventDraft(
        title = "Dentist", startIso = "2026-11-25T10:00:00", endIso = "2026-11-25T11:00:00", allDay = false
    )

    @Test
    fun `successful extraction reaches Review state`() = runTest(dispatcher) {
        val vm = vm(ExtractionResult.Success(draft))
        vm.onTextChange("Dentist at ten")
        vm.onExtract()
        advanceUntilIdle()
        val review = vm.state.value as ExtractUiState.Review
        assertIs<ExtractUiState.Review>(review)
        assertEquals("Dentist", review.draft.title)
        assertEquals("Dentist at ten", review.inputText)
    }

    @Test
    fun `no-event extraction stays on Input with notice and retains text`() = runTest(dispatcher) {
        val vm = vm(ExtractionResult.NoEvent)
        vm.onTextChange("just chatting")
        vm.onExtract()
        advanceUntilIdle()
        val input = vm.state.value as ExtractUiState.Input
        assertIs<ExtractUiState.Input>(input)
        assertEquals("just chatting", input.text)
        assertNotNull(input.notice)
        assertEquals(
            "No calendar event was found in this text. Try including a clear date or time.",
            input.notice,
        )
    }

    @Test
    fun `no-model failure surfaces Error with isNoModel`() = runTest(dispatcher) {
        val vm = vm(ExtractionResult.Failure("No model", isNoModel = true))
        vm.onTextChange("Dentist at ten")
        vm.onExtract()
        advanceUntilIdle()
        val err = vm.state.value as ExtractUiState.Error
        assertIs<ExtractUiState.Error>(err)
        check(err.isNoModel)
    }

    @Test
    fun `cancel extract returns to Input with text intact`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val extractor = FakeExtractor(ExtractionResult.Success(draft), gate = gate)
        val vm = vm(extractor)
        vm.onTextChange("Dentist at ten")
        vm.onExtract()
        runCurrent()
        assertIs<ExtractUiState.Extracting>(vm.state.value)
        vm.onCancelExtract()
        val input = vm.state.value as ExtractUiState.Input
        assertIs<ExtractUiState.Input>(input)
        assertEquals("Dentist at ten", input.text)
        assertEquals(1, extractor.calls)
        gate.complete(Unit)
        advanceUntilIdle()
        // Still Input after start-up completes; extraction was cancelled, not failed.
        assertIs<ExtractUiState.Input>(vm.state.value)
    }

    @Test
    fun `double-tap extract is single-flight`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val extractor = FakeExtractor(ExtractionResult.Success(draft), gate = gate)
        val vm = vm(extractor)
        vm.onTextChange("Dentist at ten")
        vm.onExtract()
        runCurrent()
        vm.onExtract()
        runCurrent()
        assertIs<ExtractUiState.Extracting>(vm.state.value)
        assertEquals(1, extractor.calls)
        gate.complete(Unit)
        advanceUntilIdle()
        assertIs<ExtractUiState.Review>(vm.state.value)
        assertEquals(1, extractor.calls)
    }

    @Test
    fun `draft edit clears notice`() = runTest(dispatcher) {
        val extractor = FakeExtractor(ExtractionResult.Success(draft))
        val sharer = FakeSharer(shareResult = null)
        val vm = vm(extractor, sharer = sharer)
        vm.onTextChange("Dentist at ten")
        vm.onExtract()
        advanceUntilIdle()
        // Produce a notice via an invalid draft share.
        val invalid = draft.copy(title = "")
        vm.onDraftChanged(invalid)
        vm.onShare()
        var review = vm.state.value as ExtractUiState.Review
        assertIs<ExtractUiState.Review>(review)
        assertNotNull(review.notice)
        // Editing the draft clears the notice.
        vm.onDraftChanged(draft)
        review = vm.state.value as ExtractUiState.Review
        assertIs<ExtractUiState.Review>(review)
        assertNull(review.notice)
        assertEquals(false, review.noticeIsError)
        assertEquals("Dentist", review.draft.title)
        assertEquals(0, sharer.shareCalls)
    }

    @Test
    fun `dismiss notice clears Input and Review notices`() = runTest(dispatcher) {
        val vmNoEvent = vm(ExtractionResult.NoEvent)
        vmNoEvent.onTextChange("just chatting")
        vmNoEvent.onExtract()
        advanceUntilIdle()
        var input = vmNoEvent.state.value as ExtractUiState.Input
        assertNotNull(input.notice)
        vmNoEvent.onDismissNotice()
        input = vmNoEvent.state.value as ExtractUiState.Input
        assertNull(input.notice)

        val vmReview = vm(ExtractionResult.Success(draft))
        vmReview.onTextChange("Dentist at ten")
        vmReview.onExtract()
        advanceUntilIdle()
        vmReview.onDraftChanged(draft.copy(title = ""))
        vmReview.onShare()
        var review = vmReview.state.value as ExtractUiState.Review
        assertNotNull(review.notice)
        vmReview.onDismissNotice()
        review = vmReview.state.value as ExtractUiState.Review
        assertNull(review.notice)
        assertEquals(false, review.noticeIsError)
    }

    @Test
    fun `onShare blocks invalid draft without calling sharer`() = runTest(dispatcher) {
        val invalid = draft.copy(title = "")
        val extractor = FakeExtractor(ExtractionResult.Success(invalid))
        val sharer = FakeSharer(shareResult = null)
        val vm = vm(extractor, sharer = sharer)
        vm.onTextChange("text")
        vm.onExtract()
        advanceUntilIdle()
        vm.onShare()
        val review = vm.state.value as ExtractUiState.Review
        assertIs<ExtractUiState.Review>(review)
        assertEquals(0, sharer.shareCalls)
        assertNotNull(review.notice)
        assertEquals(true, review.noticeIsError)
    }

    @Test
    fun `onShare surfaces sharer error as notice`() = runTest(dispatcher) {
        val extractor = FakeExtractor(ExtractionResult.Success(draft))
        val sharer = FakeSharer(shareResult = "Calendar app not available.")
        val vm = vm(extractor, sharer = sharer)
        vm.onTextChange("text")
        vm.onExtract()
        advanceUntilIdle()
        vm.onShare()
        val review = vm.state.value as ExtractUiState.Review
        assertEquals(1, sharer.shareCalls)
        assertEquals("Calendar app not available.", review.notice)
        assertEquals(true, review.noticeIsError)
    }

    @Test
    fun `onDownload blocks invalid draft without calling exporter`() = runTest(dispatcher) {
        val invalid = draft.copy(startIso = "2026-11-25T11:00:00", endIso = "2026-11-25T10:00:00")
        val extractor = FakeExtractor(ExtractionResult.Success(invalid))
        val exporter = FakeExporter(exportResult = "event.ics")
        val vm = vm(extractor, exporter = exporter)
        vm.onTextChange("text")
        vm.onExtract()
        advanceUntilIdle()
        vm.onDownload()
        val review = vm.state.value as ExtractUiState.Review
        assertIs<ExtractUiState.Review>(review)
        assertEquals(0, exporter.exportCalls)
        assertEquals(false, review.isExporting)
        assertNotNull(review.notice)
        assertEquals(true, review.noticeIsError)
    }

    @Test
    fun `onDownload is single-flight`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val extractor = FakeExtractor(ExtractionResult.Success(draft))
        val exporter = FakeExporter(exportResult = "event.ics", gate = gate)
        val vm = vm(extractor, exporter = exporter)
        vm.onTextChange("text")
        vm.onExtract()
        advanceUntilIdle()
        vm.onDownload()
        runCurrent()
        vm.onDownload()
        runCurrent()
        assertEquals(1, exporter.exportCalls)
        var review = vm.state.value as ExtractUiState.Review
        assertEquals(true, review.isExporting)
        gate.complete(Unit)
        advanceUntilIdle()
        review = vm.state.value as ExtractUiState.Review
        assertEquals(false, review.isExporting)
        assertEquals("Saved to Downloads: event.ics", review.notice)
        assertEquals(1, exporter.exportCalls)
    }

    @Test
    fun `stale isNoModel error clears on refresh when model becomes ready`() = runTest(dispatcher) {
        for (readyStatus in listOf(ModelStatus.LiteRtReady("/data/model.litertlm"), ModelStatus.NanoAvailable)) {
            val catalog = FakeCatalog(ModelStatus.None)
            val extractor = FakeExtractor(ExtractionResult.Failure("No model", isNoModel = true))
            val vm = vm(extractor, catalog = catalog)
            advanceUntilIdle()
            vm.onTextChange("Dentist at ten")
            vm.onExtract()
            advanceUntilIdle()
            assertIs<ExtractUiState.Error>(vm.state.value)
            catalog.status = readyStatus
            vm.refreshModelStatus()
            advanceUntilIdle()
            val input = vm.state.value as ExtractUiState.Input
            assertIs<ExtractUiState.Input>(input)
            assertEquals("Dentist at ten", input.text)
            assertEquals(readyStatus, vm.modelStatus.value)
        }
    }

    @Test
    fun `non-model error and no-model with no ready status are not cleared`() = runTest(dispatcher) {
        val catalog = FakeCatalog(ModelStatus.None)
        val extractor = FakeExtractor(ExtractionResult.Failure("Boom", isNoModel = false))
        val vm = vm(extractor, catalog = catalog)
        advanceUntilIdle()
        vm.onTextChange("Dentist at ten")
        vm.onExtract()
        advanceUntilIdle()
        assertIs<ExtractUiState.Error>(vm.state.value)
        catalog.status = ModelStatus.LiteRtReady("/data/model.litertlm")
        vm.refreshModelStatus()
        advanceUntilIdle()
        // isNoModel=false must stay as Error even when a model becomes ready.
        assertIs<ExtractUiState.Error>(vm.state.value)

        val catalog2 = FakeCatalog(ModelStatus.None)
        val extractor2 = FakeExtractor(ExtractionResult.Failure("No model", isNoModel = true))
        val vm2 = vm(extractor2, catalog = catalog2)
        advanceUntilIdle()
        vm2.onTextChange("Dentist at ten")
        vm2.onExtract()
        advanceUntilIdle()
        assertIs<ExtractUiState.Error>(vm2.state.value)
        vm2.refreshModelStatus()
        advanceUntilIdle()
        assertIs<ExtractUiState.Error>(vm2.state.value)
    }
}
