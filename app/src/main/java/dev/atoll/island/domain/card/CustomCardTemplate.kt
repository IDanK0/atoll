package dev.atoll.island.domain.card

import dev.atoll.island.data.model.CardAction
import dev.atoll.island.data.model.CardBackground
import dev.atoll.island.data.model.CounterSource
import dev.atoll.island.data.model.ExpandedCardConfig
import dev.atoll.island.data.model.ExpandedCardMode
import dev.atoll.island.data.model.StatMetric
import java.util.Locale

// schede personalizzate in JSON senza codice eseguibile
sealed interface MakerBlock {
    data class Value(val text: String, val sub: String? = null, val caption: String? = null) : MakerBlock
    data class Text(val text: String, val muted: Boolean = false) : MakerBlock
    data class Bar(val value: String, val max: Int = 100, val segments: Int = 10) : MakerBlock
    data class Rings(val cells: List<MakerRingSpec>) : MakerBlock
    data class Buttons(val actions: List<CardAction>) : MakerBlock
}

data class MakerRingSpec(
    val metric: StatMetric? = null,
    val value: String = "",
    val caption: String = "",
    val percent: String? = null
)

data class CustomCardTemplate(
    val name: String = "",
    val background: CardBackground? = null,
    val customBgHex: String? = null,
    val blocks: List<MakerBlock> = emptyList()
) {
    val actions: List<CardAction>
        get() = blocks.filterIsInstance<MakerBlock.Buttons>().flatMap { it.actions }.take(MAX_BUTTONS)

    companion object {
        const val MAX_BLOCKS = 4
        const val MAX_RINGS = 3
        const val MAX_BUTTONS = 4
        const val MAX_BAR_SEGMENTS = 24
        const val MAX_JSON_BYTES = 32_768
    }
}

sealed interface MakerParseResult {
    data class Ok(val template: CustomCardTemplate) : MakerParseResult
    data class Invalid(val reason: MakerError) : MakerParseResult
}

enum class MakerError { EMPTY, TOO_LARGE, SYNTAX, NO_BLOCKS, UNKNOWN_BLOCK }

data class ResolvedMakerBar(val filled: Int, val segments: Int)

sealed interface ResolvedMakerBlock {
    data class Value(val text: String, val sub: String?, val caption: String?) : ResolvedMakerBlock
    data class Text(val text: String, val muted: Boolean) : ResolvedMakerBlock
    data class Bar(val bar: ResolvedMakerBar) : ResolvedMakerBlock
    data class Rings(val cells: List<StatCell>) : ResolvedMakerBlock
    data class Buttons(val buttons: List<SceneButton>) : ResolvedMakerBlock
}

data class MakerMonitorNeeds(
    val battery: Boolean = false,
    val tick: Boolean = false,
    val fastStats: Boolean = false
)

object CustomCardPresets {
    val CLOCK_AND_CONTROLS: String = """
        {
          "name": "Clock & Tools",
          "background": "default",
          "blocks": [
            { "type": "value", "text": "{time}", "sub": "{battery} · {day_left}" },
            { "type": "bar", "value": "{day_pct}", "max": 100, "segments": 12 },
            { "type": "buttons", "items": ["camera", "wifi", "flashlight"] }
          ]
        }
    """.trimIndent()

    val HARDWARE_DECK: String = """
        {
          "name": "Hardware",
          "background": "ocean",
          "blocks": [
            { "type": "rings", "metrics": ["BATTERY", "CPU_FREQUENCY", "THERMAL_STATE"] },
            { "type": "text", "text": "{charge} · {power} · RAM {ram}" }
          ]
        }
    """.trimIndent()

    val FOCUS_NOTE: String = """
        {
          "name": "Focus Desk",
          "background": "forest",
          "blocks": [
            { "type": "value", "text": "{note_title}", "sub": "{time}" },
            { "type": "text", "text": "{note}", "muted": true },
            { "type": "buttons", "items": ["previous", "play_pause", "next"] }
          ]
        }
    """.trimIndent()

