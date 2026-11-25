package com.example.ctx2cal.presentation.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ctx2cal.domain.model.ModelStatus
import com.example.ctx2cal.domain.ports.DownloadProgress

@Composable
fun ModelSetupScreen(
    state: ModelSetupUiState,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text("On-device model")
        when (state) {
            is ModelSetupUiState.Loading -> CircularProgressIndicator()
            is ModelSetupUiState.Failed -> {
                Text(state.message)
                TextButton(onClick = onRetry) { Text("Retry") }
            }
            is ModelSetupUiState.Ready -> {
                Text(statusText(state.status))
                val candidate = state.candidate
                when (val dl = state.download) {
                    is DownloadProgress.Running -> {
                        val total = dl.totalBytes
                        if (total != null && total > 0 && dl.receivedBytes != null) {
                            LinearProgressIndicator(
                                progress = { dl.receivedBytes.toFloat() / total },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Text("Downloading…")
                    }
                    is DownloadProgress.Done -> Text("Downloaded to ${dl.path}")
                    is DownloadProgress.Failed -> {
                        Text(dl.reason)
                        if (candidate != null) Button(onClick = onDownload) { Text("Retry download") }
                    }
                    null -> {
                        if (state.status is ModelStatus.None || state.status is ModelStatus.LiteRtNotDownloaded) {
                            if (candidate != null) {
                                Text("Offered: ${candidate.name}")
                                candidate.approxSizeBytes?.let { Text("Size ≈ ${it / 1_000_000} MB") }
                                Button(onClick = onDownload) { Text("Download model") }
                            } else {
                                Text("No model is available on this device.")
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
