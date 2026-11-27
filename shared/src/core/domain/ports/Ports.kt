package de.franzsw.ctx2cal.core.domain.ports

import de.franzsw.ctx2cal.core.domain.model.CalendarEventDraft
import de.franzsw.ctx2cal.core.domain.model.DeviceCapabilities
import de.franzsw.ctx2cal.core.domain.model.ExtractionResult
import de.franzsw.ctx2cal.core.domain.model.ModelCandidate
import de.franzsw.ctx2cal.core.domain.model.ModelCatalogSnapshot
import de.franzsw.ctx2cal.core.domain.model.ModelStatus
import kotlinx.coroutines.flow.Flow

/** On-device single-event extraction. Implemented per platform with a real LLM backend. */
interface EventExtractor {
    /**
     * @param prompt the fully built prompt (today/timezone already injected by the caller)
     */
    suspend fun extract(prompt: String): ExtractionResult

    /** Stable backend name for diagnostics (e.g. "gemini-nano", "litertlm"). */
    val backendName: String
}

/** Supplies current device hardware capabilities for model suitability evaluation. */
interface DeviceCapabilitiesProvider {
    fun getCapabilities(): DeviceCapabilities
}

/** Reports which model backend is usable and exposes the multi-model catalog snapshot. */
interface ModelCatalog {
    suspend fun currentStatus(): ModelStatus
    suspend fun availableCandidate(): ModelCandidate?
    suspend fun catalogSnapshot(): ModelCatalogSnapshot = ModelCatalogSnapshot(
        nanoStatus = currentStatus(),
        models = emptyList(),
        activeModelId = null,
        showNoAcceleratedWarning = false,
    )
    suspend fun selectActiveModel(modelId: String) {}
}

/** Downloads `.litertlm` models into internal storage under per-model paths. */
interface ModelDownloader {
    fun download(candidate: ModelCandidate): Flow<DownloadProgress>
    fun downloadedModelPath(): String?
    fun downloadedModelPath(modelId: String): String? = downloadedModelPath()
    fun downloadedModelPaths(): Map<String, String> = emptyMap()
    fun cancelDownload() {}
    fun deleteModel(): Boolean = false
    fun deleteModel(modelId: String): Boolean = deleteModel()
}

sealed interface DownloadProgress {
    data class Running(val receivedBytes: Long?, val totalBytes: Long?) : DownloadProgress
    data class Paused(val receivedBytes: Long?, val totalBytes: Long?, val reason: String?) : DownloadProgress
    data class Done(val path: String) : DownloadProgress
    data object Cancelled : DownloadProgress
    data class Failed(val reason: String) : DownloadProgress
}

/** Opens the user's Calendar app insert flow (Android ACTION_INSERT). */
interface CalendarSharer {
    fun share(draft: CalendarEventDraft)

    /**
     * Shares the event and returns `null` on success or a user-readable error message on failure.
     * Defaults to delegating to [share] for backwards compatibility with existing fakes.
     */
    fun shareWithResult(draft: CalendarEventDraft): String? {
        share(draft)
        return null
    }
}

/** Writes an RFC 5545 `.ics` file to Downloads (MediaStore on Android 10+). */
interface IcsExporter {
    /** Returns the display name of the written file, or null on failure. */
    suspend fun export(draft: CalendarEventDraft): String?
}

sealed interface RawPromptResult {
    data class Success(val output: String, val durationMs: Long) : RawPromptResult
    data class Failure(val reason: String) : RawPromptResult
}

/** Runs single-turn raw inference against a specific on-device model without JSON validation or fallback. */
interface ModelTester {
    suspend fun runPrompt(
        modelId: String,
        systemPrompt: String,
        userPrompt: String,
    ): RawPromptResult
}
