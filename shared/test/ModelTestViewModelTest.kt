package de.franzsw.ctx2cal

import de.franzsw.ctx2cal.core.domain.ports.ModelTester
import de.franzsw.ctx2cal.core.domain.ports.RawPromptResult
import de.franzsw.ctx2cal.core.domain.repository.SettingsRepository
import de.franzsw.ctx2cal.extract.domain.EventJsonParser
import de.franzsw.ctx2cal.test_playground.presentation.ModelTestViewModel
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ModelTestViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeModelTester(
        var result: RawPromptResult = RawPromptResult.Success("ok", 42L),
        var gate: CompletableDeferred<Unit>? = null,
    ) : ModelTester {
        var calls = 0
        var lastModelId: String? = null
        var lastSystemPrompt: String? = null
        var lastUserPrompt: String? = null

        override suspend fun runPrompt(
            modelId: String,
            systemPrompt: String,
            userPrompt: String,
        ): RawPromptResult {
            calls++
            lastModelId = modelId
            lastSystemPrompt = systemPrompt
            lastUserPrompt = userPrompt
            gate?.await()
            return result
        }
    }

    private class FakeSettings(var customPrompt: String? = null) : SettingsRepository {
        override fun preferredBackend(): String? = null
        override fun setPreferredBackend(name: String?) = Unit
        override fun modelPath(): String? = null
        override fun setModelPath(path: String?) = Unit
        override fun customSystemPrompt(): String? = customPrompt
        override fun setCustomSystemPrompt(prompt: String?) {
            customPrompt = prompt
        }
    }

    @Test
    fun `prefills system prompt from custom setting or falls back to default`() = runTest(dispatcher) {
        val customVm = ModelTestViewModel(
            modelId = "qwen3-0.6b-int4",
            modelName = "Qwen3 0.6B INT4",
            modelTester = FakeModelTester(),
            settings = FakeSettings(customPrompt = "Custom prompt"),
        )
        assertEquals("Custom prompt", customVm.state.value.systemPrompt)
        assertEquals("qwen3-0.6b-int4", customVm.state.value.modelId)
        assertEquals("Qwen3 0.6B INT4", customVm.state.value.modelName)

        val defaultVm = ModelTestViewModel(
            modelId = "nano",
            modelName = "Gemini Nano",
            modelTester = FakeModelTester(),
            settings = FakeSettings(customPrompt = "   "),
        )
        assertEquals(EventJsonParser.buildSystemInstruction(), defaultVm.state.value.systemPrompt)
    }

    @Test
    fun `run success displays raw output and duration and ignores blank user prompt`() = runTest(dispatcher) {
        val tester = FakeModelTester(
            result = RawPromptResult.Success(output = "Raw model reply", durationMs = 123L)
        )
        val vm = ModelTestViewModel(
            modelId = "nano",
            modelName = "Gemini Nano",
            modelTester = tester,
            settings = FakeSettings(),
        )

        // Blank user prompt does not start inference.
        vm.onUserPromptChange("   ")
        vm.onRun()
        advanceUntilIdle()
        assertEquals(0, tester.calls)

        vm.onSystemPromptChange("Be concise.")
        vm.onUserPromptChange("Hello world")
        vm.onRun()
        advanceUntilIdle()

        assertEquals(1, tester.calls)
        assertEquals("nano", tester.lastModelId)
        assertEquals("Be concise.", tester.lastSystemPrompt)
        assertEquals("Hello world", tester.lastUserPrompt)
        assertFalse(vm.state.value.isRunning)
        assertEquals("Raw model reply", vm.state.value.output)
        assertEquals(123L, vm.state.value.durationMs)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `run failure surfaces error without clearing prompt inputs`() = runTest(dispatcher) {
        val tester = FakeModelTester(
            result = RawPromptResult.Failure("Model crashed")
        )
        val vm = ModelTestViewModel(
            modelId = "qwen3-1.7b-int4",
            modelName = "Qwen3 1.7B INT4",
            modelTester = tester,
            settings = FakeSettings(),
        )
        vm.onUserPromptChange("Test input")
        vm.onRun()
        advanceUntilIdle()

        assertFalse(vm.state.value.isRunning)
        assertNull(vm.state.value.output)
        assertEquals("Model crashed", vm.state.value.error)
        assertEquals("Test input", vm.state.value.userPrompt)
    }

    @Test
    fun `cancel in-flight run returns to idle without failure error`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val tester = FakeModelTester(
            result = RawPromptResult.Success("Late output", 500L),
            gate = gate,
        )
        val vm = ModelTestViewModel(
            modelId = "qwen3-0.6b-int4",
            modelName = "Qwen3 0.6B INT4",
            modelTester = tester,
            settings = FakeSettings(),
        )
        vm.onUserPromptChange("Test prompt")
        vm.onRun()
        runCurrent()
        assertTrue(vm.state.value.isRunning)

        vm.onCancel()
        assertFalse(vm.state.value.isRunning)
        assertNull(vm.state.value.error)
        assertNull(vm.state.value.output)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.state.value.isRunning)
        assertNull(vm.state.value.error)
        assertNull(vm.state.value.output)
    }

    @Test
    fun `timeout surfaces recoverable error`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val tester = FakeModelTester(gate = gate)
        val vm = ModelTestViewModel(
            modelId = "nano",
            modelName = "Gemini Nano",
            modelTester = tester,
            settings = FakeSettings(),
            executionTimeoutMs = 1_000L,
        )
        vm.onUserPromptChange("Test prompt")
        vm.onRun()
        advanceUntilIdle()

        assertFalse(vm.state.value.isRunning)
        assertNull(vm.state.value.output)
        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.error!!.contains("timed out"))
    }
}
