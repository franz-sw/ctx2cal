package de.franzsw.ctx2cal.test_playground.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.franzsw.ctx2cal.core.domain.ports.ModelTester
import de.franzsw.ctx2cal.core.domain.ports.RawPromptResult
import de.franzsw.ctx2cal.core.domain.repository.SettingsRepository
import de.franzsw.ctx2cal.extract.domain.EventJsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class ModelTestUiState(
    val modelId: String,
    val modelName: String,
    val systemPrompt: String,
    val userPrompt: String = "",
    val isRunning: Boolean = false,
    val output: String? = null,
    val durationMs: Long? = null,
    val error: String? = null,
)

class ModelTestViewModel(
    private val modelId: String,
    private val modelName: String,
    private val modelTester: ModelTester,
    private val settings: SettingsRepository,
    private val executionTimeoutMs: Long = DEFAULT_EXECUTION_TIMEOUT_MS,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ModelTestUiState(
            modelId = modelId,
            modelName = modelName,
            systemPrompt = activeSetupSystemPrompt(),
        )
    )
    val state: StateFlow<ModelTestUiState> = _state.asStateFlow()

    private var runJob: Job? = null
    private var runGeneration: Long = 0L

    private fun activeSetupSystemPrompt(): String =
        settings.customSystemPrompt()?.takeIf { it.isNotBlank() }
            ?: EventJsonParser.buildSystemInstruction()

    fun onSystemPromptChange(prompt: String) {
        _state.update { it.copy(systemPrompt = prompt) }
    }

    fun onResetSystemPrompt() {
        _state.update { it.copy(systemPrompt = activeSetupSystemPrompt()) }
    }

    fun onUserPromptChange(prompt: String) {
        _state.update { it.copy(userPrompt = prompt) }
    }

    fun onRun() {
        if (runJob?.isActive == true) return
        val current = _state.value
        if (current.userPrompt.isBlank()) return

        _state.update {
            it.copy(
                isRunning = true,
                output = null,
                durationMs = null,
                error = null,
            )
        }
        val generation = ++runGeneration
        runJob = viewModelScope.launch {
            try {
                val result = withTimeout(executionTimeoutMs) {
                    modelTester.runPrompt(
                        modelId = modelId,
                        systemPrompt = current.systemPrompt,
                        userPrompt = current.userPrompt,
                    )
                }
                if (generation != runGeneration) return@launch
                when (result) {
                    is RawPromptResult.Success -> _state.update {
                        it.copy(
                            isRunning = false,
                            output = result.output,
                            durationMs = result.durationMs,
                            error = null,
                        )
                    }
                    is RawPromptResult.Failure -> _state.update {
                        it.copy(
                            isRunning = false,
                            output = null,
                            durationMs = null,
                            error = result.reason,
                        )
                    }
                }
            } catch (_: TimeoutCancellationException) {
                if (generation == runGeneration) {
                    _state.update {
                        it.copy(
                            isRunning = false,
                            output = null,
                            durationMs = null,
                            error = "Model test timed out after ${executionTimeoutMs / 1000}s. Please retry.",
                        )
                    }
                }
            } catch (e: CancellationException) {
                if (generation == runGeneration) {
                    _state.update { it.copy(isRunning = false) }
                }
                throw e
            } catch (e: Exception) {
                if (generation == runGeneration) {
                    _state.update {
                        it.copy(
                            isRunning = false,
                            output = null,
                            durationMs = null,
                            error = e.message?.takeIf { msg -> msg.isNotBlank() } ?: "Model test failed unexpectedly.",
                        )
                    }
                }
            }
        }
    }

    fun onCancel() {
        runGeneration++
        runJob?.cancel()
        runJob = null
        _state.update { it.copy(isRunning = false) }
    }

    companion object {
        const val DEFAULT_EXECUTION_TIMEOUT_MS = 65_000L
    }
}
