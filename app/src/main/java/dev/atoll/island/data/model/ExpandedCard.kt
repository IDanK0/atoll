package dev.atoll.island.data.model

enum class ExpandedCardMode {
    MINIMAL,
    CUSTOM_NOTE,
    SHORTCUT_DOCK,
    DEVICE_STATS,
    PROGRESS_COUNTER,
    FOCUS_TIMER,
    QUICK_TOOLS;

    companion object {
        fun fromName(name: String?): ExpandedCardMode = entries.firstOrNull { it.name == name } ?: MINIMAL
    }
}

enum class StatMetric {
    BATTERY,
    BATTERY_TEMP,
    DAY_PROGRESS,
    FREE_RAM,
    DATE_TIME;

    companion object {
        fun fromName(name: String?): StatMetric? = entries.firstOrNull { it.name == name }
    }
}

enum class CounterSource { CUSTOM, DAY }

enum class TapAction { OPEN_ATOLL, LAUNCH_APP, NONE }

enum class LargeIconType { NONE, VECTOR, IMAGE }

enum class BadgeIcon { BOLT, NOTE, TIMER, STAR, TERMINAL, MUSIC, PHONE, BOOKMARK }

data class LargeIconConfig(
    val type: LargeIconType = LargeIconType.NONE,
    val icon: BadgeIcon = BadgeIcon.BOLT,
    val tint: Int = CardColors.BADGE,
    // cambia a ogni foto nuova, così la cache dell'immagine si svuota
    val imageVersion: Long = 0L
)

object CardColors {
    // 0 = colore d'accento del sistema, che segue lo sfondo
    const val DEFAULT = 0
    const val BADGE = 0xFF3F51B5.toInt()
    val SWATCHES = listOf(
        0xFF3F51B5.toInt(),
        0xFF00897B.toInt(),
        0xFF43A047.toInt(),
        0xFFF4511E.toInt(),
        0xFFD81B60.toInt(),
        0xFF5E35B1.toInt(),
        0xFF546E7A.toInt()
    )
}

sealed interface CardAction {
    data class LaunchApp(val packageName: String, val appLabel: String) : CardAction
    data class OpenUrl(val url: String, val label: String) : CardAction
    data class CopySnippet(val label: String, val text: String) : CardAction
    data object ToggleFlashlight : CardAction
    data object MediaPlayPause : CardAction
    data object MediaNext : CardAction
    data object EditNoteInline : CardAction
    data object CounterPlus : CardAction
    data object CounterMinus : CardAction
    data object CounterReset : CardAction
    data object TimerToggle : CardAction
    data object TimerAdd5Min : CardAction
    data object TimerReset : CardAction
    data object RollDice : CardAction
    data object FlipCoin : CardAction
    data object RandomNumber : CardAction
    data object RefreshStats : CardAction

    // questi aprono qualcosa, quindi PendingIntent.getActivity; gli altri vanno al receiver
    val opensUi: Boolean get() = this is LaunchApp || this is OpenUrl
}

data class CounterState(val count: Int = 0, val goal: Int = 8)

// endAtMs > 0 = sta andando; da fermo il tempo rimasto sta in remainingMs
data class TimerState(
    val durationMs: Long = DEFAULT_DURATION_MS,
    val totalMs: Long = DEFAULT_DURATION_MS,
    val remainingMs: Long = DEFAULT_DURATION_MS,
    val endAtMs: Long = 0L
) {
    val isRunning: Boolean get() = endAtMs > 0L
    val isFinished: Boolean get() = !isRunning && remainingMs <= 0L

    fun remainingAt(nowMs: Long): Long = if (isRunning) (endAtMs - nowMs).coerceAtLeast(0L) else remainingMs

    companion object {
        const val MINUTE_MS = 60_000L
        const val DEFAULT_DURATION_MS = 25 * MINUTE_MS
    }
}

sealed interface ToolResult {
    data class Dice(val value: Int) : ToolResult
    data class Coin(val heads: Boolean) : ToolResult
    data class Number(val value: Int, val min: Int, val max: Int) : ToolResult
}

data class ExpandedCardConfig(
    val mode: ExpandedCardMode = ExpandedCardMode.MINIMAL,
    val noteTitle: String = "",
    val noteSubText: String = "",
    val noteBody: String = "",
    val dockTitle: String = "",
    val stats: List<StatMetric> = DEFAULT_STATS,
    // con MetricStyle ColorOS aggiunge l'icona dell'app alla scheda, quindi è da attivare a mano
    val useMetricStyle: Boolean = false,
    // schede disegnate con i layout di ColorOS (extra oplus.livealert.card); fuori da ColorOS vengono ignorate
    val useColorOsLayouts: Boolean = true,
    val counterSource: CounterSource = CounterSource.CUSTOM,
    val counterLabel: String = "",
    val counter: CounterState = CounterState(),
    val accentColor: Int = CardColors.DEFAULT,
    val timer: TimerState = TimerState(),
    val randomMin: Int = 1,
    val randomMax: Int = 100,
    val lastTool: ToolResult? = null,
    // null = pulsanti predefiniti della modalità, lista vuota = nessun pulsante
    val actionOverrides: Map<ExpandedCardMode, List<CardAction>> = emptyMap(),
    val tapAction: TapAction = TapAction.OPEN_ATOLL,
    val tapPackage: String = "",
    val largeIcon: LargeIconConfig = LargeIconConfig()
) {
    companion object {
        const val MAX_STATS = 3
        val DEFAULT_STATS = listOf(StatMetric.BATTERY, StatMetric.DAY_PROGRESS, StatMetric.DATE_TIME)
    }
}
