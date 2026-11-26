package dev.atoll.island.domain.card

import dev.atoll.island.data.model.CardAction
import dev.atoll.island.data.model.CounterSource
import dev.atoll.island.data.model.CounterState
import dev.atoll.island.data.model.ExpandedCardConfig
import dev.atoll.island.data.model.ExpandedCardMode
import dev.atoll.island.data.model.StatMetric
import dev.atoll.island.data.model.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CardReducersTest {

    @Test
    fun counterStaysBetweenZeroAndGoal() {
        var state = CounterState(count = 0, goal = 3)
        state = CounterReducer.minus(state)
        assertEquals(0, state.count)
        repeat(10) { state = CounterReducer.plus(state) }
        assertEquals(3, state.count)
        assertEquals(0, CounterReducer.reset(state).count)
    }

    @Test
    fun loweringTheGoalPullsTheCountDown() {
        val state = CounterReducer.setGoal(CounterState(count = 7, goal = 8), 5)
        assertEquals(5, state.goal)
        assertEquals(5, state.count)
        assertEquals(CounterReducer.MIN_GOAL, CounterReducer.setGoal(state, 0).goal)
        assertEquals(CounterReducer.MAX_GOAL, CounterReducer.setGoal(state, 5_000).goal)
    }

    @Test
    fun newInstallsStartWithShortcuts() {
        assertEquals(ExpandedCardMode.SHORTCUT_DOCK, ExpandedCardConfig().mode)
        assertEquals(ExpandedCardMode.SHORTCUT_DOCK, ExpandedCardMode.fromName(null))
        // chi aveva il timer torna alle scorciatoie
        assertEquals(ExpandedCardMode.SHORTCUT_DOCK, ExpandedCardMode.fromName("FOCUS_TIMER"))
    }

    @Test
    fun randomResultsStayInRange() {
        val random = Random(42)
        repeat(5_000) {
            assertTrue(Randomizer.rollDice(random).value in 1..6)
            val n = Randomizer.number(random, 10, -3)
            assertEquals(-3, n.min)
            assertEquals(10, n.max)
            assertTrue(n.value in -3..10)
        }
        assertEquals(7, Randomizer.number(random, 7, 7).value)
        val coins = List(200) { Randomizer.flipCoin(random).heads }
        assertTrue(coins.any { it } && coins.any { !it })
    }

    @Test
    fun dockAllowsFourButtonsAndOtherModesStayAtThree() {
        val many = listOf(
            CardAction.ToggleFlashlight, CardAction.MediaNext, CardAction.MediaPlayPause,
            CardAction.OpenUrl("example.com", "x"), CardAction.CopySnippet("a", "b")
        )
        assertEquals(4, ActionSlots.sanitize(many, ExpandedCardMode.SHORTCUT_DOCK).size)
        assertEquals(3, ActionSlots.sanitize(many, ExpandedCardMode.CUSTOM_NOTE).size)
        ExpandedCardMode.entries.forEach { mode ->
            val config = ExpandedCardConfig(mode = mode)
            assertTrue(ActionSlots.resolve(config).size <= ActionSlots.maxSlots(mode))
            assertEquals(ActionSlots.defaultsFor(config), ActionSlots.resolve(config))
        }
    }

    @Test
    fun invalidAndForeignButtonsAreDropped() {
        val input = listOf(
            CardAction.LaunchApp("", "Empty"),
            CardAction.OpenUrl("javascript:alert(1)", "bad"),
            CardAction.CopySnippet("empty", ""),
            CardAction.CounterPlus,
            CardAction.ToggleFlashlight,
            CardAction.ToggleFlashlight
        )
        assertEquals(listOf(CardAction.ToggleFlashlight), ActionSlots.sanitize(input, ExpandedCardMode.CUSTOM_NOTE))
        assertTrue(ActionSlots.sanitize(listOf(CardAction.MediaNext), ExpandedCardMode.MINIMAL).isEmpty())
    }

    @Test
    fun overrideReplacesDefaultsAndEmptyMeansNone() {
        val base = ExpandedCardConfig(mode = ExpandedCardMode.QUICK_TOOLS)
        val none = base.copy(actionOverrides = mapOf(ExpandedCardMode.QUICK_TOOLS to emptyList()))
        assertTrue(ActionSlots.resolve(none).isEmpty())
        val one = base.copy(actionOverrides = mapOf(ExpandedCardMode.QUICK_TOOLS to listOf(CardAction.FlipCoin)))
        assertEquals(listOf(CardAction.FlipCoin), ActionSlots.resolve(one))
        val day = ExpandedCardConfig(mode = ExpandedCardMode.PROGRESS_COUNTER, counterSource = CounterSource.DAY)
        assertTrue(ActionSlots.resolve(day).isEmpty())
        val week = ExpandedCardConfig(mode = ExpandedCardMode.PROGRESS_COUNTER, counterSource = CounterSource.WEEK)
        assertTrue(ActionSlots.resolve(week).isEmpty())
    }

    @Test
    fun counterStepAndMidnightReset() {
        val stepped = CounterState(count = 2, goal = 10, step = 3)
        assertEquals(5, CounterReducer.plus(stepped).count)
        assertEquals(0, CounterReducer.minus(stepped).count)
        val armed = CounterReducer.setResetAtMidnight(stepped, enabled = true, todayEpochDay = 200L)
        assertNull(CounterReducer.resetIfNewDay(armed, todayEpochDay = 200L))
        val nextDay = CounterReducer.resetIfNewDay(armed, todayEpochDay = 201L)
        assertEquals(0, nextDay?.count)
        assertEquals(201L, nextDay?.lastResetEpochDay)
    }

    @Test
    fun urlsGetAScheme() {
        assertEquals("https://example.com", ActionSlots.normalizeUrl(" example.com "))
        assertEquals("http://a.b/c?d", ActionSlots.normalizeUrl("http://a.b/c?d"))
        assertNull(ActionSlots.normalizeUrl("ftp://example.com"))
        assertNull(ActionSlots.normalizeUrl("not a url"))
        assertNull(ActionSlots.normalizeUrl("nodot"))
    }

    @Test
    fun codecSurvivesTabsAndNewlines() {
        val actions = listOf(
            CardAction.CopySnippet("a\tb", "riga 1\nriga 2 \\ fine"),
            CardAction.LaunchApp("com.example", "Example"),
            CardAction.AppShortcut("intent:#Intent;package=com.ex;end", "Chat", "com.ex"),
            CardAction.OpenUrl("https://x.y", ""),
            CardAction.DialNumber("+39 333-1234567", "Casa"),
            CardAction.ClockTimer(300, "Pasta"),
            CardAction.WifiPanel,
            CardAction.InternetPanel,
            CardAction.FlipCoin
        )
        assertEquals(actions, CardCodec.decodeActions(CardCodec.encodeActions(actions)))
        assertEquals("+39333123", ActionSlots.normalizePhone("+39 333 123"))
        assertNull(ActionSlots.normalizePhone("abc"))
        assertTrue(CardCodec.decodeActions("").isEmpty())
        assertTrue(CardCodec.decodeActions("unknown\tx").isEmpty())

        listOf(ToolResult.Dice(4), ToolResult.Coin(false), ToolResult.Number(-5, -10, 10)).forEach {
            assertEquals(it, CardCodec.decodeTool(CardCodec.encodeTool(it)))
        }
        assertNull(CardCodec.decodeTool("dice:9"))
        assertEquals(listOf(StatMetric.FREE_RAM, StatMetric.BATTERY), CardCodec.decodeStats("FREE_RAM,BATTERY,NOPE"))
    }

    @Test
    fun statSelectionStopsAtThree() {
        var stats = emptyList<StatMetric>()
        StatMetric.entries.forEach { stats = StatSelection.toggle(stats, it) }
        assertEquals(3, stats.size)
        stats = StatSelection.toggle(stats, stats.first())
        assertEquals(2, stats.size)
    }

    @Test
    fun powerAndHardwareSurviveASavedSelection() {
        val saved = "BATTERY_POWER,CPU_FREQUENCY,THERMAL_STATE"
        assertEquals(saved, CardCodec.encodeStats(StatSelection.sanitize(CardCodec.decodeStats(saved).orEmpty())))
    }

    @Test
    fun systemShortcutsKeepTheirOrderAfterRestart() {
        val file = "camera\nvolume\nprevious"
        assertEquals(file, CardCodec.encodeActions(ActionSlots.sanitize(CardCodec.decodeActions(file), ExpandedCardMode.SHORTCUT_DOCK)))
    }

    @Test
    fun onlyStatefulActionsReduce() {
        val card = ExpandedCardConfig(mode = ExpandedCardMode.PROGRESS_COUNTER)
        assertEquals(1, CardActionReducer.reduce(card, CardAction.CounterPlus, Random(1))?.counter?.count)
        assertNull(CardActionReducer.reduce(card, CardAction.ToggleFlashlight, Random(1)))
        listOf(
            CardAction.ToggleFlashlight, CardAction.MediaPlayPause, CardAction.MediaNext, CardAction.EditNoteInline,
            CardAction.CounterPlus, CardAction.CounterMinus, CardAction.CounterReset,
            CardAction.RollDice, CardAction.FlipCoin, CardAction.RandomNumber, CardAction.RefreshStats
        ).forEach { action ->
            assertEquals(CardActionReducer.changesState(action), CardActionReducer.reduce(card, action, Random(1)) != null)
        }
    }
}
