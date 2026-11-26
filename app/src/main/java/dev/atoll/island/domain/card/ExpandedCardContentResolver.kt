package dev.atoll.island.domain.card

import dev.atoll.island.data.model.CardAction
import dev.atoll.island.data.model.CounterSource
import dev.atoll.island.data.model.ExpandedCardConfig
import dev.atoll.island.data.model.ExpandedCardMode
import dev.atoll.island.data.model.StatMetric
import dev.atoll.island.data.model.ToolResult

enum class CardStyle { STANDARD, BIG_TEXT, PROGRESS, METRIC }

data class CardProgress(val max: Int, val value: Int, val segments: List<Int>, val color: Int)

data class CardMetric(val label: String, val valueText: String) {
    val text: String get() = "$label $valueText"
}

data class ResolvedAction(val action: CardAction, val label: String)

data class CardContent(
    val title: String,
    val subText: String? = null,
    val body: String? = null,
    val style: CardStyle = CardStyle.STANDARD,
    val progress: CardProgress? = null,
    val metrics: List<CardMetric> = emptyList(),
    val actions: List<ResolvedAction> = emptyList()
)

data class DeviceStats(
    val batteryPercent: Int? = null,
    val batteryTempC: Float? = null,
    val freeRamGb: Float? = null
)

data class CardEnvironment(
    val minuteOfDay: Int,
    val apiLevel: Int,
    val stats: DeviceStats = DeviceStats(),
    val dateText: String = "",
    val timeText: String = "",
    val confirmation: String? = null,
    val minimalTitle: String = CardText.BLANK,
    val minimalText: String = CardText.BLANK
)

interface CardStrings {
    fun statLabel(metric: StatMetric): String
    fun counterValue(count: Int, goal: Int): String
    fun percent(value: Int): String
    fun temperature(celsius: Float): String
    fun gigabytes(value: Float): String
    fun unavailable(): String
    fun toolsHint(): String
    fun toolResult(result: ToolResult): String
    fun actionLabel(action: CardAction): String
}

object CardText {
    const val BLANK = "\u2800"

    // una Live Update senza titolo non viene promossa: i titoli generici diventano U+2800
    fun nonBlankTitle(text: String?): String = text?.trim().takeUnless { it.isNullOrEmpty() } ?: BLANK

    fun dayPercent(minuteOfDay: Int): Int = (minuteOfDay.coerceIn(0, MINUTES_PER_DAY) * 100) / MINUTES_PER_DAY

    const val MINUTES_PER_DAY = 1440
}

object ExpandedCardContentResolver {

    fun usesMetricStyle(config: ExpandedCardConfig, apiLevel: Int): Boolean =
        apiLevel >= 37 && config.useMetricStyle &&
            config.mode == ExpandedCardMode.DEVICE_STATS && config.stats.isNotEmpty()

    fun resolve(config: ExpandedCardConfig, env: CardEnvironment, strings: CardStrings): CardContent {
        val base = when (config.mode) {
            ExpandedCardMode.MINIMAL -> CardContent(title = env.minimalTitle, body = env.minimalText)
            ExpandedCardMode.CUSTOM_NOTE -> note(config)
            ExpandedCardMode.SHORTCUT_DOCK -> CardContent(title = config.dockTitle)
            ExpandedCardMode.DEVICE_STATS -> stats(config, env, strings)
            ExpandedCardMode.PROGRESS_COUNTER -> counter(config, env, strings)
            ExpandedCardMode.QUICK_TOOLS -> CardContent(
                title = CardText.BLANK,
                body = config.lastTool?.let(strings::toolResult) ?: strings.toolsHint(),
                style = CardStyle.BIG_TEXT
            )
        }
        return base.copy(
            title = CardText.nonBlankTitle(base.title),
            subText = if (config.mode == ExpandedCardMode.MINIMAL) null else env.confirmation ?: base.subText,
            actions = ActionSlots.resolve(config).map { ResolvedAction(it, strings.actionLabel(it)) }
        )
    }

    private fun note(config: ExpandedCardConfig): CardContent {
        val body = config.noteBody.trim().ifEmpty { null }
        return CardContent(
            title = config.noteTitle,
            subText = config.noteSubText.trim().ifEmpty { null },
            body = body,
            style = if (body != null) CardStyle.BIG_TEXT else CardStyle.STANDARD
        )
    }

    private fun stats(config: ExpandedCardConfig, env: CardEnvironment, strings: CardStrings): CardContent {
        val metrics = StatSelection.sanitize(config.stats).map { metric(it, env, strings) }
        val metric = usesMetricStyle(config, env.apiLevel)
        return CardContent(
            title = CardText.BLANK,
            // ColorOS mostra al massimo due righe: una riga continua va a capo da sola
            body = metrics.joinToString("  \u00B7  ") { it.text }.ifEmpty { null },
            style = if (metric) CardStyle.METRIC else CardStyle.BIG_TEXT,
            metrics = metrics
        )
    }

    private fun metric(metric: StatMetric, env: CardEnvironment, strings: CardStrings): CardMetric {
        val label = if (metric == StatMetric.DATE_TIME) env.dateText else strings.statLabel(metric)
        val shown = when (metric) {
            StatMetric.BATTERY -> env.stats.batteryPercent?.let(strings::percent)
            StatMetric.BATTERY_TEMP -> env.stats.batteryTempC?.let(strings::temperature)
            StatMetric.DAY_PROGRESS -> strings.percent(CardText.dayPercent(env.minuteOfDay))
            StatMetric.FREE_RAM -> env.stats.freeRamGb?.let(strings::gigabytes)
            StatMetric.DATE_TIME -> env.timeText
        } ?: strings.unavailable()
        return CardMetric(label, shown)
    }

    private fun counter(config: ExpandedCardConfig, env: CardEnvironment, strings: CardStrings): CardContent {
        if (config.counterSource == CounterSource.DAY) {
            return CardContent(
                title = CardText.BLANK,
                body = strings.statLabel(StatMetric.DAY_PROGRESS) + " " +
                    strings.percent(CardText.dayPercent(env.minuteOfDay)),
                style = CardStyle.PROGRESS,
                progress = CardProgress(
                    max = CardText.MINUTES_PER_DAY,
                    value = env.minuteOfDay.coerceIn(0, CardText.MINUTES_PER_DAY),
                    segments = List(4) { CardText.MINUTES_PER_DAY / 4 },
                    color = config.accentColor
                )
            )
        }
        val c = config.counter
        return CardContent(
            title = config.counterLabel,
            body = strings.counterValue(c.count, c.goal),
            style = CardStyle.PROGRESS,
            progress = CardProgress(
                max = c.goal,
                value = c.count.coerceIn(0, c.goal),
                // un segmento per passo finché si leggono, poi una barra sola
                segments = if (c.goal <= MAX_SEGMENTS) List(c.goal) { 1 } else listOf(c.goal),
                color = config.accentColor
            )
        )
    }

    private const val MAX_SEGMENTS = 20
}
