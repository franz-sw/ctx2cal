package de.franzsw.ctx2cal.core.domain.model

import kotlinx.serialization.Serializable

/** A single editable calendar event produced by on-device extraction. */
@Serializable
data class CalendarEventDraft(
    val title: String,
    /** ISO-8601 local datetime, e.g. 2026-11-25T10:00:00. For all-day, date only is allowed. */
    val startIso: String,
    val endIso: String,
    val allDay: Boolean,
    val description: String = "",
    val location: String = "",
) {
    fun titleError(): String? = if (title.isBlank()) "Title must not be blank" else null

    fun startError(): String? = when {
        startIso.isBlank() -> "Start is required"
        !IsoDateTime.isValid(startIso) -> "Start time is not a valid ISO date/time"
        else -> null
    }

    fun endError(): String? = when {
        endIso.isBlank() -> "End is required"
        !IsoDateTime.isValid(endIso) -> "End time is not a valid ISO date/time"
        else -> null
    }

    fun rangeError(): String? {
        if (startError() != null || endError() != null) return null
        if (!allDay && IsoDateTime.isDateOnly(startIso) != IsoDateTime.isDateOnly(endIso)) {
            return "Start and end must both include a time (or mark as All day)"
        }
        val cmp = if (allDay) {
            IsoDateTime.toDateOnly(endIso).compareTo(IsoDateTime.toDateOnly(startIso))
        } else {
            IsoDateTime.compare(endIso, startIso)
        }
        return if (cmp < 0) "End must not be before start" else null
    }

    /**
     * Minimal validation shared by extraction, edit form, and ICS/share outputs.
     * Returns null when valid, otherwise a human-readable reason.
     */
    fun validationError(): String? =
        titleError()
            ?: (if (startIso.isBlank() || endIso.isBlank()) "Start and end are required" else null)
            ?: startError()
            ?: endError()
            ?: rangeError()

    val isValid: Boolean get() = validationError() == null
}

/** Pure-Kotlin ISO-8601 validation, normalization, and calendar math (no kotlinx-datetime). */
object IsoDateTime {
    private val DATE_RE = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")
    private val DATETIME_RE = Regex(
        """^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.\d{1,9})?)?(?:Z|([+-])(\d{2}):?(\d{2}))?$"""
    )

    fun isDateOnly(value: String): Boolean = DATE_RE.matches(value.trim())

    fun isValid(value: String): Boolean {
        val v = value.trim()
        DATE_RE.matchEntire(v)?.let { m ->
            val y = m.groupValues[1].toIntOrNull() ?: return false
            val mo = m.groupValues[2].toIntOrNull() ?: return false
            val d = m.groupValues[3].toIntOrNull() ?: return false
            return isValidDate(y, mo, d)
        }
        DATETIME_RE.matchEntire(v)?.let { m ->
            val y = m.groupValues[1].toIntOrNull() ?: return false
            val mo = m.groupValues[2].toIntOrNull() ?: return false
            val d = m.groupValues[3].toIntOrNull() ?: return false
            val hh = m.groupValues[4].toIntOrNull() ?: return false
            val mm = m.groupValues[5].toIntOrNull() ?: return false
            val ss = m.groupValues[6].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            if (!isValidDate(y, mo, d)) return false
            if (hh !in 0..23 || mm !in 0..59 || ss !in 0..59) return false
            val offH = m.groupValues[8]
            val offM = m.groupValues[9]
            if (offH.isNotEmpty() && offM.isNotEmpty()) {
                val oh = offH.toIntOrNull() ?: return false
                val om = offM.toIntOrNull() ?: return false
                if (oh !in 0..23 || om !in 0..59) return false
            }
            return true
        }
        return false
    }

    /** Normalizes to `YYYY-MM-DDTHH:mm:ss` (or `YYYY-MM-DD` for date-only values). */
    fun normalize(value: String): String {
        val v = value.trim().replace(' ', 'T')
        DATE_RE.matchEntire(v)?.let { return v }
        DATETIME_RE.matchEntire(v)?.let { m ->
            val date = "${m.groupValues[1]}-${m.groupValues[2]}-${m.groupValues[3]}"
            val hh = m.groupValues[4]
            val mm = m.groupValues[5]
            val ss = m.groupValues[6].ifEmpty { "00" }
            return "${date}T$hh:$mm:$ss"
        }
        return v.take(19)
    }

    /** Extracts `YYYY-MM-DD` from either a date or datetime ISO string. */
    fun toDateOnly(value: String): String {
        val v = value.trim()
        return if (v.length >= 10) v.substring(0, 10) else v
    }

    /** `2026-11-25T10:00:00` -> `20261125T100000`; `2026-11-25` -> `20261125`. */
    fun toBasic(value: String): String = normalize(value).replace("-", "").replace(":", "")

