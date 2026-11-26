package dev.atoll.island.domain.card

import dev.atoll.island.data.model.CardAction
import dev.atoll.island.data.model.ChargeState
import dev.atoll.island.data.model.CounterSource
import dev.atoll.island.data.model.ExpandedCardConfig
import dev.atoll.island.data.model.ExpandedCardMode
import dev.atoll.island.data.model.StatMetric
import dev.atoll.island.data.model.ToolResult
import java.util.Locale
import kotlin.math.abs

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
    val batteryPowerWatts: Float? = null,
    val chargeState: ChargeState? = null,
    val chargeTimeMinutes: Int? = null,
    val batteryTempC: Float? = null,
    val batteryCycles: Int? = null,
    val freeRamGb: Float? = null,
    val storageFreeGb: Float? = null,
    val storageTotalGb: Float? = null,
    val cpuMaxMhz: Int? = null,
    val thermalStatus: Int? = null,
    val uptimeMinutes: Long? = null
)

data class CardEnvironment(
    val minuteOfDay: Int,
    val apiLevel: Int,
    val stats: DeviceStats = DeviceStats(),
    val dateText: String = "",
    val timeText: String = "",
    val confirmation: String? = null,
    val minimalTitle: String = CardText.BLANK,
    val minimalText: String = CardText.BLANK,
    val dayOfWeekIndex: Int = 1,
    val dayOfMonth: Int = 1,
    val daysInMonth: Int = 30,
    val dayOfYear: Int = 1,
    val daysInYear: Int = 365,
    val monthOfYear: Int = 1
)

interface CardStrings {
    fun statLabel(metric: StatMetric): String
    fun counterValue(count: Int, goal: Int): String
    fun percent(value: Int): String
    fun temperature(celsius: Float): String
    fun gigabytes(value: Float): String
    fun watts(value: Float): String = if (abs(value) >= 10f) "%+.0fW".format(Locale.US, value) else "%+.1fW".format(Locale.US, value)
    fun chargeState(state: ChargeState): String = state.name
    fun durationMinutes(minutes: Long): String = "${minutes}m"
    fun cycles(count: Int): String = count.toString()
    fun cpuFreq(mhz: Int): String = if (mhz >= 1000) "%.1f GHz".format(Locale.US, mhz / 1000f) else "$mhz MHz"
    fun thermalStatus(level: Int): String = level.toString()
    fun counterSourceName(source: CounterSource): String = source.name
    fun counterRemaining(minutes: Int, days: Int = 0): String = if (days > 0) "${days}d" else durationMinutes(minutes.toLong())
    fun unavailable(): String
    fun toolsHint(): String
    fun toolResult(result: ToolResult): String
    fun actionLabel(action: CardAction): String
}

data class TimeProgress(
    val percent: Int,
    val elapsedUnits: Int,
    val totalUnits: Int,
    val remainingMinutesToday: Int,
    val remainingDays: Int
)

object CardText {
    const val BLANK = "\u2800"

    // una Live Update senza titolo non viene promossa: i titoli generici diventano U+2800
    fun nonBlankTitle(text: String?): String = text?.trim().takeUnless { it.isNullOrEmpty() } ?: BLANK

    fun dayPercent(minuteOfDay: Int): Int = (minuteOfDay.coerceIn(0, MINUTES_PER_DAY) * 100) / MINUTES_PER_DAY

    fun remainingMinutesOfDay(minuteOfDay: Int): Int = (MINUTES_PER_DAY - minuteOfDay).coerceIn(0, MINUTES_PER_DAY)

    fun timeProgress(source: CounterSource, env: CardEnvironment): TimeProgress {
        val m = env.minuteOfDay.coerceIn(0, MINUTES_PER_DAY)
        val remToday = remainingMinutesOfDay(env.minuteOfDay)
        return when (source) {
            CounterSource.WEEK -> {
                val dow = env.dayOfWeekIndex.coerceIn(1, 7)
                val total = 7 * MINUTES_PER_DAY
                val elapsed = (dow - 1) * MINUTES_PER_DAY + m
                TimeProgress((elapsed * 100) / total, elapsed, total, remToday, 7 - dow)
            }
            CounterSource.MONTH -> {
                val days = env.daysInMonth.coerceIn(1, 31)
                val dom = env.dayOfMonth.coerceIn(1, days)
                val total = days * MINUTES_PER_DAY
                val elapsed = (dom - 1) * MINUTES_PER_DAY + m
                TimeProgress((elapsed * 100) / total, elapsed, total, remToday, days - dom)
            }
            CounterSource.YEAR -> {
                val days = env.daysInYear.coerceIn(1, 366)
                val doy = env.dayOfYear.coerceIn(1, days)
                val total = days * MINUTES_PER_DAY
                val elapsed = (doy - 1) * MINUTES_PER_DAY + m
                TimeProgress((elapsed * 100) / total, elapsed, total, remToday, days - doy)
            }
            CounterSource.DAY, CounterSource.CUSTOM ->
                TimeProgress(dayPercent(env.minuteOfDay), m, MINUTES_PER_DAY, remToday, 0)
        }
    }

