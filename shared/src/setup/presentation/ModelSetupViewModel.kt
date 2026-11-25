package de.franzsw.ctx2cal.setup.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.franzsw.ctx2cal.core.domain.model.ModelCandidate
import de.franzsw.ctx2cal.core.domain.model.ModelStatus
import de.franzsw.ctx2cal.core.domain.ports.DownloadProgress
import de.franzsw.ctx2cal.core.domain.ports.ModelCatalog
import de.franzsw.ctx2cal.core.domain.ports.ModelDownloader
import de.franzsw.ctx2cal.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ModelSetupUiState {
    data object Loading : ModelSetupUiState
    data class Ready(
        val status: ModelStatus,
        val candidate: ModelCandidate?,
        val download: DownloadProgress?,
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

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = when (runCatching { catalog.currentStatus() }.isSuccess) {
                true -> ModelSetupUiState.Ready(
                    status = catalog.currentStatus(),
                    candidate = catalog.availableCandidate(),
                    download = null,
                )
                false -> ModelSetupUiState.Failed("Could not read model status")
            }
        }
    }

    fun onDownload() {
        viewModelScope.launch {
            val current = _state.value as? ModelSetupUiState.Ready ?: return@launch
            val candidate = current.candidate ?: return@launch
            downloader.download(candidate).collect { progress ->
                when (progress) {
                    is DownloadProgress.Done -> {
                        settings.setModelPath(progress.path)
                        settings.setPreferredBackend("litertlm")
                    }
                    is DownloadProgress.Failed -> Unit
                    is DownloadProgress.Running -> Unit
                }
                _state.value = ModelSetupUiState.Ready(
                    status = catalog.currentStatus(),
                    candidate = candidate,
                    download = progress,
                )
            }
        }
    }
}