    fun effectiveJson(raw: String): String = raw.trim().ifEmpty { CLOCK_AND_CONTROLS }

    fun resolveTemplate(raw: String): CustomCardTemplate =
        (CustomCardParser.parse(effectiveJson(raw)) as? MakerParseResult.Ok)?.template
            ?: (CustomCardParser.parse(CLOCK_AND_CONTROLS) as MakerParseResult.Ok).template
}

object CustomCardParser {

    fun parse(raw: String): MakerParseResult {
        val text = raw.trim()
        if (text.isEmpty()) return MakerParseResult.Invalid(MakerError.EMPTY)
        if (text.length > CustomCardTemplate.MAX_JSON_BYTES) return MakerParseResult.Invalid(MakerError.TOO_LARGE)
        val root = runCatching { SimpleJson.parse(text) }.getOrNull() as? Map<*, *>
            ?: return MakerParseResult.Invalid(MakerError.SYNTAX)

        val blocksRaw = root["blocks"] as? List<*> ?: return MakerParseResult.Invalid(MakerError.NO_BLOCKS)
        val blocks = mutableListOf<MakerBlock>()
        for (entry in blocksRaw) {
            if (blocks.size >= CustomCardTemplate.MAX_BLOCKS) break
            val obj = entry as? Map<*, *> ?: return MakerParseResult.Invalid(MakerError.SYNTAX)
            val block = parseBlock(obj) ?: return MakerParseResult.Invalid(MakerError.UNKNOWN_BLOCK)
            blocks += block
        }
        if (blocks.isEmpty()) return MakerParseResult.Invalid(MakerError.NO_BLOCKS)

        val name = (root["name"] as? String)?.trim().orEmpty().take(40)
        val bgRaw = (root["background"] as? String)?.trim()
        val (bgPreset, bgHex) = parseBackground(bgRaw)
        return MakerParseResult.Ok(CustomCardTemplate(name = name, background = bgPreset, customBgHex = bgHex, blocks = blocks))
    }

    fun monitorNeeds(raw: String): MakerMonitorNeeds {
        val text = CustomCardPresets.effectiveJson(raw)
        val parsed = (parse(text) as? MakerParseResult.Ok)?.template
        val used = extractPlaceholders(text)
        val metrics = parsed?.blocks?.filterIsInstance<MakerBlock.Rings>()
            ?.flatMap { it.cells.mapNotNull { c -> c.metric } }.orEmpty()
        val battery = metrics.any {
            it == StatMetric.BATTERY || it == StatMetric.BATTERY_POWER || it == StatMetric.CHARGE_STATE ||
                it == StatMetric.CHARGE_TIME || it == StatMetric.BATTERY_TEMP || it == StatMetric.BATTERY_CYCLES
        } || used.any { it in BATTERY_KEYS }
        val tick = metrics.any { it == StatMetric.DAY_PROGRESS || it == StatMetric.DATE_TIME } ||
            used.any { it in TICK_KEYS }
        val fast = metrics.any { it.needsFastRefresh } || used.any { it in FAST_KEYS }
        return MakerMonitorNeeds(battery = battery, tick = tick, fastStats = fast)
    }

    private fun extractPlaceholders(raw: String): Set<String> {
        val out = hashSetOf<String>()
        var i = 0
        while (i < raw.length) {
            if (raw[i] == '{') {
                val end = raw.indexOf('}', i + 1)
                if (end > i + 1 && end - i <= 24) {
                    out += raw.substring(i + 1, end).lowercase(Locale.ROOT)
                    i = end + 1
                    continue
                }
            }
            i++
        }
        return out
    }

    private fun parseBackground(raw: String?): Pair<CardBackground?, String?> {
        if (raw.isNullOrEmpty()) return null to null
        val byName = CardBackground.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        if (byName != null) return byName to null
        val hex = raw.removePrefix("#")
        if (hex.length == 6 && hex.all(::isHex)) return null to "99${hex.uppercase(Locale.ROOT)}"
        if (hex.length == 8 && hex.all(::isHex)) return null to hex.uppercase(Locale.ROOT)
        return null to null
    }

