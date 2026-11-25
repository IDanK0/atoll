package dev.atoll.island.domain.card

import dev.atoll.island.data.model.CardAction
import dev.atoll.island.data.model.CounterSource
import dev.atoll.island.data.model.CounterState
import dev.atoll.island.data.model.ExpandedCardConfig
import dev.atoll.island.data.model.ExpandedCardMode
import dev.atoll.island.data.model.StatMetric
import dev.atoll.island.data.model.TimerState
import dev.atoll.island.data.model.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CardReducersTest {

    private val minute = TimerState.MINUTE_MS

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
    fun timerStartsPausesAndResumes() {
        val ready = TimerState()
        val running = TimerReducer.toggle(ready, nowMs = 1_000L)
        assertTrue(running.isRunning)
        assertEquals(1_000L + 25 * minute, running.endAtMs)

        val paused = TimerReducer.toggle(running, nowMs = 1_000L + 10 * minute)
        assertFalse(paused.isRunning)
        assertEquals(15 * minute, paused.remainingMs)

        val resumed = TimerReducer.toggle(paused, nowMs = 100 * minute)
        assertEquals(100 * minute + 15 * minute, resumed.endAtMs)
    }

    @Test
    fun addingFiveMinutesWorksRunningAndPaused() {
        val running = TimerReducer.toggle(TimerState(), nowMs = 0L)
        val longer = TimerReducer.addFiveMinutes(running, nowMs = 0L)
        assertEquals(30 * minute, longer.endAtMs)
        assertEquals(30 * minute, longer.totalMs)

        val paused = TimerReducer.addFiveMinutes(TimerState(), nowMs = 0L)
        assertFalse(paused.isRunning)
        assertEquals(30 * minute, paused.remainingMs)
    }

    @Test
    fun timerFinishesAndRestartsFromTheTop() {
        val running = TimerReducer.toggle(TimerState(), nowMs = 0L)
        assertEquals(running, TimerReducer.finishIfDue(running, nowMs = 24 * minute))
        val done = TimerReducer.finishIfDue(running, nowMs = 25 * minute)
        assertTrue(done.isFinished)
        assertEquals(0L, done.remainingAt(30 * minute))

        val again = TimerReducer.toggle(done, nowMs = 40 * minute)
        assertTrue(again.isRunning)
        assertEquals(40 * minute + 25 * minute, again.endAtMs)
    }

    @Test
    fun resetAndDurationChangeGoBackToReady() {
        val running = TimerReducer.toggle(TimerReducer.addFiveMinutes(TimerState(), 0L), 0L)
        val reset = TimerReducer.reset(running)
        assertFalse(reset.isRunning)
        assertEquals(25 * minute, reset.remainingMs)
        assertEquals(25 * minute, reset.totalMs)
        assertEquals(50 * minute, TimerReducer.setDurationMinutes(reset, 50).remainingMs)
        assertEquals(TimerReducer.MAX_MINUTES * minute, TimerReducer.setDurationMinutes(reset, 999).durationMs)
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
    fun neverMoreThanThreeButtons() {
        val many = listOf(
            CardAction.ToggleFlashlight, CardAction.MediaNext, CardAction.MediaPlayPause,
            CardAction.OpenUrl("example.com", "x"), CardAction.CopySnippet("a", "b")
        )
        assertEquals(3, ActionSlots.sanitize(many, ExpandedCardMode.SHORTCUT_DOCK).size)
        ExpandedCardMode.entries.forEach { mode ->
            val config = ExpandedCardConfig(mode = mode)
            assertTrue(ActionSlots.resolve(config).size <= ActionSlots.MAX_SLOTS)
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
        val base = ExpandedCardConfig(mode = ExpandedCardMode.FOCUS_TIMER)
        val none = base.copy(actionOverrides = mapOf(ExpandedCardMode.FOCUS_TIMER to emptyList()))
        assertTrue(ActionSlots.resolve(none).isEmpty())
        val one = base.copy(actionOverrides = mapOf(ExpandedCardMode.FOCUS_TIMER to listOf(CardAction.TimerReset)))
        assertEquals(listOf(CardAction.TimerReset), ActionSlots.resolve(one))
        val day = ExpandedCardConfig(mode = ExpandedCardMode.PROGRESS_COUNTER, counterSource = CounterSource.DAY)
        assertTrue(ActionSlots.resolve(day).isEmpty())
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
            CardAction.OpenUrl("https://x.y", ""),
            CardAction.TimerToggle
        )
        assertEquals(actions, CardCodec.decodeActions(CardCodec.encodeActions(actions)))
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
    fun onlyStatefulActionsReduce() {
        val card = ExpandedCardConfig(mode = ExpandedCardMode.PROGRESS_COUNTER)
        assertEquals(1, CardActionReducer.reduce(card, CardAction.CounterPlus, 0L, Random(1))?.counter?.count)
        assertNull(CardActionReducer.reduce(card, CardAction.ToggleFlashlight, 0L, Random(1)))
        listOf(
            CardAction.ToggleFlashlight, CardAction.MediaPlayPause, CardAction.MediaNext, CardAction.EditNoteInline,
            CardAction.CounterPlus, CardAction.CounterMinus, CardAction.CounterReset,
            CardAction.TimerToggle, CardAction.TimerAdd5Min, CardAction.TimerReset,
            CardAction.RollDice, CardAction.FlipCoin, CardAction.RandomNumber, CardAction.RefreshStats
        ).forEach { action ->
            assertEquals(CardActionReducer.changesState(action), CardActionReducer.reduce(card, action, 0L, Random(1)) != null)
        }
    }
}
