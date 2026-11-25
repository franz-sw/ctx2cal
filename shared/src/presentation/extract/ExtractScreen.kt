package com.example.ctx2cal.presentation.extract

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import com.example.ctx2cal.domain.model.CalendarEventDraft

@Composable
fun ExtractScreen(
    state: ExtractUiState,
    onTextChange: (String) -> Unit,
    onExtract: () -> Unit,
    onDraftChanged: (CalendarEventDraft) -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onBackToInput: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        when (state) {
            is ExtractUiState.Input -> {
                Text("Paste text containing one event")
                OutlinedTextField(
                    value = state.text,
                    onValueChange = onTextChange,
                    label = { Text("Text") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onExtract, enabled = state.text.isNotBlank()) { Text("Extract event") }
            }
            is ExtractUiState.Extracting -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("Extracting on-device…")
            }
            is ExtractUiState.Review -> {
                Text("Review & edit")
                EventForm(draft = state.draft, onDraftChanged = onDraftChanged)
                state.notice?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it)
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(onClick = onShare) { Text("Share to calendar") }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onDownload) { Text("Download .ics") }
                }
                TextButton(onClick = onBackToInput) { Text("New text") }
            }
            is ExtractUiState.Error -> {
                Text(state.message)
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(onClick = onExtract) { Text("Retry") }
                    if (state.isNoModel) TextButton(onClick = onOpenSetup) { Text("Open Setup") }
                    TextButton(onClick = onBackToInput) { Text("Back") }
                }
            }
        }
    }
}

@Composable
fun EventForm(draft: CalendarEventDraft, onDraftChanged: (CalendarEventDraft) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = draft.title,
            onValueChange = { onDraftChanged(draft.copy(title = it)) },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.startIso,
            onValueChange = { onDraftChanged(draft.copy(startIso = it)) },
            label = { Text("Start (ISO: 2026-11-25T10:00:00)") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.endIso,
            onValueChange = { onDraftChanged(draft.copy(endIso = it)) },
            label = { Text("End (ISO)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            Checkbox(checked = draft.allDay, onCheckedChange = { onDraftChanged(draft.copy(allDay = it)) })
            Text("All day")
        }
        OutlinedTextField(
            value = draft.location,
            onValueChange = { onDraftChanged(draft.copy(location = it)) },
            label = { Text("Location") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.description,
            onValueChange = { onDraftChanged(draft.copy(description = it)) },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
        )
        draft.validationError()?.let { Text(it) }
    }
}
