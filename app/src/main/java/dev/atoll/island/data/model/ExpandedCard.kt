package dev.atoll.island.data.model

enum class ExpandedCardMode {
    MINIMAL,
    CUSTOM_NOTE,
    SHORTCUT_DOCK,
    DEVICE_STATS,
    PROGRESS_COUNTER,
    QUICK_TOOLS,
    CUSTOM_TEMPLATE;

    companion object {
        fun fromName(name: String?): ExpandedCardMode = entries.firstOrNull { it.name == name } ?: SHORTCUT_DOCK
    }
}

enum class StatMetric {
    BATTERY,
    BATTERY_POWER,
    CHARGE_STATE,
    CHARGE_TIME,
    BATTERY_TEMP,
    BATTERY_CYCLES,
    FREE_RAM,
    STORAGE_FREE,
    CPU_FREQUENCY,
    THERMAL_STATE,
    UPTIME,
    DAY_PROGRESS,
    DATE_TIME;

    val needsFastRefresh: Boolean
        get() = this == BATTERY_POWER || this == CPU_FREQUENCY || this == CHARGE_TIME

    companion object {
        fun fromName(name: String?): StatMetric? = entries.firstOrNull { it.name == name }
    }
}

enum class ChargeState { AC, USB, WIRELESS, FULL, DISCHARGING }

enum class CounterSource { CUSTOM, DAY, WEEK, MONTH, YEAR }

enum class TapAction { OPEN_ATOLL, LAUNCH_APP, NONE }

enum class LargeIconType { NONE, VECTOR, IMAGE }

enum class BadgeIcon { BOLT, NOTE, TIMER, STAR, TERMINAL, MUSIC, PHONE, BOOKMARK }

enum class CardBackground(val colorHex: String?) {
    DEFAULT(null),
    SYSTEM(null),
    OCEAN("88142C48"),
    FOREST("88143628"),
    PLUM("88301B47"),
    AMBER("883D2812"),
    PITCH("F2090B0E");

    companion object {
        fun fromName(name: String?): CardBackground = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

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
}

sealed interface CardAction {
    data class LaunchApp(val packageName: String, val appLabel: String) : CardAction
    data class AppShortcut(val intentUri: String, val label: String, val packageName: String = "") : CardAction
    data class OpenUrl(val url: String, val label: String) : CardAction
    data class CopySnippet(val label: String, val text: String) : CardAction
    data class DialNumber(val phoneNumber: String, val label: String) : CardAction
    data class ClockTimer(val seconds: Int, val label: String) : CardAction
    data object Camera : CardAction
    data object VolumePanel : CardAction
    data object InternetPanel : CardAction
    data object WifiPanel : CardAction
    data object ToggleFlashlight : CardAction
    data object MediaPlayPause : CardAction
    data object MediaNext : CardAction
    data object MediaPrevious : CardAction
    data object EditNoteInline : CardAction
    data object CounterPlus : CardAction
    data object CounterMinus : CardAction
    data object CounterReset : CardAction
    data object RollDice : CardAction
    data object FlipCoin : CardAction
    data object RandomNumber : CardAction
    data object RefreshStats : CardAction

    // questi aprono qualcosa, quindi PendingIntent.getActivity; gli altri vanno al receiver
    val opensUi: Boolean
        get() = this is LaunchApp || this is AppShortcut || this is OpenUrl ||
            this is DialNumber || this is ClockTimer ||
            this == Camera || this == VolumePanel || this == InternetPanel || this == WifiPanel
}

data class CounterState(
    val count: Int = 0,
    val goal: Int = 8,
    val step: Int = 1,
    val resetAtMidnight: Boolean = false,
    val lastResetEpochDay: Long = 0L
)

sealed interface ToolResult {
    data class Dice(val value: Int) : ToolResult
    data class Coin(val heads: Boolean) : ToolResult
    data class Number(val value: Int, val min: Int, val max: Int) : ToolResult
}

data class ExpandedCardConfig(
    val mode: ExpandedCardMode = ExpandedCardMode.SHORTCUT_DOCK,
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
    val background: CardBackground = CardBackground.DEFAULT,
    val customTemplateJson: String = "",
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