    private fun isHex(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    private fun parseBlock(obj: Map<*, *>): MakerBlock? = when ((obj["type"] as? String)?.lowercase(Locale.ROOT)) {
        "value" -> MakerBlock.Value(
            text = (obj["text"] as? String).orEmpty(),
            sub = (obj["sub"] as? String)?.takeIf { it.isNotEmpty() },
            caption = (obj["caption"] as? String)?.takeIf { it.isNotEmpty() }
        )
        "text" -> MakerBlock.Text(
            text = (obj["text"] as? String).orEmpty(),
            muted = obj["muted"] as? Boolean ?: false
        )
        "caption" -> MakerBlock.Text(
            text = (obj["text"] as? String).orEmpty(),
            muted = true
        )
        "bar" -> MakerBlock.Bar(
            value = strOrNum(obj["value"]) ?: "0",
            max = intOr(obj["max"], 100).coerceIn(1, 10_000),
            segments = intOr(obj["segments"], 10).coerceIn(1, CustomCardTemplate.MAX_BAR_SEGMENTS)
        )
        "ring", "rings" -> parseRings(obj)
        "buttons" -> parseButtons(obj)
        else -> null
    }

    private fun parseRings(obj: Map<*, *>): MakerBlock.Rings? {
        val metrics = (obj["metrics"] as? List<*>)?.mapNotNull { m ->
            StatMetric.fromName((m as? String)?.uppercase(Locale.ROOT))?.let { MakerRingSpec(metric = it) }
        }.orEmpty()
        val items = (obj["items"] as? List<*>)?.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val metric = (m["metric"] as? String)?.let { StatMetric.fromName(it.uppercase(Locale.ROOT)) }
            if (metric != null) {
                MakerRingSpec(metric = metric)
            } else {
                MakerRingSpec(
                    value = strOrNum(m["value"]).orEmpty(),
                    caption = (m["label"] as? String ?: m["caption"] as? String).orEmpty(),
                    percent = strOrNum(m["percent"])
                )
            }
        }.orEmpty()
        val cells = (metrics + items).take(CustomCardTemplate.MAX_RINGS)
        return if (cells.isEmpty()) null else MakerBlock.Rings(cells)
    }

    private fun parseButtons(obj: Map<*, *>): MakerBlock.Buttons {
        val items = obj["items"] as? List<*> ?: emptyList<Any?>()
        val actions = items.mapNotNull(::parseAction).filter {
            ActionSlots.ownMode(it) == null && ActionSlots.isValid(it)
        }.distinct().take(CustomCardTemplate.MAX_BUTTONS)
        return MakerBlock.Buttons(actions)
    }

    private fun parseAction(raw: Any?): CardAction? = when (raw) {
        is String -> parseActionTag(raw)
        is Map<*, *> -> {
            val tag = (raw["action"] as? String ?: raw["type"] as? String)?.lowercase(Locale.ROOT)
            when (tag) {
                "app" -> CardAction.LaunchApp((raw["package"] as? String).orEmpty(), (raw["label"] as? String).orEmpty())
                "url" -> CardAction.OpenUrl((raw["url"] as? String).orEmpty(), (raw["label"] as? String).orEmpty())
                "copy" -> CardAction.CopySnippet((raw["label"] as? String).orEmpty(), (raw["text"] as? String).orEmpty())
                "dial" -> CardAction.DialNumber((raw["phone"] as? String).orEmpty(), (raw["label"] as? String).orEmpty())
                "timer_app", "timer" -> CardAction.ClockTimer(intOr(raw["seconds"], 300), (raw["label"] as? String).orEmpty())
                else -> tag?.let(::parseActionTag)
            }
        }
        else -> null
    }