    const val MINUTES_PER_DAY = 1440
}

object ExpandedCardContentResolver {

    fun usesMetricStyle(config: ExpandedCardConfig, apiLevel: Int): Boolean =
        apiLevel >= 37 && config.useMetricStyle &&
            config.mode == ExpandedCardMode.DEVICE_STATS && config.stats.isNotEmpty()

    fun resolve(config: ExpandedCardConfig, env: CardEnvironment, strings: CardStrings): CardContent {
        if (config.mode == ExpandedCardMode.CUSTOM_TEMPLATE) {
            return customTemplate(config, env, strings)
        }
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
            ExpandedCardMode.CUSTOM_TEMPLATE -> CardContent(title = CardText.BLANK)
        }
        return base.copy(
            title = CardText.nonBlankTitle(base.title),
            subText = if (config.mode == ExpandedCardMode.MINIMAL) null else env.confirmation ?: base.subText,
            actions = ActionSlots.resolve(config).map { ResolvedAction(it, strings.actionLabel(it)) }
        )
    }

    private fun customTemplate(config: ExpandedCardConfig, env: CardEnvironment, strings: CardStrings): CardContent {
        val template = CustomCardPresets.resolveTemplate(config.customTemplateJson)
        val sceneAdapter = object : SceneStrings {
            override fun counterOf(goal: Int) = ""
            override fun toolValue(result: ToolResult) = ""
            override fun toolName(result: ToolResult) = ""
            override fun toolsChoose() = ""
            override fun actionLabel(action: CardAction) = strings.actionLabel(action)
        }
        val blocks = CustomCardBinder.resolveBlocks(template, config, env, strings, sceneAdapter)
        val lines = CustomCardBinder.summaryLines(blocks)
        val title = if (config.customTemplateJson.isNotBlank()) CardText.nonBlankTitle(template.name) else CardText.BLANK
        val body = lines.joinToString("\n").ifEmpty { null }
        return CardContent(
            title = title,
            subText = env.confirmation,
            body = body,
            style = if (body != null) CardStyle.BIG_TEXT else CardStyle.STANDARD,
            actions = template.actions.map { ResolvedAction(it, strings.actionLabel(it)) }
        )
    }

    private fun note(config: ExpandedCardConfig): CardContent {
        val body = NoteMarkdown.parse(config.noteBody).text.ifEmpty { null }
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
        val s = env.stats
        val shown = when (metric) {
            StatMetric.BATTERY -> s.batteryPercent?.let(strings::percent)
            StatMetric.BATTERY_POWER -> s.batteryPowerWatts?.let(strings::watts)
            StatMetric.CHARGE_STATE -> s.chargeState?.let(strings::chargeState)
            StatMetric.CHARGE_TIME -> s.chargeTimeMinutes?.let { strings.durationMinutes(it.toLong()) }
            StatMetric.BATTERY_TEMP -> s.batteryTempC?.let(strings::temperature)
            StatMetric.BATTERY_CYCLES -> s.batteryCycles?.let(strings::cycles)
            StatMetric.DAY_PROGRESS -> strings.percent(CardText.dayPercent(env.minuteOfDay))
            StatMetric.FREE_RAM -> s.freeRamGb?.let(strings::gigabytes)
            StatMetric.STORAGE_FREE -> s.storageFreeGb?.let(strings::gigabytes)
            StatMetric.CPU_FREQUENCY -> s.cpuMaxMhz?.let(strings::cpuFreq)
            StatMetric.THERMAL_STATE -> s.thermalStatus?.let(strings::thermalStatus)
            StatMetric.UPTIME -> s.uptimeMinutes?.let(strings::durationMinutes)
            StatMetric.DATE_TIME -> env.timeText
        } ?: strings.unavailable()
        return CardMetric(label, shown)
    }

    private fun counter(config: ExpandedCardConfig, env: CardEnvironment, strings: CardStrings): CardContent {
        if (config.counterSource == CounterSource.DAY) {
            return CardContent(
                title = CardText.BLANK,
                subText = env.timeText.takeIf { it.isNotBlank() },
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
        if (config.counterSource != CounterSource.CUSTOM) {
            val tp = CardText.timeProgress(config.counterSource, env)
            val segments = when (config.counterSource) {
                CounterSource.WEEK -> List(7) { CardText.MINUTES_PER_DAY }
                else -> listOf(tp.totalUnits)
            }
            val label = strings.counterSourceName(config.counterSource)
            return CardContent(
                title = CardText.BLANK,
                subText = strings.counterRemaining(tp.remainingMinutesToday, tp.remainingDays),
                body = "$label ${strings.percent(tp.percent)}",
                style = CardStyle.PROGRESS,
                progress = CardProgress(
                    max = tp.totalUnits,
                    value = tp.elapsedUnits.coerceIn(0, tp.totalUnits),
                    segments = segments,
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
