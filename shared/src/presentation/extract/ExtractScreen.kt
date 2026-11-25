package de.franzsw.ctx2cal.presentation.extract

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.franzsw.ctx2cal.core.presentation.design_system.components.AppActionButton
import de.franzsw.ctx2cal.core.presentation.design_system.components.AppOutlinedActionButton
import de.franzsw.ctx2cal.core.presentation.design_system.components.CustomElevatedCard
import de.franzsw.ctx2cal.core.presentation.design_system.components.DefaultLoadingScreen
import de.franzsw.ctx2cal.core.presentation.design_system.components.ErrorCard
import de.franzsw.ctx2cal.core.presentation.design_system.components.HintCard
import de.franzsw.ctx2cal.core.presentation.design_system.components.LCHeadline
import de.franzsw.ctx2cal.domain.model.CalendarEventDraft

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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        when (state) {
            is ExtractUiState.Input -> {
                LCHeadline(name = "Extract event")
                Text(
                    "Paste text containing one event",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.text,
                    onValueChange = onTextChange,
                    label = { Text("Text") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6,
                )
                Spacer(Modifier.height(8.dp))
                HintCard(hintTitle = "On-device") {
                    Text(
                        "Extraction runs entirely on-device. Nothing you paste leaves this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(8.dp))
                AppActionButton(
                    text = "Extract event",
                    isLoading = false,
                    enabled = state.text.isNotBlank(),
                    onClick = onExtract,
                )
            }
            is ExtractUiState.Extracting -> {
                LCHeadline(name = "Extracting")
                DefaultLoadingScreen()
                Spacer(Modifier.height(8.dp))
                Text(
                    "Extracting on-device…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            is ExtractUiState.Review -> {
                LCHeadline(name = "Review & edit")
                CustomElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        EventForm(draft = state.draft, onDraftChanged = onDraftChanged)
                    }
                }
                state.notice?.let {
                    Spacer(Modifier.height(8.dp))
                    HintCard(hintTitle = "Notice") {
                        Text(it)
                    }
                }
                Spacer(Modifier.height(8.dp))
                AppActionButton(
                    text = "Share to calendar",
                    isLoading = false,
                    onClick = onShare,
                )
                Spacer(Modifier.height(8.dp))
                AppOutlinedActionButton(
                    text = "Download .ics",
                    onClick = onDownload,
                )
                TextButton(onClick = onBackToInput) { Text("New text") }
            }
            is ExtractUiState.Error -> {
                LCHeadline(name = "Something went wrong")
                ErrorCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        state.message,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(8.dp))
                AppActionButton(
                    text = "Retry",
                    isLoading = false,
                    onClick = onExtract,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
        draft.validationError()?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
