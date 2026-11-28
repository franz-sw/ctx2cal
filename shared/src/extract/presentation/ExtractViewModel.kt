package de.franzsw.ctx2cal.extract.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.franzsw.ctx2cal.core.domain.llm.LlmTimeouts
import de.franzsw.ctx2cal.core.domain.model.CalendarEventDraft
import de.franzsw.ctx2cal.core.domain.model.ExtractionResult
import de.franzsw.ctx2cal.core.domain.model.ModelStatus
import de.franzsw.ctx2cal.core.domain.ports.CalendarSharer
import de.franzsw.ctx2cal.core.domain.ports.EventExtractor
import de.franzsw.ctx2cal.core.domain.ports.IcsExporter
import de.franzsw.ctx2cal.core.domain.ports.ModelCatalog
import de.franzsw.ctx2cal.core.domain.repository.SettingsRepository
import de.franzsw.ctx2cal.deviceTimeZoneId
import de.franzsw.ctx2cal.extract.domain.EventJsonParser
import de.franzsw.ctx2cal.todayIso
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

sealed interface ExtractUiState {
    data class Input(val text: String, val notice: String? = null) : ExtractUiState
    data object Extracting : ExtractUiState
    data class Review(
        val draft: CalendarEventDraft,
        val inputText: String,
        val notice: String? = null,
        val noticeIsError: Boolean = false,
        val isExporting: Boolean = false,
        val isPartialExtraction: Boolean = false,
        /** Raw model output that produced [draft]; surfaced for partial extraction. */
        val rawOutput: String? = null,
    ) : ExtractUiState
    data class Error(
        val message: String,
        val isNoModel: Boolean,
        val inputText: String,
        /** Last raw model output, when a failure followed model output. */
        val rawOutput: String? = null,
    ) : ExtractUiState
}

