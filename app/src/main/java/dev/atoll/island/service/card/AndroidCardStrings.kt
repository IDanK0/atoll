package dev.atoll.island.service.card

import android.content.Context
import android.os.PowerManager
import android.text.format.DateFormat
import android.text.format.DateUtils
import dev.atoll.island.R
import dev.atoll.island.data.model.CardAction
import dev.atoll.island.data.model.CardBackground
import dev.atoll.island.data.model.ChargeState
import dev.atoll.island.data.model.CounterSource
import dev.atoll.island.data.model.ExpandedCardMode
import dev.atoll.island.data.model.StatMetric
import dev.atoll.island.data.model.ToolResult
import dev.atoll.island.domain.card.CardStrings
import dev.atoll.island.domain.card.SceneStrings
import java.util.Date
import kotlin.math.abs

class AndroidCardStrings(private val context: Context) : CardStrings, SceneStrings {

    override fun toolValue(result: ToolResult): String = when (result) {
        is ToolResult.Dice -> result.value.toString()
        is ToolResult.Coin -> s(if (result.heads) R.string.card_tool_value_heads else R.string.card_tool_value_tails)
        is ToolResult.Number -> result.value.toString()
    }

    override fun toolName(result: ToolResult): String = when (result) {
        is ToolResult.Dice -> s(R.string.card_tool_name_dice)
        is ToolResult.Coin -> s(R.string.card_tool_name_coin)
        is ToolResult.Number -> s(R.string.card_tool_name_number, result.min, result.max)
    }

    override fun counterOf(goal: Int) = s(R.string.card_counter_of, goal)
    override fun counterSourceName(source: CounterSource): String = s(counterSourceRes(source))

    override fun counterRemaining(minutes: Int, days: Int): String {
        val m = minutes.coerceAtLeast(0)
        val h = m / 60
        val remMin = m % 60
        return when {
            days >= 60 -> context.resources.getQuantityString(R.plurals.card_remaining_days, days, days)
            days == 1 -> if (h > 0) s(R.string.card_remaining_1dh, h) else context.resources.getQuantityString(R.plurals.card_remaining_days, 1, 1)
            days > 1 -> if (h > 0) s(R.string.card_remaining_dh, days, h) else s(R.string.card_remaining_d, days)
            h > 0 && remMin > 0 -> s(R.string.card_remaining_hm, h, remMin)
            h == 1 -> s(R.string.card_remaining_1h)
            h > 1 -> s(R.string.card_remaining_h, h)
            remMin == 1 -> s(R.string.card_remaining_1m)
            else -> s(R.string.card_remaining_m, remMin)
        }
    }

    override fun toolsChoose() = s(R.string.card_tools_choose)

    private fun s(id: Int, vararg args: Any): String = context.getString(id, *args)

    // corte, perché ColorOS mette tre metriche affiancate
    override fun statLabel(metric: StatMetric): String = s(
        when (metric) {
            StatMetric.BATTERY -> R.string.card_metric_battery
            StatMetric.BATTERY_POWER -> R.string.card_metric_power
            StatMetric.CHARGE_STATE -> R.string.card_metric_charge
            StatMetric.CHARGE_TIME -> R.string.card_metric_charge_time
            StatMetric.BATTERY_TEMP -> R.string.card_metric_temp
            StatMetric.BATTERY_CYCLES -> R.string.card_metric_cycles
            StatMetric.DAY_PROGRESS -> R.string.card_metric_day
            StatMetric.FREE_RAM -> R.string.card_metric_ram
            StatMetric.STORAGE_FREE -> R.string.card_metric_storage
            StatMetric.CPU_FREQUENCY -> R.string.card_metric_cpu
            StatMetric.THERMAL_STATE -> R.string.card_metric_thermal
            StatMetric.UPTIME -> R.string.card_metric_uptime
            StatMetric.DATE_TIME -> R.string.card_stat_datetime
        }
    )

