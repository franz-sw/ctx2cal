package de.franzsw.ctx2cal.core.data.llm

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import de.franzsw.ctx2cal.core.domain.download.DownloadErrorMapper
import de.franzsw.ctx2cal.core.domain.model.ModelCandidate
import de.franzsw.ctx2cal.core.domain.model.ModelCatalogEntries
import de.franzsw.ctx2cal.core.domain.ports.DownloadProgress
import de.franzsw.ctx2cal.core.domain.ports.ModelDownloader
import de.franzsw.ctx2cal.core.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * DownloadManager-based `.litertlm` downloader storing each catalog model independently
 * under `filesDir/models/<candidate.id>.litertlm`, with per-model progress tracking,
 * cancellation, 1.15x free-space checks, atomic copy on [Dispatchers.IO], and size validation.
 */
class ModelDownloaderAndroid(
    private val context: Context,
    private val settings: SettingsRepository? = null,
) : ModelDownloader {

    private val manager: DownloadManager
        get() = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private fun modelsDir(): File = File(context.filesDir, "models")

    fun modelFile(modelId: String): File = File(modelsDir(), "$modelId.litertlm")

    private fun cleanupLegacyModel() {
        val dir = modelsDir()
        dir.listFiles()?.forEach { file ->
            if (file.name == LEGACY_MODEL_FILENAME ||
                file.name == "$LEGACY_MODEL_FILENAME.tmp" ||
                file.name.startsWith("${LEGACY_MODEL_FILENAME}_")
            ) {
                file.delete()
            }
        }
        val savedPath = settings?.modelPath()
        if (savedPath != null && savedPath.endsWith("/$LEGACY_MODEL_FILENAME")) {
            settings.setModelPath(null)
        }
    }

    override fun downloadedModelPath(): String? {
        val activeId = settings?.activeModelId()
        if (activeId != null && activeId != ModelCatalogEntries.NANO_MODEL_ID) {
            downloadedModelPath(activeId)?.let { return it }
        }
        return downloadedModelPaths().values.firstOrNull()
    }

    override fun downloadedModelPath(modelId: String): String? {
        cleanupLegacyModel()
        val file = modelFile(modelId)
        return if (file.isFile && file.length() > 0L) file.absolutePath else null
    }

    override fun downloadedModelPaths(): Map<String, String> {
        cleanupLegacyModel()
        val result = linkedMapOf<String, String>()
        for (candidate in ModelCatalogEntries.all) {
            val file = modelFile(candidate.id)
            if (file.isFile && file.length() > 0L) {
                result[candidate.id] = file.absolutePath
            }
        }
        return result
    }

    override fun cancelDownload() {
        val activeId = settings?.activeDownloadId()
        if (activeId != null) {
            try {
                manager.remove(activeId)
            } catch (_: Exception) {
                // Best-effort cancel
            }
        }
        settings?.setActiveDownloadId(null)
        settings?.setActiveDownloadModelId(null)
    }

    override fun deleteModel(): Boolean {
        cancelDownload()
        cleanupLegacyModel()
        settings?.setModelPath(null)
        if (settings?.activeModelId() != ModelCatalogEntries.NANO_MODEL_ID) {
            settings?.setActiveModelId(null)
        }
        var allDeleted = true
        val dir = modelsDir()
        dir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".litertlm") || file.name.endsWith(".litertlm.tmp")) {
                if (!file.delete()) allDeleted = false
            }
        }
        return allDeleted
    }

    override fun deleteModel(modelId: String): Boolean {
        if (settings?.activeDownloadModelId() == modelId) {
            cancelDownload()
        }
        cleanupLegacyModel()
        val file = modelFile(modelId)
        val deleted = !file.exists() || file.delete()
        File(modelsDir(), "${modelId}.litertlm.tmp").delete()
        modelsDir().listFiles()?.forEach { extra ->
            if (extra.name.startsWith("${modelId}.litertlm_")) extra.delete()
        }
        if (settings?.modelPath() == file.absolutePath) {
            settings.setModelPath(null)
        }
        if (settings?.activeModelId() == modelId) {
            settings.setActiveModelId(null)
        }
        return deleted
    }

    override fun download(candidate: ModelCandidate): Flow<DownloadProgress> = flow {
        cleanupLegacyModel()
        val dest = modelFile(candidate.id)
        val destDir = dest.parentFile
        if (destDir != null && !destDir.exists()) {
            destDir.mkdirs()
        }

        val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (extDir == null) {
            emit(DownloadProgress.Failed("External storage is unavailable for staging the download."))
            return@flow
        }

        val stagingName = candidate.localFileName
        val existingId = settings?.activeDownloadId()
        val existingModelId = settings?.activeDownloadModelId()
        val existingQuery = if (existingId != null && existingModelId == candidate.id) query(existingId) else null
        val id: Long = if (
            existingId != null &&
            existingQuery != null &&
            existingQuery.status in setOf(
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PAUSED,
                DownloadManager.STATUS_SUCCESSFUL,
            )
        ) {
            existingId
        } else {
            if (existingId != null) {
                cancelDownload()
            }
            val neededBytes = candidate.approxSizeBytes
            if (neededBytes != null && neededBytes > 0L) {
                val internalFree = availableBytes(context.filesDir)
                val externalFree = availableBytes(extDir)
                val requiredEach = (neededBytes * 1.15).toLong()
                if (internalFree < requiredEach || externalFree < requiredEach) {
                    val neededMb = (requiredEach / 1_000_000L)
                    emit(DownloadProgress.Failed("Not enough free storage space (need ~${neededMb} MB free)."))
                    return@flow
                }
            }

            File(extDir, stagingName).delete()

            val enqueuedId = try {
                val request = DownloadManager.Request(Uri.parse(candidate.url))
                    .setTitle(candidate.name)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setMimeType("application/octet-stream")
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, stagingName)
                manager.enqueue(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emit(DownloadProgress.Failed("Download failed to start: ${e.message ?: "unknown error"}"))
                return@flow
            }
            settings?.setActiveDownloadId(enqueuedId)
            settings?.setActiveDownloadModelId(candidate.id)
            enqueuedId
        }

        var lastBytes = -2L
        var lastStatus = -2
        while (true) {
            val info = query(id)
            when (info.status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    settings?.setActiveDownloadId(null)
                    settings?.setActiveDownloadModelId(null)
                    val staged = resolveStagedFile(info.localUri, extDir, stagingName)
                    if (staged == null || !staged.isFile) {
                        emit(DownloadProgress.Failed("Downloaded file could not be located."))
                        return@flow
                    }
                    val actualSize = staged.length()
                    if (DownloadErrorMapper.isIncompleteDownload(actualSize, candidate.approxSizeBytes)) {
                        staged.delete()
                        emit(DownloadProgress.Failed(DownloadErrorMapper.incompleteMessage(actualSize)))
                        return@flow
                    }
                    val tmpDest = File(dest.parentFile, "$stagingName.tmp")
                    try {
                        staged.copyTo(tmpDest, overwrite = true)
                        staged.delete()
                        if (dest.exists()) dest.delete()
                        if (!tmpDest.renameTo(dest)) {
                            tmpDest.copyTo(dest, overwrite = true)
                            tmpDest.delete()
                        }
                        emit(DownloadProgress.Done(dest.absolutePath))
                    } catch (e: CancellationException) {
                        tmpDest.delete()
                        throw e
                    } catch (e: Exception) {
                        tmpDest.delete()
                        emit(DownloadProgress.Failed("Failed to save downloaded model: ${e.message ?: "I/O error"}"))
                    }
                    return@flow
                }
                DownloadManager.STATUS_FAILED -> {
                    settings?.setActiveDownloadId(null)
                    settings?.setActiveDownloadModelId(null)
                    emit(DownloadProgress.Failed(mapErrorReason(info.reason)))
                    return@flow
                }
                DownloadManager.STATUS_PAUSED -> {
                    if (info.status != lastStatus || info.receivedBytes != lastBytes) {
                        emit(
                            DownloadProgress.Paused(
                                receivedBytes = info.receivedBytes.takeIf { it >= 0L },
                                totalBytes = info.totalBytes.takeIf { it > 0L },
                                reason = mapPauseReason(info.reason),
                            )
                        )
                        lastStatus = info.status
                        lastBytes = info.receivedBytes
                    }
                }
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_RUNNING -> {
                    if (info.status != lastStatus || info.receivedBytes != lastBytes) {
                        emit(
                            DownloadProgress.Running(
                                receivedBytes = info.receivedBytes.takeIf { it >= 0L },
                                totalBytes = info.totalBytes.takeIf { it > 0L },
                            )
                        )
                        lastStatus = info.status
                        lastBytes = info.receivedBytes
                    }
                }
                else -> {
                    settings?.setActiveDownloadId(null)
                    settings?.setActiveDownloadModelId(null)
                    emit(DownloadProgress.Cancelled)
                    return@flow
                }
            }
            delay(400)
        }
    }.flowOn(Dispatchers.IO)

    private fun resolveStagedFile(localUri: String?, extDir: File, stagingName: String): File? {
        if (!localUri.isNullOrBlank()) {
            val path = Uri.parse(localUri).path
            if (!path.isNullOrBlank()) {
                val fromUri = File(path)
                if (fromUri.isFile) return fromUri
            }
        }
        val fallback = File(extDir, stagingName)
        return if (fallback.isFile) fallback else null
    }

    private fun availableBytes(dir: File): Long = try {
        StatFs(dir.absolutePath).availableBytes
    } catch (_: Exception) {
        Long.MAX_VALUE
    }

    private data class QueryResult(
        val status: Int,
        val receivedBytes: Long,
        val totalBytes: Long,
        val reason: Int,
        val localUri: String?,
    )

    private fun query(id: Long): QueryResult {
        val cursor: Cursor? = manager.query(DownloadManager.Query().setFilterById(id))
        return try {
            if (cursor != null && cursor.moveToFirst()) {
                val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val receivedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                QueryResult(
                    status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1,
                    receivedBytes = if (receivedIdx >= 0) cursor.getLong(receivedIdx) else -1L,
                    totalBytes = if (totalIdx >= 0) cursor.getLong(totalIdx) else -1L,
                    reason = if (reasonIdx >= 0) cursor.getInt(reasonIdx) else 0,
                    localUri = if (uriIdx >= 0) cursor.getString(uriIdx) else null,
                )
            } else {
                QueryResult(-1, -1L, -1L, 0, null)
            }
        } finally {
            cursor?.close()
        }
    }

    private fun mapErrorReason(reason: Int): String = DownloadErrorMapper.errorMessage(reason)

    private fun mapPauseReason(reason: Int): String = DownloadErrorMapper.pauseMessage(reason)

    companion object {
        private const val LEGACY_MODEL_FILENAME = "model.litertlm"

        fun candidate(): ModelCandidate = ModelCatalogEntries.QWEN3_0_6B_INT4
    }
}
