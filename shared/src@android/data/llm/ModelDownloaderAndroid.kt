package com.example.ctx2cal.data.llm

import android.content.Context
import android.app.DownloadManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.example.ctx2cal.domain.model.ModelCandidate
import com.example.ctx2cal.domain.ports.DownloadProgress
import com.example.ctx2cal.domain.ports.ModelDownloader
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * DownloadManager-based `.litertlm` download into internal storage
 * (`filesDir/models/model.litertlm`), with progress polling and a size check.
 * DownloadManager is the platform-standard path (vs. the app HTTP stack).
 */
class ModelDownloaderAndroid(private val context: Context) : ModelDownloader {

    private val manager: DownloadManager
        get() = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    override fun downloadedModelPath(): String? {
        val f = modelFile()
        return if (f.isFile && f.length() > 0) f.absolutePath else null
    }

    fun modelFile(): File = File(context.filesDir, "models/$MODEL_FILENAME")

    override fun download(candidate: ModelCandidate): Flow<DownloadProgress> = flow {
        val dest = modelFile()
        dest.parentFile?.mkdirs()
        val request = DownloadManager.Request(Uri.parse(candidate.url))
            .setTitle(candidate.name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("application/octet-stream")
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, MODEL_FILENAME)
        val id = try {
            manager.enqueue(request)
        } catch (e: Exception) {
            emit(DownloadProgress.Failed("Download failed to start: ${e.message}"))
            return@flow
        }
        var lastBytes = -1L
        while (true) {
            val (status, received, total) = query(id)
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                // Move from the public Downloads staging file into internal storage.
                val staging = stagedFile()
                if (staging.isFile) {
                    staging.copyTo(dest, overwrite = true)
                    staging.delete()
                }
                val path = dest.absolutePath
                if (candidate.approxSizeBytes != null && dest.length() < candidate.approxSizeBytes * 0.5) {
                    emit(DownloadProgress.Failed("Downloaded model looks truncated (${dest.length()} bytes)"))
                } else {
                    emit(DownloadProgress.Done(path))
                }
                return@flow
            }
            if (status == DownloadManager.STATUS_FAILED) {
                emit(DownloadProgress.Failed("Model download failed"))
                return@flow
            }
            if (received != lastBytes) {
                emit(DownloadProgress.Running(received.takeIf { it >= 0 }, total.takeIf { it > 0 }))
                lastBytes = received
            }
            delay(400)
        }
    }

    private fun stagedFile(): File = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), MODEL_FILENAME)

    private fun query(id: Long): Triple<Int, Long, Long> {
        val cursor: Cursor? = manager.query(DownloadManager.Query().setFilterById(id))
        return try {
            if (cursor != null && cursor.moveToFirst()) {
                Triple(
                    cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)),
                    cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                )
            } else {
                Triple(-1, -1, -1)
            }
        } finally {
            cursor?.close()
        }
    }

    companion object {
        const val MODEL_FILENAME = "model.litertlm"
        @Suppress("DEPRECATION")
        fun candidate(): ModelCandidate = ModelCandidate(
            // Placeholder asset until the v1 default model/repo is fixed (design open question);
            // see README "Which model" note. Qwen3-0.6B-class small model preferred.
            name = "Qwen3 0.6B (.litertlm)",
            url = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
            approxSizeBytes = 380_000_000L,
        )
    }
}
