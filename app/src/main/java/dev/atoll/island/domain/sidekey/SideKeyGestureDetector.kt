package dev.atoll.island.domain.sidekey

import dev.atoll.island.data.model.SideKeyConfig
import dev.atoll.island.data.model.SideKeyGesture

class SideKeyGestureDetector(
    private val scheduler: TimerScheduler,
    private val onGesture: (SideKeyGesture) -> Unit
) {
    interface TimerScheduler {
        fun schedule(token: Long, delayMs: Long, block: () -> Unit)
        fun cancel(token: Long)
    }

    data class Config(
        val hasSingle: Boolean = false,
        val hasDouble: Boolean = false,
        val hasTriple: Boolean = false,
        val hasLong: Boolean = false,
        val longPressTimeoutMs: Long = SideKeyConfig.DEFAULT_LONG_PRESS_MS,
        val multiTapTimeoutMs: Long = SideKeyConfig.DEFAULT_MULTI_TAP_MS
    ) {
        val boundedLongPressMs: Long
            get() = longPressTimeoutMs.coerceIn(SideKeyConfig.MIN_LONG_PRESS_MS, SideKeyConfig.MAX_LONG_PRESS_MS)

        val boundedMultiTapMs: Long
            get() = multiTapTimeoutMs.coerceIn(SideKeyConfig.MIN_MULTI_TAP_MS, SideKeyConfig.MAX_MULTI_TAP_MS)

        companion object {
            fun from(sideKeyConfig: SideKeyConfig): Config = Config(
                hasSingle = sideKeyConfig.actionFor(SideKeyGesture.SINGLE_PRESS).isAssigned,
                hasDouble = sideKeyConfig.actionFor(SideKeyGesture.DOUBLE_PRESS).isAssigned,
                hasTriple = sideKeyConfig.actionFor(SideKeyGesture.TRIPLE_PRESS).isAssigned,
                hasLong = sideKeyConfig.actionFor(SideKeyGesture.LONG_PRESS).isAssigned,
                longPressTimeoutMs = sideKeyConfig.longPressTimeoutMs,
                multiTapTimeoutMs = sideKeyConfig.multiTapTimeoutMs
            )
        }
    }

    private var config = Config()
    private var pressed = false
    private var longPressTriggered = false
    private var tapCount = 0
    private var nextToken = 1L
    private var activeLongToken = 0L
    private var activeMultiTapToken = 0L

    fun updateConfig(newConfig: Config) {
        config = newConfig
    }

    fun onDown(@Suppress("UNUSED_PARAMETER") timestampMs: Long = 0L) {
        if (pressed) return
        pressed = true
        longPressTriggered = false
        cancelMultiTapTimer()
        if (config.hasLong) {
            val token = nextToken++
            activeLongToken = token
            scheduler.schedule(token, config.boundedLongPressMs) {
                onLongPressExpired(token)
            }
        }
    }

    fun onUp(@Suppress("UNUSED_PARAMETER") timestampMs: Long = 0L) {
        if (!pressed) return
        pressed = false
        cancelLongTimer()
        if (longPressTriggered) {
            longPressTriggered = false
            tapCount = 0
            return
        }

        tapCount += 1
        when {
            tapCount == 1 && !config.hasDouble && !config.hasTriple -> {
                tapCount = 0
                if (config.hasSingle) onGesture(SideKeyGesture.SINGLE_PRESS)
            }
            tapCount == 1 -> scheduleMultiTapTimer()
            tapCount == 2 && !config.hasTriple -> {
                cancelMultiTapTimer()
                tapCount = 0
                if (config.hasDouble) onGesture(SideKeyGesture.DOUBLE_PRESS)
            }
            tapCount == 2 -> scheduleMultiTapTimer()
            else -> {
                cancelMultiTapTimer()
                tapCount = 0
                if (config.hasTriple) onGesture(SideKeyGesture.TRIPLE_PRESS)
            }
        }
    }

    fun reset() {
        pressed = false
        longPressTriggered = false
        tapCount = 0
        cancelLongTimer()
        cancelMultiTapTimer()
    }

    private fun onLongPressExpired(token: Long) {
        if (token != activeLongToken || !pressed || longPressTriggered) return
        activeLongToken = 0L
        longPressTriggered = true
        tapCount = 0
        cancelMultiTapTimer()
        onGesture(SideKeyGesture.LONG_PRESS)
    }

    private fun scheduleMultiTapTimer() {
        cancelMultiTapTimer()
        val token = nextToken++
        activeMultiTapToken = token
        scheduler.schedule(token, config.boundedMultiTapMs) {
            onMultiTapExpired(token)
        }
    }

    private fun onMultiTapExpired(token: Long) {
        if (token != activeMultiTapToken || pressed) return
        activeMultiTapToken = 0L
        val count = tapCount
        tapCount = 0
        when {
            count == 1 && config.hasSingle -> onGesture(SideKeyGesture.SINGLE_PRESS)
            count == 2 && config.hasDouble -> onGesture(SideKeyGesture.DOUBLE_PRESS)
            count >= 3 && config.hasTriple -> onGesture(SideKeyGesture.TRIPLE_PRESS)
        }
    }

    private fun cancelLongTimer() {
        if (activeLongToken != 0L) {
            scheduler.cancel(activeLongToken)
            activeLongToken = 0L
        }
    }

    private fun cancelMultiTapTimer() {
        if (activeMultiTapToken != 0L) {
            scheduler.cancel(activeMultiTapToken)
            activeMultiTapToken = 0L
        }
    }
}
