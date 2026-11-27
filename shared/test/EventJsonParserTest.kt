package de.franzsw.ctx2cal

import de.franzsw.ctx2cal.extract.domain.EventJsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EventJsonParserTest {

    @Test
    fun `parses a well-formed single-event JSON object`() {
        val raw = """{"title":"Dentist","start":"2026-11-25T10:00:00","end":"2026-11-25T11:00:00","allDay":false,"description":"Check-up","location":"Clinic"}"""
        val parsed = EventJsonParser.parse(raw)
        assertIs<EventJsonParser.Parsed.Event>(parsed)
        assertEquals("Dentist", parsed.draft.title)
        assertEquals("2026-11-25T10:00:00", parsed.draft.startIso)
        assertEquals("Clinic", parsed.draft.location)
    }

    @Test
    fun `strips code fences and surrounding prose`() {
        val raw = "Here it is:\n```json\n{\"title\":\"A\",\"start\":\"2026-01-01T09:00:00\",\"end\":\"2026-01-01T10:00:00\"}\n```\nDone"
        val parsed = EventJsonParser.parse(raw)
        assertIs<EventJsonParser.Parsed.Event>(parsed)
        assertEquals("A", parsed.draft.title)
    }

    @Test
    fun `rejects malformed JSON`() {
        assertIs<EventJsonParser.Parsed.Invalid>(EventJsonParser.parse("{title: dentist"))
    }

    @Test
    fun `rejects empty objects and bad dates`() {
        assertIs<EventJsonParser.Parsed.Invalid>(
            EventJsonParser.parse("""{}""")
        )
        assertIs<EventJsonParser.Parsed.Invalid>(
            EventJsonParser.parse("""{"title":"","start":"","end":"","allDay":false,"description":"","location":""}""")
        )
        assertIs<EventJsonParser.Parsed.Invalid>(
            EventJsonParser.parse("""{"title":"X","start":"next Friday","end":"2026-01-01T10:00:00"}""")
        )
        assertIs<EventJsonParser.Parsed.Invalid>(
            EventJsonParser.parse("""{"title":"X","start":"2026-02-29","end":""}""")
        )
        assertIs<EventJsonParser.Parsed.Invalid>(
            EventJsonParser.parse("""{"title":"X","start":"2026-11-25","end":"2026-11-25T11:00:00","allDay":false}""")
        )
        assertIs<EventJsonParser.Parsed.Invalid>(
            EventJsonParser.parse("""{"title":"X","start":"2026-11-25T12:00:00","end":"2026-11-25T11:00:00","allDay":false}""")
        )
    }

    @Test
    fun `accepts partial events leaving missing fields empty for user completion`() {
        // Missing end time
        val missingEnd = EventJsonParser.parse(
            """{"title":"Dentist","start":"2026-11-26T14:30:00","end":"","allDay":false,"location":"Main St Clinic"}"""
        )
        assertIs<EventJsonParser.Parsed.Event>(missingEnd)
        assertEquals("Dentist", missingEnd.draft.title)
        assertEquals("2026-11-26T14:30:00", missingEnd.draft.startIso)
        assertEquals("", missingEnd.draft.endIso)
        assertEquals("Main St Clinic", missingEnd.draft.location)
        assertTrue(!missingEnd.draft.isValid)

        // Missing title only
        val missingTitle = EventJsonParser.parse(
            """{"start":"2026-01-01T09:00:00","end":"2026-01-01T10:00:00"}"""
        )
        assertIs<EventJsonParser.Parsed.Event>(missingTitle)
        assertEquals("", missingTitle.draft.title)
        assertEquals("2026-01-01T09:00:00", missingTitle.draft.startIso)
        assertEquals("2026-01-01T10:00:00", missingTitle.draft.endIso)
        assertTrue(!missingTitle.draft.isValid)

        // Title and location only, no dates
        val noDates = EventJsonParser.parse(
            """{"title":"Team-Abendessen","start":"","end":null,"allDay":false,"description":"Abends","location":"Pizzeria Roma"}"""
        )
        assertIs<EventJsonParser.Parsed.Event>(noDates)
        assertEquals("Team-Abendessen", noDates.draft.title)
        assertEquals("", noDates.draft.startIso)
        assertEquals("", noDates.draft.endIso)
        assertEquals("Abends", noDates.draft.description)
        assertEquals("Pizzeria Roma", noDates.draft.location)

        // Date-only start without end stays partial non-all-day
        val dateOnlyStart = EventJsonParser.parse(
            """{"title":"Conference","start":"2026-11-27","end":""}"""
        )
        assertIs<EventJsonParser.Parsed.Event>(dateOnlyStart)
        assertEquals("2026-11-27", dateOnlyStart.draft.startIso)
        assertEquals("", dateOnlyStart.draft.endIso)
        assertTrue(!dateOnlyStart.draft.allDay)
        assertTrue(!dateOnlyStart.draft.isValid)

        // Both dates date-only infer allDay = true even without allDay field
        val bothDateOnly = EventJsonParser.parse(
            """{"title":"Workshop","start":"2026-11-27","end":"2026-11-28"}"""
        )
        assertIs<EventJsonParser.Parsed.Event>(bothDateOnly)
        assertEquals("2026-11-27", bothDateOnly.draft.startIso)
        assertEquals("2026-11-28", bothDateOnly.draft.endIso)
        assertTrue(bothDateOnly.draft.allDay)
        assertTrue(bothDateOnly.draft.isValid)
    }

    @Test
    fun `honours the noEvent marker`() {
        assertIs<EventJsonParser.Parsed.NoEvent>(EventJsonParser.parse("""{"noEvent": true}"""))
    }

    @Test
    fun `prompt injects today with weekday and timezone for relative dates`() {
        val prompt = EventJsonParser.buildPrompt("Lunch next Friday", "2026-11-25", "Europe/Berlin")
        assertTrue(prompt.contains("2026-11-25 (Wednesday)"))
        assertTrue(prompt.contains("Europe/Berlin"))
        assertTrue(prompt.contains("next Friday"))
    }

    @Test
    fun `system instruction covers multilingual all-day date-only and partial events without allDay field`() {
        val instruction = EventJsonParser.buildSystemInstruction()
        assertTrue(instruction.contains("Output ONLY"))
        assertTrue(instruction.contains("prose"))
        assertTrue(instruction.contains("language", ignoreCase = true))
        assertTrue(!instruction.contains("\"allDay\""))
        assertTrue(instruction.contains("YYYY-MM-DD"))
        assertTrue(instruction.contains("inclusive last day"))
        assertTrue(instruction.contains("Never use 24:00"))
        assertTrue(instruction.contains("Examples:"))
        assertTrue(instruction.contains("2026-11-26T14:30:00"))
        assertTrue(instruction.contains("2026-11-28"))
        assertTrue(instruction.contains("""{"noEvent":true}"""))
    }

    @Test
    fun `rejects non-primitive scalar fields without crashing`() {
        assertIs<EventJsonParser.Parsed.Invalid>(
            EventJsonParser.parse(
                """{"title":{"first":"Dentist"},"start":"2026-11-25T10:00:00","end":"2026-11-25T11:00:00"}"""
            )
        )
        assertIs<EventJsonParser.Parsed.Invalid>(
            EventJsonParser.parse(
                """{"title":"X","start":["2026-11-25T10:00:00"],"end":"2026-11-25T11:00:00"}"""
            )
        )
        assertIs<EventJsonParser.Parsed.Invalid>(
            EventJsonParser.parse(
                """{"title":"X","start":"2026-11-25T10:00:00","end":{"value":"2026-11-25T11:00:00"}}"""
            )
        )
        assertIs<EventJsonParser.Parsed.Invalid>(
            EventJsonParser.parse(
                """{"title":"X","start":"2026-11-25T10:00:00","end":"2026-11-25T11:00:00","allDay":{"flag":true}}"""
            )
        )
        assertIs<EventJsonParser.Parsed.Invalid>(
            EventJsonParser.parse(
                """{"title":"X","start":"2026-11-25T10:00:00","end":"2026-11-25T11:00:00","description":["a"]}"""
            )
        )
        assertIs<EventJsonParser.Parsed.Invalid>(
            EventJsonParser.parse(
                """{"title":"X","start":"2026-11-25T10:00:00","end":"2026-11-25T11:00:00","location":{"city":"Berlin"}}"""
            )
        )
        // Null stays missing/optional, not a non-primitive crash.
        assertIs<EventJsonParser.Parsed.Event>(
            EventJsonParser.parse(
                """{"title":"X","start":"2026-11-25T10:00:00","end":"2026-11-25T11:00:00","location":null,"description":null}"""
            )
        )
    }

    @Test
    fun `unwrap respects braces inside strings`() {
        val raw = """{"title":"Meet {team} now }","start":"2026-11-25T10:00:00","end":"2026-11-25T11:00:00","location":"Room {A} }"} trailing prose"""
        val parsed = EventJsonParser.parse(raw)
        assertIs<EventJsonParser.Parsed.Event>(parsed)
        assertEquals("Meet {team} now }", parsed.draft.title)
        assertEquals("Room {A} }", parsed.draft.location)
    }

    @Test
    fun `unwrap strips fences without json tag and surrounding prose without fence`() {
        val fenced = " ```\n{\"title\":\"A\",\"start\":\"2026-01-01T09:00:00\",\"end\":\"2026-01-01T10:00:00\"}\n``` "
        assertIs<EventJsonParser.Parsed.Event>(EventJsonParser.parse(fenced))

        val proseOnly = "Here is the event: {\"title\":\"B\",\"start\":\"2026-01-01T09:00:00\",\"end\":\"2026-01-01T10:00:00\"} hope that helps"
        val parsed = EventJsonParser.parse(proseOnly)
        assertIs<EventJsonParser.Parsed.Event>(parsed)
        assertEquals("B", parsed.draft.title)
    }

    @Test
    fun `retry prompt includes original context`() {
        val original = EventJsonParser.buildPrompt("Lunch next Friday", "2026-11-25", "Europe/Berlin")
        val retry = EventJsonParser.buildRetryPrompt(
            previousRaw = """{"title":""}""",
            reason = "Missing title",
            originalPrompt = original,
        )
        assertTrue(retry.contains(original))
        assertTrue(retry.contains("Missing title"))
        assertTrue(retry.contains("""{"title":""}"""))

        val withoutOriginal = EventJsonParser.buildRetryPrompt(
            previousRaw = "bad output",
            reason = "Malformed JSON",
        )
        assertTrue(withoutOriginal.contains("bad output"))
        assertTrue(withoutOriginal.contains("Malformed JSON"))
        assertTrue(!withoutOriginal.contains("Today is"))
    }

    @Test
    fun `oversized pasted text is truncated to MAX_INPUT_CHARS`() {
        val oversized = "A".repeat(EventJsonParser.MAX_INPUT_CHARS + 500) + "TAIL_MARKER"
        val userPrompt = EventJsonParser.buildUserPrompt(oversized, "2026-11-25", "UTC")
        assertTrue(!userPrompt.contains("TAIL_MARKER"))
        assertTrue(userPrompt.length <= EventJsonParser.MAX_INPUT_CHARS + 120)

        val fullPrompt = EventJsonParser.buildPrompt(oversized, "2026-11-25", "UTC")
        assertTrue(!fullPrompt.contains("TAIL_MARKER"))
    }

    @Test
    fun `prompt parts include one-shot example and splitForConversation avoids duplicating system instruction`() {
        val parts = EventJsonParser.buildPromptParts("Team lunch tomorrow 12:00", "2026-11-25", "Europe/Berlin")
        assertTrue(parts.systemInstruction.contains("Examples:"))
        assertTrue(parts.systemInstruction.contains("Output ONLY"))
        assertTrue(parts.userPrompt.contains("Team lunch tomorrow 12:00"))
        assertTrue(!parts.userPrompt.contains("Examples:"))

        val combined = EventJsonParser.buildPrompt("Team lunch tomorrow 12:00", "2026-11-25", "Europe/Berlin")
        val split = EventJsonParser.splitForConversation(combined)
        assertEquals(parts.systemInstruction, split.systemInstruction)
        assertEquals(parts.userPrompt, split.userPrompt)
    }

    @Test
    fun `retry prompt bounds oversized previous output and original prompt`() {
        val hugeRaw = "X".repeat(1000) + "RAW_END"
        val hugeOriginal = "Y".repeat(5000) + "ORIG_END"
        val retry = EventJsonParser.buildRetryPrompt(hugeRaw, "Malformed JSON", hugeOriginal)
        assertTrue(!retry.contains("RAW_END"))
        assertTrue(!retry.contains("ORIG_END"))
    }

    @Test
    fun `custom system instruction overrides default in buildPromptParts buildPrompt and splitForConversation`() {
        val custom = "Custom system instruction for testing."
        val parts = EventJsonParser.buildPromptParts(
            text = "Team lunch tomorrow 12:00",
            todayIso = "2026-11-25",
            timeZoneId = "Europe/Berlin",
            systemInstruction = custom,
        )
        assertEquals(custom, parts.systemInstruction)
        assertTrue(parts.userPrompt.contains("Team lunch tomorrow 12:00"))
        assertTrue(parts.userPrompt.contains("2026-11-25"))

        val combined = EventJsonParser.buildPrompt(
            text = "Team lunch tomorrow 12:00",
            todayIso = "2026-11-25",
            timeZoneId = "Europe/Berlin",
            systemInstruction = custom,
        )
        assertTrue(combined.startsWith("$custom\n\n"))

        val split = EventJsonParser.splitForConversation(combined, systemInstruction = custom)
        assertEquals(custom, split.systemInstruction)
        assertEquals(parts.userPrompt, split.userPrompt)

        val blankFallback = EventJsonParser.buildPromptParts(
            text = "Team lunch",
            todayIso = "2026-11-25",
            timeZoneId = "UTC",
            systemInstruction = "   ",
        )
        assertEquals(EventJsonParser.buildSystemInstruction(), blankFallback.systemInstruction)
    }

    @Test
    fun `collapses whitespace and newlines in title`() {
        val raw = """{"title":"  Team\n  Sync\tMeeting  ","start":"2026-11-25T10:00:00","end":"2026-11-25T11:00:00"}"""
        val parsed = EventJsonParser.parse(raw)
        assertIs<EventJsonParser.Parsed.Event>(parsed)
        assertEquals("Team Sync Meeting", parsed.draft.title)
    }

    @Test
    fun `normalizes same-day 00-00 to 24-00 into date-only all-day draft`() {
        val raw = """{"title":"Holiday","start":"2026-11-28T00:00:00","end":"2026-11-28T24:00:00","allDay":false}"""
        val parsed = EventJsonParser.parse(raw)
        assertIs<EventJsonParser.Parsed.Event>(parsed)
        assertEquals("2026-11-28", parsed.draft.startIso)
        assertEquals("2026-11-28", parsed.draft.endIso)
        assertTrue(parsed.draft.allDay)
        assertTrue(parsed.draft.isValid)

        val shortTime = """{"title":"Holiday","start":"2026-11-28T00:00","end":"2026-11-28T24:00"}"""
        val parsedShort = EventJsonParser.parse(shortTime)
        assertIs<EventJsonParser.Parsed.Event>(parsedShort)
        assertEquals("2026-11-28", parsedShort.draft.startIso)
        assertEquals("2026-11-28", parsedShort.draft.endIso)
        assertTrue(parsedShort.draft.allDay)
    }

    @Test
    fun `rolls non-all-day T24-00 end over to next day 00-00-00`() {
        val raw = """{"title":"Late Shift","start":"2026-11-28T18:00:00","end":"2026-11-28T24:00:00"}"""
        val parsed = EventJsonParser.parse(raw)
        assertIs<EventJsonParser.Parsed.Event>(parsed)
        assertEquals("2026-11-28T18:00:00", parsed.draft.startIso)
        assertEquals("2026-11-29T00:00:00", parsed.draft.endIso)
        assertTrue(!parsed.draft.allDay)
        assertTrue(parsed.draft.isValid)
    }

    @Test
    fun `still honors explicit allDay true for legacy or custom prompts`() {
        val raw = """{"title":"Day Off","start":"2026-11-28T09:00:00","end":"2026-11-28T17:00:00","allDay":true}"""
        val parsed = EventJsonParser.parse(raw)
        assertIs<EventJsonParser.Parsed.Event>(parsed)
        assertEquals("2026-11-28", parsed.draft.startIso)
        assertEquals("2026-11-28", parsed.draft.endIso)
        assertTrue(parsed.draft.allDay)
    }

    @Test
    fun `parses when closing brace is missing but all fields are complete`() {
        val raw = """{"title":"Dentist","start":"2026-11-25T10:00:00","end":"2026-11-25T11:00:00","location":"Clinic" """
        val parsed = EventJsonParser.parse(raw)
        assertIs<EventJsonParser.Parsed.Event>(parsed)
        assertEquals("Dentist", parsed.draft.title)
        assertEquals("2026-11-25T10:00:00", parsed.draft.startIso)
        assertEquals("2026-11-25T11:00:00", parsed.draft.endIso)
        assertEquals("Clinic", parsed.draft.location)
        assertTrue(parsed.draft.isValid)
    }

    @Test
    fun `recovers complete fields when output is truncated mid-value`() {
        // Truncated inside the "end" string: the incomplete field is dropped so
        // the already-sent title/start survive as a partial event.
        val raw = """{"title":"Dentist","start":"2026-11-25T10:00:00","end":"2026-11-25T11:00:"""
        val parsed = EventJsonParser.parse(raw)
        assertIs<EventJsonParser.Parsed.Event>(parsed)
        assertEquals("Dentist", parsed.draft.title)
        assertEquals("2026-11-25T10:00:00", parsed.draft.startIso)
        assertEquals("", parsed.draft.endIso)
        assertTrue(!parsed.draft.isValid)
    }

    @Test
    fun `recovers complete fields when output is truncated mid-key`() {
        val raw = """{"title":"Dentist","start":"2026-11-25T10:00:00","end":"2026-11-25T11:00:00","descri"""
        val parsed = EventJsonParser.parse(raw)
        assertIs<EventJsonParser.Parsed.Event>(parsed)
        assertEquals("Dentist", parsed.draft.title)
        assertEquals("2026-11-25T10:00:00", parsed.draft.startIso)
        assertEquals("2026-11-25T11:00:00", parsed.draft.endIso)
        assertEquals("", parsed.draft.description)
    }

    @Test
    fun `recovers complete fields when output is truncated after a comma`() {
        val raw = """{"title":"Dentist","start":"2026-11-25T10:00:00","""
        val parsed = EventJsonParser.parse(raw)
        assertIs<EventJsonParser.Parsed.Event>(parsed)
        assertEquals("Dentist", parsed.draft.title)
        assertEquals("2026-11-25T10:00:00", parsed.draft.startIso)
        assertEquals("", parsed.draft.endIso)
    }

    @Test
    fun `recovers fields with escaped quotes and braces when truncated`() {
        val raw = """{"title":"Meet \"team\" {now}","start":"2026-11-25T10:00:00","end":"2026-11-25T11:00:00","location":"Room {A}"""
        val parsed = EventJsonParser.parse(raw)
        assertIs<EventJsonParser.Parsed.Event>(parsed)
        assertEquals("Meet \"team\" {now}", parsed.draft.title)
        assertEquals("2026-11-25T10:00:00", parsed.draft.startIso)
        // Truncated inside location: dropped, not closed blindly.
        assertEquals("", parsed.draft.location)
    }

    @Test
    fun `honours noEvent when closing brace is missing`() {
        assertIs<EventJsonParser.Parsed.NoEvent>(EventJsonParser.parse("""{"noEvent":true"""))
    }

    @Test
    fun `strips unclosed code fences`() {
        val fenced = "```json\n{\"title\":\"A\",\"start\":\"2026-01-01T09:00:00\",\"end\":\"2026-01-01T10:00:00\"}"
        val parsed = EventJsonParser.parse(fenced)
        assertIs<EventJsonParser.Parsed.Event>(parsed)
        assertEquals("A", parsed.draft.title)

        val sameLine = "```json {\"title\":\"B\",\"start\":\"2026-01-01T09:00:00\",\"end\":\"2026-01-01T10:00:00\"}"
        val parsedSameLine = EventJsonParser.parse(sameLine)
        assertIs<EventJsonParser.Parsed.Event>(parsedSameLine)
        assertEquals("B", parsedSameLine.draft.title)

        val truncatedFence = "```json\n{\"title\":\"C\",\"start\":\"2026-01-01T09:00:00\",\"end\":\"2026-01-01T10:00:"
        val parsedTruncated = EventJsonParser.parse(truncatedFence)
        assertIs<EventJsonParser.Parsed.Event>(parsedTruncated)
        assertEquals("C", parsedTruncated.draft.title)
        assertEquals("", parsedTruncated.draft.endIso)
    }

    @Test
    fun `recovers fields with trailing comma missing commas or single quotes`() {
        val trailingComma = """{"title":"X","start":"2026-11-25T10:00:00","end":"2026-11-25T11:00:00",}"""
        val parsedTrailing = EventJsonParser.parse(trailingComma)
        assertIs<EventJsonParser.Parsed.Event>(parsedTrailing)
        assertEquals("X", parsedTrailing.draft.title)

        val missingCommas = """{"title":"X" "start":"2026-11-25T10:00:00" "end":"2026-11-25T11:00:00"}"""
        val parsedMissing = EventJsonParser.parse(missingCommas)
        assertIs<EventJsonParser.Parsed.Event>(parsedMissing)
        assertEquals("2026-11-25T10:00:00", parsedMissing.draft.startIso)

        val singleQuoted = "{'title':'X','start':'2026-11-25T10:00:00','end':'2026-11-25T11:00:00'"
        val parsedSingle = EventJsonParser.parse(singleQuoted)
        assertIs<EventJsonParser.Parsed.Event>(parsedSingle)
        assertEquals("X", parsedSingle.draft.title)
    }

    @Test
    fun `accepts capitalized field names`() {
        val raw = """{"Title":"X","Start":"2026-11-25T10:00:00","End":"2026-11-25T11:00:00"}"""
        val parsed = EventJsonParser.parse(raw)
        assertIs<EventJsonParser.Parsed.Event>(parsed)
        assertEquals("X", parsed.draft.title)
        assertEquals("2026-11-25T10:00:00", parsed.draft.startIso)
    }

    @Test
    fun `truncates anything before first brace and after last matching brace`() {
        // Rule: output must start with "{" and end with "}". Anything before the
        // first "{" (e.g. "My Output: ") and after the matching "}" is cut off.
        val prefixed = "My Output: {\"title\":\"A\",\"start\":\"2026-01-01T09:00:00\",\"end\":\"2026-01-01T10:00:00\"} etc."
        val parsed = EventJsonParser.parse(prefixed)
        assertIs<EventJsonParser.Parsed.Event>(parsed)
        assertEquals("A", parsed.draft.title)
        assertEquals("2026-01-01T09:00:00", parsed.draft.startIso)
        assertEquals("2026-01-01T10:00:00", parsed.draft.endIso)

        // Same prefix rule combined with a missing closing brace: complete
        // fields after the first "{" are still recovered.
        val truncatedPrefixed = "My Output: {\"title\":\"B\",\"start\":\"2026-01-01T09:00:00\",\"end\":\"2026-01-01T10:00:00\""
        val parsedTruncated = EventJsonParser.parse(truncatedPrefixed)
        assertIs<EventJsonParser.Parsed.Event>(parsedTruncated)
        assertEquals("B", parsedTruncated.draft.title)
        assertEquals("2026-01-01T10:00:00", parsedTruncated.draft.endIso)
    }

    @Test
    fun `does not fabricate fields from truncated incomplete output`() {
        // No complete field was sent yet: nothing to recover.
        assertIs<EventJsonParser.Parsed.Invalid>(EventJsonParser.parse("""{"title":"Dent"""))
        // A cut-off boolean literal is not a complete noEvent marker.
        assertIs<EventJsonParser.Parsed.Invalid>(EventJsonParser.parse("""{"noEvent":tru"""))
        // Still invalid when there is no object at all.
        assertIs<EventJsonParser.Parsed.Invalid>(EventJsonParser.parse("no json here"))
        assertIs<EventJsonParser.Parsed.Invalid>(EventJsonParser.parse("{title: dentist"))
    }
}
