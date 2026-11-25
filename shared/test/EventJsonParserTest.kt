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
    fun `rejects missing title and bad dates`() {
        assertIs<EventJsonParser.Parsed.Invalid>(
            EventJsonParser.parse("""{"start":"2026-01-01T09:00:00","end":"2026-01-01T10:00:00"}""")
        )
        assertIs<EventJsonParser.Parsed.Invalid>(
            EventJsonParser.parse("""{"title":"X","start":"next Friday","end":"2026-01-01T10:00:00"}""")
        )
    }

    @Test
    fun `honours the noEvent marker`() {
        assertIs<EventJsonParser.Parsed.NoEvent>(EventJsonParser.parse("""{"noEvent": true}"""))
    }

    @Test
    fun `prompt injects today and timezone for relative dates`() {
        val prompt = EventJsonParser.buildPrompt("Lunch next Friday", "2026-11-25", "Europe/Berlin")
        assertTrue(prompt.contains("2026-11-25"))
        assertTrue(prompt.contains("Europe/Berlin"))
        assertTrue(prompt.contains("next Friday"))
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
}
