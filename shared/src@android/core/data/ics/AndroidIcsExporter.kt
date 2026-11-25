package de.franzsw.ctx2cal.core.data.ics

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import de.franzsw.ctx2cal.core.domain.model.CalendarEventDraft
import de.franzsw.ctx2cal.core.domain.ports.IcsExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Writes an RFC 5545 `.ics` via MediaStore to Downloads (scoped storage on
 * Android 10+). Below API 29 falls back to an internal file (v1 focus is 10+).
 */
class AndroidIcsExporter(private val context: Context) : IcsExporter {

    override suspend fun export(draft: CalendarEventDraft): String? = withContext(Dispatchers.IO) {
        if (!draft.isValid) return@withContext null
        val uid = UUID.randomUUID().toString() + "@ctx2cal.local"
        val dtstamp = DTSTAMP_FORMAT.format(Instant.now().atOffset(ZoneOffset.UTC))
        val content = IcsBuilder.build(draft, uid, dtstamp)
        val fileName = IcsBuilder.suggestedFileName(draft)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeToDownloads(fileName, content)
            } else {
                writeInternal(fileName, content)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun writeToDownloads(name: String, content: String): String? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/calendar")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            val stream = resolver.openOutputStream(uri)
            if (stream == null) {
                resolver.delete(uri, null, null)
                return null
            }
            stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            name
        } catch (e: Exception) {
            // Clean up orphaned IS_PENDING row on failure.
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            throw e
        }
    }

    private fun writeInternal(name: String, content: String): String {
        val file = File(context.filesDir, name)
        file.writeText(content, Charsets.UTF_8)
        return file.name
    }

    companion object {
        private val DTSTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    }
}