    private fun parseActionTag(spec: String): CardAction? {
        val parts = spec.split(':', limit = 3)
        return when (parts.first().lowercase(Locale.ROOT)) {
            "flashlight" -> CardAction.ToggleFlashlight
            "camera" -> CardAction.Camera
            "volume" -> CardAction.VolumePanel
            "internet" -> CardAction.InternetPanel
            "wifi" -> CardAction.WifiPanel
            "play_pause" -> CardAction.MediaPlayPause
            "next" -> CardAction.MediaNext
            "previous" -> CardAction.MediaPrevious
            "app" -> parts.getOrNull(1)?.let { CardAction.LaunchApp(it, parts.getOrNull(2).orEmpty()) }
            "url" -> if (spec.startsWith("url:", ignoreCase = true)) {
                val rest = spec.substring(4)
                val labelSep = rest.lastIndexOf('|')
                if (labelSep >= 0) CardAction.OpenUrl(rest.substring(0, labelSep), rest.substring(labelSep + 1))
                else CardAction.OpenUrl(rest, "")
            } else null
            "dial" -> parts.getOrNull(1)?.let { CardAction.DialNumber(it, parts.getOrNull(2).orEmpty()) }
            "copy" -> if (parts.size >= 3) CardAction.CopySnippet(parts[1], parts[2]) else null
            "timer_app" -> parts.getOrNull(1)?.toIntOrNull()?.let { CardAction.ClockTimer(it, parts.getOrNull(2).orEmpty()) }
            else -> null
        }
    }

    private fun strOrNum(v: Any?): String? = when (v) {
        is String -> v
        is Number -> v.toInt().toString()
        else -> null
    }

    private fun intOr(v: Any?, fallback: Int): Int = when (v) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull() ?: fallback
        else -> fallback
    }

    private val BATTERY_KEYS = setOf("battery", "battery_pct", "power", "charge", "charge_time", "temp", "cycles")
    private val TICK_KEYS = setOf("time", "date", "day_pct", "day_left", "week_pct", "week_left", "month_pct", "month_left", "year_pct", "year_left")
    private val FAST_KEYS = setOf("power", "cpu", "ram", "free_ram")
}

object CustomCardBinder {

