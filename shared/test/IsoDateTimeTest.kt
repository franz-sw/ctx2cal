package de.franzsw.ctx2cal

import de.franzsw.ctx2cal.core.domain.model.IsoDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsoDateTimeTest {

    @Test
    fun `rejects month and day out of bounds`() {
        assertFalse(IsoDateTime.isValid("2026-00-10"))
        assertFalse(IsoDateTime.isValid("2026-13-01"))
        assertFalse(IsoDateTime.isValid("2026-01-00"))
        assertFalse(IsoDateTime.isValid("2026-01-32"))
        assertFalse(IsoDateTime.isValid("2026-04-31"))
        assertTrue(IsoDateTime.isValid("2026-04-30"))
        assertTrue(IsoDateTime.isValid("2026-01-31"))
        assertTrue(IsoDateTime.isValid("2026-12-31"))
    }

    @Test
    fun `rejects impossible time and offset`() {
        assertFalse(IsoDateTime.isValid("2026-11-25T24:00:00"))
        assertFalse(IsoDateTime.isValid("2026-11-25T10:60:00"))
        assertFalse(IsoDateTime.isValid("2026-11-25T10:00:60"))
        assertTrue(IsoDateTime.isValid("2026-11-25T23:59:59"))
        assertTrue(IsoDateTime.isValid("2026-11-25T00:00:00"))
        assertTrue(IsoDateTime.isValid("2026-11-25T10:00:00Z"))
        assertTrue(IsoDateTime.isValid("2026-11-25T10:00:00+01:00"))
        assertTrue(IsoDateTime.isValid("2026-11-25T10:00:00+0100"))
        assertFalse(IsoDateTime.isValid("2026-11-25T10:00:00+24:00"))
        assertFalse(IsoDateTime.isValid("2026-11-25T10:00:00+01:60"))
        assertFalse(IsoDateTime.isValid("2026-11-25T10:00:00-24:00"))
    }

    @Test
    fun `enforces leap year calendar`() {
        assertFalse(IsoDateTime.isValid("2026-02-29"))
        assertFalse(IsoDateTime.isValid("2026-02-29T10:00:00"))
        assertTrue(IsoDateTime.isValid("2024-02-29"))
        assertTrue(IsoDateTime.isValid("2024-02-29T10:00:00"))
        assertTrue(IsoDateTime.isValid("2000-02-29"))
        assertFalse(IsoDateTime.isValid("1900-02-29"))
        assertTrue(IsoDateTime.isValid("2026-02-28"))
    }

    @Test
    fun `normalize pads missing seconds`() {
        assertEquals("2026-11-25T10:00:00", IsoDateTime.normalize("2026-11-25T10:00"))
        assertEquals("2026-11-25T10:00:00", IsoDateTime.normalize("2026-11-25 10:00"))
        assertEquals("2026-11-25T10:00:45", IsoDateTime.normalize("2026-11-25T10:00:45"))
        assertEquals("2026-11-25", IsoDateTime.normalize("2026-11-25"))
    }

    @Test
    fun `toBasic pads missing seconds and strips separators`() {
        assertEquals("20261125T100000", IsoDateTime.toBasic("2026-11-25T10:00"))
        assertEquals("20261125T100000", IsoDateTime.toBasic("2026-11-25T10:00:00"))
        assertEquals("20261125T100045", IsoDateTime.toBasic("2026-11-25T10:00:45"))
        assertEquals("20261125", IsoDateTime.toBasic("2026-11-25"))
    }

    @Test
    fun `compare orders normalized values`() {
        assertTrue(IsoDateTime.compare("2026-11-25T10:00:00", "2026-11-25T11:00:00") < 0)
        assertTrue(IsoDateTime.compare("2026-11-25T11:00:00", "2026-11-25T10:00:00") > 0)
        assertEquals(0, IsoDateTime.compare("2026-11-25T10:00", "2026-11-25T10:00:00"))
        assertTrue(IsoDateTime.compare("2026-11-24", "2026-11-25") < 0)
        assertEquals(0, IsoDateTime.compare("2026-11-25", "2026-11-25"))
    }

    @Test
    fun `nextDate advances one calendar day`() {
        assertEquals("2026-11-26", IsoDateTime.nextDate("2026-11-25"))
        assertEquals("2026-12-01", IsoDateTime.nextDate("2026-11-30"))
        assertEquals("2027-01-01", IsoDateTime.nextDate("2026-12-31"))
        assertEquals("2024-02-29", IsoDateTime.nextDate("2024-02-28"))
        assertEquals("2024-03-01", IsoDateTime.nextDate("2024-02-29"))
        assertEquals("2026-03-01", IsoDateTime.nextDate("2026-02-28"))
        assertEquals("2026-11-26", IsoDateTime.nextDate("2026-11-25T10:00:00"))
    }

    @Test
    fun `utc midnight roundtrips date`() {
        val dates = listOf(
            "2026-11-25",
            "2024-02-29",
            "2026-02-28",
            "2000-01-01",
            "1999-12-31",
            "2026-12-31",
        )
        for (date in dates) {
            val millis = IsoDateTime.toEpochMillisUtcMidnight(date)
            assertTrue(millis != null, "expected millis for $date")
            assertEquals(date, IsoDateTime.fromEpochMillisUtcDate(millis!!), "roundtrip for $date")
        }
        // Datetime input uses only its date part.
        val millis = IsoDateTime.toEpochMillisUtcMidnight("2026-11-25T10:00:00")
        assertEquals("2026-11-25", IsoDateTime.fromEpochMillisUtcDate(millis!!))
        // Invalid dates yield null rather than a bogus epoch.
        assertEquals(null, IsoDateTime.toEpochMillisUtcMidnight("2026-02-29"))
        assertEquals(null, IsoDateTime.toEpochMillisUtcMidnight("not-a-date"))
    }

    @Test
    fun `toDateOnly extracts date part`() {
        assertEquals("2026-11-25", IsoDateTime.toDateOnly("2026-11-25T10:00:00"))
        assertEquals("2026-11-25", IsoDateTime.toDateOnly("2026-11-25"))
    }

    @Test
    fun `isDateOnly distinguishes date from datetime`() {
        assertTrue(IsoDateTime.isDateOnly("2026-11-25"))
        assertFalse(IsoDateTime.isDateOnly("2026-11-25T10:00:00"))
    }

    @Test
    fun `hasTime is true only for valid datetimes with time`() {
        assertTrue(IsoDateTime.hasTime("2026-11-25T10:00:00"))
        assertTrue(IsoDateTime.hasTime("2026-11-25T10:00"))
        assertFalse(IsoDateTime.hasTime("2026-11-25"))
        assertFalse(IsoDateTime.hasTime(""))
        assertFalse(IsoDateTime.hasTime("not-a-date"))
        assertFalse(IsoDateTime.hasTime("2026-02-29T10:00:00"))
    }

    @Test
    fun `dayOfWeekName computes accurate weekday names`() {
        assertEquals("Thursday", IsoDateTime.dayOfWeekName("1970-01-01"))
        assertEquals("Wednesday", IsoDateTime.dayOfWeekName("2026-11-25"))
        assertEquals("Friday", IsoDateTime.dayOfWeekName("2026-11-27"))
        assertEquals("Friday", IsoDateTime.dayOfWeekName("2026-11-27T14:30:00"))
        assertEquals("Thursday", IsoDateTime.dayOfWeekName("2024-02-29"))
        assertEquals(null, IsoDateTime.dayOfWeekName("2026-02-29"))
        assertEquals(null, IsoDateTime.dayOfWeekName(""))
    }

    @Test
    fun `withTime uses fallbackDateIso when source has no date`() {
        assertEquals("2026-11-27T15:30:00", IsoDateTime.withTime("", 15, 30, fallbackDateIso = "2026-11-27"))
        assertEquals("2026-11-25T15:30:00", IsoDateTime.withTime("2026-11-25", 15, 30, fallbackDateIso = "2026-11-27"))
    }

    @Test
    fun `plusHours shifts within same day`() {
        assertEquals("2026-11-25T11:00:00", IsoDateTime.plusHours("2026-11-25T10:00:00", 1))
        assertEquals("2026-11-25T12:30:00", IsoDateTime.plusHours("2026-11-25T10:30:00", 2))
    }

    @Test
    fun `plusHours rolls date on midnight crossover`() {
        assertEquals("2026-11-26T00:30:00", IsoDateTime.plusHours("2026-11-25T23:30:00", 1))
        assertEquals("2026-12-01T00:00:00", IsoDateTime.plusHours("2026-11-30T23:00:00", 1))
        assertEquals("2027-01-01T00:00:00", IsoDateTime.plusHours("2026-12-31T23:00:00", 1))
    }

    @Test
    fun `plusHours preserves minutes and seconds and handles leap day`() {
        assertEquals("2026-11-25T12:15:45", IsoDateTime.plusHours("2026-11-25T10:15:45", 2))
        assertEquals("2024-02-29T00:00:00", IsoDateTime.plusHours("2024-02-28T23:00:00", 1))
    }
}
