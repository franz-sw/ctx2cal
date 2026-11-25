package de.franzsw.ctx2cal

import de.franzsw.ctx2cal.domain.model.CalendarEventDraft
import de.franzsw.ctx2cal.domain.model.ExtractionResult
import de.franzsw.ctx2cal.domain.ports.CalendarSharer
import de.franzsw.ctx2cal.domain.ports.EventExtractor
import de.franzsw.ctx2cal.domain.ports.IcsExporter
import de.franzsw.ctx2cal.presentation.extract.ExtractUiState
import de.franzsw.ctx2cal.presentation.extract.ExtractViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs

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

    private fun vm(result: ExtractionResult): ExtractViewModel = ExtractViewModel(
        extractor = object : EventExtractor {
            override val backendName = "fake"
            override suspend fun extract(prompt: String): ExtractionResult = result
        },
        sharer = object : CalendarSharer {
            override fun share(draft: CalendarEventDraft) = Unit
        },
        exporter = object : IcsExporter {
            override suspend fun export(draft: CalendarEventDraft): String? = "event.ics"
        },
    )

    private val draft = CalendarEventDraft(
        title = "Dentist", startIso = "2026-11-25T10:00:00", endIso = "2026-11-25T11:00:00", allDay = false
    )

    @Test
    fun `successful extraction reaches Review state`() = runTest(dispatcher) {
        val vm = vm(ExtractionResult.Success(draft))
        vm.onTextChange("Dentist at ten")
        vm.onExtract()
        advanceUntilIdle()
        assertIs<ExtractUiState.Review>(vm.state.value)
    }

    @Test
    fun `no-event extraction stays on Input`() = runTest(dispatcher) {
        val vm = vm(ExtractionResult.NoEvent)
        vm.onTextChange("just chatting")
        vm.onExtract()
        advanceUntilIdle()
        assertIs<ExtractUiState.Input>(vm.state.value)
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
}
