package de.franzsw.ctx2cal.extract.domain

import de.franzsw.ctx2cal.core.domain.model.CalendarEventDraft
import de.franzsw.ctx2cal.core.domain.model.IsoDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Single-event JSON contract produced by the on-device model.
 *
 * Expected shape (one event per paste — other dates in the text are ignored;
 * unknown fields may be left empty `""` so the user can fill them in manually):
 * ```
 * { "title": "...", "start": "2026-11-25T10:00:00", "end": "2026-11-25T11:00:00",
 *   "description": "...", "location": "..." }
 * ```
 * `{"noEvent": true}` means the text contains no event.
 */
object EventJsonParser {

    const val MAX_INPUT_CHARS = 2000
    const val MAX_RETRY_RAW_CHARS = 250
    const val MAX_RETRY_ORIGINAL_CHARS = 3500

    data class PromptParts(
        val systemInstruction: String,
        val userPrompt: String,
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val FENCE_RE = Regex("""```(?:json)?\s*\r?\n([\s\S]*?)```""", RegexOption.IGNORE_CASE)
    private val WHITESPACE_RE = Regex("""\s+""")
    private val START_OF_DAY_00_RE = Regex(
        """^(\d{4}-\d{2}-\d{2})[T ]00:00(?::00)?(?:\.\d{1,9})?(?:Z|[+-]\d{2}:?\d{2})?$"""
    )
    private val END_OF_DAY_24_RE = Regex(
        """^(\d{4}-\d{2}-\d{2})[T ]24:00(?::00)?(?:\.\d{1,9})?(?:Z|[+-]\d{2}:?\d{2})?$"""
    )

    sealed interface Parsed {
        data class Event(val draft: CalendarEventDraft) : Parsed
        data object NoEvent : Parsed
        data class Invalid(val reason: String) : Parsed
    }

    /** Strips Markdown code fences / surrounding prose around the JSON object. */
    fun unwrap(raw: String): String {
        val trimmed = raw.trim()
        val fenced = FENCE_RE.find(trimmed)?.groupValues?.get(1)?.trim()
        val candidate = fenced ?: trimmed
        val start = candidate.indexOf('{')
        if (start < 0) return candidate
        // Find matching closing brace while respecting JSON strings/escapes.
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until candidate.length) {
            val c = candidate[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return candidate.substring(start, i + 1)
                    }
                }
            }
        }
        val end = candidate.lastIndexOf('}')
        return if (end > start) candidate.substring(start, end + 1) else candidate
    }

    fun parse(raw: String): Parsed {
        val element = try {
            json.parseToJsonElement(unwrap(raw))
        } catch (e: Exception) {
            return Parsed.Invalid("Malformed JSON: ${e.message ?: "unexpected format"}")
        }
        val obj = element as? JsonObject
            ?: return Parsed.Invalid("Expected a JSON object")

        if (obj["noEvent"].primitiveContent()?.equals("true", ignoreCase = true) == true) {
            return Parsed.NoEvent
        }

        // Non-primitive (object/array) values for scalar fields are invalid, never coerced.
        for (field in listOf("title", "start", "end", "allDay", "description", "location")) {
            val el = obj[field]
            if (el != null && el !is JsonNull && el !is JsonPrimitive) {
                return Parsed.Invalid("Invalid $field: expected a primitive value")
            }
        }

        val title = obj["title"].primitiveContent()
            ?.replace(WHITESPACE_RE, " ")
            ?.trim()
            ?: ""
        val rawStart = obj["start"].primitiveContent()?.trim() ?: ""
        val rawEnd = obj["end"].primitiveContent()?.trim() ?: ""
        val explicitAllDayTrue = obj["allDay"].primitiveContent()?.trim()
            ?.equals("true", ignoreCase = true) ?: false
        val description = obj["description"].primitiveContent()?.trim() ?: ""
        val location = obj["location"].primitiveContent()?.trim() ?: ""

        val (startRaw, endRaw) = normalizeEndOfDay24(rawStart, rawEnd)

        if (startRaw.isNotEmpty() && !IsoDateTime.isValid(startRaw)) {
            return Parsed.Invalid("Invalid start: $startRaw")
        }
        if (endRaw.isNotEmpty() && !IsoDateTime.isValid(endRaw)) {
            return Parsed.Invalid("Invalid end: $endRaw")
        }

        val allDay = explicitAllDayTrue || (
            startRaw.isNotEmpty() &&
                endRaw.isNotEmpty() &&
                IsoDateTime.isDateOnly(startRaw) &&
                IsoDateTime.isDateOnly(endRaw)
            )

        val hasAnyField = title.isNotEmpty() ||
            startRaw.isNotEmpty() ||
            endRaw.isNotEmpty() ||
            description.isNotEmpty() ||
            location.isNotEmpty()
        if (!hasAnyField) {
            return Parsed.Invalid("No event fields found")
        }

        val startNormalized = when {
            startRaw.isEmpty() -> ""
            allDay -> IsoDateTime.toDateOnly(startRaw)
            else -> IsoDateTime.normalize(startRaw)
        }
        val endNormalized = when {
            endRaw.isEmpty() -> ""
            allDay -> IsoDateTime.toDateOnly(endRaw)
            else -> IsoDateTime.normalize(endRaw)
        }

        if (startNormalized.isNotEmpty() && endNormalized.isNotEmpty()) {
            if (!allDay && IsoDateTime.isDateOnly(startNormalized) != IsoDateTime.isDateOnly(endNormalized)) {
                return Parsed.Invalid("Start and end must both include a time (or mark as All day)")
            }
            val cmp = if (allDay) {
                IsoDateTime.toDateOnly(endNormalized).compareTo(IsoDateTime.toDateOnly(startNormalized))
            } else {
                IsoDateTime.compare(endNormalized, startNormalized)
            }
            if (cmp < 0) return Parsed.Invalid("End must not be before start")
        }

        val draft = CalendarEventDraft(
            title = title,
            startIso = startNormalized,
            endIso = endNormalized,
            allDay = allDay,
            description = description,
            location = location,
        )
        return Parsed.Event(draft)
    }

    private fun normalizeEndOfDay24(rawStart: String, rawEnd: String): Pair<String, String> {
        val start00Match = START_OF_DAY_00_RE.matchEntire(rawStart)
        val end24Match = END_OF_DAY_24_RE.matchEntire(rawEnd)
        if (start00Match != null && end24Match != null) {
            val startDate = start00Match.groupValues[1]
            val endDate = end24Match.groupValues[1]
            if (startDate == endDate && IsoDateTime.isValid(startDate)) {
                return startDate to endDate
            }
        }
        return roll24ToNextDay(rawStart) to roll24ToNextDay(rawEnd)
    }

    private fun roll24ToNextDay(value: String): String {
        val m = END_OF_DAY_24_RE.matchEntire(value) ?: return value
        val date = m.groupValues[1]
        if (!IsoDateTime.isValid(date)) return value
        return "${IsoDateTime.nextDate(date)}T00:00:00"
    }

    private fun JsonElement?.primitiveContent(): String? {
        if (this == null || this is JsonNull) return null
        return (this as? JsonPrimitive)?.content
    }

    fun boundInput(text: String): String = text.trim().take(MAX_INPUT_CHARS)

    /** Concise system instruction supporting multilingual, prose/mixed, and partial event extraction. */
    fun buildSystemInstruction(): String =
        """
Extract the main calendar event from the text (any language), if not clear, pick first. Output ONLY single valid JSON—no prose, code fences, or thinking.

Schema: {"title":string,"start":"YYYY-MM-DDTHH:mm:ss"|"YYYY-MM-DD"|"","end":"YYYY-MM-DDTHH:mm:ss"|"YYYY-MM-DD"|"","description":string,"location":string}

Rules:
- Keep text fields in the original language. Resolve relative dates/times against Today and timezone.
- Ignore chatter. Educated guesses may be allowed (e.g. endtime +1h for meeting); use "" for unknown fields.
- For all-day events, use YYYY-MM-DD for both start and end (inclusive last day). Never use 24:00.
- "description": concise agenda, links, or notes only.
- if timezone is mentioned in input, use this
- If zero event details exist, output ONLY: {"noEvent":true}

Examples:
{"title":"Dentist","start":"2026-11-26T14:30:00","end":"2026-11-26T15:30:00","description":"","location":"Main St Clinic"}
{"title":"Conference","start":"2026-11-28","end":"2026-11-29","description":"Annual summit","location":"Berlin"}
{"title":"Team-Abendessen","start":"2026-11-27","end":"","description":"Abends, Uhrzeit offen","location":"Pizzeria Roma"}
        """.trimIndent()

    /** Bounded user message containing date/timezone reference and truncated pasted text. */
    fun buildUserPrompt(text: String, todayIso: String, timeZoneId: String): String {
        val weekdaySuffix = IsoDateTime.dayOfWeekName(todayIso)?.let { " ($it)" } ?: ""
        return """
            Today is $todayIso$weekdaySuffix in timezone $timeZoneId. Resolve relative dates against today.
            Text:
            ${boundInput(text)}
        """.trimIndent()
    }

    fun buildPromptParts(
        text: String,
        todayIso: String,
        timeZoneId: String,
        systemInstruction: String? = null,
    ): PromptParts =
        PromptParts(
            systemInstruction = systemInstruction?.takeIf { it.isNotBlank() } ?: buildSystemInstruction(),
            userPrompt = buildUserPrompt(text, todayIso, timeZoneId),
        )

    /** Combined prompt for single-string backends (e.g. Gemini Nano). */
    fun buildPrompt(
        text: String,
        todayIso: String,
        timeZoneId: String,
        systemInstruction: String? = null,
    ): String {
        val parts = buildPromptParts(text, todayIso, timeZoneId, systemInstruction)
        return "${parts.systemInstruction}\n\n${parts.userPrompt}"
    }

    /** Splits a combined [buildPrompt] output into separate system/user parts for Conversation sessions. */
    fun splitForConversation(prompt: String, systemInstruction: String? = null): PromptParts {
        val system = systemInstruction?.takeIf { it.isNotBlank() } ?: buildSystemInstruction()
        val prefix = "$system\n\n"
        val user = if (prompt.startsWith(prefix)) prompt.removePrefix(prefix) else prompt
        return PromptParts(
            systemInstruction = system,
            userPrompt = user.take(MAX_INPUT_CHARS + 300),
        )
    }

    /** Bounded retry prompt served once after a validation failure. */
    fun buildRetryPrompt(previousRaw: String, reason: String, originalPrompt: String? = null): String = buildString {
        if (!originalPrompt.isNullOrBlank()) {
            appendLine(originalPrompt.take(MAX_RETRY_ORIGINAL_CHARS))
            appendLine()
        }
        appendLine("Your previous output was rejected: $reason. Output ONLY a corrected JSON object for the same event (leave unknown fields as \"\"), no prose, no code fences.")
        appendLine("Previous output:")
        append(previousRaw.trim().take(MAX_RETRY_RAW_CHARS))
    }
}
