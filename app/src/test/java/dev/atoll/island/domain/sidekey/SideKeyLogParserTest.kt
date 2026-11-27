package dev.atoll.island.domain.sidekey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SideKeyLogParserTest {

    @Test
    fun parsesPhoneWindowManagerAndOplusKeyEventUtilLinesFromUid1000() {
        val downLine =
            "11-26 22:14:47.330  1000  2173 22747 D KEYLOG_PhoneWindowManagerExtImpl: interceptKeyBeforeQueueing KeyEvent { action=ACTION_DOWN, keyCode=KEYCODE_ACTION_BUTTON_CLICK, scanCode=735, eventTime=8451200 }"
        val upLine =
            "11-26 22:14:47.410  1000  2173 22747 I KEYLOG_OplusKeyEventUtil: dispatch action=ACTION_UP scanCode=735 eventTime=8451280"

        val down = SideKeyLogParser.parseLine(downLine)
        val up = SideKeyLogParser.parseLine(upLine)

        assertNotNull(down)
        assertEquals("1000", down?.uid)
        assertEquals("KEYLOG_PhoneWindowManagerExtImpl", down?.tag)
        assertEquals(SideKeyLogParser.KeyEdge.DOWN, down?.edge)
        assertEquals(8451200L, down?.timestampMs)

        assertNotNull(up)
        assertEquals("KEYLOG_OplusKeyEventUtil", up?.tag)
        assertEquals(SideKeyLogParser.KeyEdge.UP, up?.edge)
        assertEquals(8451280L, up?.timestampMs)
    }

    @Test
    fun acceptsSymbolicSystemUidAndFallsBackToMonotonicTime() {
        val line =
            "11-26 22:14:47.330 system 2173 22747 D KEYLOG_PhoneWindowManagerExtImpl: key scanCode=735 action=ACTION_DOWN"

        val parsed = SideKeyLogParser.parseLine(line, monotonicNowMs = 99_420L)

        assertNotNull(parsed)
        assertEquals("system", parsed?.uid)
        assertEquals(99_420L, parsed?.timestampMs)
        assertFalse(parsed!!.hasEventTime)
    }

    @Test
    fun strictlyRejectsSpoofedNonSystemUidLines() {
        val numericAppUid =
            "11-26 22:14:47.330 10312  9900  9900 D KEYLOG_PhoneWindowManagerExtImpl: KeyEvent { action=ACTION_DOWN, keyCode=KEYCODE_ACTION_BUTTON_CLICK, scanCode=735, eventTime=8451200 }"
        val namedAppUid =
            "11-26 22:14:47.330 u0_a312 9900 9900 D KEYLOG_OplusKeyEventUtil: uid=1000 action=ACTION_DOWN scanCode=735"
        val shellUid =
            "11-26 22:14:47.330 shell  3479  3479 I KEYLOG_PhoneWindowManagerExtImpl: action=ACTION_UP scanCode=735"
        val missingHeader =
            "KEYLOG_PhoneWindowManagerExtImpl: action=ACTION_DOWN scanCode=735"

        assertNull(SideKeyLogParser.parseLine(numericAppUid))
        assertNull(SideKeyLogParser.parseLine(namedAppUid))
        assertNull(SideKeyLogParser.parseLine(shellUid))
        assertNull(SideKeyLogParser.parseLine(missingHeader))
    }

    @Test
    fun rejectsSpoofedUidEvenWithCustomFilterPattern() {
        val spoofed =
            "11-26 22:14:47.330 u0_a312 9900 9900 D CustomHardwareKey: scanCode=999 action=ACTION_DOWN"
        val valid =
            "11-26 22:14:47.330  1000  2173 22747 D CustomHardwareKey: scanCode=999 action=ACTION_DOWN eventTime=1234"

        assertNull(SideKeyLogParser.parseLine(spoofed, filterPattern = "CustomHardwareKey.*scanCode=999"))
        assertNotNull(SideKeyLogParser.parseLine(valid, filterPattern = "CustomHardwareKey.*scanCode=999"))
    }

    @Test
    fun deduplicatesDoubleLoggedEdgesWithin25MsWithoutDroppingFastRelease() {
        val firstDown =
            "11-26 22:14:47.330  1000  2173 22747 D KEYLOG_OplusKeyEventUtil: action=ACTION_DOWN scanCode=735 eventTime=5000"
        val mirrorDownSameTime =
            "11-26 22:14:47.332  1000  2173 22747 D KEYLOG_PhoneWindowManagerExtImpl: action=ACTION_DOWN KEYCODE_ACTION_BUTTON_CLICK eventTime=5000"
        val mirrorDown12MsLater =
            "11-26 22:14:47.342  1000  2173 22747 D KEYLOG_PhoneWindowManagerExtImpl: action=ACTION_DOWN KEYCODE_ACTION_BUTTON_CLICK eventTime=5012"
        val fastUp14MsAfterDown =
            "11-26 22:14:47.344  1000  2173 22747 D KEYLOG_PhoneWindowManagerExtImpl: action=ACTION_UP KEYCODE_ACTION_BUTTON_CLICK eventTime=5014"
        val nextDownLater =
            "11-26 22:14:47.420  1000  2173 22747 D KEYLOG_PhoneWindowManagerExtImpl: action=ACTION_DOWN KEYCODE_ACTION_BUTTON_CLICK eventTime=5120"

        val dedup = SideKeyEventDeduplicator(25L)

        assertTrue(dedup.accept(SideKeyLogParser.parseLine(firstDown)!!, arrivalMs = 100L))
        assertFalse(dedup.accept(SideKeyLogParser.parseLine(mirrorDownSameTime)!!, arrivalMs = 102L))
        assertFalse(dedup.accept(SideKeyLogParser.parseLine(mirrorDown12MsLater)!!, arrivalMs = 112L))
        assertTrue(dedup.accept(SideKeyLogParser.parseLine(fastUp14MsAfterDown)!!, arrivalMs = 114L))
        assertTrue(dedup.accept(SideKeyLogParser.parseLine(nextDownLater)!!, arrivalMs = 220L))
    }

    @Test
    fun analyzerExtractsRecurringPatternFromCapturedUid1000Lines() {
        val captured = listOf(
            "11-26 22:14:47.100 u0_a200 500 500 D FakeTag: scanCode=735 action=ACTION_DOWN",
            "11-26 22:14:47.200  1000  200 200 D VendorKeyService: keyCode=740 action=ACTION_DOWN",
            "11-26 22:14:47.280  1000  200 200 D VendorKeyService: keyCode=740 action=ACTION_UP",
            "11-26 22:14:48.100  1000  200 200 D VendorKeyService: keyCode=740 action=ACTION_DOWN",
            "11-26 22:14:48.180  1000  200 200 D VendorKeyService: keyCode=740 action=ACTION_UP"
        )

        val candidate = SideKeyCaptureAnalyzer.analyze(captured)

        assertNotNull(candidate)
        assertEquals("VendorKeyService", candidate?.tag)
        assertEquals("keyCode=740", candidate?.token)
        assertTrue(candidate!!.hasDownAndUp)
        assertEquals(4, candidate.matchCount)
        assertNotNull(SideKeyLogParser.parseLine(captured[1], filterPattern = candidate.regexPattern))
    }
}
