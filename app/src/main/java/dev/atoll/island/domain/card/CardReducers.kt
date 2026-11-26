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

    fun plus(state: CounterState) = state.copy(count = (state.count + 1).coerceIn(0, state.goal))
    fun minus(state: CounterState) = state.copy(count = (state.count - 1).coerceIn(0, state.goal))
    fun reset(state: CounterState) = state.copy(count = 0)

    fun setGoal(state: CounterState, goal: Int): CounterState {
        val clamped = goal.coerceIn(MIN_GOAL, MAX_GOAL)
        return state.copy(goal = clamped, count = state.count.coerceIn(0, clamped))
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
        ExpandedCardMode.QUICK_TOOLS -> listOf(CardAction.RollDice, CardAction.FlipCoin, CardAction.RandomNumber)
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
