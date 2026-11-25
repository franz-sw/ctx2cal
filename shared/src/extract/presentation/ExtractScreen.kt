package de.franzsw.ctx2cal.extract.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.franzsw.ctx2cal.core.domain.model.CalendarEventDraft
import de.franzsw.ctx2cal.core.domain.model.IsoDateTime
import de.franzsw.ctx2cal.core.domain.model.ModelStatus
import de.franzsw.ctx2cal.core.presentation.design_system.components.AppActionButton
import de.franzsw.ctx2cal.core.presentation.design_system.components.AppOutlinedActionButton
import de.franzsw.ctx2cal.core.presentation.design_system.components.CustomElevatedCard
import de.franzsw.ctx2cal.core.presentation.design_system.components.ErrorCard
import de.franzsw.ctx2cal.core.presentation.design_system.components.HintCard
import de.franzsw.ctx2cal.extract.domain.EventJsonParser
import kotlinx.coroutines.delay

@Composable
fun ExtractScreen(
    state: ExtractUiState,
    modelStatus: ModelStatus? = null,
    onTextChange: (String) -> Unit,
    onExtract: () -> Unit,
    onCancelExtract: () -> Unit = {},
    onClearText: () -> Unit = { onTextChange("") },
    onDismissNotice: () -> Unit = {},
    onDraftChanged: (CalendarEventDraft) -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onBackToInput: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state) {
            is ExtractUiState.Input -> {
                ModelStatusBanner(
                    status = modelStatus,
                    onOpenSetup = onOpenSetup,
                )

                state.notice?.let { notice ->
                    HintCard(
                        hintTitle = "No event found",
                        hasDismissButton = true,
                        onDismiss = onDismissNotice,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(notice, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                val clipboard = LocalClipboardManager.current
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Paste text containing one event",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                val pasted = clipboard.getText()?.text
                                if (!pasted.isNullOrBlank()) onTextChange(pasted)
                            },
                        ) {
                            Text("Paste")
                        }
                        if (state.text.isNotEmpty()) {
                            TextButton(onClick = onClearText) {
                                Text("Clear")
                            }
                        }
                    }
                }

                val isOverLimit = state.text.length > EventJsonParser.MAX_INPUT_CHARS
                OutlinedTextField(
                    value = state.text,
                    onValueChange = onTextChange,
                    label = { Text("Event text") },
                    placeholder = {
                        Text("e.g., Dentist appointment next Tuesday at 14:30 at Main St Clinic")
                    },
                    supportingText = if (isOverLimit) {
                        {
                            Text(
                                "Input exceeds ${EventJsonParser.MAX_INPUT_CHARS} characters; extra text will be trimmed for extraction."
                            )
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    maxLines = 10,
                )

                val isUnsupported = modelStatus is ModelStatus.Unsupported
                AppActionButton(
                    text = "Extract event",
                    isLoading = false,
                    enabled = state.text.isNotBlank() && !isUnsupported,
                    onClick = onExtract,
                )

                HintCard(
                    hintTitle = "Private & on-device",
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Extraction runs entirely on this device. Nothing you paste is sent to a server.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            is ExtractUiState.Extracting -> {
                var elapsedSeconds by remember { mutableIntStateOf(0) }
                LaunchedEffect(Unit) {
                    elapsedSeconds = 0
                    while (true) {
                        delay(1_000L)
                        elapsedSeconds++
                    }
                }
                val activeModelLabel = when (modelStatus) {
                    is ModelStatus.NanoAvailable -> "Gemini Nano"
                    is ModelStatus.LiteRtReady -> modelStatus.modelName ?: "LiteRT-LM"
                    else -> "on-device model"
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        "Extracting event on-device…",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        "Using $activeModelLabel · ${elapsedSeconds}s elapsed (bounded per attempt)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    AppOutlinedActionButton(
                        text = "Cancel",
                        onClick = onCancelExtract,
                    )
                }
            }

            is ExtractUiState.Review -> {
                CustomElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        EventForm(draft = state.draft, onDraftChanged = onDraftChanged)
                    }
                }

                state.notice?.let { notice ->
                    if (state.noticeIsError) {
                        ErrorCard(modifier = Modifier.fillMaxWidth()) {
                            Text(notice)
                        }
                    } else {
                        HintCard(
                            hintTitle = "Saved",
                            hasDismissButton = true,
                            onDismiss = onDismissNotice,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(notice)
                        }
                    }
                }

                AppActionButton(
                    text = "Share to calendar",
                    isLoading = false,
                    enabled = state.draft.isValid,
                    onClick = onShare,
                )
                AppOutlinedActionButton(
                    text = "Download .ics",
                    isLoading = state.isExporting,
                    enabled = state.draft.isValid,
                    onClick = onDownload,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onBackToInput) { Text("Edit input text") }
                    TextButton(onClick = onClearText) { Text("Start over") }
                }
            }

            is ExtractUiState.Error -> {
                ErrorCard(
                    title = if (state.isNoModel) "Model required" else "Extraction failed",
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(state.message)
                }

                if (state.isNoModel) {
                    AppActionButton(
                        text = "Open Setup",
                        isLoading = false,
                        onClick = onOpenSetup,
                    )
                    AppOutlinedActionButton(
                        text = "Retry",
                        onClick = onExtract,
                    )
                } else {
                    AppActionButton(
                        text = "Retry",
                        isLoading = false,
                        onClick = onExtract,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBackToInput) { Text("Back to input") }
                }
            }
        }
    }
}

