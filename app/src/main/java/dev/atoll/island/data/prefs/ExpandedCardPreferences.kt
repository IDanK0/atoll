package dev.atoll.island.data.prefs

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.atoll.island.data.model.BadgeIcon
import dev.atoll.island.data.model.CardColors
import dev.atoll.island.data.model.CounterSource
import dev.atoll.island.data.model.CounterState
import dev.atoll.island.data.model.ExpandedCardConfig
import dev.atoll.island.data.model.ExpandedCardMode
import dev.atoll.island.data.model.LargeIconConfig
import dev.atoll.island.data.model.LargeIconType
import dev.atoll.island.data.model.TapAction
import dev.atoll.island.data.model.TimerState
import dev.atoll.island.domain.card.CardCodec
import dev.atoll.island.domain.card.CounterReducer
import dev.atoll.island.domain.card.Randomizer
import dev.atoll.island.domain.card.StatSelection

internal object ExpandedCardPreferences {
    private val mode = stringPreferencesKey("card_mode")
    private val noteTitle = stringPreferencesKey("card_note_title")
    private val noteSub = stringPreferencesKey("card_note_sub")
    private val noteBody = stringPreferencesKey("card_note_body")
    private val dockTitle = stringPreferencesKey("card_dock_title")
    private val stats = stringPreferencesKey("card_stats")
    private val useMetricStyle = booleanPreferencesKey("card_use_metric_style")
    private val useColorOs = booleanPreferencesKey("card_use_coloros_layouts")
    private val counterSource = stringPreferencesKey("card_counter_source")
    private val counterLabel = stringPreferencesKey("card_counter_label")
    private val counterCount = intPreferencesKey("card_counter_count")
    private val counterGoal = intPreferencesKey("card_counter_goal")
    private val accent = intPreferencesKey("card_accent")
    private val timerDuration = longPreferencesKey("card_timer_duration")
    private val timerTotal = longPreferencesKey("card_timer_total")
    private val timerRemaining = longPreferencesKey("card_timer_remaining")
    private val timerEnd = longPreferencesKey("card_timer_end")
    private val randomMin = intPreferencesKey("card_random_min")
    private val randomMax = intPreferencesKey("card_random_max")
    private val lastTool = stringPreferencesKey("card_last_tool")
    private val tapAction = stringPreferencesKey("card_tap_action")
    private val tapPackage = stringPreferencesKey("card_tap_package")
    private val iconType = stringPreferencesKey("card_icon_type")
    private val iconBadge = stringPreferencesKey("card_icon_badge")
    private val iconTint = intPreferencesKey("card_icon_tint")
    private val iconVersion = longPreferencesKey("card_icon_version")
    private val actions = ExpandedCardMode.entries.associateWith { stringPreferencesKey("card_actions_${it.name.lowercase()}") }

    fun read(prefs: Preferences): ExpandedCardConfig {
        val defaults = ExpandedCardConfig()
        val goal = (prefs[counterGoal] ?: defaults.counter.goal).coerceIn(CounterReducer.MIN_GOAL, CounterReducer.MAX_GOAL)
        val duration = (prefs[timerDuration] ?: TimerState.DEFAULT_DURATION_MS).coerceAtLeast(TimerState.MINUTE_MS)
        return ExpandedCardConfig(
            mode = ExpandedCardMode.fromName(prefs[mode]),
            noteTitle = prefs[noteTitle] ?: "",
            noteSubText = prefs[noteSub] ?: "",
            noteBody = prefs[noteBody] ?: "",
            dockTitle = prefs[dockTitle] ?: "",
            stats = CardCodec.decodeStats(prefs[stats])?.let(StatSelection::sanitize) ?: defaults.stats,
            useMetricStyle = prefs[useMetricStyle] ?: false,
            useColorOsLayouts = prefs[useColorOs] ?: true,
            counterSource = CounterSource.entries.firstOrNull { it.name == prefs[counterSource] } ?: CounterSource.CUSTOM,
            counterLabel = prefs[counterLabel] ?: "",
            counter = CounterState(count = (prefs[counterCount] ?: 0).coerceIn(0, goal), goal = goal),
            // il vecchio indaco predefinito diventa l'accento di sistema
            accentColor = (prefs[accent] ?: defaults.accentColor).let { if (it == CardColors.BADGE) CardColors.DEFAULT else it },
            timer = TimerState(
                durationMs = duration,
                totalMs = (prefs[timerTotal] ?: duration).coerceAtLeast(0L),
                remainingMs = (prefs[timerRemaining] ?: duration).coerceAtLeast(0L),
                endAtMs = (prefs[timerEnd] ?: 0L).coerceAtLeast(0L)
            ),
            randomMin = prefs[randomMin] ?: defaults.randomMin,
            randomMax = prefs[randomMax] ?: defaults.randomMax,
            lastTool = CardCodec.decodeTool(prefs[lastTool]),
            actionOverrides = actions.mapNotNull { (m, key) -> prefs[key]?.let { m to CardCodec.decodeActions(it) } }.toMap(),
            tapAction = TapAction.entries.firstOrNull { it.name == prefs[tapAction] } ?: TapAction.OPEN_ATOLL,
            tapPackage = prefs[tapPackage] ?: "",
            largeIcon = LargeIconConfig(
                type = LargeIconType.entries.firstOrNull { it.name == prefs[iconType] } ?: LargeIconType.NONE,
                icon = BadgeIcon.entries.firstOrNull { it.name == prefs[iconBadge] } ?: BadgeIcon.BOLT,
                tint = prefs[iconTint] ?: defaults.largeIcon.tint,
                imageVersion = prefs[iconVersion] ?: 0L
            )
        )
    }

    fun write(prefs: MutablePreferences, card: ExpandedCardConfig) {
        val (min, max) = Randomizer.normalizedRange(card.randomMin, card.randomMax)
        prefs[mode] = card.mode.name
        prefs[noteTitle] = card.noteTitle
        prefs[noteSub] = card.noteSubText
        prefs[noteBody] = card.noteBody
        prefs[dockTitle] = card.dockTitle
        prefs[stats] = CardCodec.encodeStats(StatSelection.sanitize(card.stats))
        prefs[useMetricStyle] = card.useMetricStyle
        prefs[useColorOs] = card.useColorOsLayouts
        prefs[counterSource] = card.counterSource.name
        prefs[counterLabel] = card.counterLabel
        prefs[counterGoal] = card.counter.goal
        prefs[counterCount] = card.counter.count
        prefs[accent] = card.accentColor
        prefs[timerDuration] = card.timer.durationMs
        prefs[timerTotal] = card.timer.totalMs
        prefs[timerRemaining] = card.timer.remainingMs
        prefs[timerEnd] = card.timer.endAtMs
        prefs[randomMin] = min
        prefs[randomMax] = max
        prefs[lastTool] = CardCodec.encodeTool(card.lastTool)
        prefs[tapAction] = card.tapAction.name
        prefs[tapPackage] = card.tapPackage
        prefs[iconType] = card.largeIcon.type.name
        prefs[iconBadge] = card.largeIcon.icon.name
        prefs[iconTint] = card.largeIcon.tint
        prefs[iconVersion] = card.largeIcon.imageVersion
        actions.forEach { (m, key) ->
            val override = card.actionOverrides[m]
            if (override == null) prefs.remove(key) else prefs[key] = CardCodec.encodeActions(override)
        }
    }
}
