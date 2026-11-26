package dev.atoll.island.domain.card

import dev.atoll.island.data.model.CardBackground
import dev.atoll.island.data.model.ExpandedCardConfig
import dev.atoll.island.data.model.ExpandedCardMode
import java.util.Locale

internal data class CardBackgroundAppearance(
    val preset: CardBackground,
    val argbHex: String? = null,
    val gradientRgb: Int? = null
)

internal fun resolveCardBackground(
    card: ExpandedCardConfig,
    templatePreset: CardBackground? = null,
    templateHex: String? = null,
    systemColor: (gradient: Boolean) -> Int
): CardBackgroundAppearance {
    if (card.mode == ExpandedCardMode.MINIMAL) return CardBackgroundAppearance(CardBackground.DEFAULT)

    val useTemplate = card.mode == ExpandedCardMode.CUSTOM_TEMPLATE && card.background == CardBackground.DEFAULT
    val preset = if (useTemplate) templatePreset ?: card.background else card.background
    if (useTemplate && templateHex != null) {
        val rgb = if (card.backgroundGradient) templateHex.toLongOrNull(16)?.toInt()?.and(0xFFFFFF) else null
        return CardBackgroundAppearance(preset, templateHex, rgb)
    }

    val customRgb = card.customBgColor and 0xFFFFFF
    val argbHex = when (preset) {
        CardBackground.SYSTEM -> "88%06X".format(Locale.ROOT, systemColor(false) and 0xFFFFFF)
        CardBackground.CUSTOM -> "9E%06X".format(Locale.ROOT, customRgb)
        else -> preset.colorHex
    }
    val gradientRgb = if (!card.backgroundGradient) null else when (preset) {
        CardBackground.DEFAULT, CardBackground.PITCH -> null
        CardBackground.SYSTEM -> systemColor(true) and 0xFFFFFF
        CardBackground.OCEAN -> 0x1A497F
        CardBackground.FOREST -> 0x1A5940
        CardBackground.PLUM -> 0x4E2978
        CardBackground.AMBER -> 0x6B421A
        CardBackground.CUSTOM -> customRgb
    }
    return CardBackgroundAppearance(preset, argbHex, gradientRgb)
}
