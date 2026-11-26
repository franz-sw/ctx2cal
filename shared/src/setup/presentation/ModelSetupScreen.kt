package de.franzsw.ctx2cal.setup.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.franzsw.ctx2cal.core.domain.model.CatalogModelState
import de.franzsw.ctx2cal.core.domain.model.ModelCatalogEntries
import de.franzsw.ctx2cal.core.domain.model.ModelStatus
import de.franzsw.ctx2cal.core.domain.model.ModelSuitability
import de.franzsw.ctx2cal.core.domain.ports.DownloadProgress
import de.franzsw.ctx2cal.core.presentation.design_system.components.AppActionButton
import de.franzsw.ctx2cal.core.presentation.design_system.components.AppOutlinedActionButton
import de.franzsw.ctx2cal.core.presentation.design_system.components.CustomElevatedCard
import de.franzsw.ctx2cal.core.presentation.design_system.components.DefaultLoadingScreen
import de.franzsw.ctx2cal.core.presentation.design_system.components.ErrorCard
import de.franzsw.ctx2cal.core.presentation.design_system.components.HintCard

@Composable
fun ModelSetupScreen(
    state: ModelSetupUiState,
    onDownload: (String) -> Unit,
    onRetry: () -> Unit,
    onCancelDownload: () -> Unit = {},
    onSelectActiveModel: (String) -> Unit = {},
    onRequestDeleteModel: (String) -> Unit = {},
    onConfirmDeleteModel: () -> Unit = {},
    onDismissDeleteDialog: () -> Unit = {},
    onDone: () -> Unit = {},
) {
    when (state) {
        is ModelSetupUiState.Loading -> DefaultLoadingScreen(modifier = Modifier.fillMaxSize())
        is ModelSetupUiState.Failed -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ErrorCard(modifier = Modifier.fillMaxWidth()) {
                    Text(state.message)
                }
                AppActionButton(
                    text = "Retry",
                    isLoading = false,
                    onClick = onRetry,
                )
            }
        }
        is ModelSetupUiState.Ready -> {
            state.pendingDeleteCandidate?.let { pending ->
                AlertDialog(
                    onDismissRequest = onDismissDeleteDialog,
                    title = { Text("Delete ${pending.name}?") },
                    text = {
                        val sizeText = pending.approxSizeBytes?.let { " (${formatSize(it)})" }.orEmpty()
                        Text(
                            "This deletes the local model file$sizeText from app storage. You can re-download it anytime."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = onConfirmDeleteModel) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = onDismissDeleteDialog) {
                            Text("Cancel")
                        }
                    },
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.status is ModelStatus.Unsupported) {
                    CustomElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "On-device models unsupported",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "On-device extraction is currently available on Android only.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    return@Column
                }

                // Top-level hardware warning banner when Nano is unavailable and no GPU-recommended model exists.
                if (state.showNoAcceleratedWarning) {
                    ErrorCard(
                        title = "No accelerated model supported",
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "This device lacks Gemini Nano and supported LiteRT GPU acceleration. " +
                                "Smaller models can still run on CPU, though extraction may be slow or unavailable.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                // Active summary card + quick start button when any model is ready.
                CustomElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = statusTitle(state.status),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = statusDescription(state.status, state.candidate?.name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (state.status is ModelStatus.NanoAvailable || state.status is ModelStatus.LiteRtReady) {
                    AppActionButton(
                        text = "Start extracting",
                        isLoading = false,
                        onClick = onDone,
                    )
                }

                // System model section (Gemini Nano)
                SystemModelSection(
                    nanoStatus = state.nanoStatus,
                    isActive = state.activeModelId == ModelCatalogEntries.NANO_MODEL_ID,
                    onSelectNano = { onSelectActiveModel(ModelCatalogEntries.NANO_MODEL_ID) },
                    onRefresh = onRetry,
                )

                // Active download progress / error / completion feedback
                val activeDlCandidate = state.models
                    .firstOrNull { it.candidate.id == state.activeDownloadModelId }
                    ?.candidate ?: state.candidate
                when (val dl = state.download) {
                    is DownloadProgress.Running -> {
                        DownloadProgressSection(
                            modelName = activeDlCandidate?.name ?: "model",
                            receivedBytes = dl.receivedBytes,
                            totalBytes = dl.totalBytes ?: activeDlCandidate?.approxSizeBytes,
                            statusLabel = "Downloading ${activeDlCandidate?.name ?: "model"}…",
                            onCancel = onCancelDownload,
                        )
                    }
                    is DownloadProgress.Paused -> {
                        DownloadProgressSection(
                            modelName = activeDlCandidate?.name ?: "model",
                            receivedBytes = dl.receivedBytes,
                            totalBytes = dl.totalBytes ?: activeDlCandidate?.approxSizeBytes,
                            statusLabel = dl.reason ?: "Download paused…",
                            onCancel = onCancelDownload,
                        )
                    }
                    is DownloadProgress.Done -> {
                        HintCard(hintTitle = "Model ready", modifier = Modifier.fillMaxWidth()) {
                            Text("${activeDlCandidate?.name ?: "Model"} is downloaded and set as active.")
                        }
                    }
                    is DownloadProgress.Failed -> {
                        ErrorCard(modifier = Modifier.fillMaxWidth()) {
                            Text(dl.reason)
                        }
                    }
                    is DownloadProgress.Cancelled,
                    null -> Unit
                }

                // Multi-model ungated catalog
                if (state.models.isNotEmpty()) {
                    Text(
                        text = "Downloadable open models (Apache-2.0)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    val isAnyDownloadRunning = state.download is DownloadProgress.Running ||
                        state.download is DownloadProgress.Paused
                    for (entry in state.models) {
                        val isDownloadingThis = isAnyDownloadRunning &&
                            state.activeDownloadModelId == entry.candidate.id
                        CatalogModelCard(
                            entry = entry,
                            isDownloadingThis = isDownloadingThis,
                            isAnyDownloadRunning = isAnyDownloadRunning,
                            onDownload = { onDownload(entry.candidate.id) },
                            onCancelDownload = onCancelDownload,
                            onSelectActive = { onSelectActiveModel(entry.candidate.id) },
                            onRequestDelete = { onRequestDeleteModel(entry.candidate.id) },
                        )
                    }
                } else if (state.status !is ModelStatus.NanoAvailable) {
                    Text(
                        "No downloadable model is available on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    TextButton(onClick = onRetry) { Text("Refresh status") }
                }
            }
        }
    }
}

@Composable
private fun SystemModelSection(
    nanoStatus: ModelStatus,
    isActive: Boolean,
    onSelectNano: () -> Unit,
    onRefresh: () -> Unit,
) {
    when (nanoStatus) {
        is ModelStatus.NanoAvailable -> {
            CustomElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = nanoStatus.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (isActive) {
                                BadgeChip(
                                    text = "Active",
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        Text(
                            text = "Built-in system model (AICore) · No download needed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!isActive) {
                        OutlinedButton(onClick = onSelectNano) {
                            Text("Use")
                        }
                    }
                }
            }
        }
        is ModelStatus.NanoDownloadable,
        is ModelStatus.NanoDownloading -> {
            HintCard(hintTitle = "System model pending", modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (nanoStatus is ModelStatus.NanoDownloading) {
                        "Android AICore is downloading Gemini Nano in the background. You can refresh status once it completes, or download an open model below."
                    } else {
                        "Gemini Nano is supported on this device via Android AICore. You can refresh status or download an open model below."
                    }
                )
                Spacer(Modifier.height(6.dp))
                AppOutlinedActionButton(
                    text = "Refresh status",
                    onClick = onRefresh,
                )
            }
        }
        else -> Unit
    }
}

@Composable
private fun CatalogModelCard(
    entry: CatalogModelState,
    isDownloadingThis: Boolean,
    isAnyDownloadRunning: Boolean,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onSelectActive: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val candidate = entry.candidate
    val suitability = entry.suitability
    val isIncompatible = suitability is ModelSuitability.Incompatible

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
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = candidate.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val meta = buildString {
                        candidate.approxSizeBytes?.let { append(formatSize(it)) }
                        if (candidate.description.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(candidate.description)
                        }
                    }
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (entry.isActive) {
                    BadgeChip(
                        text = "Active",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (suitability) {
                    is ModelSuitability.Recommended -> BadgeChip(
                        text = "Recommended",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    is ModelSuitability.CpuOnlySlow -> BadgeChip(
                        text = "CPU only · may be slow",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    is ModelSuitability.Incompatible -> BadgeChip(
                        text = "Incompatible",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                if (entry.isDownloaded && !entry.isActive) {
                    BadgeChip(
                        text = "Downloaded",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when (suitability) {
                is ModelSuitability.CpuOnlySlow -> Text(
                    text = suitability.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is ModelSuitability.Incompatible -> Text(
                    text = suitability.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                is ModelSuitability.Recommended -> Unit
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    isDownloadingThis -> {
                        OutlinedButton(onClick = onCancelDownload) {
                            Text("Cancel download")
                        }
                    }
                    entry.isDownloaded -> {
                        if (!entry.isActive) {
                            OutlinedButton(onClick = onSelectActive) {
                                Text("Use")
                            }
                        }
                        TextButton(onClick = onRequestDelete) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    else -> {
                        Button(
                            onClick = onDownload,
                            enabled = !isIncompatible && !isAnyDownloadRunning,
                        ) {
                            Text("Download")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun DownloadProgressSection(
    modelName: String,
    receivedBytes: Long?,
    totalBytes: Long?,
    statusLabel: String,
    onCancel: () -> Unit,
) {
    CustomElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (totalBytes != null && totalBytes > 0L && receivedBytes != null && receivedBytes >= 0L) {
                val fraction = (receivedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                val percent = (fraction * 100).toInt()
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${formatMb(receivedBytes)} / ${formatMb(totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                if (receivedBytes != null && receivedBytes > 0L) {
                    Text(
                        text = "${formatMb(receivedBytes)} downloaded ($modelName)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            AppOutlinedActionButton(
                text = "Cancel download",
                onClick = onCancel,
            )
        }
    }
}

private fun formatMb(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    val tenths = (mb * 10).toLong()
    return "${tenths / 10}.${tenths % 10} MB"
}

private fun formatSize(bytes: Long): String {
    return if (bytes >= 1_000_000_000L) {
        val hundredths = (bytes / 10_000_000.0 + 0.5).toLong()
        "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')} GB"
    } else {
        "${bytes / 1_000_000L} MB"
    }
}

private fun statusTitle(status: ModelStatus): String = when (status) {
    is ModelStatus.NanoAvailable -> "${status.displayName} ready"
    is ModelStatus.NanoDownloadable -> "Gemini Nano downloadable"
    is ModelStatus.NanoDownloading -> "Gemini Nano downloading"
    is ModelStatus.NanoUnavailable -> "Gemini Nano unavailable"
    is ModelStatus.LiteRtReady -> "${status.modelName ?: "LiteRT model"} ready"
    is ModelStatus.LiteRtNotDownloaded -> "No model downloaded yet"
    is ModelStatus.Downloading -> "Downloading model…"
    is ModelStatus.Unsupported -> "On-device models unsupported"
    is ModelStatus.None -> "No on-device model ready"
}

private fun statusDescription(status: ModelStatus, candidateName: String?): String = when (status) {
    is ModelStatus.NanoAvailable -> "Using the built-in system model (AICore). No download needed."
    is ModelStatus.NanoDownloadable -> "Your device supports Gemini Nano via Android AICore."
    is ModelStatus.NanoDownloading -> "Android AICore is currently downloading Gemini Nano."
    is ModelStatus.NanoUnavailable -> "Gemini Nano is not available on this device."
    is ModelStatus.LiteRtReady -> "Using ${status.modelName ?: candidateName ?: "downloaded LiteRT model"} stored locally on this device."
    is ModelStatus.LiteRtNotDownloaded -> "Download a model below to extract events offline."
    is ModelStatus.Downloading -> "Model download is currently in progress."
    is ModelStatus.Unsupported -> "On-device extraction is not supported on this platform in v1."
    is ModelStatus.None -> "Download a compatible model below to extract calendar events completely offline."
}
