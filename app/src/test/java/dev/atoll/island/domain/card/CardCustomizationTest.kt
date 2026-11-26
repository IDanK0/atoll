package dev.atoll.island.domain.card

import dev.atoll.island.data.model.CardAction
import dev.atoll.island.data.model.CardBackground
import dev.atoll.island.data.model.ExpandedCardConfig
import dev.atoll.island.data.model.ExpandedCardMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardCustomizationTest {

    @Test
    fun codecPreservesShortcutIconIdAndLegacyFormat() {
        val withIcon = CardAction.AppShortcut("intent:#Intent;end", "Chat", "com.whatsapp", "abc-123")
        val legacy = CardAction.AppShortcut("intent:#Intent;end", "Mail", "com.example.mail")

        val decoded = CardCodec.decodeActions(CardCodec.encodeActions(listOf(withIcon, legacy)))

        assertEquals(listOf(withIcon, legacy), decoded)
    }

    @Test
    fun colorOsJsonSupportsGradientIconBackground() {
        val json = ColorOsCardJson.card(
            tappable = true,
            backgroundArgbHex = "88142C48",
            backgroundIconKey = ColorOsCardJson.KEY_BG_ICON
        )

        assertTrue(json.contains("\"background\":\"icon/atoll_bg\""))
        assertTrue(json.contains("\"bgLight\":\"false\""))
        assertFalse(json.contains("colorInt/#"))
    }

    @Test
    fun customBackgroundIsTintCapableWhileGlassAndBlackAreNot() {
        assertTrue(CardBackground.CUSTOM.isSolidTintCapable)
        assertTrue(CardBackground.OCEAN.isSolidTintCapable)
        assertTrue(CardBackground.SYSTEM.isSolidTintCapable)
        assertFalse(CardBackground.DEFAULT.isSolidTintCapable)
        assertFalse(CardBackground.PITCH.isSolidTintCapable)
    }

    @Test
    fun makerParserIgnoresLiteralCustomKeywordWithoutColor() {
        val res = CustomCardParser.parse(
            """{"background":"custom","blocks":[{"type":"text","text":"hi"}]}"""
        ) as MakerParseResult.Ok

        assertNull(res.template.background)
        assertNull(res.template.customBgHex)
    }

    @Test
    fun chosenColorOverridesTheTemplate() {
        val card = ExpandedCardConfig(
            mode = ExpandedCardMode.CUSTOM_TEMPLATE,
            background = CardBackground.CUSTOM,
            customBgColor = 0xFF16538A.toInt(),
            backgroundGradient = true
        )

        val background = resolveCardBackground(card, templateHex = "99224466") { error("Unexpected system color") }

        assertEquals("9E16538A", background.argbHex)
        assertEquals(0x16538A, background.gradientRgb)
    }

    @Test
    fun templateColorRespectsTheGradientSetting() {
        val card = ExpandedCardConfig(mode = ExpandedCardMode.CUSTOM_TEMPLATE)
        val solid = resolveCardBackground(card, templateHex = "99224466") { error("Unexpected system color") }
        val gradient = resolveCardBackground(
            card.copy(backgroundGradient = true),
            templateHex = "99224466"
        ) { error("Unexpected system color") }

        assertEquals("99224466", solid.argbHex)
        assertNull(solid.gradientRgb)
        assertEquals(0x224466, gradient.gradientRgb)
    }

    @Test
    fun templatePresetIsUsedWhenNoColorIsChosen() {
        val card = ExpandedCardConfig(mode = ExpandedCardMode.CUSTOM_TEMPLATE, backgroundGradient = true)
        val background = resolveCardBackground(card, templatePreset = CardBackground.FOREST) {
            error("Unexpected system color")
        }

        assertEquals(CardBackground.FOREST, background.preset)
        assertEquals("88143628", background.argbHex)
        assertEquals(0x1A5940, background.gradientRgb)
    }

    @Test
    fun blackStaysSolidAndBlankUsesTheDefaultBackground() {
        val card = ExpandedCardConfig(background = CardBackground.PITCH, backgroundGradient = true)
        val black = resolveCardBackground(card) { error("Unexpected system color") }
        val blank = resolveCardBackground(card.copy(mode = ExpandedCardMode.MINIMAL)) {
            error("Unexpected system color")
        }

        assertEquals("F2090B0E", black.argbHex)
        assertNull(black.gradientRgb)
        assertEquals(CardBackground.DEFAULT, blank.preset)
        assertNull(blank.argbHex)
        assertNull(blank.gradientRgb)
    }

    @Test
    fun systemTintUsesDifferentShadesForSolidAndGradient() {
        val card = ExpandedCardConfig(background = CardBackground.SYSTEM, backgroundGradient = true)
        val background = resolveCardBackground(card) { gradient ->
            if (gradient) 0xFF557799.toInt() else 0xFF224466.toInt()
        }

        assertEquals("88224466", background.argbHex)
        assertEquals(0x557799, background.gradientRgb)
    }
}
