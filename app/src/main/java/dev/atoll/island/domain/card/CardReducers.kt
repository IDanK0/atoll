package dev.atoll.island.domain.card

import dev.atoll.island.data.model.CardAction
import dev.atoll.island.data.model.CounterSource
import dev.atoll.island.data.model.CounterState
import dev.atoll.island.data.model.ExpandedCardConfig
import dev.atoll.island.data.model.ExpandedCardMode
import dev.atoll.island.data.model.StatMetric
import dev.atoll.island.data.model.TimerState
import dev.atoll.island.data.model.ToolResult
import kotlin.random.Random

object CounterReducer {
    const val MIN_GOAL = 1
    const val MAX_GOAL = 999

    fun plus(state: CounterState) = state.copy(count = (state.count + 1).coerceIn(0, state.goal))
    fun minus(state: CounterState) = state.copy(count = (state.count - 1).coerceIn(0, state.goal))
    fun reset(state: CounterState) = state.copy(count = 0)

    fun setGoal(state: CounterState, goal: Int): CounterState {
        val clamped = goal.coerceIn(MIN_GOAL, MAX_GOAL)
        return state.copy(goal = clamped, count = state.count.coerceIn(0, clamped))
    }
}

object TimerReducer {
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 180
    private const val STEP_MS = 5 * TimerState.MINUTE_MS
    private const val MAX_TOTAL_MS = 12 * 60 * TimerState.MINUTE_MS

    fun toggle(state: TimerState, nowMs: Long): TimerState = when {
        state.isRunning -> state.copy(remainingMs = state.remainingAt(nowMs), endAtMs = 0L)
        // finito: si riparte da capo
        state.isFinished -> start(reset(state), nowMs)
        else -> start(state, nowMs)
    }

    fun addFiveMinutes(state: TimerState, nowMs: Long): TimerState {
        val remaining = state.remainingAt(nowMs)
        val added = (remaining + STEP_MS).coerceAtMost(MAX_TOTAL_MS) - remaining
        val total = (state.totalMs.coerceAtLeast(remaining) + added).coerceAtMost(MAX_TOTAL_MS)
        return if (state.isRunning) {
            state.copy(totalMs = total, endAtMs = nowMs + remaining + added)
        } else {
            state.copy(totalMs = total, remainingMs = remaining + added)
        }
    }

    fun reset(state: TimerState) =
        state.copy(totalMs = state.durationMs, remainingMs = state.durationMs, endAtMs = 0L)

    fun finishIfDue(state: TimerState, nowMs: Long): TimerState =
        if (state.isRunning && state.endAtMs <= nowMs) state.copy(remainingMs = 0L, endAtMs = 0L) else state

    fun setDurationMinutes(state: TimerState, minutes: Int): TimerState {
        val duration = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES) * TimerState.MINUTE_MS
        return if (state.isRunning) state.copy(durationMs = duration)
        else TimerState(durationMs = duration, totalMs = duration, remainingMs = duration)
    }

    fun elapsedFraction(state: TimerState, nowMs: Long): Float {
        if (state.totalMs <= 0L) return 0f
        return (1f - state.remainingAt(nowMs).toFloat() / state.totalMs).coerceIn(0f, 1f)
    }

    private fun start(state: TimerState, nowMs: Long) =
        state.copy(endAtMs = nowMs + state.remainingMs.coerceAtLeast(1_000L))
}

object Randomizer {
    const val MIN_VALUE = -999_999
    const val MAX_VALUE = 999_999

    fun rollDice(random: Random) = ToolResult.Dice(random.nextInt(1, 7))

    fun flipCoin(random: Random) = ToolResult.Coin(random.nextBoolean())

    fun number(random: Random, a: Int, b: Int): ToolResult.Number {
        val (min, max) = normalizedRange(a, b)
        return ToolResult.Number(random.nextInt(min, max + 1), min, max)
    }

    fun normalizedRange(a: Int, b: Int): Pair<Int, Int> {
        val x = a.coerceIn(MIN_VALUE, MAX_VALUE)
        val y = b.coerceIn(MIN_VALUE, MAX_VALUE)
        return minOf(x, y) to maxOf(x, y)
    }
}

object ActionSlots {
    const val MAX_SLOTS = 3

    private val universal = listOf(
        CardAction.ToggleFlashlight,
        CardAction.MediaPlayPause,
        CardAction.MediaNext
    )

