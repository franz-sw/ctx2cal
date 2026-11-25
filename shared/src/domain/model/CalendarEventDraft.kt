package com.example.ctx2cal.domain.model

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
    /**
     * Minimal validation shared by extraction, edit form, and ICS/share outputs.
     * Returns null when valid, otherwise a human-readable reason.
     */
    fun validationError(): String? = when {
        title.isBlank() -> "Title must not be blank"
        startIso.isBlank() || endIso.isBlank() -> "Start and end are required"
        !IsoDateTime.isValid(startIso) -> "Start time is not a valid ISO date/time"
        !IsoDateTime.isValid(endIso) -> "End time is not a valid ISO date/time"
        endIso < startIso -> "End must not be before start"
        else -> null
    }

    val isValid: Boolean get() = validationError() == null
}

/** Hand-rolled ISO-8601 validation (no kotlinx-datetime on the classpath). */
object IsoDateTime {
    private val DATE_RE = Regex("""\d{4}-\d{2}-\d{2}""")
    private val DATETIME_RE = Regex("""\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}(:\d{2}(\.\d{1,3})?)?(Z|[+-]\d{2}:?\d{2})?""")

    fun isValid(value: String): Boolean {
        val v = value.trim()
        return DATE_RE.matches(v) || DATETIME_RE.matches(v)
    }

    /** Normalizes to `YYYY-MM-DDTHH:mm:ss` (date part kept for all-day values). */
    fun normalize(value: String): String {
        val v = value.trim().replace(' ', 'T')
        return if (DATE_RE.matches(v)) v else v.take(19)
    }

    /** `2026-11-25T10:00:00` -> `20261125T100000`; `2026-11-25` -> `20261125`. */
    fun toBasic(value: String): String = normalize(value).replace("-", "").replace(":", "").replace("T", "T")
}

/** Result of one extraction attempt against an on-device LLM backend. */
sealed interface ExtractionResult {
    data class Success(val draft: CalendarEventDraft) : ExtractionResult
    data object NoEvent : ExtractionResult
    data class Failure(val reason: String, val isNoModel: Boolean = false) : ExtractionResult
}

/** What on-device model capability is currently available. */
sealed interface ModelStatus {
    data object NanoAvailable : ModelStatus
    data object NanoDownloadable : ModelStatus
    data object NanoDownloading : ModelStatus
    data object NanoUnavailable : ModelStatus
    data class LiteRtReady(val modelPath: String) : ModelStatus
    data object LiteRtNotDownloaded : ModelStatus
    data class Downloading(val progressBytes: Long?, val totalBytes: Long?) : ModelStatus
    data object None : ModelStatus
}

/** A `.litertlm` model offered for download (see README for the exact v1 default). */
data class ModelCandidate(
    val name: String,
    val url: String,
    val approxSizeBytes: Long?,
)
