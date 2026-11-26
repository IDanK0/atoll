package dev.atoll.island.domain.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteMarkdownTest {

    @Test
    fun parsesHeadingsListsAndInlineStyles() {
        val parsed = NoteMarkdown.parse(
            "# Spesa\n- **Latte** e *pane*\n- `a_b` ~~ieri~~ [lista](https://example.com)"
        )
        assertEquals("Spesa\n\u2022 Latte e pane\n\u2022 a_b ieri lista", parsed.text)

        fun spanText(style: NoteSpanStyle): List<String> =
            parsed.spans.filter { it.style == style }.map { parsed.text.substring(it.start, it.end) }

        assertEquals(listOf("Spesa"), spanText(NoteSpanStyle.HEADING))
        assertEquals(listOf("\u2022 Latte e pane", "\u2022 a_b ieri lista"), spanText(NoteSpanStyle.BULLET))
        assertEquals(listOf("Latte"), spanText(NoteSpanStyle.BOLD))
        assertEquals(listOf("pane"), spanText(NoteSpanStyle.ITALIC))
        assertEquals(listOf("a_b"), spanText(NoteSpanStyle.CODE))
        assertEquals(listOf("ieri"), spanText(NoteSpanStyle.STRIKE))
    }

    @Test
    fun leavesUnderscoresAndUnclosedMarkersAlone() {
        val parsed = NoteMarkdown.parse("nome_file *aperto 2 * 3 * 4")
        assertEquals("nome_file *aperto 2 * 3 * 4", parsed.text)
        assertTrue(parsed.spans.isEmpty())
    }

    @Test
    fun ignoresFormattingInsideCodeAndSupportsNestedTextInLinks() {
        val parsed = NoteMarkdown.parse("`*finto*` [_nota_](https://example.com/a(1))")
        assertEquals("*finto* nota", parsed.text)
        assertEquals(1, parsed.spans.count { it.style == NoteSpanStyle.CODE })
        assertEquals(1, parsed.spans.count { it.style == NoteSpanStyle.ITALIC })
    }
}
