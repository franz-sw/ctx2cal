package de.franzsw.ctx2cal.setup.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.franzsw.ctx2cal.core.domain.model.CatalogModelState
import de.franzsw.ctx2cal.core.domain.model.ModelCandidate
import de.franzsw.ctx2cal.core.domain.model.ModelCatalogEntries
import de.franzsw.ctx2cal.core.domain.model.ModelStatus
import de.franzsw.ctx2cal.core.domain.model.ModelSuitability
import de.franzsw.ctx2cal.core.domain.ports.DownloadProgress
import de.franzsw.ctx2cal.core.domain.ports.ModelCatalog
import de.franzsw.ctx2cal.core.domain.ports.ModelDownloader
import de.franzsw.ctx2cal.core.domain.repository.SettingsRepository
import de.franzsw.ctx2cal.extract.domain.EventJsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ModelSetupUiState {
    data object Loading : ModelSetupUiState
    data class Ready(
        val status: ModelStatus,
        val candidate: ModelCandidate?,
        val download: DownloadProgress?,
        val nanoStatus: ModelStatus = ModelStatus.NanoUnavailable,
        val models: List<CatalogModelState> = emptyList(),
        val activeModelId: String? = null,
        val showNoAcceleratedWarning: Boolean = false,
        val activeDownloadModelId: String? = null,
        val pendingDeleteCandidate: ModelCandidate? = null,
        val systemPrompt: String = EventJsonParser.buildSystemInstruction(),
        val isDefaultSystemPrompt: Boolean = true,
    ) : ModelSetupUiState
    data class Failed(val message: String) : ModelSetupUiState
}

