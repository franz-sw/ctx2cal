package de.franzsw.ctx2cal.core.data.calendar

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import de.franzsw.ctx2cal.core.domain.model.CalendarEventDraft
import de.franzsw.ctx2cal.core.domain.model.IsoDateTime
import de.franzsw.ctx2cal.core.domain.ports.CalendarSharer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Share via the Calendar app's insert flow — the user picks the calendar app,
 * no calendar permission required (vs. direct CalendarContract insert).
 */
class AndroidCalendarSharer(private val context: Context) : CalendarSharer {

    override fun share(draft: CalendarEventDraft) {
        shareWithResult(draft)
    }

    override fun shareWithResult(draft: CalendarEventDraft): String? {
        draft.validationError()?.let { return it }
        val (begin, end) = try {
            resolveMillis(draft)
        } catch (_: Exception) {
            return "Invalid event date or time."
        }

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
        return try {
            context.startActivity(intent)
            null
        } catch (_: ActivityNotFoundException) {
            "No calendar app found on this device. You can download the .ics file instead."
        } catch (e: Exception) {
            "Could not open calendar app: ${e.message ?: "unknown error"}"
        }
    }

    private fun resolveMillis(draft: CalendarEventDraft): Pair<Long, Long> {
        if (draft.allDay) {
            // CalendarContract expects all-day begin/end at UTC midnight with exclusive end.
            val startDate = LocalDate.parse(IsoDateTime.toDateOnly(draft.startIso))
            val exclusiveEnd = LocalDate.parse(draft.exclusiveAllDayEndDate())
            val beginMs = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val endMs = exclusiveEnd.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            return beginMs to endMs
        }
        val zone = ZoneId.systemDefault()
        val beginMs = parseLocalMillis(draft.startIso, zone)
        val endMs = parseLocalMillis(draft.endIso, zone)
        return beginMs to endMs
    }

    private fun parseLocalMillis(iso: String, zone: ZoneId): Long {
        val norm = IsoDateTime.normalize(iso)
        return if (IsoDateTime.isDateOnly(norm)) {
            LocalDate.parse(norm).atStartOfDay(zone).toInstant().toEpochMilli()
        } else {
            LocalDateTime.parse(norm).atZone(zone).toInstant().toEpochMilli()
        }
    }
}