    fun defaultsFor(config: ExpandedCardConfig): List<CardAction> = when (config.mode) {
        ExpandedCardMode.MINIMAL -> emptyList()
        ExpandedCardMode.CUSTOM_NOTE -> listOf(CardAction.EditNoteInline)
        ExpandedCardMode.SHORTCUT_DOCK -> universal
        ExpandedCardMode.DEVICE_STATS -> listOf(CardAction.RefreshStats)
        ExpandedCardMode.PROGRESS_COUNTER ->
            if (config.counterSource == CounterSource.DAY) emptyList()
            else listOf(CardAction.CounterPlus, CardAction.CounterMinus, CardAction.CounterReset)
        ExpandedCardMode.FOCUS_TIMER -> listOf(CardAction.TimerToggle, CardAction.TimerAdd5Min, CardAction.TimerReset)
        ExpandedCardMode.QUICK_TOOLS -> listOf(CardAction.RollDice, CardAction.FlipCoin, CardAction.RandomNumber)
    }

    // pulsanti che hanno senso solo nella loro modalità
    fun ownMode(action: CardAction): ExpandedCardMode? = when (action) {
        CardAction.EditNoteInline -> ExpandedCardMode.CUSTOM_NOTE
        CardAction.RefreshStats -> ExpandedCardMode.DEVICE_STATS
        CardAction.CounterPlus, CardAction.CounterMinus, CardAction.CounterReset -> ExpandedCardMode.PROGRESS_COUNTER
        CardAction.TimerToggle, CardAction.TimerAdd5Min, CardAction.TimerReset -> ExpandedCardMode.FOCUS_TIMER
        CardAction.RollDice, CardAction.FlipCoin, CardAction.RandomNumber -> ExpandedCardMode.QUICK_TOOLS
        else -> null
    }

    fun isAllowed(action: CardAction, mode: ExpandedCardMode): Boolean {
        if (mode == ExpandedCardMode.MINIMAL) return false
        val own = ownMode(action)
        return (own == null || own == mode) && isValid(action)
    }

    fun isValid(action: CardAction): Boolean = when (action) {
        is CardAction.LaunchApp -> action.packageName.isNotBlank()
        is CardAction.OpenUrl -> normalizeUrl(action.url) != null
        is CardAction.CopySnippet -> action.text.isNotEmpty()
        else -> true
    }

    fun sanitize(actions: List<CardAction>, mode: ExpandedCardMode): List<CardAction> =
        actions.filter { isAllowed(it, mode) }.distinct().take(MAX_SLOTS)

    fun resolve(config: ExpandedCardConfig): List<CardAction> =
        sanitize(config.actionOverrides[config.mode] ?: defaultsFor(config), config.mode)

    // "example.com" diventa https://example.com; tutto quello che non è http(s) viene scartato
    fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        val scheme = withScheme.substringBefore("://").lowercase()
        val host = withScheme.substringAfter("://").substringBefore('/').substringBefore('?')
        if (scheme != "http" && scheme != "https") return null
        if (host.isEmpty() || '.' !in host && host != "localhost") return null
        return withScheme
    }
}

// null = l'azione non cambia lo stato della scheda (torcia, media, link...)
object CardActionReducer {
    fun changesState(action: CardAction): Boolean = when (action) {
        CardAction.CounterPlus, CardAction.CounterMinus, CardAction.CounterReset,
        CardAction.TimerToggle, CardAction.TimerAdd5Min, CardAction.TimerReset,
        CardAction.RollDice, CardAction.FlipCoin, CardAction.RandomNumber -> true
        else -> false
    }

    fun reduce(card: ExpandedCardConfig, action: CardAction, nowMs: Long, random: Random): ExpandedCardConfig? =
        when (action) {
            CardAction.CounterPlus -> card.copy(counter = CounterReducer.plus(card.counter))
            CardAction.CounterMinus -> card.copy(counter = CounterReducer.minus(card.counter))
            CardAction.CounterReset -> card.copy(counter = CounterReducer.reset(card.counter))
            CardAction.TimerToggle -> card.copy(timer = TimerReducer.toggle(card.timer, nowMs))
            CardAction.TimerAdd5Min -> card.copy(timer = TimerReducer.addFiveMinutes(card.timer, nowMs))
            CardAction.TimerReset -> card.copy(timer = TimerReducer.reset(card.timer))
            CardAction.RollDice -> card.copy(lastTool = Randomizer.rollDice(random))
            CardAction.FlipCoin -> card.copy(lastTool = Randomizer.flipCoin(random))
            CardAction.RandomNumber -> card.copy(lastTool = Randomizer.number(random, card.randomMin, card.randomMax))
            else -> null
        }
}

object StatSelection {
    fun toggle(current: List<StatMetric>, metric: StatMetric): List<StatMetric> = when {
        metric in current -> current - metric
        current.size >= ExpandedCardConfig.MAX_STATS -> current
        else -> StatMetric.entries.filter { it in current || it == metric }
    }

    fun sanitize(stats: List<StatMetric>): List<StatMetric> =
        stats.distinct().take(ExpandedCardConfig.MAX_STATS)
}
