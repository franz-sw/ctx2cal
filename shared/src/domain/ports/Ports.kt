package de.franzsw.ctx2cal.domain.ports

import de.franzsw.ctx2cal.domain.model.CalendarEventDraft
import de.franzsw.ctx2cal.domain.model.ExtractionResult
import de.franzsw.ctx2cal.domain.model.ModelCandidate
import de.franzsw.ctx2cal.domain.model.ModelStatus
import kotlinx.coroutines.flow.Flow

/** On-device single-event extraction. Implemented per platform with a real LLM backend. */
interface EventExtractor {
    /**
     * @param text the pasted free text
     * @param prompt the fully built prompt (today/timezone already injected by the caller)
     */
    suspend fun extract(prompt: String): ExtractionResult

    /** Stable backend name for diagnostics (e.g. "gemini-nano", "litertlm"). */
    val backendName: String
}

/** Reports which model backend is usable, preferring Nano > LiteRT > none. */
interface ModelCatalog {
    suspend fun currentStatus(): ModelStatus
    suspend fun availableCandidate(): ModelCandidate?
}

/** Downloads the offered `.litertlm` model into internal storage. */
interface ModelDownloader {
    fun download(candidate: ModelCandidate): Flow<DownloadProgress>
    fun downloadedModelPath(): String?
}

sealed interface DownloadProgress {
    data class Running(val receivedBytes: Long?, val totalBytes: Long?) : DownloadProgress
    data class Done(val path: String) : DownloadProgress
    data class Failed(val reason: String) : DownloadProgress
}

/** Opens the user's Calendar app insert flow (Android ACTION_INSERT). */
interface CalendarSharer {
    fun share(draft: CalendarEventDraft)
}

/** Writes an RFC 5545 `.ics` file to Downloads (MediaStore on Android 10+). */
interface IcsExporter {
    /** Returns the display name of the written file, or null on failure. */
    suspend fun export(draft: CalendarEventDraft): String?
}

/** Persisted backend / model-path settings (internal storage). */
interface SettingsRepository {
    fun preferredBackend(): String?
    fun setPreferredBackend(name: String?)
    fun modelPath(): String?
    fun setModelPath(path: String?)
}