    fun resolveBlocks(
        template: CustomCardTemplate,
        card: ExpandedCardConfig,
        env: CardEnvironment,
        strings: CardStrings,
        scene: SceneStrings
    ): List<ResolvedMakerBlock> {
        val vars = buildVars(card, env, strings)
        return template.blocks.mapNotNull { block ->
            when (block) {
                is MakerBlock.Value -> ResolvedMakerBlock.Value(
                    text = substitute(block.text, vars).ifEmpty { CardText.BLANK },
                    sub = block.sub?.let { substitute(it, vars) }?.takeIf { it.isNotEmpty() },
                    caption = block.caption?.let { substitute(it, vars) }?.takeIf { it.isNotEmpty() }
                )
                is MakerBlock.Text -> ResolvedMakerBlock.Text(
                    text = substitute(block.text, vars),
                    muted = block.muted
                )
                is MakerBlock.Bar -> {
                    val v = parseFloat(substitute(block.value, vars)) ?: 0f
                    val frac = (v / block.max.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val filled = (frac * block.segments + 0.5f).toInt().coerceIn(0, block.segments)
                    ResolvedMakerBlock.Bar(ResolvedMakerBar(filled, block.segments))
                }
                is MakerBlock.Rings -> {
                    val cells = block.cells.take(CustomCardTemplate.MAX_RINGS).map { cell ->
                        if (cell.metric != null) {
                            CardSceneBuilder.statCell(cell.metric, env, strings)
                        } else {
                            val pct = cell.percent?.let { parseFloat(substitute(it, vars)) }?.div(100f)?.coerceIn(0f, 1f)
                            StatCell(
                                metric = StatMetric.BATTERY,
                                value = substitute(cell.value, vars).ifEmpty { strings.unavailable() },
                                caption = substitute(cell.caption, vars),
                                fraction = pct
                            )
                        }
                    }
                    if (cells.isEmpty()) null else ResolvedMakerBlock.Rings(cells)
                }
                is MakerBlock.Buttons -> {
                    val btns = block.actions.take(CustomCardTemplate.MAX_BUTTONS).mapIndexed { i, a ->
                        SceneButton(i, a, scene.actionLabel(a))
                    }
                    if (btns.isEmpty()) null else ResolvedMakerBlock.Buttons(btns)
                }
            }
        }
    }

    fun summaryLines(blocks: List<ResolvedMakerBlock>): List<String> = blocks.mapNotNull { b ->
        when (b) {
            is ResolvedMakerBlock.Value -> listOfNotNull(b.text.takeIf { it != CardText.BLANK }, b.sub, b.caption).joinToString(" · ").takeIf { it.isNotBlank() }
            is ResolvedMakerBlock.Text -> b.text.takeIf { it.isNotBlank() }
            is ResolvedMakerBlock.Rings -> b.cells.joinToString(" · ") { "${it.caption} ${it.value}" }.takeIf { it.isNotBlank() }
            is ResolvedMakerBlock.Bar, is ResolvedMakerBlock.Buttons -> null
        }
    }

    private fun buildVars(card: ExpandedCardConfig, env: CardEnvironment, strings: CardStrings): Map<String, String> {
        val s = env.stats
        val day = CardText.timeProgress(CounterSource.DAY, env)
        val week = CardText.timeProgress(CounterSource.WEEK, env)
        val month = CardText.timeProgress(CounterSource.MONTH, env)
        val year = CardText.timeProgress(CounterSource.YEAR, env)
        val c = card.counter
        val cPct = if (c.goal > 0) (c.count.coerceIn(0, c.goal) * 100) / c.goal else 0
        val ram = s.freeRamGb?.let(strings::gigabytes) ?: strings.unavailable()
        val noteText = NoteMarkdown.parse(card.noteBody).text.trim().ifEmpty { strings.unavailable() }
        return mapOf(
            "battery" to (s.batteryPercent?.let(strings::percent) ?: strings.unavailable()),
            "battery_pct" to (s.batteryPercent ?: 0).toString(),
            "power" to (s.batteryPowerWatts?.let(strings::watts) ?: strings.unavailable()),
            "charge" to (s.chargeState?.let(strings::chargeState) ?: strings.unavailable()),
            "charge_time" to (s.chargeTimeMinutes?.let { strings.durationMinutes(it.toLong()) } ?: strings.unavailable()),
            "temp" to (s.batteryTempC?.let(strings::temperature) ?: strings.unavailable()),
            "cycles" to (s.batteryCycles?.let(strings::cycles) ?: strings.unavailable()),
            "ram" to ram,
            "free_ram" to ram,
            "storage" to (s.storageFreeGb?.let(strings::gigabytes) ?: strings.unavailable()),
            "cpu" to (s.cpuMaxMhz?.let(strings::cpuFreq) ?: strings.unavailable()),
            "thermal" to (s.thermalStatus?.let(strings::thermalStatus) ?: strings.unavailable()),
            "uptime" to (s.uptimeMinutes?.let(strings::durationMinutes) ?: strings.unavailable()),
            "time" to env.timeText,
            "date" to env.dateText,
            "day_pct" to day.percent.toString(),
            "day_left" to strings.counterRemaining(day.remainingMinutesToday, day.remainingDays),
            "week_pct" to week.percent.toString(),
            "week_left" to strings.counterRemaining(week.remainingMinutesToday, week.remainingDays),
            "month_pct" to month.percent.toString(),
            "month_left" to strings.counterRemaining(month.remainingMinutesToday, month.remainingDays),
            "year_pct" to year.percent.toString(),
            "year_left" to strings.counterRemaining(year.remainingMinutesToday, year.remainingDays),
            "counter" to c.count.toString(),
            "goal" to c.goal.toString(),
            "counter_pct" to cPct.toString(),
            "note_title" to card.noteTitle.trim().ifEmpty { card.counterLabel.trim().ifEmpty { strings.unavailable() } },
            "note" to noteText
        )
    }

    private fun substitute(template: String, vars: Map<String, String>): String {
        if ('{' !in template) return template
        val out = StringBuilder(template.length)
        var i = 0
        while (i < template.length) {
            if (template[i] == '{') {
                val end = template.indexOf('}', i + 1)
                if (end > i + 1) {
                    val key = template.substring(i + 1, end).lowercase(Locale.ROOT)
                    val value = vars[key]
                    if (value != null) {
                        out.append(value)
                        i = end + 1
                        continue
                    }
                }
            }
            out.append(template[i])
            i++
        }
        return out.toString()
    }

    private fun parseFloat(raw: String): Float? =
        raw.trim().removeSuffix("%").replace(',', '.').toFloatOrNull()
}

// parser JSON senza dipendenze Android, per i test JVM
internal object SimpleJson {
    fun parse(input: String): Any? = Reader(input).readDocument()

