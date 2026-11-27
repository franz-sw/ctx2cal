package de.franzsw.ctx2cal

import de.franzsw.ctx2cal.core.data.ics.IcsBuilder
import de.franzsw.ctx2cal.core.domain.model.CalendarEventDraft
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IcsBuilderTest {

    @Test
    fun `escapes RFC 5545 special characters`() {
        assertEquals("a\\,b\\;c\\\\d\\ne", IcsBuilder.escape("a,b;c\\d\ne"))
    }

    @Test
    fun `builds a timed event with UID and DTSTAMP`() {
        val draft = CalendarEventDraft(
            title = "Meet, now",
            startIso = "2026-11-25T10:00:00",
            endIso = "2026-11-25T11:00:00",
            allDay = false,
            description = "line1\nline2",
            location = "Room; 1",
        )
        val ics = IcsBuilder.build(draft, "u1@x", "20261125T120000Z")
        assertContains(ics, "UID:u1@x")
        assertContains(ics, "DTSTAMP:20261125T120000Z")
        assertContains(ics, "DTSTART:20261125T100000")
        assertContains(ics, "DTEND:20261125T110000")
        assertContains(ics, "SUMMARY:Meet\\, now")
        assertContains(ics, "DESCRIPTION:line1\\nline2")
        assertContains(ics, "LOCATION:Room\\; 1")
        assertContains(ics, "BEGIN:VCALENDAR")
        assertContains(ics, "END:VEVENT")
    }

    @Test
    fun `builds a multi-day all-day event with inclusive endIso exported as exclusive VALUE=DATE DTEND`() {
        val draft = CalendarEventDraft(
            title = "Holiday",
            startIso = "2026-12-25",
            endIso = "2026-12-26",
            allDay = true,
        )
        val ics = IcsBuilder.build(draft, "u2@x", "20261125T120000Z")
        assertContains(ics, "DTSTART;VALUE=DATE:20261225")
        assertContains(ics, "DTEND;VALUE=DATE:20261227")
    }

    @Test
    fun `output uses CRLF only`() {
        val draft = CalendarEventDraft(
            title = "Meet",
            startIso = "2026-11-25T10:00:00",
            endIso = "2026-11-25T11:00:00",
            allDay = false,
            description = "a\nb",
        )
        val ics = IcsBuilder.build(draft, "u3@x", "20261125T120000Z")
        assertTrue(ics.contains("\r\n"))
        assertTrue(ics.endsWith("\r\n"))
        // No bare LF: every \n must be preceded by \r.
        ics.forEachIndexed { index, ch ->
            if (ch == '\n') assertEquals('\r', ics[index - 1])
        }
        assertFalse(ics.contains("DESCRIPTION:a\nb"))
    }

    @Test
    fun `long lines are folded at 75 characters`() {
        val longTitle = "x".repeat(100)
        val draft = CalendarEventDraft(
            title = longTitle,
            startIso = "2026-11-25T10:00:00",
            endIso = "2026-11-25T11:00:00",
            allDay = false,
        )
        val ics = IcsBuilder.build(draft, "u4@x", "20261125T120000Z")
        // Every physical line (split by CRLF) must fit in 75 characters.
        val physicalLines = ics.split("\r\n")
        for (line in physicalLines) {
            assertTrue(line.length <= 75, "line too long (${line.length}): $line")
        }
        // Folding uses CRLF followed by a single space continuation.
        assertTrue(ics.contains("\r\n "))
    }

    @Test
    fun `single day all-day DTEND is exclusive`() {
        val draft = CalendarEventDraft(
            title = "Holiday",
            startIso = "2026-12-25",
            endIso = "2026-12-25",
            allDay = true,
        )
        val ics = IcsBuilder.build(draft, "u5@x", "20261125T120000Z")
        assertContains(ics, "DTSTART;VALUE=DATE:20261225")
        assertContains(ics, "DTEND;VALUE=DATE:20261226")
    }

    @Test
    fun `all-day with datetime input is stripped to date only`() {
        val draft = CalendarEventDraft(
            title = "Holiday",
            startIso = "2026-12-25T09:00:00",
            endIso = "2026-12-25T18:00:00",
            allDay = true,
        )
        val ics = IcsBuilder.build(draft, "u6@x", "20261125T120000Z")
        assertContains(ics, "DTSTART;VALUE=DATE:20261225")
        assertContains(ics, "DTEND;VALUE=DATE:20261226")
    }

    @Test
    fun `suggestedFileName derives slug and date`() {
        val draft = CalendarEventDraft(
            title = "Dentist Visit!",
            startIso = "2026-11-25T10:00:00",
            endIso = "2026-11-25T11:00:00",
            allDay = false,
        )
        assertEquals("dentist-visit-2026-11-25.ics", IcsBuilder.suggestedFileName(draft))

        val blank = CalendarEventDraft(
            title = "   ",
            startIso = "2026-11-25",
            endIso = "2026-11-26",
            allDay = true,
        )
        assertEquals("event-2026-11-25.ics", IcsBuilder.suggestedFileName(blank))

        val longTitle = "y".repeat(100)
        val longDraft = CalendarEventDraft(
            title = longTitle,
            startIso = "2026-11-25T10:00:00",
            endIso = "2026-11-25T11:00:00",
            allDay = false,
        )
        val longName = IcsBuilder.suggestedFileName(longDraft)
        assertTrue(longName.endsWith("-2026-11-25.ics"))
        assertTrue(longName.startsWith("y".repeat(40)))
    }
}
