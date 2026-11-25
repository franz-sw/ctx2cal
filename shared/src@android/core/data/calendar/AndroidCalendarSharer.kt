package de.franzsw.ctx2cal.core.data.calendar

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import de.franzsw.ctx2cal.core.domain.model.CalendarEventDraft
import de.franzsw.ctx2cal.core.domain.ports.CalendarSharer

/**
 * Share via the Calendar app's insert flow — the user picks the calendar app,
 * no calendar permission required (vs. direct CalendarContract insert).
 */
class AndroidCalendarSharer(private val context: Context) : CalendarSharer {

    override fun share(draft: CalendarEventDraft) {
        val begin = epochMillis(draft.startIso)
        val end = epochMillis(draft.endIso)
        // Official ACTION_INSERT contract: data = Events.CONTENT_URI,
        // times via CalendarContract extras, fields via Events columns.
        val intent = Intent(Intent.ACTION_INSERT).apply {
            setData(CalendarContract.Events.CONTENT_URI)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, draft.allDay)
            putExtra(CalendarContract.Events.TITLE, draft.title)
            putExtra(CalendarContract.Events.DESCRIPTION, draft.description)
            putExtra(CalendarContract.Events.EVENT_LOCATION, draft.location)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Parses `YYYY-MM-DD` / `YYYY-MM-DDTHH:mm:ss` into epoch millis (device timezone). */
    private fun epochMillis(iso: String): Long {
        val v = iso.trim().replace(' ', 'T')
        val date = v.substring(0, 10)
        val time = v.substringAfter('T', "00:00:00")
        val (y, m, d) = date.split("-").map { it.toInt() }
        val parts = time.split(":").map { it.toIntOrNull() ?: 0 }
        val hh = parts.getOrElse(0) { 0 }
        val mm = parts.getOrElse(1) { 0 }
        val ss = parts.getOrElse(2) { 0 }
        return java.util.Calendar.getInstance().apply {
            set(y, m - 1, d, hh, mm, ss)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