    /** Compares two ISO date/datetime strings after normalization. */
    fun compare(a: String, b: String): Int = normalize(a).compareTo(normalize(b))

    /** Formats for readable display in the UI. */
    fun formatDisplay(iso: String, allDay: Boolean): String {
        if (!isValid(iso)) return iso
        val norm = normalize(iso)
        if (allDay || isDateOnly(norm)) return toDateOnly(norm)
        val date = norm.substring(0, 10)
        val time = norm.substring(11, 16)
        return "$date  ·  $time"
    }

    /** Extracts (hour, minute) from an ISO datetime, defaulting to (9, 0) for date-only. */
    fun extractHourMinute(iso: String): Pair<Int, Int> {
        val m = DATETIME_RE.matchEntire(iso.trim()) ?: return 9 to 0
        val hh = m.groupValues[4].toIntOrNull()?.coerceIn(0, 23) ?: 9
        val mm = m.groupValues[5].toIntOrNull()?.coerceIn(0, 59) ?: 0
        return hh to mm
    }

    /** Replaces the date component while preserving time (or returns date-only for all-day). */
    fun withDate(iso: String, dateIso: String, allDay: Boolean): String {
        if (allDay) return dateIso
        val (hh, mm) = extractHourMinute(iso)
        return "${dateIso}T${pad2(hh)}:${pad2(mm)}:00"
    }

    /** Replaces the hour and minute while preserving the date. */
    fun withTime(iso: String, hour: Int, minute: Int): String {
        val date = toDateOnly(iso).takeIf { DATE_RE.matches(it) } ?: "2026-01-01"
        return "${date}T${pad2(hour.coerceIn(0, 23))}:${pad2(minute.coerceIn(0, 59))}:00"
    }

    /** Returns the next calendar day (`YYYY-MM-DD`) for RFC 5545 exclusive all-day DTEND. */
    fun nextDate(dateIso: String): String {
        val millis = toEpochMillisUtcMidnight(dateIso) ?: return dateIso
        return fromEpochMillisUtcDate(millis + MILLIS_PER_DAY)
    }

    /** Converts `YYYY-MM-DD` (or datetime's date part) to UTC midnight epoch millis for M3 DatePicker. */
    fun toEpochMillisUtcMidnight(iso: String): Long? {
        val m = DATE_RE.matchEntire(toDateOnly(iso)) ?: return null
        val y = m.groupValues[1].toIntOrNull() ?: return null
        val mo = m.groupValues[2].toIntOrNull() ?: return null
        val d = m.groupValues[3].toIntOrNull() ?: return null
        if (!isValidDate(y, mo, d)) return null
        return daysFromCivil(y, mo, d) * MILLIS_PER_DAY
    }

    /** Converts UTC epoch millis (from M3 DatePicker) back to `YYYY-MM-DD`. */
    fun fromEpochMillisUtcDate(millis: Long): String {
        val days = floorDiv(millis, MILLIS_PER_DAY)
        val (y, mo, d) = civilFromDays(days)
        return "${pad4(y)}-${pad2(mo)}-${pad2(d)}"
    }

    private fun isValidDate(year: Int, month: Int, day: Int): Boolean {
        if (year !in 1..9999 || month !in 1..12) return false
        return day in 1..daysInMonth(year, month)
    }

    private fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

    private fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> 0
    }

    // Howard Hinnant's civil calendar algorithms (days since 1970-01-01).
    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = year.toLong() - if (month <= 2) 1L else 0L
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val m = month.toLong()
        val doy = (153 * (m + if (m > 2) -3 else 9) + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    private fun civilFromDays(z: Long): Triple<Int, Int, Int> {
        val shifted = z + 719468
        val era = (if (shifted >= 0) shifted else shifted - 146096) / 146097
        val doe = shifted - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val m = (mp + if (mp < 10) 3 else -9).toInt()
        val year = (y + if (m <= 2) 1 else 0).toInt()
        return Triple(year, m, d)
    }

    private fun floorDiv(a: Long, b: Long): Long {
        var r = a / b
        if ((a xor b) < 0 && r * b != a) r--
        return r
    }

    private fun pad2(n: Int): String = n.toString().padStart(2, '0')
    private fun pad4(n: Int): String = n.toString().padStart(4, '0')

    private const val MILLIS_PER_DAY = 86_400_000L
}

/** Result of one extraction attempt against an on-device LLM backend. */
sealed interface ExtractionResult {
    data class Success(val draft: CalendarEventDraft) : ExtractionResult
    data object NoEvent : ExtractionResult
    data class Failure(val reason: String, val isNoModel: Boolean = false) : ExtractionResult
}
