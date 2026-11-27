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
    private val FENCE_RE = Regex("""```(?:json)?[ \t]*\r?\n?([\s\S]*?)```""", RegexOption.IGNORE_CASE)
    private val FENCE_LANGUAGE_RE = Regex("""[A-Za-z0-9_-]+""")
    private val NUMBER_RE = Regex("""-?\d+(\.\d+)?([eE][+-]?\d+)?""")
    private val KNOWN_FIELDS = setOf("title", "start", "end", "allDay", "description", "location", "noEvent")
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

    /**
     * Strips Markdown code fences (closed or truncated/unclosed) and surrounding
     * prose, truncating everything before the first `{` and after its matching
     * `}` when present. Truncated objects without a matching `}` are returned
     * from the first `{` to the end so [parse] can salvage complete fields.
     */
    fun unwrap(raw: String): String {
        val trimmed = raw.trim()
        val fenced = FENCE_RE.find(trimmed)?.groupValues?.get(1)?.trim()
        val candidate = when {
            fenced != null -> fenced
            trimmed.contains("```") -> stripUnclosedFence(trimmed)
            else -> trimmed
        }
        return extractObject(candidate)
    }

    private fun stripUnclosedFence(text: String): String {
        val fenceStart = text.indexOf("```")
        if (fenceStart < 0) return text
        val content = stripFenceOpening(text.substring(fenceStart + 3))
        val closing = content.indexOf("```")
        return (if (closing >= 0) content.substring(0, closing) else content).trim()
    }

    private fun stripFenceOpening(afterOpening: String): String {
        val newline = afterOpening.indexOf('\n')
        if (newline >= 0) {
            val firstLine = afterOpening.substring(0, newline).trim()
            if (firstLine.isEmpty() || firstLine.matches(FENCE_LANGUAGE_RE)) {
                return afterOpening.substring(newline + 1)
            }
        } else {
            val brace = afterOpening.indexOf('{')
            if (brace > 0) {
                if (afterOpening.substring(0, brace).trim().matches(FENCE_LANGUAGE_RE)) {
                    return afterOpening.substring(brace)
                }
            } else if (afterOpening.trim().matches(FENCE_LANGUAGE_RE)) {
                return ""
            }
        }
        val sameLine = Regex("""^\s*[A-Za-z0-9_-]+\s+(?=\{)""").find(afterOpening)
        if (sameLine != null) return afterOpening.substring(sameLine.range.last + 1)
        return afterOpening
    }

    private fun extractObject(candidate: String): String {
        val start = candidate.indexOf('{')
        if (start < 0) return candidate
        // Find matching closing brace while respecting strings/escapes.
        var depth = 0
        var stringQuote: Char? = null
        var escaped = false
        for (i in start until candidate.length) {
            val c = candidate[i]
            if (stringQuote != null) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == stringQuote) {
                    stringQuote = null
                }
            } else {
                when (c) {
                    '"', '\'' -> stringQuote = c
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return candidate.substring(start, i + 1)
                    }
                }
            }
        }
        // No matching `}` (truncated output): keep from the first `{` so complete
        // fields can still be recovered by salvage in parse().
        return candidate.substring(start).trimEnd()
    }

    fun parse(raw: String): Parsed {
        val unwrapped = unwrap(raw)
        val element = try {
            json.parseToJsonElement(unwrapped)
        } catch (e: Exception) {
            // Model output is often truncated or otherwise malformed: recover
            // every complete field that was already sent before giving up.
            val salvaged = salvageJsonObject(unwrapped)
                ?: return Parsed.Invalid("Malformed JSON: ${e.message ?: "unexpected format"}")
            return validateObject(salvaged)
        }
        val obj = element as? JsonObject
            ?: return Parsed.Invalid("Expected a JSON object")
        return validateObject(obj)
    }

    private fun validateObject(obj: JsonObject): Parsed {
        fun field(name: String): JsonElement? =
            obj[name] ?: obj.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

        if (field("noEvent").primitiveContent()?.equals("true", ignoreCase = true) == true) {
            return Parsed.NoEvent
        }

        // Non-primitive (object/array) values for scalar fields are invalid, never coerced.
        for (fieldName in listOf("title", "start", "end", "allDay", "description", "location")) {
            val el = field(fieldName)
            if (el != null && el !is JsonNull && el !is JsonPrimitive) {
                return Parsed.Invalid("Invalid $fieldName: expected a primitive value")
            }
        }

        val title = field("title").primitiveContent()
            ?.replace(WHITESPACE_RE, " ")
            ?.trim()
            ?: ""
        val rawStart = field("start").primitiveContent()?.trim() ?: ""
        val rawEnd = field("end").primitiveContent()?.trim() ?: ""
        val explicitAllDayTrue = field("allDay").primitiveContent()?.trim()
            ?.equals("true", ignoreCase = true) ?: false
        val description = field("description").primitiveContent()?.trim() ?: ""
        val location = field("location").primitiveContent()?.trim() ?: ""

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

    /**
     * Recovers complete known fields from truncated/malformed JSON, e.g. output
     * that breaks mid-object, mid-key or mid-value, has a trailing comma, or is
     * missing commas/closing braces. Only pairs whose value is complete (closed
     * string, or a full `true`/`false`/`null`/number literal) are kept; the
     * incomplete trailing pair is dropped so earlier fields survive validation.
     * Returns null when no known field could be recovered.
     */
    private fun salvageJsonObject(text: String): JsonObject? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var i = start + 1
        val fields = LinkedHashMap<String, JsonElement>()
        outer@ while (i < text.length) {
            while (i < text.length && (text[i].isWhitespace() || text[i] == ',')) i++
            if (i >= text.length) break
            if (text[i] == '}') break
            // Key must be a complete quoted string; a truncated key ends recovery.
            val (key, afterKey) = parseStringToken(text, i) ?: break@outer
            i = afterKey
            while (i < text.length && text[i].isWhitespace()) i++
            if (i >= text.length || text[i] != ':') break@outer
            i++
            while (i < text.length && text[i].isWhitespace()) i++
            if (i >= text.length) break@outer
            val canonicalKey = KNOWN_FIELDS.firstOrNull { it.equals(key, ignoreCase = true) }
            when (text[i]) {
                '"', '\'' -> {
                    // A truncated string value is dropped, not closed blindly, so a
                    // cut-off datetime cannot poison the fields recovered before it.
                    val (value, afterValue) = parseStringToken(text, i) ?: break@outer
                    i = afterValue
                    if (canonicalKey != null) fields[canonicalKey] = JsonPrimitive(value)
                }
                '{', '[' -> {
                    // Preserve strictness: a non-primitive value for a known scalar
                    // field is invalid; skip balanced values of unknown fields.
                    if (canonicalKey != null) return null
                    i = skipBalancedValue(text, i) ?: break@outer
                }
                else -> {
                    val tokenStart = i
                    while (i < text.length && !text[i].isWhitespace() && text[i] != ',' && text[i] != '}' && text[i] != ']') i++
                    val token = text.substring(tokenStart, i)
                    val isLiteral = token.equals("true", ignoreCase = true) ||
                        token.equals("false", ignoreCase = true) ||
                        token.equals("null", ignoreCase = true)
                    if (!isLiteral && !NUMBER_RE.matches(token)) break@outer
                    if (canonicalKey != null) {
                        fields[canonicalKey] =
                            if (token.equals("null", ignoreCase = true)) JsonNull else JsonPrimitive(token)
                    }
                }
            }
        }
        return if (fields.isEmpty()) null else JsonObject(fields)
    }

    /** Parses a complete quoted string (also single-quoted) at [start]; null when truncated. */
    private fun parseStringToken(text: String, start: Int): Pair<String, Int>? {
        if (start >= text.length) return null
        val quote = text[start]
        if (quote != '"' && quote != '\'') return null
        val sb = StringBuilder()
        var i = start + 1
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' -> {
                    if (i + 1 >= text.length) return null
                    when (val esc = text[i + 1]) {
                        '"' -> sb.append('"')
                        '\'' -> sb.append('\'')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append(12.toChar())
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (i + 5 >= text.length) return null
                            val code = text.substring(i + 2, i + 6).toIntOrNull(16) ?: return null
                            sb.append(code.toChar())
                            i += 4
                        }
                        else -> sb.append(esc)
                    }
                    i += 2
                }
                c == quote -> return sb.toString() to (i + 1)
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return null
    }

    /** Returns the index just past a balanced `{...}`/`[...]` value at [start], or null if truncated. */
    private fun skipBalancedValue(text: String, start: Int): Int? {
        var depth = 0
        var stringQuote: Char? = null
        var escaped = false
        var i = start
        while (i < text.length) {
            val c = text[i]
            if (stringQuote != null) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == stringQuote) stringQuote = null
            } else {
                when (c) {
                    '"', '\'' -> stringQuote = c
                    '{', '[' -> depth++
                    '}', ']' -> {
                        depth--
                        if (depth == 0) return i + 1
                    }
                }
            }
            i++
        }
        return null
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