    private class Reader(private val s: String) {
        private var pos = 0

        fun readDocument(): Any? {
            skipWs()
            val v = readValue()
            skipWs()
            if (pos != s.length) error("trailing characters")
            return v
        }

        private fun readValue(): Any? {
            skipWs()
            if (pos >= s.length) error("unexpected eof")
            return when (val c = s[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't', 'f' -> readBool()
                'n' -> readNull()
                '-', in '0'..'9' -> readNumber()
                else -> error("unexpected char $c")
            }
        }

        private fun readObject(): Map<String, Any?> {
            pos++
            val map = linkedMapOf<String, Any?>()
            skipWs()
            if (peek('}')) {
                pos++
                return map
            }
            while (true) {
                skipWs()
                if (!peek('"')) error("expected string key")
                val key = readString()
                skipWs()
                expect(':')
                map[key] = readValue()
                skipWs()
                when {
                    peek(',') -> pos++
                    peek('}') -> {
                        pos++
                        return map
                    }
                    else -> error("expected , or }")
                }
            }
        }

        private fun readArray(): List<Any?> {
            pos++
            val list = mutableListOf<Any?>()
            skipWs()
            if (peek(']')) {
                pos++
                return list
            }
            while (true) {
                list += readValue()
                skipWs()
                when {
                    peek(',') -> pos++
                    peek(']') -> {
                        pos++
                        return list
                    }
                    else -> error("expected , or ]")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val out = StringBuilder()
            while (pos < s.length) {
                val c = s[pos++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (pos >= s.length) error("bad escape")
                        when (val e = s[pos++]) {
                            '"', '\\', '/' -> out.append(e)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) error("bad unicode")
                                out.append(s.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> error("bad escape $e")
                        }
                    }
                    else -> out.append(c)
                }
            }
            error("unterminated string")
        }

        private fun readBool(): Boolean = if (s.startsWith("true", pos)) {
            pos += 4; true
        } else if (s.startsWith("false", pos)) {
            pos += 5; false
        } else error("bad bool")

        private fun readNull(): Any? {
            if (!s.startsWith("null", pos)) error("bad null")
            pos += 4
            return null
        }

        private fun readNumber(): Number {
            val start = pos
            if (peek('-')) pos++
            while (pos < s.length && s[pos].isDigit()) pos++
            var isFloat = false
            if (peek('.')) {
                isFloat = true
                pos++
                while (pos < s.length && s[pos].isDigit()) pos++
            }
            if (peek('e') || peek('E')) {
                isFloat = true
                pos++
                if (peek('+') || peek('-')) pos++
                while (pos < s.length && s[pos].isDigit()) pos++
            }
            val raw = s.substring(start, pos)
            return if (isFloat) raw.toDouble() else raw.toLong()
        }

        private fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        private fun peek(c: Char) = pos < s.length && s[pos] == c

        private fun expect(c: Char) {
            if (!peek(c)) error("expected $c")
            pos++
        }
    }
}
