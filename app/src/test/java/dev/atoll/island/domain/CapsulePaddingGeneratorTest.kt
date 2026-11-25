package dev.atoll.island.domain

import dev.atoll.island.data.model.CapsulePreset
import dev.atoll.island.data.model.LiveAlertConfig
import dev.atoll.island.data.model.PaddingGlyph
import dev.atoll.island.data.model.TitleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapsulePaddingGeneratorTest {

    @Test
    fun `generates exact outputs for every length from 0 to 12`() {
        assertEquals("", CapsulePaddingGenerator.generate(0, PaddingGlyph.BRAILLE_BLANK))

        for (len in 1..12) {
            val result = CapsulePaddingGenerator.generate(len, PaddingGlyph.BRAILLE_BLANK)
            assertEquals(len, result.length)
            assertTrue(result.all { it == '\u2800' })
        }

        assertEquals("", CapsulePaddingGenerator.generate(-4, PaddingGlyph.EM_SPACE))
        assertEquals(
            "\u2003".repeat(12),
            CapsulePaddingGenerator.generate(25, PaddingGlyph.EM_SPACE)
        )
    }

    @Test
    fun `braille blank shortCriticalText is non-empty and survives trim`() {
        for (len in 1..12) {
            val text = CapsulePaddingGenerator.generate(len, PaddingGlyph.BRAILLE_BLANK)
            assertTrue(text.isNotEmpty())
            assertTrue(text.isNotBlank())
            assertEquals(text, text.trim())
            assertEquals(len, text.trim().length)
        }
    }

    @Test
    fun `generates expected glyphs for all invisible and debug padding options`() {
        for (glyph in PaddingGlyph.entries) {
            val generated = CapsulePaddingGenerator.generate(4, glyph)
            assertEquals(4, generated.length)
            assertTrue(generated.all { it == glyph.char })
        }
    }

    @Test
    fun `debug visible mode overrides invisible glyph while preserving character length`() {
        val bullet = CapsulePaddingGenerator.generate(
            length = 5,
            glyph = PaddingGlyph.BRAILLE_BLANK,
            debugVisibleMode = true,
            debugGlyph = PaddingGlyph.DEBUG_BULLET
        )
        assertEquals("•••••", bullet)

        val block = CapsulePaddingGenerator.generate(
            length = 7,
            glyph = PaddingGlyph.EM_SPACE,
            debugVisibleMode = true,
            debugGlyph = PaddingGlyph.DEBUG_BLOCK
        )
        assertEquals("███████", block)

        assertEquals(
            "",
            CapsulePaddingGenerator.generate(
                length = 0,
                glyph = PaddingGlyph.BRAILLE_BLANK,
                debugVisibleMode = true,
                debugGlyph = PaddingGlyph.DEBUG_BULLET
            )
        )

        assertEquals(
            "██████",
            CapsulePaddingGenerator.applyDebugOverlay(
                text = "\u2800\u2003\u2003\u2002\u2009\u2800",
                debugVisibleMode = true,
                debugGlyph = PaddingGlyph.DEBUG_BLOCK
            )
        )
    }

    @Test
    fun `manual custom config and title modes resolve cleanly`() {
        val config = LiveAlertConfig(
            selectedPreset = CapsulePreset.CUSTOM,
            customLength = 9,
            customGlyph = PaddingGlyph.THIN_SPACE,
            titleMode = TitleMode.BRAILLE_BLANK
        )

        assertEquals("\u2009".repeat(9), CapsulePaddingGenerator.generateCustomForConfig(config))
        assertEquals("\u2800", CapsulePaddingGenerator.titleFor(TitleMode.BRAILLE_BLANK))
        assertEquals("Atoll", CapsulePaddingGenerator.titleFor(TitleMode.SUBTLE_TEXT))
    }
}
