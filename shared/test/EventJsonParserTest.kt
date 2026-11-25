package de.franzsw.ctx2cal

import de.franzsw.ctx2cal.domain.extraction.EventJsonParser
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
}
