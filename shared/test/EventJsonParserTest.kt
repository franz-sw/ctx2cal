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

        // Date-only start without end
        val dateOnlyStart = EventJsonParser.parse(
            """{"title":"Conference","start":"2026-11-27","end":"","allDay":false}"""
        )
        assertIs<EventJsonParser.Parsed.Event>(dateOnlyStart)
        assertEquals("2026-11-27", dateOnlyStart.draft.startIso)
        assertEquals("", dateOnlyStart.draft.endIso)

        // Both dates date-only without allDay (times unknown, user completes in form)
        val bothDateOnly = EventJsonParser.parse(
            """{"title":"Workshop","start":"2026-11-27","end":"2026-11-28","allDay":false}"""
        )
        assertIs<EventJsonParser.Parsed.Event>(bothDateOnly)
        assertEquals("2026-11-27", bothDateOnly.draft.startIso)
        assertEquals("2026-11-28", bothDateOnly.draft.endIso)
        assertTrue(!bothDateOnly.draft.isValid)
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
    fun `system instruction covers multilingual mixed text and partial events`() {
        val instruction = EventJsonParser.buildSystemInstruction()
        assertTrue(instruction.contains("Output ONLY"))
        assertTrue(instruction.contains("prose"))
        assertTrue(instruction.contains("mixed"))
        assertTrue(instruction.contains("language", ignoreCase = true))
        assertTrue(instruction.contains("Never guess"))
        assertTrue(instruction.contains("Example:"))
        assertTrue(instruction.contains("Partial example:"))
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
        assertTrue(parts.systemInstruction.contains("Example:"))
        assertTrue(parts.systemInstruction.contains("Output ONLY"))
        assertTrue(parts.userPrompt.contains("Team lunch tomorrow 12:00"))
        assertTrue(!parts.userPrompt.contains("Example:"))

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
}
