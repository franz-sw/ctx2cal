package de.franzsw.ctx2cal

import de.franzsw.ctx2cal.core.domain.model.CalendarEventDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarEventDraftTest {

    private fun timed(
        title: String = "Dentist",
        start: String = "2026-11-25T10:00:00",
        end: String = "2026-11-25T11:00:00",
        allDay: Boolean = false,
    ) = CalendarEventDraft(
        title = title,
        startIso = start,
        endIso = end,
        allDay = allDay,
    )

    @Test
    fun `valid timed draft has no field errors`() {
        val draft = timed()
        assertNull(draft.titleError())
        assertNull(draft.startError())
        assertNull(draft.endError())
        assertNull(draft.rangeError())
        assertNull(draft.validationError())
        assertTrue(draft.isValid)
    }

    @Test
    fun `blank title fails titleError and validationError`() {
        val draft = timed(title = "   ")
        assertNotNull(draft.titleError())
        assertEquals("Title must not be blank", draft.titleError())
        assertNotNull(draft.validationError())
        assertTrue(!draft.isValid)
    }

    @Test
    fun `blank and invalid start and end fail per field`() {
        val blankStart = timed(start = "")
        assertNotNull(blankStart.startError())
        assertNotNull(blankStart.validationError())

        val invalidStart = timed(start = "2026-13-01T10:00:00")
        assertNotNull(invalidStart.startError())
        assertNull(invalidStart.endError())
        assertNotNull(invalidStart.validationError())

        val blankEnd = timed(end = "")
        assertNotNull(blankEnd.endError())

        val invalidEnd = timed(end = "not-a-date")
        assertNotNull(invalidEnd.endError())
        assertNotNull(invalidEnd.validationError())
    }

    @Test
    fun `rangeError is null when a field is already invalid`() {
        val draft = timed(start = "not-a-date", end = "2026-11-25T11:00:00")
        assertNull(draft.rangeError())
    }

    @Test
    fun `mixed date and datetime is rejected when not all day`() {
        val startDateOnly = timed(start = "2026-11-25", end = "2026-11-25T11:00:00")
        assertNotNull(startDateOnly.rangeError())
        assertEquals(
            "Start and end must both include a time (or mark as All day)",
            startDateOnly.rangeError(),
        )

        val endDateOnly = timed(start = "2026-11-25T10:00:00", end = "2026-11-25")
        assertNotNull(endDateOnly.rangeError())
        assertNotNull(endDateOnly.validationError())

        val bothDateOnly = timed(start = "2026-11-25", end = "2026-11-26")
        assertNotNull(bothDateOnly.rangeError())
        assertEquals(
            "Start and end must both include a time (or mark as All day)",
            bothDateOnly.rangeError(),
        )
    }

    @Test
    fun `all day accepts date only and same day`() {
        val singleDay = CalendarEventDraft(
            title = "Holiday",
            startIso = "2026-12-25",
            endIso = "2026-12-25",
            allDay = true,
        )
        assertNull(singleDay.rangeError())
        assertNull(singleDay.validationError())
        assertTrue(singleDay.isValid)

        val multiDay = CalendarEventDraft(
            title = "Trip",
            startIso = "2026-12-25",
            endIso = "2026-12-26",
            allDay = true,
        )
        assertNull(multiDay.rangeError())
        assertTrue(multiDay.isValid)
    }

    @Test
    fun `all day with mixed datetime forms uses date parts`() {
        // Under allDay the mixed-form branch is skipped; comparison is date-only.
        val draft = CalendarEventDraft(
            title = "Mixed",
            startIso = "2026-12-25T09:00:00",
            endIso = "2026-12-26",
            allDay = true,
        )
        assertNull(draft.rangeError())
        assertTrue(draft.isValid)
    }

    @Test
    fun `all day end before start is rejected`() {
        val draft = CalendarEventDraft(
            title = "Backwards",
            startIso = "2026-12-26",
            endIso = "2026-12-25",
            allDay = true,
        )
        assertEquals("End must not be before start", draft.rangeError())
        assertNotNull(draft.validationError())
    }

    @Test
    fun `timed end before start after normalization is rejected`() {
        val earlierEnd = timed(start = "2026-11-25T10:00:00", end = "2026-11-25T09:00:00")
        assertEquals("End must not be before start", earlierEnd.rangeError())

        val earlierEqualSecond = timed(start = "2026-11-25T10:00:30", end = "2026-11-25T10:00")
        assertEquals("End must not be before start", earlierEqualSecond.rangeError())

        val previousDay = timed(start = "2026-11-25T10:00:00", end = "2026-11-24T11:00:00")
        assertNotNull(previousDay.rangeError())

        val equalTimes = timed(start = "2026-11-25T10:00:00", end = "2026-11-25T10:00:00")
        assertNull(equalTimes.rangeError())
        assertTrue(equalTimes.isValid)
    }

    @Test
    fun `validationError prioritises title over invalid dates`() {
        val draft = timed(title = "", start = "not-a-date")
        assertEquals("Title must not be blank", draft.validationError())
    }

    @Test
    fun `exclusiveAllDayEndDate returns day after inclusive endIso`() {
        val singleDay = CalendarEventDraft(
            title = "Holiday",
            startIso = "2026-12-25",
            endIso = "2026-12-25",
            allDay = true,
        )
        assertEquals("2026-12-26", singleDay.exclusiveAllDayEndDate())

        val multiDay = CalendarEventDraft(
            title = "Conference",
            startIso = "2026-12-25",
            endIso = "2026-12-27",
            allDay = true,
        )
        assertEquals("2026-12-28", multiDay.exclusiveAllDayEndDate())

        val yearRollover = CalendarEventDraft(
            title = "New Year",
            startIso = "2026-12-31",
            endIso = "2026-12-31",
            allDay = true,
        )
        assertEquals("2027-01-01", yearRollover.exclusiveAllDayEndDate())
    }

    @Test
    fun `withStart moves end date to start date when end date is lower but end time stays after`() {
        val draft = timed(start = "2026-11-25T10:00:00", end = "2026-11-26T11:00:00")
        // Start date moved to 27th (past end date 26th), start time kept at 10:00.
        val updated = draft.withStart("2026-11-27T10:00:00")
        assertEquals("2026-11-27T10:00:00", updated.startIso)
        assertEquals("2026-11-27T11:00:00", updated.endIso)
    }

    @Test
    fun `withStart sets end to start plus 1h when end is still before after date fix`() {
        val draft = timed(start = "2026-11-25T10:00:00", end = "2026-11-25T11:00:00")
        // Start date moved to 26th with later time 12:00 via withDate-style new start.
        // End date bumped to 26th (11:00) which is still before start (12:00).
        val updated = draft.withStart("2026-11-26T12:00:00")
        assertEquals("2026-11-26T12:00:00", updated.startIso)
        assertEquals("2026-11-26T13:00:00", updated.endIso)
    }

    @Test
    fun `withStart sets end to start plus 1h when start time edit passes end same day`() {
        val draft = timed(start = "2026-11-25T10:00:00", end = "2026-11-25T11:00:00")
        val updated = draft.withStart("2026-11-25T12:00:00")
        assertEquals("2026-11-25T12:00:00", updated.startIso)
        assertEquals("2026-11-25T13:00:00", updated.endIso)
    }

    @Test
    fun `withStart leaves end untouched when end stays after start`() {
        val draft = timed(start = "2026-11-25T10:00:00", end = "2026-11-25T11:00:00")
        val updated = draft.withStart("2026-11-25T10:30:00")
        assertEquals("2026-11-25T10:30:00", updated.startIso)
        assertEquals("2026-11-25T11:00:00", updated.endIso)
    }

    @Test
    fun `withStart leaves equal end time as is`() {
        val draft = timed(start = "2026-11-25T10:00:00", end = "2026-11-25T11:00:00")
        val updated = draft.withStart("2026-11-25T11:00:00")
        assertEquals("2026-11-25T11:00:00", updated.endIso)
    }

    @Test
    fun `withStart rolls end date when start plus 1h crosses midnight`() {
        val draft = timed(start = "2026-11-25T10:00:00", end = "2026-11-25T11:00:00")
        val updated = draft.withStart("2026-11-25T23:30:00")
        assertEquals("2026-11-25T23:30:00", updated.startIso)
        assertEquals("2026-11-26T00:30:00", updated.endIso)
    }

    @Test
    fun `withStart moves all-day end date to start date`() {
        val draft = CalendarEventDraft(
            title = "Trip",
            startIso = "2026-12-25",
            endIso = "2026-12-26",
            allDay = true,
        )
        val updated = draft.withStart("2026-12-27")
        assertEquals("2026-12-27", updated.startIso)
        assertEquals("2026-12-27", updated.endIso)
    }

    @Test
    fun `withStart leaves blank or invalid end untouched`() {
        val blankEnd = timed(start = "2026-11-25T10:00:00", end = "")
        assertEquals("", blankEnd.withStart("2026-11-26T12:00:00").endIso)

        val invalidEnd = timed(start = "2026-11-25T10:00:00", end = "not-a-date")
        assertEquals("not-a-date", invalidEnd.withStart("2026-11-26T12:00:00").endIso)
    }
}