class ModelSetupViewModel(
    private val catalog: ModelCatalog,
    private val downloader: ModelDownloader,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ModelSetupUiState>(ModelSetupUiState.Loading)
    val state: StateFlow<ModelSetupUiState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = ModelSetupUiState.Loading
            try {
                val status = catalog.currentStatus()
                val snapshot = catalog.catalogSnapshot()
                val fallbackCandidate = snapshot.models.firstOrNull { it.isActive }?.candidate
                    ?: catalog.availableCandidate()
                    ?: snapshot.models.firstOrNull()?.candidate
                val (prompt, isDefault) = resolveSystemPrompt()
                _state.value = ModelSetupUiState.Ready(
                    status = status,
                    candidate = fallbackCandidate,
                    download = null,
                    nanoStatus = snapshot.nanoStatus,
                    models = snapshot.models,
                    activeModelId = snapshot.activeModelId,
                    showNoAcceleratedWarning = snapshot.showNoAcceleratedWarning,
                    activeDownloadModelId = settings.activeDownloadModelId(),
                    systemPrompt = prompt,
                    isDefaultSystemPrompt = isDefault,
                )
                val activeDlId = settings.activeDownloadId()
                if (activeDlId != null && downloadJob?.isActive != true) {
                    val dlModelId = settings.activeDownloadModelId()
                    val dlEntry = snapshot.models.firstOrNull { it.candidate.id == dlModelId }
                    val dlCandidate = dlEntry?.candidate ?: fallbackCandidate
                    val alreadyDownloaded = dlEntry?.isDownloaded == true ||
                        (dlEntry == null && status is ModelStatus.LiteRtReady)
                    if (dlCandidate != null && !alreadyDownloaded) {
                        startDownload(dlCandidate)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = ModelSetupUiState.Failed("Could not read model status. Please try again.")
            }
        }
    }

    fun onSystemPromptChange(prompt: String) {
        val defaultPrompt = EventJsonParser.buildSystemInstruction()
        val isDefault = prompt == defaultPrompt
        settings.setCustomSystemPrompt(if (isDefault) null else prompt)
        _state.update { current ->
            if (current is ModelSetupUiState.Ready) {
                current.copy(systemPrompt = prompt, isDefaultSystemPrompt = isDefault)
            } else current
        }
    }

    fun onResetSystemPrompt() {
        val defaultPrompt = EventJsonParser.buildSystemInstruction()
        settings.setCustomSystemPrompt(null)
        _state.update { current ->
            if (current is ModelSetupUiState.Ready) {
                current.copy(systemPrompt = defaultPrompt, isDefaultSystemPrompt = true)
            } else current
        }
    }

    private fun resolveSystemPrompt(): Pair<String, Boolean> {
        val defaultPrompt = EventJsonParser.buildSystemInstruction()
        val saved = settings.customSystemPrompt()?.takeIf { it.isNotBlank() }
        return if (saved != null && saved != defaultPrompt) {
            saved to false
        } else {
            defaultPrompt to true
        }
    }

    fun onDownload() {
        val current = _state.value as? ModelSetupUiState.Ready ?: return
        val candidate = current.candidate ?: return
        onDownload(candidate.id)
    }

    fun onDownload(modelId: String) {
        if (downloadJob?.isActive == true) return
        val current = _state.value as? ModelSetupUiState.Ready ?: return
        val entry = current.models.firstOrNull { it.candidate.id == modelId }
        if (entry != null && entry.suitability is ModelSuitability.Incompatible) return
        val candidate = entry?.candidate
            ?: current.candidate?.takeIf { it.id == modelId }
            ?: ModelCatalogEntries.findById(modelId)
            ?: return
        startDownload(candidate)
    }

    fun onCancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        downloader.cancelDownload()
        refresh()
    }

    fun onSelectActiveModel(modelId: String) {
        viewModelScope.launch {
            try {
                catalog.selectActiveModel(modelId)
                val status = catalog.currentStatus()
                val snapshot = catalog.catalogSnapshot()
                _state.update { current ->
                    if (current is ModelSetupUiState.Ready) {
                        current.copy(
                            status = status,
                            nanoStatus = snapshot.nanoStatus,
                            models = snapshot.models,
                            activeModelId = snapshot.activeModelId,
                            showNoAcceleratedWarning = snapshot.showNoAcceleratedWarning,
                            candidate = snapshot.models.firstOrNull { it.isActive }?.candidate ?: current.candidate,
                        )
                    } else current
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                refresh()
            }
        }
    }

    fun onRequestDeleteModel(modelId: String) {
        _state.update { current ->
            if (current is ModelSetupUiState.Ready) {
                val target = current.models.firstOrNull { it.candidate.id == modelId }?.candidate
                    ?: current.candidate?.takeIf { it.id == modelId }
                current.copy(pendingDeleteCandidate = target)
            } else current
        }
    }

    fun onDismissDeleteDialog() {
        _state.update { current ->
            if (current is ModelSetupUiState.Ready) current.copy(pendingDeleteCandidate = null) else current
        }
    }

    fun onConfirmDeleteModel() {
        val current = _state.value as? ModelSetupUiState.Ready ?: return
        val target = current.pendingDeleteCandidate ?: return
        _state.update { s ->
            if (s is ModelSetupUiState.Ready) s.copy(pendingDeleteCandidate = null) else s
        }
        onDeleteModel(target.id)
    }

    fun onDeleteModel() {
        val current = _state.value as? ModelSetupUiState.Ready
        val activeId = current?.activeModelId?.takeIf { it != ModelCatalogEntries.NANO_MODEL_ID }
            ?: current?.candidate?.id
        if (activeId != null) {
            onDeleteModel(activeId)
        } else {
            downloadJob?.cancel()
            downloadJob = null
            downloader.deleteModel()
            settings.setPreferredBackend(null)
            settings.setActiveModelId(null)
            refresh()
        }
    }

    fun onDeleteModel(modelId: String) {
        val current = _state.value as? ModelSetupUiState.Ready
        if (current?.activeDownloadModelId == modelId || settings.activeDownloadModelId() == modelId) {
            downloadJob?.cancel()
            downloadJob = null
        }
        downloader.deleteModel(modelId)
        if (settings.activeModelId() == modelId || settings.activeModelId() == null) {
            settings.setActiveModelId(null)
            settings.setPreferredBackend(null)
        }
        refresh()
    }

    private fun startDownload(candidate: ModelCandidate) {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            val initialReady = _state.value as? ModelSetupUiState.Ready
            val initialStatus = initialReady?.status ?: ModelStatus.None
            _state.update { current ->
                if (current is ModelSetupUiState.Ready) {
                    current.copy(
                        candidate = candidate,
                        activeDownloadModelId = candidate.id,
                        download = DownloadProgress.Running(receivedBytes = null, totalBytes = candidate.approxSizeBytes),
                    )
                } else {
                    val (prompt, isDefault) = resolveSystemPrompt()
                    ModelSetupUiState.Ready(
                        status = initialStatus,
                        candidate = candidate,
                        activeDownloadModelId = candidate.id,
                        download = DownloadProgress.Running(receivedBytes = null, totalBytes = candidate.approxSizeBytes),
                        systemPrompt = prompt,
                        isDefaultSystemPrompt = isDefault,
                    )
                }
            }
            try {
                downloader.download(candidate).collect { progress ->
                    when (progress) {
                        is DownloadProgress.Running,
                        is DownloadProgress.Paused -> {
                            // Do not poll catalog.currentStatus() on every 400ms tick.
                            _state.update { current ->
                                if (current is ModelSetupUiState.Ready) {
                                    current.copy(
                                        candidate = candidate,
                                        activeDownloadModelId = candidate.id,
                                        download = progress,
                                    )
                                } else current
                            }
                        }
                        is DownloadProgress.Done -> {
                            settings.setModelPath(progress.path)
                            settings.setActiveModelId(candidate.id)
                            settings.setPreferredBackend("litertlm")
                            runCatching { catalog.selectActiveModel(candidate.id) }
                            val refreshedStatus = runCatching { catalog.currentStatus() }
                                .getOrDefault(ModelStatus.LiteRtReady(progress.path, candidate.id, candidate.name))
                            val refreshedSnapshot = runCatching { catalog.catalogSnapshot() }.getOrNull()
                            _state.update { current ->
                                if (current is ModelSetupUiState.Ready) {
                                    current.copy(
                                        status = refreshedStatus,
                                        candidate = candidate,
                                        activeDownloadModelId = null,
                                        download = progress,
                                        nanoStatus = refreshedSnapshot?.nanoStatus ?: current.nanoStatus,
                                        models = refreshedSnapshot?.models ?: current.models,
                                        activeModelId = refreshedSnapshot?.activeModelId ?: candidate.id,
                                        showNoAcceleratedWarning = refreshedSnapshot?.showNoAcceleratedWarning
                                            ?: current.showNoAcceleratedWarning,
                                    )
                                } else current
                            }
                        }
                        is DownloadProgress.Failed -> {
                            val refreshedStatus = runCatching { catalog.currentStatus() }
                                .getOrDefault(initialStatus)
                            val refreshedSnapshot = runCatching { catalog.catalogSnapshot() }.getOrNull()
                            _state.update { current ->
                                if (current is ModelSetupUiState.Ready) {
                                    current.copy(
                                        status = refreshedStatus,
                                        candidate = candidate,
                                        activeDownloadModelId = null,
                                        download = progress,
                                        nanoStatus = refreshedSnapshot?.nanoStatus ?: current.nanoStatus,
                                        models = refreshedSnapshot?.models ?: current.models,
                                        activeModelId = refreshedSnapshot?.activeModelId ?: current.activeModelId,
                                        showNoAcceleratedWarning = refreshedSnapshot?.showNoAcceleratedWarning
                                            ?: current.showNoAcceleratedWarning,
                                    )
                                } else current
                            }
                        }
                        is DownloadProgress.Cancelled -> {
                            val refreshedStatus = runCatching { catalog.currentStatus() }
                                .getOrDefault(initialStatus)
                            val refreshedSnapshot = runCatching { catalog.catalogSnapshot() }.getOrNull()
                            _state.update { current ->
                                if (current is ModelSetupUiState.Ready) {
                                    current.copy(
                                        status = refreshedStatus,
                                        candidate = candidate,
                                        activeDownloadModelId = null,
                                        download = null,
                                        nanoStatus = refreshedSnapshot?.nanoStatus ?: current.nanoStatus,
                                        models = refreshedSnapshot?.models ?: current.models,
                                        activeModelId = refreshedSnapshot?.activeModelId ?: current.activeModelId,
                                        showNoAcceleratedWarning = refreshedSnapshot?.showNoAcceleratedWarning
                                            ?: current.showNoAcceleratedWarning,
                                    )
                                } else current
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { current ->
                    if (current is ModelSetupUiState.Ready) {
                        current.copy(
                            candidate = candidate,
                            activeDownloadModelId = null,
                            download = DownloadProgress.Failed(e.message ?: "Download failed unexpectedly."),
                        )
                    } else current
                }
            }
        }
    }
}
