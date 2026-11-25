package de.franzsw.ctx2cal.core.domain.repository

/** Persisted backend / model-path settings (internal storage). */
interface SettingsRepository {
    fun preferredBackend(): String?
    fun setPreferredBackend(name: String?)
    fun modelPath(): String?
    fun setModelPath(path: String?)
}
