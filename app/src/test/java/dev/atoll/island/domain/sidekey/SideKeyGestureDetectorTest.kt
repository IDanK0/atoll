package dev.atoll.island.domain.sidekey

import dev.atoll.island.data.model.SideKeyGesture
import org.junit.Assert.assertEquals
import org.junit.Test

class SideKeyGestureDetectorTest {

    private class FakeClockScheduler : SideKeyGestureDetector.TimerScheduler {
        private data class PendingTask(val token: Long, val fireAtMs: Long, val block: () -> Unit)

        var nowMs = 0L
        private val queue = mutableListOf<PendingTask>()

        override fun schedule(token: Long, delayMs: Long, block: () -> Unit) {
            queue += PendingTask(token, nowMs + delayMs, block)
        }

        override fun cancel(token: Long) {
            queue.removeAll { it.token == token }
        }

        fun advanceBy(ms: Long) {
            val target = nowMs + ms
            while (true) {
                val next = queue.filter { it.fireAtMs <= target }.minByOrNull { it.fireAtMs } ?: break
                queue.remove(next)
                nowMs = next.fireAtMs
                next.block()
            }
            nowMs = target
        }
    }

    @Test
    fun singlePressFiresImmediatelyOnUpWhenDoubleAndTripleUnassigned() {
        val scheduler = FakeClockScheduler()
        val fired = mutableListOf<Pair<Long, SideKeyGesture>>()
        val detector = SideKeyGestureDetector(scheduler) { fired += scheduler.nowMs to it }
        detector.updateConfig(SideKeyGestureDetector.Config(hasSingle = true, hasLong = true))

        detector.onDown(0L)
        scheduler.advanceBy(80L)
        detector.onUp(80L)

        assertEquals(listOf(80L to SideKeyGesture.SINGLE_PRESS), fired)
    }

    @Test
    fun singlePressWaitsForMultiTapTimeoutWhenDoubleIsAssigned() {
        val scheduler = FakeClockScheduler()
        val fired = mutableListOf<Pair<Long, SideKeyGesture>>()
        val detector = SideKeyGestureDetector(scheduler) { fired += scheduler.nowMs to it }
        detector.updateConfig(
            SideKeyGestureDetector.Config(hasSingle = true, hasDouble = true, multiTapTimeoutMs = 280L)
        )

        detector.onDown()
        scheduler.advanceBy(50L)
        detector.onUp()
        assertTrueEmpty(fired)

        scheduler.advanceBy(279L)
        assertTrueEmpty(fired)

        scheduler.advanceBy(1L)
        assertEquals(listOf(330L to SideKeyGesture.SINGLE_PRESS), fired)
    }

    @Test
    fun doublePressFiresImmediatelyOnSecondUpWhenTripleUnassigned() {
        val scheduler = FakeClockScheduler()
        val fired = mutableListOf<Pair<Long, SideKeyGesture>>()
        val detector = SideKeyGestureDetector(scheduler) { fired += scheduler.nowMs to it }
        detector.updateConfig(
            SideKeyGestureDetector.Config(hasSingle = true, hasDouble = true, multiTapTimeoutMs = 280L)
        )

        detector.onDown()
        scheduler.advanceBy(40L)
        detector.onUp()
        scheduler.advanceBy(120L)
        detector.onDown()
        scheduler.advanceBy(40L)
        detector.onUp()

        assertEquals(listOf(200L to SideKeyGesture.DOUBLE_PRESS), fired)
        scheduler.advanceBy(500L)
        assertEquals(1, fired.size)
    }

    @Test
    fun doubleAndTriplePressRespectTimeoutBoundariesWhenAllBound() {
        val scheduler = FakeClockScheduler()
        val fired = mutableListOf<Pair<Long, SideKeyGesture>>()
        val detector = SideKeyGestureDetector(scheduler) { fired += scheduler.nowMs to it }
        detector.updateConfig(
            SideKeyGestureDetector.Config(
                hasSingle = true,
                hasDouble = true,
                hasTriple = true,
                multiTapTimeoutMs = 280L
            )
        )

        // Doppio tocco: attende multiTapTimeoutMs prima di escludere il triplo.
        detector.onDown(); scheduler.advanceBy(30L); detector.onUp()
        scheduler.advanceBy(100L)
        detector.onDown(); scheduler.advanceBy(30L); detector.onUp()
        assertTrueEmpty(fired)
        scheduler.advanceBy(279L)
        assertTrueEmpty(fired)
        scheduler.advanceBy(1L)
        assertEquals(listOf(440L to SideKeyGesture.DOUBLE_PRESS), fired)

        fired.clear()

        // Triplo tocco: parte subito al terzo rilascio.
        detector.onDown(); scheduler.advanceBy(30L); detector.onUp()
        scheduler.advanceBy(90L)
        detector.onDown(); scheduler.advanceBy(30L); detector.onUp()
        scheduler.advanceBy(90L)
        detector.onDown(); scheduler.advanceBy(30L); detector.onUp()
        assertEquals(listOf(710L to SideKeyGesture.TRIPLE_PRESS), fired)
    }

    @Test
    fun longPressFiresAtThresholdWhileHeldAndIgnoresSubsequentUp() {
        val scheduler = FakeClockScheduler()
        val fired = mutableListOf<Pair<Long, SideKeyGesture>>()
        val detector = SideKeyGestureDetector(scheduler) { fired += scheduler.nowMs to it }
        detector.updateConfig(
            SideKeyGestureDetector.Config(
                hasSingle = true,
                hasDouble = true,
                hasLong = true,
                longPressTimeoutMs = 500L,
                multiTapTimeoutMs = 280L
            )
        )

        detector.onDown()
        scheduler.advanceBy(499L)
        assertTrueEmpty(fired)

        scheduler.advanceBy(1L)
        assertEquals(listOf(500L to SideKeyGesture.LONG_PRESS), fired)

        scheduler.advanceBy(200L)
        detector.onUp()
        scheduler.advanceBy(500L)
        assertEquals(1, fired.size)
    }

    private fun assertTrueEmpty(items: List<*>) {
        assertEquals(emptyList<Any>(), items)
    }
}
