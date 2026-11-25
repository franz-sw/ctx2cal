package com.example.ctx2cal.domain.ics

import com.example.ctx2cal.domain.model.CalendarEventDraft
import com.example.ctx2cal.domain.model.IsoDateTime

/** Pure-Kotlin RFC 5545 `.ics` builder (escaping, UID, DTSTAMP) — testable without a phone. */
object IcsBuilder {

    fun build(draft: CalendarEventDraft, uid: String, dtstampUtcBasic: String): String {
        val isAllDay = draft.allDay
        val start = IsoDateTime.toBasic(draft.startIso)
        val end = IsoDateTime.toBasic(draft.endIso)
        val dtStart = if (isAllDay) "DTSTART;VALUE=DATE:$start" else "DTSTART:$start"
        val dtEnd = if (isAllDay) "DTEND;VALUE=DATE:$end" else "DTEND:$end"
        return buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("PRODID:-//ContextToCalendar//EN")
            appendLine("BEGIN:VEVENT")
            appendLine("UID:$uid")
            appendLine("DTSTAMP:$dtstampUtcBasic")
            appendLine(dtStart)
            appendLine(dtEnd)
            appendLine("SUMMARY:${escape(draft.title)}")
            if (draft.description.isNotBlank()) appendLine("DESCRIPTION:${escape(draft.description)}")
            if (draft.location.isNotBlank()) appendLine("LOCATION:${escape(draft.location)}")
            appendLine("END:VEVENT")
            appendLine("END:VCALENDAR")
        }
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
}
