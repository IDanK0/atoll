package dev.atoll.island.domain.card

import dev.atoll.island.data.model.CardAction
import dev.atoll.island.data.model.CounterSource
import dev.atoll.island.data.model.CounterState
import dev.atoll.island.data.model.ExpandedCardConfig
import dev.atoll.island.data.model.ExpandedCardMode
import dev.atoll.island.data.model.StatMetric
import dev.atoll.island.data.model.ToolResult
import kotlin.random.Random

object CounterReducer {
    const val MIN_GOAL = 1
    const val MAX_GOAL = 999
    const val MIN_STEP = 1
    const val MAX_STEP = 100

    fun plus(state: CounterState): CounterState {
        val delta = state.step.coerceIn(MIN_STEP, MAX_STEP)
        return state.copy(count = (state.count + delta).coerceIn(0, state.goal))
    }

    fun minus(state: CounterState): CounterState {
        val delta = state.step.coerceIn(MIN_STEP, MAX_STEP)
        return state.copy(count = (state.count - delta).coerceIn(0, state.goal))
    }

    fun reset(state: CounterState) = state.copy(count = 0)

    fun setGoal(state: CounterState, goal: Int): CounterState {
        val clamped = goal.coerceIn(MIN_GOAL, MAX_GOAL)
        return state.copy(goal = clamped, count = state.count.coerceIn(0, clamped))
    }

    fun setStep(state: CounterState, step: Int): CounterState =
        state.copy(step = step.coerceIn(MIN_STEP, MAX_STEP))

    fun setResetAtMidnight(state: CounterState, enabled: Boolean, todayEpochDay: Long): CounterState =
        state.copy(
            resetAtMidnight = enabled,
            lastResetEpochDay = if (enabled) todayEpochDay else state.lastResetEpochDay
        )

    fun resetIfNewDay(state: CounterState, todayEpochDay: Long): CounterState? {
        if (!state.resetAtMidnight || todayEpochDay <= 0L) return null
        if (state.lastResetEpochDay <= 0L) return state.copy(lastResetEpochDay = todayEpochDay)
        if (todayEpochDay <= state.lastResetEpochDay) return null
        return state.copy(count = 0, lastResetEpochDay = todayEpochDay)
    }
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
    const val MAX_DOCK_SLOTS = 4
    const val MAX_TIMER_SECONDS = 86_400

    fun maxSlots(mode: ExpandedCardMode): Int =
        if (mode == ExpandedCardMode.SHORTCUT_DOCK) MAX_DOCK_SLOTS else MAX_SLOTS

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
            if (config.counterSource != CounterSource.CUSTOM) emptyList()
            else listOf(CardAction.CounterPlus, CardAction.CounterMinus, CardAction.CounterReset)
        ExpandedCardMode.QUICK_TOOLS -> listOf(CardAction.RollDice, CardAction.FlipCoin, CardAction.RandomNumber)
        ExpandedCardMode.CUSTOM_TEMPLATE -> emptyList()
    }

    // pulsanti che hanno senso solo nella loro modalità
    fun ownMode(action: CardAction): ExpandedCardMode? = when (action) {
        CardAction.EditNoteInline -> ExpandedCardMode.CUSTOM_NOTE
        CardAction.RefreshStats -> ExpandedCardMode.DEVICE_STATS
        CardAction.CounterPlus, CardAction.CounterMinus, CardAction.CounterReset -> ExpandedCardMode.PROGRESS_COUNTER
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
        is CardAction.AppShortcut -> action.intentUri.isNotBlank() && !action.intentUri.trimStart().startsWith("javascript:", ignoreCase = true)
        is CardAction.OpenUrl -> normalizeUrl(action.url) != null
        is CardAction.CopySnippet -> action.text.isNotEmpty()
        is CardAction.DialNumber -> normalizePhone(action.phoneNumber) != null
        is CardAction.ClockTimer -> action.seconds in 1..MAX_TIMER_SECONDS
        else -> true
    }

    fun sanitize(actions: List<CardAction>, mode: ExpandedCardMode): List<CardAction> =
        actions.filter { isAllowed(it, mode) }.distinct().take(maxSlots(mode))

    fun normalizePhone(raw: String): String? {
        val cleaned = raw.trim().filterIndexed { i, c -> c.isDigit() || (c == '+' && i == 0) || c == '*' || c == '#' }
        return cleaned.takeIf { it.any(Char::isDigit) }
    }

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
        CardAction.RollDice, CardAction.FlipCoin, CardAction.RandomNumber -> true
        else -> false
    }

    fun reduce(card: ExpandedCardConfig, action: CardAction, random: Random): ExpandedCardConfig? =
        when (action) {
            CardAction.CounterPlus -> card.copy(counter = CounterReducer.plus(card.counter))
            CardAction.CounterMinus -> card.copy(counter = CounterReducer.minus(card.counter))
            CardAction.CounterReset -> card.copy(counter = CounterReducer.reset(card.counter))
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
