package de.franzsw.ctx2cal.test_playground.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.franzsw.ctx2cal.core.presentation.design_system.components.AppActionButton
import de.franzsw.ctx2cal.core.presentation.design_system.components.AppOutlinedActionButton
import de.franzsw.ctx2cal.core.presentation.design_system.components.CustomElevatedCard
import de.franzsw.ctx2cal.core.presentation.design_system.components.ErrorCard

@Composable
fun ModelTestScreen(
    state: ModelTestUiState,
    onSystemPromptChange: (String) -> Unit,
    onResetSystemPrompt: () -> Unit,
    onUserPromptChange: (String) -> Unit,
    onRun: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CustomElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = state.modelName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Target model ID: ${state.modelId} · Single-turn raw inference (no fallback)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "System prompt",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Row {
                    TextButton(
                        onClick = onResetSystemPrompt,
                        enabled = !state.isRunning,
                    ) {
                        Text("Reset")
                    }
                    TextButton(
                        onClick = { onSystemPromptChange("") },
                        enabled = !state.isRunning && state.systemPrompt.isNotEmpty(),
                    ) {
                        Text("Clear")
                    }
                }
            }
            OutlinedTextField(
                value = state.systemPrompt,
                onValueChange = onSystemPromptChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isRunning,
                minLines = 3,
                maxLines = 8,
                textStyle = MaterialTheme.typography.bodySmall,
                placeholder = { Text("Optional system instruction") },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "User prompt",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                TextButton(
                    onClick = { onUserPromptChange("") },
                    enabled = !state.isRunning && state.userPrompt.isNotEmpty(),
                ) {
                    Text("Clear")
                }
            }
            OutlinedTextField(
                value = state.userPrompt,
                onValueChange = onUserPromptChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isRunning,
                minLines = 3,
                maxLines = 8,
                placeholder = { Text("Enter a prompt to send to ${state.modelName}…") },
            )
        }

        if (state.isRunning) {
            AppOutlinedActionButton(
                text = "Cancel",
                onClick = onCancel,
            )
        } else {
            AppActionButton(
                text = "Run",
                isLoading = false,
                enabled = state.userPrompt.isNotBlank(),
                onClick = onRun,
            )
        }

        state.error?.let { errorMessage ->
            ErrorCard(
                title = "Inference error",
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        state.output?.let { rawOutput ->
            CustomElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Raw output",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        state.durationMs?.let { ms ->
                            Text(
                                text = "${ms} ms",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    SelectionContainer {
                        Text(
                            text = rawOutput.ifEmpty { "(empty response)" },
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
