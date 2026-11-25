package de.franzsw.ctx2cal

import de.franzsw.ctx2cal.core.data.ics.IcsBuilder
import de.franzsw.ctx2cal.core.domain.model.CalendarEventDraft
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

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
    fun `builds an all-day event with VALUE=DATE`() {
        val draft = CalendarEventDraft(
            title = "Holiday",
            startIso = "2026-12-25",
            endIso = "2026-12-26",
            allDay = true,
        )
        val ics = IcsBuilder.build(draft, "u2@x", "20261125T120000Z")
        assertContains(ics, "DTSTART;VALUE=DATE:20261225")
        assertContains(ics, "DTEND;VALUE=DATE:20261226")
    }
}