    override fun counterValue(count: Int, goal: Int) = s(R.string.card_counter_value, count, goal)
    override fun percent(value: Int) = s(R.string.card_percent, value)
    override fun temperature(celsius: Float) = s(R.string.card_temperature, celsius)
    override fun gigabytes(value: Float) =
        if (value >= 100f) s(R.string.card_gigabytes_whole, value) else s(R.string.card_gigabytes, value)

    override fun watts(value: Float): String = when {
        abs(value) < 0.05f -> s(R.string.card_watts_zero)
        abs(value) >= 10f -> s(R.string.card_watts_whole, value)
        else -> s(R.string.card_watts, value)
    }

    override fun chargeState(state: ChargeState): String = s(
        when (state) {
            ChargeState.AC -> R.string.card_charge_ac
            ChargeState.USB -> R.string.card_charge_usb
            ChargeState.WIRELESS -> R.string.card_charge_wireless
            ChargeState.FULL -> R.string.card_charge_full
            ChargeState.DISCHARGING -> R.string.card_charge_discharging
        }
    )

    override fun durationMinutes(minutes: Long): String {
        val m = minutes.coerceAtLeast(0L)
        return when {
            m < 60L -> s(R.string.card_duration_m, m)
            m < 1440L -> {
                val h = m / 60L
                val rem = m % 60L
                if (rem == 0L) s(R.string.card_duration_h, h) else s(R.string.card_duration_hm, h, rem)
            }
            else -> s(R.string.card_duration_dh, m / 1440L, (m % 1440L) / 60L)
        }
    }

    override fun cycles(count: Int) = count.toString()

    override fun cpuFreq(mhz: Int): String =
        if (mhz >= 1000) s(R.string.card_cpu_ghz, mhz / 1000f) else s(R.string.card_cpu_mhz, mhz)

    override fun thermalStatus(level: Int): String = s(
        when {
            level <= PowerManager.THERMAL_STATUS_NONE -> R.string.card_thermal_none
            level == PowerManager.THERMAL_STATUS_LIGHT -> R.string.card_thermal_light
            level == PowerManager.THERMAL_STATUS_MODERATE -> R.string.card_thermal_moderate
            level == PowerManager.THERMAL_STATUS_SEVERE -> R.string.card_thermal_severe
            else -> R.string.card_thermal_critical
        }
    )

    override fun unavailable() = s(R.string.card_unavailable)
    override fun toolsHint() = s(R.string.card_tools_hint)

    fun clock(epochMs: Long): String = DateFormat.getTimeFormat(context).format(Date(epochMs))

    fun date(epochMs: Long): String = DateUtils.formatDateTime(
        context, epochMs,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR
    )

    override fun toolResult(result: ToolResult): String = when (result) {
        is ToolResult.Dice -> s(R.string.card_tool_dice, result.value)
        is ToolResult.Coin -> s(if (result.heads) R.string.card_tool_heads else R.string.card_tool_tails)
        is ToolResult.Number -> s(R.string.card_tool_number, result.value, result.min, result.max)
    }

    // etichette corte: su ColorOS con 3-4 pulsanti ci stanno circa 7 caratteri
    override fun actionLabel(action: CardAction): String = when (action) {
        is CardAction.LaunchApp -> action.appLabel.ifBlank { s(R.string.card_btn_app) }
        is CardAction.AppShortcut -> action.label.ifBlank { s(R.string.card_btn_shortcut) }
        is CardAction.OpenUrl -> action.label.ifBlank { s(R.string.card_btn_url) }
        is CardAction.CopySnippet -> action.label.ifBlank { s(R.string.card_btn_copy) }
        is CardAction.DialNumber -> action.label.ifBlank { action.phoneNumber.take(10).ifBlank { s(R.string.card_btn_dial) } }
        is CardAction.ClockTimer -> action.label.ifBlank { formatTimerDuration(context, action.seconds) }
        CardAction.Camera -> s(R.string.card_btn_camera)
        CardAction.VolumePanel -> s(R.string.card_btn_volume)
        CardAction.InternetPanel -> s(R.string.card_btn_internet)
        CardAction.WifiPanel -> s(R.string.card_btn_wifi)
        CardAction.ToggleFlashlight -> s(R.string.card_btn_flashlight)
        CardAction.MediaPlayPause -> s(R.string.card_btn_play_pause)
        CardAction.MediaNext -> s(R.string.card_btn_next)
        CardAction.MediaPrevious -> s(R.string.card_btn_previous)
        CardAction.RollDice -> s(R.string.card_btn_dice)
        CardAction.FlipCoin -> s(R.string.card_btn_coin)
        CardAction.RandomNumber -> s(R.string.card_btn_number)
        else -> s(actionLabelRes(action))
    }

