package dev.atoll.island.domain.sidekey

import dev.atoll.island.data.model.MacroStep
import dev.atoll.island.data.model.SideKeyAction
import dev.atoll.island.data.model.SideKeyGesture
import dev.atoll.island.data.model.SideKeyGestureBinding
import dev.atoll.island.data.model.SideKeyRingerMode
import dev.atoll.island.service.sidekey.SideKeyActionExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SideKeyCodecTest {

    @Test
    fun roundTripsAllActionVariantsShortcutsMacrosAndUnlockFlags() {
        val original = mapOf(
            SideKeyGesture.SINGLE_PRESS to SideKeyGestureBinding(
                action = SideKeyAction.AppShortcut(
                    intentUri = "intent:#Intent;action=android.intent.action.VIEW;end",
                    label = "Chat",
                    packageName = "com.whatsapp",
                    iconId = "sc_1"
                ),
                unlockedOnly = true
            ),
            SideKeyGesture.DOUBLE_PRESS to SideKeyGestureBinding(
                action = SideKeyAction.Macro(
                    label = "Focus mode",
                    steps = listOf(
                        MacroStep(SideKeyAction.RingerMode(SideKeyRingerMode.SILENT, toggleBack = true), 300L),
                        MacroStep(SideKeyAction.ToggleDnd, 0L),
                        MacroStep(SideKeyAction.StartTimer(900, "Focus"), 250L),
                        MacroStep(
                            SideKeyAction.CustomNotification(
                                title = "Mamma",
                                message = "Torna a casa",
                                appPackage = "com.whatsapp",
                                appLabel = "WhatsApp",
                                iconKey = "chat",
                                delaySeconds = 5,
                                headsUp = true
                            ),
                            0L
                        )
                    )
                ),
                unlockedOnly = false
            ),
            SideKeyGesture.TRIPLE_PRESS to SideKeyGestureBinding(
                action = SideKeyAction.DialNumber("+391234567", "Home"),
                unlockedOnly = true
            ),
            SideKeyGesture.LONG_PRESS to SideKeyGestureBinding(
                action = SideKeyAction.CopySnippet("Mail", "info@example.com"),
                unlockedOnly = false
            )
        )

        val decoded = SideKeyCodec.decodeBindings(SideKeyCodec.encodeBindings(original))

        assertEquals(original, decoded)
        assertTrue(original[SideKeyGesture.DOUBLE_PRESS]!!.action.needsDndAccess)
        assertTrue(original[SideKeyGesture.DOUBLE_PRESS]!!.action.needsOverlayForBackgroundLaunch)
    }

    @Test
    fun rejectsUnsafeJavascriptUrlsNestedMacrosAndInvalidValues() {
        val malformed = """{"SINGLE_PRESS":{"type":"url","url":"javascript:alert(1)","label":"x"},"DOUBLE_PRESS":{"type":"shortcut","uri":"javascript:foo()","label":"y"},"TRIPLE_PRESS":{"type":"macro","label":"bad","steps":[{"type":"macro","steps":[]}]}}"""

        val decoded = SideKeyCodec.decodeBindings(malformed)

        assertTrue(decoded.isEmpty())
        assertFalse(SideKeyCodec.isValidAction(SideKeyAction.OpenUrl("ftp://example.com", "")))
        assertFalse(SideKeyCodec.isValidAction(SideKeyAction.CustomIntent("   ", "")))
        assertFalse(SideKeyCodec.isValidAction(SideKeyAction.StartTimer(0, "")))
        assertFalse(SideKeyCodec.isValidAction(SideKeyAction.DialNumber("abc", "")))
        assertFalse(SideKeyCodec.isValidAction(SideKeyAction.CustomNotification(title = "  ", message = "")))
        assertFalse(SideKeyCodec.isValidAction(SideKeyAction.CustomNotification(title = "Ok", message = "Hi", delaySeconds = 99)))
        assertFalse(
            SideKeyCodec.isValidAction(
                SideKeyAction.Macro("bad", listOf(MacroStep(SideKeyAction.Macro("nested", emptyList()))))
            )
        )
    }

    @Test
    fun resolvesSilentToggleRestoringPreviousAndAvoidingRingFallback() {
        val action = SideKeyAction.RingerMode(SideKeyRingerMode.SILENT, toggleBack = true)

        // normal -> silent salva normal (2)
        val (firstTarget, savedFromNormal) = SideKeyActionExecutor.resolveRingerTarget(action, 2, null, canUseSilent = true)
        assertEquals(0, firstTarget)
        assertEquals(2, savedFromNormal)

        // ripremo mentre è in silenzioso -> torna a normal (2)
        val (restoredNormal, clearedPrev) = SideKeyActionExecutor.resolveRingerTarget(action, 0, savedFromNormal, canUseSilent = true)
        assertEquals(2, restoredNormal)
        assertNull(clearedPrev)

        // già in silenzioso senza stato precedente -> va su vibrazione (1), mai con suono (2)
        val (safeFallback, _) = SideKeyActionExecutor.resolveRingerTarget(action, 0, null, canUseSilent = true)
        assertEquals(1, safeFallback)
    }

    @Test
    fun silentDoesNotAskForDoNotDisturbAccess() {
        assertFalse(SideKeyAction.RingerMode(SideKeyRingerMode.SILENT).needsDndAccess)
        assertTrue(SideKeyAction.ToggleDnd.needsDndAccess)
    }

    @Test
    fun cycleRemembersTheModeItJustLeft() {
        val action = SideKeyAction.RingerMode(SideKeyRingerMode.CYCLE)

        val (nextMode, previous) = SideKeyActionExecutor.resolveRingerTarget(action, 1, 2, canUseSilent = true)

        assertEquals(0, nextMode)
        assertEquals(1, previous)
    }
}
