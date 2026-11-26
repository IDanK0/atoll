package dev.atoll.island.domain.card

import dev.atoll.island.data.model.CardAction
import dev.atoll.island.data.model.CardBackground
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
}
