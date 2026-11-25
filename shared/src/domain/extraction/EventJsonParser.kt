package com.example.ctx2cal.domain.extraction

import com.example.ctx2cal.domain.model.CalendarEventDraft
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    sealed interface Parsed {
        data class Event(val draft: CalendarEventDraft) : Parsed
        data object NoEvent : Parsed
        data class Invalid(val reason: String) : Parsed
    }

    /** Strips Markdown code fences / surrounding prose around the JSON object. */
    fun unwrap(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```")) {
            val nl = s.indexOf('\n')
            if (nl >= 0) s = s.substring(nl + 1)
            if (s.endsWith("```")) s = s.substring(0, s.length - 3).trim()
        }
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        return if (start >= 0 && end > start) s.substring(start, end + 1) else s
    }

    fun parse(raw: String): Parsed {
        val element = try {
            json.parseToJsonElement(unwrap(raw))
        } catch (e: Exception) {
            return Parsed.Invalid("Malformed JSON: ${e.message}")
        }
        if (element !is kotlinx.serialization.json.JsonObject) {
            return Parsed.Invalid("Expected a JSON object")
        }
        val obj = element.jsonObject
        if (obj["noEvent"]?.jsonPrimitive?.let { it !is JsonNull && it.content == "true" } == true) {
            return Parsed.NoEvent
        }
        val title = obj["title"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content?.trim() ?: ""
        if (title.isBlank()) return Parsed.Invalid("Missing title")

        val startRaw = obj["start"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content?.trim() ?: ""
        val endRaw = obj["end"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content?.trim() ?: ""
        if (!com.example.ctx2cal.domain.model.IsoDateTime.isValid(startRaw)) {
            return Parsed.Invalid("Missing or invalid start: $startRaw")
        }
        if (!com.example.ctx2cal.domain.model.IsoDateTime.isValid(endRaw)) {
            return Parsed.Invalid("Missing or invalid end: $endRaw")
        }
        val allDay = obj["allDay"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content?.trim()
            ?.let { it.equals("true", true) } ?: false
        val description = obj["description"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content ?: ""
        val location = obj["location"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content ?: ""

        val draft = CalendarEventDraft(
            title = title,
            startIso = com.example.ctx2cal.domain.model.IsoDateTime.normalize(startRaw),
            endIso = com.example.ctx2cal.domain.model.IsoDateTime.normalize(endRaw),
            allDay = allDay,
            description = description,
            location = location,
        )
        return draft.validationError()?.let { Parsed.Invalid(it) } ?: Parsed.Event(draft)
    }

    /** Prompt sections shared by every on-device backend. */
    fun buildPrompt(text: String, todayIso: String, timeZoneId: String): String =
        """
        You extract exactly one calendar event from free text. Output ONLY a JSON object, no prose, no code fences.
        Fields: title (string), start (ISO-8601 local datetime, e.g. 2026-11-25T10:00:00), end (ISO-8601 local datetime),
        allDay (boolean), description (string), location (string).
        If the text contains no event, output exactly: {"noEvent": true}
        Resolve relative dates ("next Friday", "tomorrow") against today.
        Today is $todayIso in timezone $timeZoneId.
        Text:
        $text
        """.trimIndent()

    /** Retry prompt served once after a validation failure. */
    fun buildRetryPrompt(previousRaw: String, reason: String): String =
        """
        Your previous output was rejected: $reason. Output ONLY a corrected JSON object for the same event, no prose, no code fences.
        Previous output:
        ${previousRaw.take(500)}
        """.trimIndent()
}
