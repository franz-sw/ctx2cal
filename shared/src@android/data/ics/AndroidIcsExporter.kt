package de.franzsw.ctx2cal.data.ics

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import de.franzsw.ctx2cal.domain.ics.IcsBuilder
import de.franzsw.ctx2cal.domain.model.CalendarEventDraft
import de.franzsw.ctx2cal.domain.ports.IcsExporter
import java.util.UUID

/**
 * Writes an RFC 5545 `.ics` via MediaStore to Downloads (scoped storage on
 * Android 10+). Below API 29 falls back to an internal file (v1 focus is 10+).
 */
class AndroidIcsExporter(private val context: Context) : IcsExporter {

    override suspend fun export(draft: CalendarEventDraft): String? {
        val uid = UUID.randomUUID().toString() + "@ctx2cal.local"
        val dtstamp = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())
        val content = IcsBuilder.build(draft, uid, dtstamp)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) writeToDownloads(content) else writeInternal(content)
        } catch (e: Exception) {
            null
        }
    }

    private fun writeToDownloads(content: String): String? {
        val name = "event-${System.currentTimeMillis()}.ics"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/calendar")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            ?: return null
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
        return name
    }

    private fun writeInternal(content: String): String {
        val file = java.io.File(context.filesDir, "event-${System.currentTimeMillis()}.ics")
        file.writeText(content, Charsets.UTF_8)
        return file.absolutePath
    }
}