    companion object {
        fun counterSourceRes(source: CounterSource): Int = when (source) {
            CounterSource.CUSTOM -> R.string.card_counter_custom
            CounterSource.DAY -> R.string.card_counter_day
            CounterSource.WEEK -> R.string.card_counter_week
            CounterSource.MONTH -> R.string.card_counter_month
            CounterSource.YEAR -> R.string.card_counter_year
        }

        fun backgroundNameRes(bg: CardBackground): Int = when (bg) {
            CardBackground.DEFAULT -> R.string.card_bg_default
            CardBackground.SYSTEM -> R.string.card_bg_system
            CardBackground.OCEAN -> R.string.card_bg_ocean
            CardBackground.FOREST -> R.string.card_bg_forest
            CardBackground.PLUM -> R.string.card_bg_plum
            CardBackground.AMBER -> R.string.card_bg_amber
            CardBackground.PITCH -> R.string.card_bg_pitch
            CardBackground.CUSTOM -> R.string.card_bg_custom
        }

        fun statLabelRes(metric: StatMetric): Int = when (metric) {
            StatMetric.BATTERY -> R.string.card_stat_battery
            StatMetric.BATTERY_POWER -> R.string.card_stat_power
            StatMetric.CHARGE_STATE -> R.string.card_stat_charge
            StatMetric.CHARGE_TIME -> R.string.card_stat_charge_time
            StatMetric.BATTERY_TEMP -> R.string.card_stat_temp
            StatMetric.BATTERY_CYCLES -> R.string.card_stat_cycles
            StatMetric.DAY_PROGRESS -> R.string.card_stat_day
            StatMetric.FREE_RAM -> R.string.card_stat_ram
            StatMetric.STORAGE_FREE -> R.string.card_stat_storage
            StatMetric.CPU_FREQUENCY -> R.string.card_stat_cpu
            StatMetric.THERMAL_STATE -> R.string.card_stat_thermal
            StatMetric.UPTIME -> R.string.card_stat_uptime
            StatMetric.DATE_TIME -> R.string.card_stat_datetime
        }

        fun actionLabelRes(action: CardAction): Int = when (action) {
            is CardAction.LaunchApp -> R.string.card_action_app
            is CardAction.AppShortcut -> R.string.card_action_shortcut
            is CardAction.OpenUrl -> R.string.card_action_url
            is CardAction.CopySnippet -> R.string.card_action_copy
            is CardAction.DialNumber -> R.string.card_action_dial
            is CardAction.ClockTimer -> R.string.card_action_timer
            CardAction.Camera -> R.string.card_action_camera
            CardAction.VolumePanel -> R.string.card_action_volume
            CardAction.InternetPanel -> R.string.card_action_internet
            CardAction.WifiPanel -> R.string.card_action_wifi
            CardAction.ToggleFlashlight -> R.string.card_action_flashlight
            CardAction.MediaPlayPause -> R.string.card_action_play_pause
            CardAction.MediaNext -> R.string.card_action_next
            CardAction.MediaPrevious -> R.string.card_action_previous
            CardAction.EditNoteInline -> R.string.card_action_edit
            CardAction.CounterPlus -> R.string.card_action_plus
            CardAction.CounterMinus -> R.string.card_action_minus
            CardAction.CounterReset -> R.string.card_action_reset
            CardAction.RollDice -> R.string.card_action_dice
            CardAction.FlipCoin -> R.string.card_action_coin
            CardAction.RandomNumber -> R.string.card_action_number
            CardAction.RefreshStats -> R.string.card_action_refresh
        }

        fun actionIconRes(action: CardAction): Int = when (action) {
            is CardAction.LaunchApp -> R.drawable.ic_act_launch
            is CardAction.AppShortcut -> R.drawable.ic_act_shortcut
            is CardAction.OpenUrl -> R.drawable.ic_act_link
            is CardAction.CopySnippet -> R.drawable.ic_act_copy
            is CardAction.DialNumber -> R.drawable.ic_card_phone
            is CardAction.ClockTimer -> R.drawable.ic_card_timer
            CardAction.Camera -> R.drawable.ic_act_camera
            CardAction.VolumePanel -> R.drawable.ic_act_volume
            CardAction.InternetPanel -> R.drawable.ic_act_internet
            CardAction.WifiPanel -> R.drawable.ic_act_wifi
            CardAction.ToggleFlashlight -> R.drawable.ic_act_flashlight
            CardAction.MediaPlayPause -> R.drawable.ic_act_play_pause
            CardAction.MediaNext -> R.drawable.ic_act_next
            CardAction.MediaPrevious -> R.drawable.ic_act_previous
            CardAction.EditNoteInline -> R.drawable.ic_act_edit
            CardAction.CounterPlus -> R.drawable.ic_act_plus
            CardAction.CounterMinus -> R.drawable.ic_act_minus
            CardAction.CounterReset -> R.drawable.ic_act_reset
            CardAction.RollDice -> R.drawable.ic_mode_tools
            CardAction.FlipCoin -> R.drawable.ic_act_coin
            CardAction.RandomNumber -> R.drawable.ic_act_number
            CardAction.RefreshStats -> R.drawable.ic_act_refresh
        }

        fun formatTimerDuration(context: Context, seconds: Int): String {
            val s = seconds.coerceAtLeast(1)
            return when {
                s < 60 -> context.getString(R.string.card_timer_s, s)
                s < 3600 -> context.getString(R.string.card_duration_m, s / 60L)
                s % 3600 == 0 -> context.getString(R.string.card_duration_h, s / 3600L)
                else -> context.getString(R.string.card_duration_hm, s / 3600L, (s % 3600L) / 60L)
            }
        }

        fun modeIconRes(mode: ExpandedCardMode): Int = when (mode) {
            ExpandedCardMode.MINIMAL -> R.drawable.ic_mode_minimal
            ExpandedCardMode.CUSTOM_NOTE -> R.drawable.ic_mode_note
            ExpandedCardMode.SHORTCUT_DOCK -> R.drawable.ic_mode_dock
            ExpandedCardMode.DEVICE_STATS -> R.drawable.ic_mode_stats
            ExpandedCardMode.PROGRESS_COUNTER -> R.drawable.ic_mode_counter
            ExpandedCardMode.QUICK_TOOLS -> R.drawable.ic_mode_tools
            ExpandedCardMode.CUSTOM_TEMPLATE -> R.drawable.ic_mode_maker
        }

        fun modeNameRes(mode: ExpandedCardMode): Int = when (mode) {
            ExpandedCardMode.MINIMAL -> R.string.card_mode_minimal
            ExpandedCardMode.CUSTOM_NOTE -> R.string.card_mode_note
            ExpandedCardMode.SHORTCUT_DOCK -> R.string.card_mode_dock
            ExpandedCardMode.DEVICE_STATS -> R.string.card_mode_stats
            ExpandedCardMode.PROGRESS_COUNTER -> R.string.card_mode_counter
            ExpandedCardMode.QUICK_TOOLS -> R.string.card_mode_tools
            ExpandedCardMode.CUSTOM_TEMPLATE -> R.string.card_mode_maker
        }
    }
}
