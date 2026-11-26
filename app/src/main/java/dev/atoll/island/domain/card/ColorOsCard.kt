package dev.atoll.island.domain.card

import dev.atoll.island.data.model.CardAction
import dev.atoll.island.data.model.CardBackground
import dev.atoll.island.data.model.CounterSource
import dev.atoll.island.data.model.ExpandedCardConfig
import dev.atoll.island.data.model.ExpandedCardMode
import dev.atoll.island.data.model.LargeIconType
import dev.atoll.island.data.model.StatMetric
import dev.atoll.island.data.model.TapAction
import dev.atoll.island.data.model.ToolResult

// cosa mostra la scheda disegnata da Atoll; la stessa scena serve alla notifica e all'anteprima nell'app
sealed interface CardScene {
    data class Note(
        val title: String,
        val body: String,
        val photo: Boolean,
        val spans: List<NoteSpan> = emptyList()
    ) : CardScene {
        val photoOnly: Boolean get() = photo && title.isEmpty() && body.isEmpty()
    }

    data class Dock(val title: String?, val slots: List<SceneButton>) : CardScene

    data class Stats(val cells: List<StatCell>) : CardScene

    data class Counter(
        val value: String,
        val total: String?,
        val caption: String?,
        val segments: Int,
        val filled: Int,
        val complete: Boolean,
        val buttons: List<SceneButton>
    ) : CardScene

    data class Tools(val result: ToolResult?, val value: String?, val caption: String, val buttons: List<SceneButton>) : CardScene

    data class Maker(
        val blocks: List<ResolvedMakerBlock>,
        val background: CardBackground? = null,
        val customBgHex: String? = null
    ) : CardScene
}

data class SceneButton(val slot: Int, val action: CardAction, val label: String, val primary: Boolean = false)

// fraction null = solo il cerchio, senza arco
data class StatCell(val metric: StatMetric, val value: String, val caption: String, val fraction: Float?)

interface SceneStrings {
    fun counterOf(goal: Int): String
    fun toolValue(result: ToolResult): String
    fun toolName(result: ToolResult): String
    fun toolsChoose(): String
    fun actionLabel(action: CardAction): String
}

object CardSceneBuilder {

    fun build(
        card: ExpandedCardConfig,
        env: CardEnvironment,
        strings: CardStrings,
        scene: SceneStrings,
        hasPhoto: Boolean
    ): CardScene? = when (card.mode) {
        ExpandedCardMode.MINIMAL -> null
        ExpandedCardMode.CUSTOM_NOTE -> {
            val parsed = NoteMarkdown.parse(card.noteBody)
            CardScene.Note(
                title = card.noteTitle.trim(),
                body = parsed.text,
                photo = hasPhoto && card.largeIcon.type == LargeIconType.IMAGE,
                spans = parsed.spans
            )
        }
        ExpandedCardMode.SHORTCUT_DOCK -> CardScene.Dock(
            title = card.dockTitle.trim().ifEmpty { null },
            slots = ActionSlots.resolve(card).mapIndexed { i, a -> SceneButton(i, a, scene.actionLabel(a)) }
        )
        ExpandedCardMode.DEVICE_STATS -> CardScene.Stats(StatSelection.sanitize(card.stats).map { cell(it, env, strings) })
        ExpandedCardMode.PROGRESS_COUNTER -> counter(card, env, strings, scene)
        ExpandedCardMode.QUICK_TOOLS -> CardScene.Tools(
            result = card.lastTool,
            value = card.lastTool?.let(scene::toolValue),
            caption = card.lastTool?.let(scene::toolName) ?: scene.toolsChoose(),
            buttons = listOf(CardAction.RollDice, CardAction.FlipCoin, CardAction.RandomNumber)
                .mapIndexed { i, a -> SceneButton(i, a, scene.actionLabel(a)) }
        )
        ExpandedCardMode.CUSTOM_TEMPLATE -> buildMaker(card, env, strings, scene)
    }

    fun statCell(metric: StatMetric, env: CardEnvironment, strings: CardStrings): StatCell = cell(metric, env, strings)

