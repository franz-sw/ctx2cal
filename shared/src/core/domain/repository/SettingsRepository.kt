package de.franzsw.ctx2cal.core.domain.repository

/** Persisted backend, active model selection, download tracking, and GPU fallback settings. */
interface SettingsRepository {
    fun preferredBackend(): String?
    fun setPreferredBackend(name: String?)
    fun modelPath(): String?
    fun setModelPath(path: String?)
    fun activeModelId(): String? = null
    fun setActiveModelId(id: String?) = Unit
    fun activeDownloadId(): Long? = null
    fun setActiveDownloadId(id: Long?) = Unit
    fun activeDownloadModelId(): String? = null
    fun setActiveDownloadModelId(id: String?) = Unit
    fun isGpuUnsupported(): Boolean = false
    fun setGpuUnsupported(unsupported: Boolean) = Unit
}
