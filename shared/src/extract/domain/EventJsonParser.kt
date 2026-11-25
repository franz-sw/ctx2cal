package de.franzsw.ctx2cal.extract.domain

import de.franzsw.ctx2cal.core.domain.model.CalendarEventDraft
import de.franzsw.ctx2cal.core.domain.model.IsoDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Strict single-event JSON contract produced by the on-device model.
 *
 * Expected shape (one event per paste — other dates in the text are ignored):
 * ```
 * { "title": "...", "start": "2026-11-25T10:00:00", "end": "2026-11-25T11:00:00",
 *   "allDay": false, "description": "...", "location": "..." }
 * ```
 * `{"noEvent": true}` means the text contains no event.
 */
object EventJsonParser {

    const val MAX_INPUT_CHARS = 2000
    const val MAX_RETRY_RAW_CHARS = 250
    const val MAX_RETRY_ORIGINAL_CHARS = 2600

    data class PromptParts(
        val systemInstruction: String,
        val userPrompt: String,
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val FENCE_RE = Regex("""```(?:json)?\s*\r?\n([\s\S]*?)```""", RegexOption.IGNORE_CASE)

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

        val title = obj["title"].primitiveContent()?.trim() ?: ""
        if (title.isBlank()) return Parsed.Invalid("Missing title")

        val startRaw = obj["start"].primitiveContent()?.trim() ?: ""
        val endRaw = obj["end"].primitiveContent()?.trim() ?: ""
        if (!IsoDateTime.isValid(startRaw)) {
            return Parsed.Invalid("Missing or invalid start: $startRaw")
        }
        if (!IsoDateTime.isValid(endRaw)) {
            return Parsed.Invalid("Missing or invalid end: $endRaw")
        }

        val allDay = obj["allDay"].primitiveContent()?.trim()
            ?.equals("true", ignoreCase = true) ?: false
        val description = obj["description"].primitiveContent() ?: ""
        val location = obj["location"].primitiveContent() ?: ""

        val startNormalized = if (allDay) IsoDateTime.toDateOnly(startRaw) else IsoDateTime.normalize(startRaw)
        val endNormalized = if (allDay) IsoDateTime.toDateOnly(endRaw) else IsoDateTime.normalize(endRaw)

        val draft = CalendarEventDraft(
            title = title,
            startIso = startNormalized,
            endIso = endNormalized,
            allDay = allDay,
            description = description,
            location = location,
        )
        return draft.validationError()?.let { Parsed.Invalid(it) } ?: Parsed.Event(draft)
    }

    private fun JsonElement?.primitiveContent(): String? {
        if (this == null || this is JsonNull) return null
        return (this as? JsonPrimitive)?.content
    }

    fun boundInput(text: String): String = text.trim().take(MAX_INPUT_CHARS)

    /** Concise system instruction with schema and one-shot JSON example for chat-templated models. */
    fun buildSystemInstruction(): String =
        """
        You extract exactly one calendar event from text. Output ONLY a single JSON object and stop. No prose, no code fences, no thinking.
        Schema: {"title":string,"start":"YYYY-MM-DDTHH:mm:ss" or "YYYY-MM-DD","end":"YYYY-MM-DDTHH:mm:ss" or "YYYY-MM-DD","allDay":boolean,"description":string,"location":string}
        Example: {"title":"Dentist","start":"2026-11-26T14:30:00","end":"2026-11-26T15:30:00","allDay":false,"description":"","location":"Main St Clinic"}
        If there is no event, output exactly: {"noEvent":true}
        """.trimIndent()

    /** Bounded user message containing date/timezone reference and truncated pasted text. */
    fun buildUserPrompt(text: String, todayIso: String, timeZoneId: String): String =
        """
        Today is $todayIso in timezone $timeZoneId. Resolve relative dates against today.
        Text:
        ${boundInput(text)}
        """.trimIndent()

    fun buildPromptParts(text: String, todayIso: String, timeZoneId: String): PromptParts =
        PromptParts(
            systemInstruction = buildSystemInstruction(),
            userPrompt = buildUserPrompt(text, todayIso, timeZoneId),
        )

    /** Combined prompt for single-string backends (e.g. Gemini Nano). */
    fun buildPrompt(text: String, todayIso: String, timeZoneId: String): String {
        val parts = buildPromptParts(text, todayIso, timeZoneId)
        return "${parts.systemInstruction}\n\n${parts.userPrompt}"
    }

    /** Splits a combined [buildPrompt] output into separate system/user parts for Conversation sessions. */
    fun splitForConversation(prompt: String): PromptParts {
        val system = buildSystemInstruction()
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
        appendLine("Your previous output was rejected: $reason. Output ONLY a corrected JSON object for the same event, no prose, no code fences.")
        appendLine("Previous output:")
        append(previousRaw.trim().take(MAX_RETRY_RAW_CHARS))
    }
}