    private fun buildMaker(
        card: ExpandedCardConfig,
        env: CardEnvironment,
        strings: CardStrings,
        scene: SceneStrings
    ): CardScene.Maker {
        val template = CustomCardPresets.resolveTemplate(card.customTemplateJson)
        return CardScene.Maker(
            blocks = CustomCardBinder.resolveBlocks(template, card, env, strings, scene),
            background = template.background,
            customBgHex = template.customBgHex
        )
    }

    private fun cell(metric: StatMetric, env: CardEnvironment, strings: CardStrings): StatCell {
        val s = env.stats
        return when (metric) {
            StatMetric.BATTERY -> StatCell(
                metric, s.batteryPercent?.let(strings::percent) ?: strings.unavailable(),
                strings.statLabel(metric), s.batteryPercent?.let { it / 100f }
            )
            StatMetric.BATTERY_POWER -> StatCell(
                metric, s.batteryPowerWatts?.let(strings::watts) ?: strings.unavailable(),
                strings.statLabel(metric), null
            )
            StatMetric.CHARGE_STATE -> StatCell(
                metric, s.chargeState?.let(strings::chargeState) ?: strings.unavailable(),
                strings.statLabel(metric), null
            )
            StatMetric.CHARGE_TIME -> StatCell(
                metric, s.chargeTimeMinutes?.let { strings.durationMinutes(it.toLong()) } ?: strings.unavailable(),
                strings.statLabel(metric), null
            )
            StatMetric.DAY_PROGRESS -> CardText.dayPercent(env.minuteOfDay).let {
                StatCell(metric, strings.percent(it), strings.statLabel(metric), it / 100f)
            }
            StatMetric.BATTERY_TEMP -> StatCell(
                metric, s.batteryTempC?.let { "%.0f\u00B0".format(it) } ?: strings.unavailable(), strings.statLabel(metric), null
            )
            StatMetric.BATTERY_CYCLES -> StatCell(
                metric, s.batteryCycles?.let(strings::cycles) ?: strings.unavailable(),
                strings.statLabel(metric), null
            )
            StatMetric.FREE_RAM -> StatCell(
                metric, s.freeRamGb?.let(strings::gigabytes) ?: strings.unavailable(), strings.statLabel(metric), null
            )
            StatMetric.STORAGE_FREE -> {
                val fraction = if (s.storageFreeGb != null && s.storageTotalGb != null && s.storageTotalGb > 0f) {
                    (s.storageFreeGb / s.storageTotalGb).coerceIn(0f, 1f)
                } else null
                StatCell(
                    metric, s.storageFreeGb?.let(strings::gigabytes) ?: strings.unavailable(),
                    strings.statLabel(metric), fraction
                )
            }
            StatMetric.CPU_FREQUENCY -> StatCell(
                metric, s.cpuMaxMhz?.let(strings::cpuFreq) ?: strings.unavailable(),
                strings.statLabel(metric), null
            )
            StatMetric.THERMAL_STATE -> StatCell(
                metric, s.thermalStatus?.let(strings::thermalStatus) ?: strings.unavailable(),
                strings.statLabel(metric), s.thermalStatus?.let { (it.coerceIn(0, 4)) / 4f }
            )
            StatMetric.UPTIME -> StatCell(
                metric, s.uptimeMinutes?.let(strings::durationMinutes) ?: strings.unavailable(),
                strings.statLabel(metric), null
            )
            StatMetric.DATE_TIME -> StatCell(metric, env.timeText, env.dateText, null)
        }
    }

