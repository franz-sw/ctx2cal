package de.franzsw.ctx2cal.core.data.ics

import de.franzsw.ctx2cal.core.domain.model.CalendarEventDraft
import de.franzsw.ctx2cal.core.domain.model.IsoDateTime

/** Pure-Kotlin RFC 5545 `.ics` builder (escaping, CRLF, line folding, UID, DTSTAMP). */
object IcsBuilder {

    private const val CRLF = "\r\n"

    fun build(draft: CalendarEventDraft, uid: String, dtstampUtcBasic: String): String {
        val dtStart: String
        val dtEnd: String
        if (draft.allDay) {
            val startDate = IsoDateTime.toDateOnly(draft.startIso)
            // RFC 5545 DTEND for VALUE=DATE is exclusive: draft.endIso is the inclusive last day.
            val exclusiveEndDate = draft.exclusiveAllDayEndDate()
            dtStart = "DTSTART;VALUE=DATE:${IsoDateTime.toBasic(startDate)}"
            dtEnd = "DTEND;VALUE=DATE:${IsoDateTime.toBasic(exclusiveEndDate)}"
        } else {
            dtStart = "DTSTART:${IsoDateTime.toBasic(draft.startIso)}"
            dtEnd = "DTEND:${IsoDateTime.toBasic(draft.endIso)}"
        }

        val lines = buildList {
            add("BEGIN:VCALENDAR")
            add("VERSION:2.0")
            add("PRODID:-//ContextToCalendar//EN")
            add("BEGIN:VEVENT")
            add("UID:$uid")
            add("DTSTAMP:$dtstampUtcBasic")
            add(dtStart)
            add(dtEnd)
            add("SUMMARY:${escape(draft.title)}")
            if (draft.description.isNotBlank()) add("DESCRIPTION:${escape(draft.description)}")
            if (draft.location.isNotBlank()) add("LOCATION:${escape(draft.location)}")
            add("END:VEVENT")
            add("END:VCALENDAR")
        }

        return buildString {
            for (line in lines) {
                append(foldLine(line))
                append(CRLF)
            }
        }
    }

    /** Produces a human-friendly `.ics` filename derived from the event title and date. */
    fun suggestedFileName(draft: CalendarEventDraft): String {
        val slug = draft.title
            .lowercase()
            .replace(Regex("""[^a-z0-9]+"""), "-")
            .trim('-')
            .take(40)
            .ifEmpty { "event" }
        val date = IsoDateTime.toDateOnly(draft.startIso)
            .takeIf { it.matches(Regex("""\d{4}-\d{2}-\d{2}""")) }
        return if (date != null) "$slug-$date.ics" else "$slug.ics"
    }

    /** RFC 5545 TEXT escaping: backslash, semicolon, comma, newline. */
    fun escape(text: String): String = buildString(text.length) {
        for (c in text) {
            when (c) {
                '\\' -> append("\\\\")
                ';' -> append("\\;")
                ',' -> append("\\,")
                '\n' -> append("\\n")
                '\r' -> { /* fold CRLF into a single \n */ }
                else -> append(c)
            }
        }
    }

    /** RFC 5545 line folding: lines longer than 75 chars are folded with CRLF + single space. */
    private fun foldLine(line: String): String {
        if (line.length <= 75) return line
        val sb = StringBuilder(line.length + 16)
        var i = 0
        var limit = 75
        while (i < line.length) {
            if (i > 0) {
                sb.append(CRLF).append(' ')
                limit = 74
            }
            val end = minOf(i + limit, line.length)
            sb.append(line, i, end)
            i = end
        }
        return sb.toString()
    }
}
