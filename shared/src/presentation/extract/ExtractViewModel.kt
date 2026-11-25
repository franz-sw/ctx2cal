package com.example.ctx2cal.presentation.extract

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ctx2cal.deviceTimeZoneId
import com.example.ctx2cal.domain.extraction.EventJsonParser
import com.example.ctx2cal.domain.model.CalendarEventDraft
import com.example.ctx2cal.domain.model.ExtractionResult
import com.example.ctx2cal.domain.ports.CalendarSharer
import com.example.ctx2cal.domain.ports.EventExtractor
import com.example.ctx2cal.domain.ports.IcsExporter
import com.example.ctx2cal.todayIso
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ExtractUiState {
    data class Input(val text: String) : ExtractUiState
    data object Extracting : ExtractUiState
    data class Review(val draft: CalendarEventDraft, val inputText: String, val notice: String? = null) : ExtractUiState
    data class Error(val message: String, val isNoModel: Boolean, val inputText: String) : ExtractUiState
}

class ExtractViewModel(
    private val extractor: EventExtractor,
    private val sharer: CalendarSharer,
    private val exporter: IcsExporter,
) : ViewModel() {

    private val savedState = SavedStateHandle()

    private val _state = MutableStateFlow<ExtractUiState>(ExtractUiState.Input(savedText()))
    val state: StateFlow<ExtractUiState> = _state.asStateFlow()

    private fun savedText(): String = savedState["inputText"] ?: ""

    fun onTextChange(text: String) {
        savedState["inputText"] = text
        if (_state.value is ExtractUiState.Input) _state.value = ExtractUiState.Input(text)
        if (_state.value is ExtractUiState.Error) {
            _state.value = ExtractUiState.Input(text)
        }
    }

    fun onExtract() {
        val current = _state.value
        val text = when (current) {
            is ExtractUiState.Input -> current.text
            is ExtractUiState.Error -> current.inputText
            is ExtractUiState.Review -> current.inputText
            else -> savedText()
        }
        if (text.isBlank()) return
        _state.value = ExtractUiState.Extracting
        viewModelScope.launch {
            val prompt = EventJsonParser.buildPrompt(text, todayIso(), deviceTimeZoneId())
            when (val result = extractor.extract(prompt)) {
                is ExtractionResult.Success -> _state.value = ExtractUiState.Review(result.draft, text)
                is ExtractionResult.NoEvent -> _state.value = ExtractUiState.Input(text)
                is ExtractionResult.Failure -> _state.value =
                    ExtractUiState.Error(result.reason, result.isNoModel, text)
            }
        }
    }

    fun onDraftChanged(draft: CalendarEventDraft) {
        val current = _state.value
        if (current is ExtractUiState.Review) _state.value = current.copy(draft = draft)
    }

    fun onShare() {
        val draft = (_state.value as? ExtractUiState.Review)?.draft ?: return
        sharer.share(draft)
    }

    fun onDownload() {
        val current = _state.value as? ExtractUiState.Review ?: return
        viewModelScope.launch {
            val name = exporter.export(current.draft)
            _state.value = current.copy(
                notice = if (name != null) "Saved: $name" else "Download failed"
            )
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
}