class ExtractViewModel(
    private val extractor: EventExtractor,
    private val sharer: CalendarSharer,
    private val exporter: IcsExporter,
    private val catalog: ModelCatalog? = null,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val extractionTimeoutMs: Long = DEFAULT_EXTRACTION_TIMEOUT_MS,
    private val settings: SettingsRepository? = null,
) : ViewModel() {

    private val _state = MutableStateFlow<ExtractUiState>(ExtractUiState.Input(savedText()))
    val state: StateFlow<ExtractUiState> = _state.asStateFlow()

    private val _modelStatus = MutableStateFlow<ModelStatus?>(null)
    val modelStatus: StateFlow<ModelStatus?> = _modelStatus.asStateFlow()

    private var extractJob: Job? = null
    private var extractGeneration: Long = 0L
    private var exportJob: Job? = null

    init {
        refreshModelStatus()
    }

    private fun savedText(): String = savedState[KEY_INPUT_TEXT] ?: ""

    fun refreshModelStatus() {
        val modelCatalog = catalog ?: return
        viewModelScope.launch {
            val status = try {
                modelCatalog.currentStatus()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            _modelStatus.value = status
            // If the user returned from Setup after downloading a model, clear the stale no-model error.
            val current = _state.value
            if (
                current is ExtractUiState.Error &&
                current.isNoModel &&
                (status is ModelStatus.NanoAvailable || status is ModelStatus.LiteRtReady)
            ) {
                _state.value = ExtractUiState.Input(current.inputText)
            }
        }
    }

    fun onTextChange(text: String) {
        savedState[KEY_INPUT_TEXT] = text
        val current = _state.value
        if (current is ExtractUiState.Input || current is ExtractUiState.Error) {
            _state.value = ExtractUiState.Input(text)
        }
    }

    fun onReceiveSharedText(text: String) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return
        extractJob?.cancel()
        extractJob = null
        savedState[KEY_INPUT_TEXT] = cleaned
        _state.value = ExtractUiState.Input(cleaned)
        onExtract()
    }

    fun onClearText() {
        savedState[KEY_INPUT_TEXT] = ""
        _state.value = ExtractUiState.Input("")
    }

    fun onDismissNotice() {
        _state.update { current ->
            when (current) {
                is ExtractUiState.Input -> current.copy(notice = null)
                is ExtractUiState.Review -> current.copy(notice = null, noticeIsError = false)
                else -> current
            }
        }
    }

    fun onExtract() {
        if (extractJob?.isActive == true) return
        val current = _state.value
        val text = when (current) {
            is ExtractUiState.Input -> current.text
            is ExtractUiState.Error -> current.inputText
            is ExtractUiState.Review -> current.inputText
            else -> savedText()
        }
        if (text.isBlank()) return
        savedState[KEY_INPUT_TEXT] = text
        _state.value = ExtractUiState.Extracting
        val generation = ++extractGeneration
        extractJob = viewModelScope.launch {
            try {
                val customSystemPrompt = settings?.customSystemPrompt()?.takeIf { it.isNotBlank() }
                val prompt = EventJsonParser.buildPrompt(
                    text = text,
                    todayIso = todayIso(),
                    timeZoneId = deviceTimeZoneId(),
                    systemInstruction = customSystemPrompt,
                )
                val result = withTimeout(extractionTimeoutMs) {
                    extractor.extract(prompt)
                }
                if (generation != extractGeneration) return@launch
                when (result) {
                    is ExtractionResult.Success -> _state.value = ExtractUiState.Review(
                        draft = result.draft,
                        inputText = text,
                        isPartialExtraction = !result.draft.isValid,
                        rawOutput = result.rawOutput,
                    )
                    is ExtractionResult.NoEvent -> _state.value = ExtractUiState.Input(
                        text = text,
                        notice = "No calendar event was found in this text. Try including a clear date or time.",
                    )
                    is ExtractionResult.Failure -> _state.value = ExtractUiState.Error(
                        message = result.reason,
                        isNoModel = result.isNoModel,
                        inputText = text,
                        rawOutput = result.rawOutput,
                    )
                }
            } catch (_: TimeoutCancellationException) {
                if (generation == extractGeneration) {
                    _state.value = ExtractUiState.Error(
                        message = "Extraction timed out. Try shorter text or retry.",
                        isNoModel = false,
                        inputText = text,
                    )
                }
            } catch (e: CancellationException) {
                if (generation == extractGeneration && _state.value is ExtractUiState.Extracting) {
                    _state.value = ExtractUiState.Input(text)
                }
                throw e
            } catch (e: Exception) {
                if (generation == extractGeneration) {
                    _state.value = ExtractUiState.Error(
                        message = e.message?.takeIf { it.isNotBlank() } ?: "Extraction failed unexpectedly.",
                        isNoModel = false,
                        inputText = text,
                    )
                }
            }
        }
    }

    fun onCancelExtract() {
        extractGeneration++
        extractJob?.cancel()
        extractJob = null
        _state.value = ExtractUiState.Input(savedText())
    }

    fun onDraftChanged(draft: CalendarEventDraft) {
        _state.update { current ->
            if (current is ExtractUiState.Review) {
                current.copy(draft = draft, notice = null, noticeIsError = false)
            } else {
                current
            }
        }
    }

    fun onShare() {
        val current = _state.value as? ExtractUiState.Review ?: return
        val validationError = current.draft.validationError()
        if (validationError != null) {
            _state.update { s ->
                if (s is ExtractUiState.Review) s.copy(notice = validationError, noticeIsError = true) else s
            }
            return
        }
        val error = sharer.shareWithResult(current.draft)
        if (error != null) {
            _state.update { s ->
                if (s is ExtractUiState.Review) s.copy(notice = error, noticeIsError = true) else s
            }
        }
    }

    fun onDownload() {
        if (exportJob?.isActive == true) return
        val current = _state.value as? ExtractUiState.Review ?: return
        val validationError = current.draft.validationError()
        if (validationError != null) {
            _state.update { s ->
                if (s is ExtractUiState.Review) s.copy(notice = validationError, noticeIsError = true) else s
            }
            return
        }
        val draftToExport = current.draft
        _state.update { s ->
            if (s is ExtractUiState.Review) s.copy(isExporting = true, notice = null, noticeIsError = false) else s
        }
        exportJob = viewModelScope.launch {
            val name = try {
                exporter.export(draftToExport)
            } catch (e: CancellationException) {
                _state.update { s -> if (s is ExtractUiState.Review) s.copy(isExporting = false) else s }
                throw e
            } catch (_: Exception) {
                null
            }
            _state.update { s ->
                if (s is ExtractUiState.Review) {
                    s.copy(
                        isExporting = false,
                        notice = if (name != null) "Saved to Downloads: $name" else "Could not save .ics file.",
                        noticeIsError = name == null,
                    )
                } else {
                    s
                }
            }
        }
    }

    fun onBackToInput() {
        val text = when (val s = _state.value) {
            is ExtractUiState.Review -> s.inputText
            is ExtractUiState.Error -> s.inputText
            else -> savedText()
        }
        _state.value = ExtractUiState.Input(text)
    }

    companion object {
        private const val KEY_INPUT_TEXT = "inputText"
        const val DEFAULT_EXTRACTION_TIMEOUT_MS = LlmTimeouts.EXTRACTION_TIMEOUT_MS
    }
}
