package dev.atoll.island.service.card

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import dev.atoll.island.R
import dev.atoll.island.data.model.CardAction
import dev.atoll.island.data.model.ExpandedCardMode
import dev.atoll.island.data.model.StatMetric
import dev.atoll.island.data.model.ToolResult
import dev.atoll.island.domain.card.CardStrings
import dev.atoll.island.domain.card.SceneStrings
import java.util.Date

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
    override fun toolsChoose() = s(R.string.card_tools_choose)

    private fun s(id: Int, vararg args: Any): String = context.getString(id, *args)

    // corte, perché ColorOS mette tre metriche affiancate
    override fun statLabel(metric: StatMetric): String = s(
        when (metric) {
            StatMetric.BATTERY -> R.string.card_metric_battery
            StatMetric.BATTERY_TEMP -> R.string.card_metric_temp
            StatMetric.DAY_PROGRESS -> R.string.card_metric_day
            StatMetric.FREE_RAM -> R.string.card_metric_ram
            StatMetric.DATE_TIME -> R.string.card_stat_datetime
        }
    )

    override fun counterValue(count: Int, goal: Int) = s(R.string.card_counter_value, count, goal)
    override fun percent(value: Int) = s(R.string.card_percent, value)
    override fun temperature(celsius: Float) = s(R.string.card_temperature, celsius)
    override fun gigabytes(value: Float) = s(R.string.card_gigabytes, value)
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

    // etichette corte: su ColorOS con tre pulsanti ci stanno circa 7 caratteri
    override fun actionLabel(action: CardAction): String = when (action) {
        is CardAction.LaunchApp -> action.appLabel.ifBlank { s(R.string.card_btn_app) }
        is CardAction.OpenUrl -> action.label.ifBlank { s(R.string.card_btn_url) }
        is CardAction.CopySnippet -> action.label.ifBlank { s(R.string.card_btn_copy) }
        CardAction.ToggleFlashlight -> s(R.string.card_btn_flashlight)
        CardAction.MediaPlayPause -> s(R.string.card_btn_play_pause)
        CardAction.MediaNext -> s(R.string.card_btn_next)
        CardAction.RollDice -> s(R.string.card_btn_dice)
        CardAction.FlipCoin -> s(R.string.card_btn_coin)
        CardAction.RandomNumber -> s(R.string.card_btn_number)
        else -> s(actionLabelRes(action))
    }

    companion object {
        fun statLabelRes(metric: StatMetric): Int = when (metric) {
            StatMetric.BATTERY -> R.string.card_stat_battery
            StatMetric.BATTERY_TEMP -> R.string.card_stat_temp
            StatMetric.DAY_PROGRESS -> R.string.card_stat_day
            StatMetric.FREE_RAM -> R.string.card_stat_ram
            StatMetric.DATE_TIME -> R.string.card_stat_datetime
        }

        fun actionLabelRes(action: CardAction): Int = when (action) {
            is CardAction.LaunchApp -> R.string.card_action_app
            is CardAction.OpenUrl -> R.string.card_action_url
            is CardAction.CopySnippet -> R.string.card_action_copy
            CardAction.ToggleFlashlight -> R.string.card_action_flashlight
            CardAction.MediaPlayPause -> R.string.card_action_play_pause
            CardAction.MediaNext -> R.string.card_action_next
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
            is CardAction.OpenUrl -> R.drawable.ic_act_link
            is CardAction.CopySnippet -> R.drawable.ic_act_copy
            CardAction.ToggleFlashlight -> R.drawable.ic_act_flashlight
            CardAction.MediaPlayPause -> R.drawable.ic_act_play_pause
            CardAction.MediaNext -> R.drawable.ic_act_next
            CardAction.EditNoteInline -> R.drawable.ic_act_edit
            CardAction.CounterPlus -> R.drawable.ic_act_plus
            CardAction.CounterMinus -> R.drawable.ic_act_minus
            CardAction.CounterReset -> R.drawable.ic_act_reset
            CardAction.RollDice -> R.drawable.ic_mode_tools
            CardAction.FlipCoin -> R.drawable.ic_act_coin
            CardAction.RandomNumber -> R.drawable.ic_act_number
            CardAction.RefreshStats -> R.drawable.ic_act_refresh
        }

        fun modeIconRes(mode: ExpandedCardMode): Int = when (mode) {
            ExpandedCardMode.MINIMAL -> R.drawable.ic_mode_minimal
            ExpandedCardMode.CUSTOM_NOTE -> R.drawable.ic_mode_note
            ExpandedCardMode.SHORTCUT_DOCK -> R.drawable.ic_mode_dock
            ExpandedCardMode.DEVICE_STATS -> R.drawable.ic_mode_stats
            ExpandedCardMode.PROGRESS_COUNTER -> R.drawable.ic_mode_counter
            ExpandedCardMode.QUICK_TOOLS -> R.drawable.ic_mode_tools
        }

        fun modeNameRes(mode: ExpandedCardMode): Int = when (mode) {
            ExpandedCardMode.MINIMAL -> R.string.card_mode_minimal
            ExpandedCardMode.CUSTOM_NOTE -> R.string.card_mode_note
            ExpandedCardMode.SHORTCUT_DOCK -> R.string.card_mode_dock
            ExpandedCardMode.DEVICE_STATS -> R.string.card_mode_stats
            ExpandedCardMode.PROGRESS_COUNTER -> R.string.card_mode_counter
            ExpandedCardMode.QUICK_TOOLS -> R.string.card_mode_tools
        }
    }
}
