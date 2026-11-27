package de.franzsw.ctx2cal

import androidx.lifecycle.SavedStateHandle
import de.franzsw.ctx2cal.core.domain.model.CalendarEventDraft
import de.franzsw.ctx2cal.core.domain.model.ExtractionResult
import de.franzsw.ctx2cal.core.domain.model.ModelCandidate
import de.franzsw.ctx2cal.core.domain.model.ModelStatus
import de.franzsw.ctx2cal.core.domain.ports.CalendarSharer
import de.franzsw.ctx2cal.core.domain.ports.EventExtractor
import de.franzsw.ctx2cal.core.domain.ports.IcsExporter
import de.franzsw.ctx2cal.core.domain.ports.ModelCatalog
import de.franzsw.ctx2cal.core.domain.repository.SettingsRepository
import de.franzsw.ctx2cal.extract.domain.EventJsonParser
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
        assertEquals(false, review.isPartialExtraction)
    }

    @Test
    fun `partial extraction reaches Review with isPartialExtraction true and allows user to complete draft`() =
        runTest(dispatcher) {
            val partialDraft = CalendarEventDraft(
                title = "Team-Abendessen",
                startIso = "2026-11-27",
                endIso = "",
                allDay = false,
                location = "Pizzeria Roma",
            )
            val sharer = FakeSharer()
            val raw = """{"title":"Team-Abendessen","start":"2026-11-27","end":"","location":"Pizzeria Roma"}"""
            val vm = vm(FakeExtractor(ExtractionResult.Success(partialDraft, rawOutput = raw)), sharer = sharer)
            vm.onTextChange("Freitag Team-Abendessen in der Pizzeria Roma")
            vm.onExtract()
            advanceUntilIdle()

            var review = assertIs<ExtractUiState.Review>(vm.state.value)
            assertEquals(true, review.isPartialExtraction)
            assertEquals(false, review.draft.isValid)
            assertEquals("Team-Abendessen", review.draft.title)
            assertEquals("Pizzeria Roma", review.draft.location)
            assertEquals(raw, review.rawOutput)

            // Cannot share until missing fields are filled
            vm.onShare()
            assertEquals(0, sharer.shareCalls)

            // User completes start/end times and shares
            vm.onDraftChanged(
                review.draft.copy(
                    startIso = "2026-11-27T19:00:00",
                    endIso = "2026-11-27T21:00:00",
                )
            )
            review = assertIs<ExtractUiState.Review>(vm.state.value)
            assertEquals(true, review.draft.isValid)
            assertEquals(raw, review.rawOutput)
            vm.onShare()
            assertEquals(1, sharer.shareCalls)
        }

    @Test
    fun `complete extraction carries rawOutput without marking partial`() = runTest(dispatcher) {
        val raw = """{"title":"Dentist","start":"2026-11-25T10:00:00","end":"2026-11-25T11:00:00"}"""
        val vm = vm(ExtractionResult.Success(draft, rawOutput = raw))
        vm.onTextChange("Dentist at ten")
        vm.onExtract()
        advanceUntilIdle()
        val review = assertIs<ExtractUiState.Review>(vm.state.value)
        assertEquals(false, review.isPartialExtraction)
        assertEquals(raw, review.rawOutput)
    }

    @Test
    fun `partial extraction without raw output leaves Review rawOutput null`() = runTest(dispatcher) {
        val partialDraft = CalendarEventDraft(
            title = "Team-Abendessen",
            startIso = "2026-11-27",
            endIso = "",
            allDay = false,
        )
        val vm = vm(ExtractionResult.Success(partialDraft))
        vm.onTextChange("Team-Abendessen Freitag")
        vm.onExtract()
        advanceUntilIdle()
        val review = assertIs<ExtractUiState.Review>(vm.state.value)
        assertEquals(true, review.isPartialExtraction)
        assertNull(review.rawOutput)
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
    fun `failure surfaces Error with raw model output`() = runTest(dispatcher) {
        val raw = """{title: dentist, broken}"""
        val vm = vm(ExtractionResult.Failure("Couldn't extract a valid event.", rawOutput = raw))
        vm.onTextChange("Dentist at ten")
        vm.onExtract()
        advanceUntilIdle()
        val err = assertIs<ExtractUiState.Error>(vm.state.value)
        assertEquals("Couldn't extract a valid event.", err.message)
        assertEquals(raw, err.rawOutput)
        assertEquals(false, err.isNoModel)
        assertEquals("Dentist at ten", err.inputText)
    }

    @Test
    fun `failure without raw output leaves Error rawOutput null`() = runTest(dispatcher) {
        val vm = vm(ExtractionResult.Failure("Boom", isNoModel = false))
        vm.onTextChange("Dentist at ten")
        vm.onExtract()
        advanceUntilIdle()
        val err = assertIs<ExtractUiState.Error>(vm.state.value)
        assertNull(err.rawOutput)
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
        for (readyStatus in listOf(ModelStatus.LiteRtReady("/data/model.litertlm"), ModelStatus.NanoAvailable())) {
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

    @Test
    fun `extraction timeout surfaces recoverable Error without retrying`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val extractor = FakeExtractor(ExtractionResult.Success(draft), gate = gate)
        val vm = ExtractViewModel(
            extractor = extractor,
            sharer = FakeSharer(),
            exporter = FakeExporter(),
            catalog = null,
            extractionTimeoutMs = 1_000L,
        )
        vm.onTextChange("Dentist at ten")
        vm.onExtract()
        advanceUntilIdle()
        val err = assertIs<ExtractUiState.Error>(vm.state.value)
        assertEquals(false, err.isNoModel)
        assertEquals(1, extractor.calls)
        assertEquals("Dentist at ten", err.inputText)
    }

    @Test
    fun `onReceiveSharedText trims text persists to SavedStateHandle and auto-extracts`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val extractor = FakeExtractor(ExtractionResult.Success(draft), gate = gate)
        val savedState = SavedStateHandle()
        val vm = ExtractViewModel(
            extractor = extractor,
            sharer = FakeSharer(),
            exporter = FakeExporter(),
            catalog = null,
            savedState = savedState,
        )

        vm.onReceiveSharedText("  Dentist at ten  ")
        assertEquals("Dentist at ten", savedState["inputText"])
        assertIs<ExtractUiState.Extracting>(vm.state.value)

        runCurrent()
        assertEquals(1, extractor.calls)
        assertIs<ExtractUiState.Extracting>(vm.state.value)

        gate.complete(Unit)
        advanceUntilIdle()
        val review = assertIs<ExtractUiState.Review>(vm.state.value)
        assertEquals("Dentist at ten", review.inputText)
    }

    @Test
    fun `onReceiveSharedText ignores blank text without altering existing state`() = runTest(dispatcher) {
        val extractor = FakeExtractor(ExtractionResult.Success(draft))
        val savedState = SavedStateHandle()
        val vm = ExtractViewModel(
            extractor = extractor,
            sharer = FakeSharer(),
            exporter = FakeExporter(),
            catalog = null,
            savedState = savedState,
        )
        vm.onTextChange("Existing draft text")

        vm.onReceiveSharedText("   \n\t  ")
        vm.onReceiveSharedText("")
        advanceUntilIdle()

        val input = assertIs<ExtractUiState.Input>(vm.state.value)
        assertEquals("Existing draft text", input.text)
        assertEquals("Existing draft text", savedState["inputText"])
        assertEquals(0, extractor.calls)
    }

    @Test
    fun `onReceiveSharedText cancels in-flight extraction and extracts new text`() = runTest(dispatcher) {
        val firstGate = CompletableDeferred<Unit>()
        val secondGate = CompletableDeferred<Unit>()
        val secondDraft = draft.copy(title = "Lunch with Alex")
        var callCount = 0
        val extractor = object : EventExtractor {
            override val backendName = "fake"
            val prompts = mutableListOf<String>()

            override suspend fun extract(prompt: String): ExtractionResult {
                prompts += prompt
                return if (++callCount == 1) {
                    firstGate.await()
                    ExtractionResult.Success(draft)
                } else {
                    secondGate.await()
                    ExtractionResult.Success(secondDraft)
                }
            }
        }
        val savedState = SavedStateHandle()
        val vm = ExtractViewModel(
            extractor = extractor,
            sharer = FakeSharer(),
            exporter = FakeExporter(),
            catalog = null,
            savedState = savedState,
        )

        vm.onTextChange("Old dentist text")
        vm.onExtract()
        runCurrent()
        assertIs<ExtractUiState.Extracting>(vm.state.value)
        assertEquals(1, extractor.prompts.size)

        vm.onReceiveSharedText("  Lunch with Alex at noon  ")
        assertEquals("Lunch with Alex at noon", savedState["inputText"])
        assertIs<ExtractUiState.Extracting>(vm.state.value)

        // Let the cancelled first job process CancellationException and the second job start.
        runCurrent()
        assertIs<ExtractUiState.Extracting>(vm.state.value)
        assertEquals(2, extractor.prompts.size)

        firstGate.complete(Unit)
        runCurrent()
        assertIs<ExtractUiState.Extracting>(vm.state.value)

        secondGate.complete(Unit)
        advanceUntilIdle()
        val review = assertIs<ExtractUiState.Review>(vm.state.value)
        assertEquals("Lunch with Alex", review.draft.title)
        assertEquals("Lunch with Alex at noon", review.inputText)
    }

    @Test
    fun `onReceiveSharedText preserves text when no model is available`() = runTest(dispatcher) {
        val extractor = FakeExtractor(ExtractionResult.Failure("No model", isNoModel = true))
        val vm = vm(extractor)

        vm.onReceiveSharedText("  Team sync tomorrow at 10  ")
        advanceUntilIdle()

        val err = assertIs<ExtractUiState.Error>(vm.state.value)
        assertEquals(true, err.isNoModel)
        assertEquals("Team sync tomorrow at 10", err.inputText)
    }

    @Test
    fun `onExtract uses custom system prompt when non-blank and falls back to default when blank or null`() =
        runTest(dispatcher) {
            val capturedPrompts = mutableListOf<String>()
            val extractor = object : EventExtractor {
                override val backendName = "fake"
                override suspend fun extract(prompt: String): ExtractionResult {
                    capturedPrompts += prompt
                    return ExtractionResult.Success(draft)
                }
            }
            var storedCustom: String? = "My custom system prompt"
            val settings = object : SettingsRepository {
                override fun preferredBackend(): String? = null
                override fun setPreferredBackend(name: String?) = Unit
                override fun modelPath(): String? = null
                override fun setModelPath(path: String?) = Unit
                override fun customSystemPrompt(): String? = storedCustom
                override fun setCustomSystemPrompt(prompt: String?) {
                    storedCustom = prompt
                }
            }

            val vm = ExtractViewModel(
                extractor = extractor,
                sharer = FakeSharer(),
                exporter = FakeExporter(),
                settings = settings,
            )

            vm.onTextChange("Dentist at ten")
            vm.onExtract()
            advanceUntilIdle()
            assertEquals(1, capturedPrompts.size)
            kotlin.test.assertTrue(capturedPrompts[0].startsWith("My custom system prompt\n\n"))

            // Blank custom prompt falls back to default system instruction
            storedCustom = "   "
            vm.onBackToInput()
            vm.onExtract()
            advanceUntilIdle()
            assertEquals(2, capturedPrompts.size)
            kotlin.test.assertTrue(capturedPrompts[1].startsWith(EventJsonParser.buildSystemInstruction() + "\n\n"))
        }
}
