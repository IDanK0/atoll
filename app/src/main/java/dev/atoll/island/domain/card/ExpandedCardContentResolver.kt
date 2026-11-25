package dev.atoll.island.domain.card

import dev.atoll.island.data.model.CardAction
import dev.atoll.island.data.model.CounterSource
import dev.atoll.island.data.model.ExpandedCardConfig
import dev.atoll.island.data.model.ExpandedCardMode
import dev.atoll.island.data.model.StatMetric
import dev.atoll.island.data.model.ToolResult

enum class CardStyle { STANDARD, BIG_TEXT, PROGRESS, METRIC }

data class CardProgress(val max: Int, val value: Int, val segments: List<Int>, val color: Int)

sealed interface MetricValue {
    data class IntValue(val value: Int, val unit: String) : MetricValue
    data class FloatValue(val value: Float, val unit: String) : MetricValue
    data class TextValue(val text: String) : MetricValue
    data class Timer(val endAtMs: Long, val pausedMs: Long) : MetricValue
}

data class CardMetric(val label: String, val value: MetricValue, val valueText: String) {
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
    val nowMs: Long,
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
    fun timerReady(duration: String): String
    fun timerRunning(endsAt: String): String
    fun timerPaused(remaining: String): String
    fun timerFinished(): String
    fun timerRemainingLabel(): String
    fun duration(ms: Long): String
    fun clock(epochMs: Long): String
    fun toolsHint(): String
    fun toolResult(result: ToolResult): String
    fun actionLabel(action: CardAction, timerRunning: Boolean): String
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
            (config.mode == ExpandedCardMode.DEVICE_STATS && config.stats.isNotEmpty() ||
                config.mode == ExpandedCardMode.FOCUS_TIMER)

    fun resolve(config: ExpandedCardConfig, env: CardEnvironment, strings: CardStrings): CardContent {
        val base = when (config.mode) {
            ExpandedCardMode.MINIMAL -> CardContent(title = env.minimalTitle, body = env.minimalText)
            ExpandedCardMode.CUSTOM_NOTE -> note(config, strings)
            ExpandedCardMode.SHORTCUT_DOCK -> CardContent(title = config.dockTitle)
            ExpandedCardMode.DEVICE_STATS -> stats(config, env, strings)
            ExpandedCardMode.PROGRESS_COUNTER -> counter(config, env, strings)
            ExpandedCardMode.FOCUS_TIMER -> timer(config, env, strings)
            ExpandedCardMode.QUICK_TOOLS -> CardContent(
                title = CardText.BLANK,
                body = config.lastTool?.let(strings::toolResult) ?: strings.toolsHint(),
                style = CardStyle.BIG_TEXT
            )
        }
        val timerRunning = config.timer.isRunning
        return base.copy(
            title = CardText.nonBlankTitle(base.title),
            subText = if (config.mode == ExpandedCardMode.MINIMAL) null else env.confirmation ?: base.subText,
            actions = ActionSlots.resolve(config).map { ResolvedAction(it, strings.actionLabel(it, timerRunning)) }
        )
    }

    private fun note(config: ExpandedCardConfig, strings: CardStrings): CardContent {
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
        val value: MetricValue = when (metric) {
            StatMetric.BATTERY -> env.stats.batteryPercent?.let { MetricValue.IntValue(it, "%") }
            StatMetric.BATTERY_TEMP -> env.stats.batteryTempC?.let { MetricValue.FloatValue(it, "\u00B0C") }
            StatMetric.DAY_PROGRESS -> MetricValue.IntValue(CardText.dayPercent(env.minuteOfDay), "%")
            StatMetric.FREE_RAM -> env.stats.freeRamGb?.let { MetricValue.FloatValue(it, "GB") }
            StatMetric.DATE_TIME -> MetricValue.TextValue(env.timeText)
        } ?: MetricValue.TextValue(strings.unavailable())
        val shown = when (metric) {
            StatMetric.BATTERY -> env.stats.batteryPercent?.let(strings::percent)
            StatMetric.BATTERY_TEMP -> env.stats.batteryTempC?.let(strings::temperature)
            StatMetric.DAY_PROGRESS -> strings.percent(CardText.dayPercent(env.minuteOfDay))
            StatMetric.FREE_RAM -> env.stats.freeRamGb?.let(strings::gigabytes)
            StatMetric.DATE_TIME -> env.timeText
        } ?: strings.unavailable()
        return CardMetric(label, value, shown)
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

    private fun timer(config: ExpandedCardConfig, env: CardEnvironment, strings: CardStrings): CardContent {
        val t = config.timer
        val remaining = t.remainingAt(env.nowMs)
        val body = when {
            t.isRunning -> strings.timerRunning(strings.clock(t.endAtMs))
            t.isFinished -> strings.timerFinished()
            remaining < t.totalMs -> strings.timerPaused(strings.duration(remaining))
            else -> strings.timerReady(strings.duration(t.totalMs))
        }
        val title = CardText.BLANK
        if (usesMetricStyle(config, env.apiLevel)) {
            val value = MetricValue.Timer(endAtMs = if (t.isRunning) t.endAtMs else 0L, pausedMs = remaining)
            return CardContent(
                title = title,
                body = body,
                style = CardStyle.METRIC,
                metrics = listOf(CardMetric(strings.timerRemainingLabel(), value, strings.duration(remaining)))
            )
        }
        val totalSec = (t.totalMs / 1000L).toInt().coerceAtLeast(1)
        val elapsedSec = ((t.totalMs - remaining) / 1000L).toInt().coerceIn(0, totalSec)
        return CardContent(
            title = title,
            body = body,
            style = CardStyle.PROGRESS,
            progress = CardProgress(totalSec, elapsedSec, listOf(totalSec), config.accentColor)
        )
    }

    private const val MAX_SEGMENTS = 20
}
