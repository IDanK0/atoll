package dev.atoll.island.domain

import dev.atoll.island.data.model.LiveAlertConfig
import dev.atoll.island.data.model.PaddingGlyph
import dev.atoll.island.data.model.TitleMode

object CapsulePaddingGenerator {

    const val MIN_LENGTH = 0
    const val MAX_LENGTH = 12
    const val ANDROID_GUARANTEED_CHIP_CHARS = 7
    const val BRAILLE_BLANK = '\u2800'

    fun generate(
        length: Int,
        glyph: PaddingGlyph,
        debugVisibleMode: Boolean = false,
        debugGlyph: PaddingGlyph = PaddingGlyph.DEBUG_BULLET
    ): String {
        val clamped = length.coerceIn(MIN_LENGTH, MAX_LENGTH)
        if (clamped == 0) return ""
        val effectiveGlyph = if (debugVisibleMode) {
            if (debugGlyph.isVisibleDebug) debugGlyph else PaddingGlyph.DEBUG_BULLET
        } else {
            glyph
        }
        return effectiveGlyph.char.toString().repeat(clamped)
    }

    fun applyDebugOverlay(
        text: String,
        debugVisibleMode: Boolean,
        debugGlyph: PaddingGlyph
    ): String {
        if (!debugVisibleMode || text.isEmpty()) return text
        val effectiveDebug = if (debugGlyph.isVisibleDebug) debugGlyph else PaddingGlyph.DEBUG_BULLET
        return effectiveDebug.char.toString().repeat(text.length)
    }

    fun generateCustomForConfig(config: LiveAlertConfig): String =
        generate(
            length = config.customLength,
            glyph = config.customGlyph,
            debugVisibleMode = config.debugVisibleMode,
            debugGlyph = config.debugGlyph
        )

    fun titleFor(mode: TitleMode): String = when (mode) {
        TitleMode.BRAILLE_BLANK -> BRAILLE_BLANK.toString()
        TitleMode.SUBTLE_TEXT -> "Atoll"
    }

    fun contentTextFor(mode: TitleMode): String = when (mode) {
        TitleMode.BRAILLE_BLANK -> BRAILLE_BLANK.toString()
        TitleMode.SUBTLE_TEXT -> "Live Alert"
    }
}
