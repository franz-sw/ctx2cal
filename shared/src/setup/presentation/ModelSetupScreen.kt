package de.franzsw.ctx2cal.setup.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.franzsw.ctx2cal.core.presentation.design_system.components.AppActionButton
import de.franzsw.ctx2cal.core.presentation.design_system.components.CustomElevatedCard
import de.franzsw.ctx2cal.core.presentation.design_system.components.DefaultLoadingScreen
import de.franzsw.ctx2cal.core.presentation.design_system.components.ErrorCard
import de.franzsw.ctx2cal.core.presentation.design_system.components.HintCard
import de.franzsw.ctx2cal.core.presentation.design_system.components.LCHeadline
import de.franzsw.ctx2cal.core.domain.model.ModelStatus
import de.franzsw.ctx2cal.core.domain.ports.DownloadProgress

@Composable
fun ModelSetupScreen(
    state: ModelSetupUiState,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        LCHeadline(name = "On-device model")
        when (state) {
            is ModelSetupUiState.Loading -> DefaultLoadingScreen()
            is ModelSetupUiState.Failed -> {
                ErrorCard(modifier = Modifier.fillMaxWidth()) {
                    Text(state.message)
                }
                Spacer(Modifier.height(8.dp))
                AppActionButton(
                    text = "Retry",
                    isLoading = false,
                    onClick = onRetry,
                )
            }
            is ModelSetupUiState.Ready -> {
                CustomElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            statusText(state.status),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                val candidate = state.candidate
                when (val dl = state.download) {
                    is DownloadProgress.Running -> {
                        val total = dl.totalBytes
                        if (total != null && total > 0 && dl.receivedBytes != null) {
                            LinearProgressIndicator(
                                progress = { dl.receivedBytes.toFloat() / total },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Downloading…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    is DownloadProgress.Done -> {
                        HintCard(hintTitle = "Downloaded", modifier = Modifier.fillMaxWidth()) {
                            Text("Downloaded to ${dl.path}")
                        }
                    }
                    is DownloadProgress.Failed -> {
                        ErrorCard(modifier = Modifier.fillMaxWidth()) {
                            Text(dl.reason)
                        }
                        if (candidate != null) {
                            Spacer(Modifier.height(8.dp))
                            AppActionButton(
                                text = "Retry download",
                                isLoading = false,
                                onClick = onDownload,
                            )
                        }
                    }
                    null -> {
                        if (state.status is ModelStatus.None || state.status is ModelStatus.LiteRtNotDownloaded) {
                            if (candidate != null) {
                                HintCard(hintTitle = "Available model", modifier = Modifier.fillMaxWidth()) {
                                    Text("Offered: ${candidate.name}")
                                    candidate.approxSizeBytes?.let { Text("Size ≈ ${it / 1_000_000} MB") }
                                }
                                Spacer(Modifier.height(8.dp))
                                AppActionButton(
                                    text = "Download model",
                                    isLoading = false,
                                    onClick = onDownload,
                                )
                            } else {
                                Text("No model is available on this device.")
                                TextButton(onClick = onRetry) { Text("Retry") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun statusText(status: ModelStatus): String = when (status) {
    is ModelStatus.NanoAvailable -> "Gemini Nano is available (system model)."
    is ModelStatus.NanoDownloadable -> "Gemini Nano can be downloaded by the system."
    is ModelStatus.NanoDownloading -> "Gemini Nano download is in progress."
    is ModelStatus.NanoUnavailable -> "Gemini Nano is not available on this device."
    is ModelStatus.LiteRtReady -> "LiteRT model ready at ${status.modelPath}."
    is ModelStatus.LiteRtNotDownloaded -> "No downloaded model yet."
    is ModelStatus.Downloading -> "Downloading…"
    is ModelStatus.None -> "No on-device model available yet."
}