@Composable
private fun ModelStatusBanner(
    status: ModelStatus?,
    onOpenSetup: () -> Unit,
) {
    when (status) {
        null -> Unit
        is ModelStatus.NanoAvailable -> {
            StatusChip(text = "Active · Gemini Nano (on-device)")
        }
        is ModelStatus.LiteRtReady -> {
            StatusChip(text = "Active · ${status.modelName ?: "LiteRT"} (on-device)")
        }
        is ModelStatus.Unsupported -> {
            HintCard(
                hintTitle = "Android only (v1)",
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("On-device extraction is not supported on iOS yet.")
            }
        }
        else -> {
            HintCard(
                hintTitle = "Model setup needed",
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("No on-device model is ready yet. Download a compact model once to enable offline extraction.")
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onOpenSetup) {
                    Text("Open Setup")
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

private enum class PickerTarget { START_DATE, START_TIME, END_DATE, END_TIME }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventForm(draft: CalendarEventDraft, onDraftChanged: (CalendarEventDraft) -> Unit) {
    var activePicker by remember { mutableStateOf<PickerTarget?>(null) }
    val titleError = draft.titleError()
    val startError = draft.startError()
    val endError = draft.endError()
    val rangeError = draft.rangeError()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = draft.title,
            onValueChange = { onDraftChanged(draft.copy(title = it)) },
            label = { Text("Title") },
            singleLine = true,
            isError = titleError != null,
            supportingText = titleError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        DateTimePickerRow(
            label = "Start",
            isoValue = draft.startIso,
            allDay = draft.allDay,
            errorText = startError,
            onPickDate = { activePicker = PickerTarget.START_DATE },
            onPickTime = { activePicker = PickerTarget.START_TIME },
        )

        DateTimePickerRow(
            label = "End",
            isoValue = draft.endIso,
            allDay = draft.allDay,
            errorText = endError ?: rangeError,
            onPickDate = { activePicker = PickerTarget.END_DATE },
            onPickTime = { activePicker = PickerTarget.END_TIME },
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = draft.allDay,
                    role = Role.Checkbox,
                    onValueChange = { checked ->
                        val updatedStart = if (checked) {
                            IsoDateTime.toDateOnly(draft.startIso)
                        } else if (IsoDateTime.isDateOnly(draft.startIso)) {
                            IsoDateTime.withTime(draft.startIso, 9, 0)
                        } else {
                            draft.startIso
                        }
                        val updatedEnd = if (checked) {
                            IsoDateTime.toDateOnly(draft.endIso)
                        } else if (IsoDateTime.isDateOnly(draft.endIso)) {
                            IsoDateTime.withTime(draft.endIso, 10, 0)
                        } else {
                            draft.endIso
                        }
                        onDraftChanged(
                            draft.copy(
                                allDay = checked,
                                startIso = updatedStart,
                                endIso = updatedEnd,
                            )
                        )
                    },
                )
                .padding(vertical = 4.dp),
        ) {
            Checkbox(
                checked = draft.allDay,
                onCheckedChange = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "All day",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        OutlinedTextField(
            value = draft.location,
            onValueChange = { onDraftChanged(draft.copy(location = it)) },
            label = { Text("Location") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = draft.description,
            onValueChange = { onDraftChanged(draft.copy(description = it)) },
            label = { Text("Description") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    when (activePicker) {
        PickerTarget.START_DATE, PickerTarget.END_DATE -> {
            val isStart = activePicker == PickerTarget.START_DATE
            val currentIso = if (isStart) draft.startIso else draft.endIso
            val dateState = rememberDatePickerState(
                initialSelectedDateMillis = IsoDateTime.toEpochMillisUtcMidnight(currentIso),
            )
            DatePickerDialog(
                onDismissRequest = { activePicker = null },
                confirmButton = {
                    TextButton(
                        onClick = {
                            dateState.selectedDateMillis?.let { millis ->
                                val pickedDate = IsoDateTime.fromEpochMillisUtcDate(millis)
                                val updated = IsoDateTime.withDate(currentIso, pickedDate, draft.allDay)
                                onDraftChanged(
                                    if (isStart) draft.copy(startIso = updated)
                                    else draft.copy(endIso = updated)
                                )
                            }
                            activePicker = null
                        },
                    ) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { activePicker = null }) { Text("Cancel") }
                },
            ) {
                DatePicker(state = dateState)
            }
        }
        PickerTarget.START_TIME, PickerTarget.END_TIME -> {
            val isStart = activePicker == PickerTarget.START_TIME
            val currentIso = if (isStart) draft.startIso else draft.endIso
            val (initialHour, initialMinute) = IsoDateTime.extractHourMinute(currentIso)
            val timeState = rememberTimePickerState(
                initialHour = initialHour,
                initialMinute = initialMinute,
                is24Hour = true,
            )
            AlertDialog(
                onDismissRequest = { activePicker = null },
                title = { Text(if (isStart) "Start time" else "End time") },
                text = { TimePicker(state = timeState) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val updated = IsoDateTime.withTime(currentIso, timeState.hour, timeState.minute)
                            onDraftChanged(
                                if (isStart) draft.copy(startIso = updated)
                                else draft.copy(endIso = updated)
                            )
                            activePicker = null
                        },
                    ) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { activePicker = null }) { Text("Cancel") }
                },
            )
        }
        null -> Unit
    }
}

@Composable
private fun DateTimePickerRow(
    label: String,
    isoValue: String,
    allDay: Boolean,
    errorText: String?,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
) {
    val dateDisplay = if (IsoDateTime.isValid(isoValue)) IsoDateTime.toDateOnly(isoValue) else isoValue.ifBlank { "Select date" }
    val (hh, mm) = IsoDateTime.extractHourMinute(isoValue)
    val timeDisplay = "${hh.toString().padStart(2, '0')}:${mm.toString().padStart(2, '0')}"

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = if (errorText != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onPickDate,
                modifier = Modifier.weight(1.3f),
            ) {
                Text(dateDisplay)
            }
            if (!allDay) {
                OutlinedButton(
                    onClick = onPickTime,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(timeDisplay)
                }
            }
        }
        if (errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.clickable(onClick = onPickDate),
            )
        }
    }
}
