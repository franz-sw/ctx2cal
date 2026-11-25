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
}
