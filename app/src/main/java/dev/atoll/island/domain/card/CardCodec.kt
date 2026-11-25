package dev.atoll.island.domain.card

import dev.atoll.island.data.model.CardAction
import dev.atoll.island.data.model.StatMetric
import dev.atoll.island.data.model.ToolResult

// una riga per pulsante, campi separati da tab; tab, a capo e \ vengono escapati
object CardCodec {

    fun encodeActions(actions: List<CardAction>): String = actions.joinToString("\n") { action ->
        fields(action).joinToString("\t") { escape(it) }
    }

    fun decodeActions(raw: String?): List<CardAction> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split('\n').mapNotNull { line -> parse(line.split('\t').map(::unescape)) }
    }

    fun encodeStats(stats: List<StatMetric>): String = stats.joinToString(",") { it.name }

    fun decodeStats(raw: String?): List<StatMetric>? =
        raw?.split(',')?.mapNotNull { StatMetric.fromName(it) }

    fun encodeTool(result: ToolResult?): String = when (result) {
        null -> ""
        is ToolResult.Dice -> "dice:${result.value}"
        is ToolResult.Coin -> "coin:${if (result.heads) 1 else 0}"
        is ToolResult.Number -> "number:${result.value}:${result.min}:${result.max}"
    }

    fun decodeTool(raw: String?): ToolResult? {
        val parts = raw?.split(':') ?: return null
        val n = parts.drop(1).map { it.toIntOrNull() ?: return null }
        return when (parts.first()) {
            "dice" -> n.getOrNull(0)?.takeIf { it in 1..6 }?.let { ToolResult.Dice(it) }
            "coin" -> n.getOrNull(0)?.let { ToolResult.Coin(it == 1) }
            "number" -> if (n.size == 3) ToolResult.Number(n[0], n[1], n[2]) else null
            else -> null
        }
    }

    private fun fields(action: CardAction): List<String> = when (action) {
        is CardAction.LaunchApp -> listOf("app", action.packageName, action.appLabel)
        is CardAction.OpenUrl -> listOf("url", action.url, action.label)
        is CardAction.CopySnippet -> listOf("copy", action.label, action.text)
        CardAction.ToggleFlashlight -> listOf("flashlight")
        CardAction.MediaPlayPause -> listOf("play_pause")
        CardAction.MediaNext -> listOf("next")
        CardAction.EditNoteInline -> listOf("edit_note")
        CardAction.CounterPlus -> listOf("counter_plus")
        CardAction.CounterMinus -> listOf("counter_minus")
        CardAction.CounterReset -> listOf("counter_reset")
        CardAction.TimerToggle -> listOf("timer_toggle")
        CardAction.TimerAdd5Min -> listOf("timer_add")
        CardAction.TimerReset -> listOf("timer_reset")
        CardAction.RollDice -> listOf("dice")
        CardAction.FlipCoin -> listOf("coin")
        CardAction.RandomNumber -> listOf("number")
        CardAction.RefreshStats -> listOf("refresh")
    }

    private fun parse(f: List<String>): CardAction? = when (f.firstOrNull()) {
        "app" -> if (f.size == 3) CardAction.LaunchApp(f[1], f[2]) else null
        "url" -> if (f.size == 3) CardAction.OpenUrl(f[1], f[2]) else null
        "copy" -> if (f.size == 3) CardAction.CopySnippet(f[1], f[2]) else null
        "flashlight" -> CardAction.ToggleFlashlight
        "play_pause" -> CardAction.MediaPlayPause
        "next" -> CardAction.MediaNext
        "edit_note" -> CardAction.EditNoteInline
        "counter_plus" -> CardAction.CounterPlus
        "counter_minus" -> CardAction.CounterMinus
        "counter_reset" -> CardAction.CounterReset
        "timer_toggle" -> CardAction.TimerToggle
        "timer_add" -> CardAction.TimerAdd5Min
        "timer_reset" -> CardAction.TimerReset
        "dice" -> CardAction.RollDice
        "coin" -> CardAction.FlipCoin
        "number" -> CardAction.RandomNumber
        "refresh" -> CardAction.RefreshStats
        else -> null
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    private fun unescape(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                out.append(
                    when (s[i + 1]) {
                        't' -> '\t'
                        'n' -> '\n'
                        else -> s[i + 1]
                    }
                )
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
