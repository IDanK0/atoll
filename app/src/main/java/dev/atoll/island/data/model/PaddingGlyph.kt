package dev.atoll.island.data.model

// spazi invisibili per allungare la capsula
enum class PaddingGlyph(
    val id: String,
    val char: Char,
    val codePoint: String,
    val shortLabel: String,
    val isVisibleDebug: Boolean = false
) {
    EM_SPACE(
        id = "em_space",
        char = '\u2003',
        codePoint = "U+2003",
        shortLabel = "Em Space"
    ),
    EN_SPACE(
        id = "en_space",
        char = '\u2002',
        codePoint = "U+2002",
        shortLabel = "En Space"
    ),
    BRAILLE_BLANK(
        id = "braille_blank",
        char = '\u2800',
        codePoint = "U+2800",
        shortLabel = "Braille Blank"
    ),
    THREE_PER_EM_SPACE(
        id = "three_per_em_space",
        char = '\u2004',
        codePoint = "U+2004",
        shortLabel = "1/3 Em"
    ),
    FOUR_PER_EM_SPACE(
        id = "four_per_em_space",
        char = '\u2005',
        codePoint = "U+2005",
        shortLabel = "1/4 Em"
    ),
    THIN_SPACE(
        id = "thin_space",
        char = '\u2009',
        codePoint = "U+2009",
        shortLabel = "Thin Space"
    ),
    HAIR_SPACE(
        id = "hair_space",
        char = '\u200A',
        codePoint = "U+200A",
        shortLabel = "Hair Space"
    ),
    ZERO_WIDTH_SPACE(
        id = "zero_width",
        char = '\u200B',
        codePoint = "U+200B",
        shortLabel = "Zero-Width"
    ),
    DEBUG_BULLET(
        id = "debug_bullet",
        char = '•',
        codePoint = "U+2022",
        shortLabel = "Debug (•)",
        isVisibleDebug = true
    ),
    DEBUG_BLOCK(
        id = "debug_block",
        char = '█',
        codePoint = "U+2588",
        shortLabel = "Debug (█)",
        isVisibleDebug = true
    );

    companion object {
        val invisibleGlyphs: List<PaddingGlyph>
            get() = entries.filter { !it.isVisibleDebug }

        val debugGlyphs: List<PaddingGlyph>
            get() = entries.filter { it.isVisibleDebug }

        fun fromId(id: String?): PaddingGlyph {
            if (id.isNullOrBlank()) return BRAILLE_BLANK
            return entries.firstOrNull { it.id == id || it.name == id } ?: BRAILLE_BLANK
        }
    }
}