    private fun counter(card: ExpandedCardConfig, env: CardEnvironment, strings: CardStrings, scene: SceneStrings): CardScene.Counter {
        if (card.counterSource != CounterSource.CUSTOM) {
            val tp = CardText.timeProgress(card.counterSource, env)
            val (segments, filled) = when (card.counterSource) {
                CounterSource.DAY -> 24 to env.minuteOfDay.coerceIn(0, CardText.MINUTES_PER_DAY) / 60
                CounterSource.WEEK -> 7 to env.dayOfWeekIndex.coerceIn(0, 7)
                CounterSource.MONTH -> env.daysInMonth.coerceIn(1, 31) to env.dayOfMonth.coerceIn(0, env.daysInMonth)
                CounterSource.YEAR -> 12 to env.monthOfYear.coerceIn(0, 12)
                CounterSource.CUSTOM -> 24 to 0
            }
            val clock = env.timeText.trim()
            val value = if (card.counterSource == CounterSource.DAY && clock.isNotEmpty()) clock else strings.percent(tp.percent)
            val total = if (card.counterSource == CounterSource.DAY) {
                if (clock.isNotEmpty()) strings.percent(tp.percent) else null
            } else {
                strings.counterSourceName(card.counterSource)
            }
            return CardScene.Counter(
                value = value,
                total = total,
                caption = strings.counterRemaining(tp.remainingMinutesToday, tp.remainingDays),
                segments = segments,
                filled = filled,
                complete = false,
                buttons = emptyList()
            )
        }
        val c = card.counter
        val complete = c.count >= c.goal
        // a obiettivo raggiunto il "+" non serve più: al suo posto si azzera
        val buttons = if (complete) {
            listOf(CardAction.CounterMinus, CardAction.CounterReset)
        } else {
            listOf(CardAction.CounterMinus, CardAction.CounterPlus)
        }.mapIndexed { i, a -> SceneButton(i, a, scene.actionLabel(a), primary = i == 1) }
        return CardScene.Counter(
            value = c.count.toString(),
            total = scene.counterOf(c.goal),
            caption = card.counterLabel.trim().ifEmpty { null },
            // oltre 20 passi la barra resta di 20 segmenti, riempiti in proporzione
            segments = c.goal.coerceAtMost(MAX_SEGMENTS),
            filled = if (c.goal <= MAX_SEGMENTS) c.count.coerceIn(0, c.goal) else c.count * MAX_SEGMENTS / c.goal,
            complete = complete,
            buttons = buttons
        )
    }

    private const val MAX_SEGMENTS = 20
}

object ColorOsCardJson {
    const val KEY_RV = "atoll_rv"
    const val KEY_TAP = "atoll_tap"

    // la capsula riusa lo stesso shortCriticalText della pillola nativa, così resta larga uguale
    fun capsule(shortCriticalText: String): String =
        Json.write(mapOf("capsuleData" to mapOf("type" to "t_sym", "lastTitle" to mapOf("text" to shortCriticalText))))

    // tutta la scheda è una vista di Atoll (t_custom): i modelli fissi di ColorOS tagliano e nascondono il testo
    fun card(tappable: Boolean, backgroundArgbHex: String? = null): String {
        val attr = linkedMapOf<String, Any?>()
        if (tappable) attr["intent"] = "pi/$KEY_TAP"
        if (!backgroundArgbHex.isNullOrBlank()) {
            attr["background"] = "colorInt/#$backgroundArgbHex"
            attr["bgLight"] = "false"
        }
        return Json.write(
            linkedMapOf<String, Any?>("primaryData" to mapOf("type" to "t_custom", "rvId" to "layout/$KEY_RV")).apply {
                if (attr.isNotEmpty()) put("attr", attr)
            }
        )
    }

    fun tappable(card: ExpandedCardConfig) = card.tapAction != TapAction.NONE
}

// JSON minimo, così questa parte resta testabile sulla JVM (org.json lì non funziona)
object Json {
    fun write(value: Any?): String = StringBuilder().also { append(it, value) }.toString()

    private fun append(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is String -> quote(sb, value)
            is Boolean, is Int, is Long -> sb.append(value.toString())
            is Number -> sb.append(value.toDouble().toString())
            is Map<*, *> -> {
                sb.append('{')
                value.entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) sb.append(',')
                    quote(sb, k.toString())
                    sb.append(':')
                    append(sb, v)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                value.forEachIndexed { i, v ->
                    if (i > 0) sb.append(',')
                    append(sb, v)
                }
                sb.append(']')
            }
            else -> quote(sb, value.toString())
        }
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }
}
