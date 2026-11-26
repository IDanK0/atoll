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

class ExpandedCardContentResolverTest {

    private val strings = object : CardStrings {
        override fun statLabel(metric: StatMetric) = metric.name
        override fun counterValue(count: Int, goal: Int) = "$count / $goal"
        override fun percent(value: Int) = "$value%"
        override fun temperature(celsius: Float) = "$celsius C"
        override fun gigabytes(value: Float) = "$value GB"
        override fun unavailable() = "n/a"
        override fun toolsHint() = "hint"
        override fun toolResult(result: ToolResult) = result.toString()
        override fun actionLabel(action: CardAction) = action.toString()
    }

    private val env = CardEnvironment(
        minuteOfDay = 720,
        apiLevel = 37,
        stats = DeviceStats(batteryPercent = 84, batteryTempC = 31.5f, freeRamGb = 5.2f),
        dateText = "Wed 25 Nov",
        timeText = "12:00"
    )

    private fun resolve(card: ExpandedCardConfig, e: CardEnvironment = env) =
        ExpandedCardContentResolver.resolve(card, e, strings)

    @Test
    fun everyModeHasATitleEvenWithBlankInput() {
        ExpandedCardMode.entries.forEach { mode ->
            val card = ExpandedCardConfig(
                mode = mode, noteTitle = "   ", dockTitle = "", counterLabel = " ", stats = emptyList()
            )
            val content = resolve(card, env.copy(minimalTitle = ""))
            assertTrue("$mode", content.title.isNotBlank() || content.title == CardText.BLANK)
            assertTrue("$mode", content.title.isNotEmpty())
            assertTrue("$mode", content.actions.size <= ActionSlots.maxSlots(mode))
        }
    }

    @Test
    fun noGenericTitlesOnlyTheOnesTheUserWrote() {
        ExpandedCardMode.entries.forEach { mode ->
            assertEquals("$mode", CardText.BLANK, resolve(ExpandedCardConfig(mode = mode)).title)
        }
        assertEquals("Spesa", resolve(ExpandedCardConfig(mode = ExpandedCardMode.CUSTOM_NOTE, noteTitle = "Spesa")).title)
        assertEquals("Casa", resolve(ExpandedCardConfig(mode = ExpandedCardMode.SHORTCUT_DOCK, dockTitle = "Casa")).title)
        assertEquals("Acqua", resolve(ExpandedCardConfig(mode = ExpandedCardMode.PROGRESS_COUNTER, counterLabel = "Acqua")).title)
    }

    @Test
    fun minimalIsTheOldBlankCard() {
        val content = resolve(ExpandedCardConfig(mode = ExpandedCardMode.MINIMAL), env.copy(confirmation = "Copied"))
        assertEquals(CardText.BLANK, content.title)
        assertEquals(CardText.BLANK, content.body)
        assertNull(content.subText)
        assertTrue(content.actions.isEmpty())
        assertEquals(CardStyle.STANDARD, content.style)
    }

    @Test
    fun noteUsesBigTextOnlyWithABody() {
        val note = ExpandedCardConfig(mode = ExpandedCardMode.CUSTOM_NOTE, noteTitle = "Spesa", noteSubText = "Casa")
        assertEquals(CardStyle.STANDARD, resolve(note).style)
        val withBody = resolve(note.copy(noteBody = "latte\npane"))
        assertEquals(CardStyle.BIG_TEXT, withBody.style)
        assertEquals("Spesa", withBody.title)
        assertEquals("Casa", withBody.subText)
        assertEquals(listOf(CardAction.EditNoteInline), withBody.actions.map { it.action })
    }

    @Test
    fun noteShowsFormattedTextWithoutChangingTheSource() {
        val source = "# Spesa\n- **Latte** e *pane*\n- `a_b` ~~ieri~~ [lista](https://example.com)"
        val note = ExpandedCardConfig(mode = ExpandedCardMode.CUSTOM_NOTE, noteBody = source)
        assertEquals("Spesa\n• Latte e pane\n• a_b ieri lista", resolve(note).body)
        assertEquals(source, note.noteBody)
    }

    @Test
    fun confirmationReplacesTheSubtitleForAWhile() {
        val note = ExpandedCardConfig(mode = ExpandedCardMode.SHORTCUT_DOCK)
        assertEquals("Copied: x", resolve(note, env.copy(confirmation = "Copied: x")).subText)
    }

    @Test
    fun metricStyleOnlyWhenTurnedOnAndOnApi37() {
        val stats = ExpandedCardConfig(
            mode = ExpandedCardMode.DEVICE_STATS,
            stats = listOf(StatMetric.BATTERY, StatMetric.BATTERY_TEMP, StatMetric.FREE_RAM, StatMetric.DATE_TIME),
            useMetricStyle = true
        )
        val metric = resolve(stats)
        assertEquals(CardStyle.METRIC, metric.style)
        assertEquals(3, metric.metrics.size)
        assertEquals(CardMetric("BATTERY", "84%"), metric.metrics[0])

        assertEquals(CardStyle.BIG_TEXT, resolve(stats, env.copy(apiLevel = 36)).style)
        val compat = resolve(stats.copy(useMetricStyle = false))
        assertEquals(CardStyle.BIG_TEXT, compat.style)
        assertEquals("BATTERY 84%  \u00B7  BATTERY_TEMP 31.5 C  \u00B7  FREE_RAM 5.2 GB", compat.body)
    }

    @Test
    fun missingStatsShowNotAvailable() {
        val stats = ExpandedCardConfig(mode = ExpandedCardMode.DEVICE_STATS, stats = listOf(StatMetric.BATTERY))
        val content = resolve(stats, env.copy(stats = DeviceStats()))
        assertEquals("n/a", content.metrics.single().valueText)
        val date = resolve(stats.copy(stats = listOf(StatMetric.DATE_TIME, StatMetric.DAY_PROGRESS)))
        assertEquals("Wed 25 Nov", date.metrics[0].label)
        assertEquals("50%", date.metrics[1].valueText)
    }

    @Test
    fun newHardwareAndPowerMetricsResolveCleanly() {
        val fullStats = DeviceStats(
            batteryPowerWatts = 18.4f,
            chargeState = dev.atoll.island.data.model.ChargeState.AC,
            chargeTimeMinutes = 24,
            batteryCycles = 42,
            storageFreeGb = 128f,
            storageTotalGb = 256f,
            cpuMaxMhz = 2400,
            thermalStatus = 1,
            uptimeMinutes = 95L
        )
        val first = resolve(
            ExpandedCardConfig(
                mode = ExpandedCardMode.DEVICE_STATS,
                stats = listOf(StatMetric.BATTERY_POWER, StatMetric.CPU_FREQUENCY, StatMetric.THERMAL_STATE)
            ),
            env.copy(stats = fullStats)
        )
        assertEquals("+18W", first.metrics[0].valueText)
        assertEquals("2.4 GHz", first.metrics[1].valueText)
        assertEquals("1", first.metrics[2].valueText)

        val second = resolve(
            ExpandedCardConfig(
                mode = ExpandedCardMode.DEVICE_STATS,
                stats = listOf(StatMetric.CHARGE_STATE, StatMetric.CHARGE_TIME, StatMetric.BATTERY_CYCLES)
            ),
            env.copy(stats = fullStats)
        )
        assertEquals("AC", second.metrics[0].valueText)
        assertEquals("24m", second.metrics[1].valueText)
        assertEquals("42", second.metrics[2].valueText)
    }

    @Test
    fun counterDrawsOneSegmentPerStep() {
        val card = ExpandedCardConfig(mode = ExpandedCardMode.PROGRESS_COUNTER, counter = CounterState(3, 8))
        val content = resolve(card)
        assertEquals(CardStyle.PROGRESS, content.style)
        assertEquals("3 / 8", content.body)
        assertEquals(CardProgress(8, 3, List(8) { 1 }, card.accentColor), content.progress)
        val big = resolve(card.copy(counter = CounterState(40, 100)))
        assertEquals(listOf(100), big.progress?.segments)
    }

    @Test
    fun dayProgressFollowsTheClock() {
        val card = ExpandedCardConfig(mode = ExpandedCardMode.PROGRESS_COUNTER, counterSource = CounterSource.DAY)
        val content = resolve(card, env.copy(minuteOfDay = 360))
        assertEquals("DAY_PROGRESS 25%", content.body)
        assertEquals(CardText.BLANK, content.title)
        assertEquals(360, content.progress?.value)
        assertEquals(1440, content.progress?.segments?.sum())
        assertTrue(content.actions.isEmpty())
    }

    @Test
    fun weekMonthYearProgressResolveCleanly() {
        val week = resolve(
            ExpandedCardConfig(mode = ExpandedCardMode.PROGRESS_COUNTER, counterSource = CounterSource.WEEK),
            env.copy(dayOfWeekIndex = 4, minuteOfDay = 0)
        )
        assertEquals(7, week.progress?.segments?.size)

        val month = resolve(
            ExpandedCardConfig(mode = ExpandedCardMode.PROGRESS_COUNTER, counterSource = CounterSource.MONTH),
            env.copy(dayOfMonth = 16, daysInMonth = 30, minuteOfDay = 0)
        )
        assertEquals(50 * 30 * 1440 / 100, month.progress?.value)

        val year = resolve(
            ExpandedCardConfig(mode = ExpandedCardMode.PROGRESS_COUNTER, counterSource = CounterSource.YEAR),
            env.copy(dayOfYear = 184, daysInYear = 366, minuteOfDay = 0)
        )
        assertEquals(50, CardText.timeProgress(CounterSource.YEAR, env.copy(dayOfYear = 184, daysInYear = 366, minuteOfDay = 0)).percent)
        assertEquals(CardText.BLANK, year.title)
    }

    @Test
    fun toolsShowTheLastResult() {
        val card = ExpandedCardConfig(mode = ExpandedCardMode.QUICK_TOOLS)
        assertEquals("hint", resolve(card).body)
        assertEquals(ToolResult.Dice(5).toString(), resolve(card.copy(lastTool = ToolResult.Dice(5))).body)
        assertEquals(3, resolve(card).actions.size)
        assertEquals(CardStyle.BIG_TEXT, resolve(card).style)
    }

    @Test
    fun blankTitlesFallBackToBraille() {
        assertEquals(CardText.BLANK, CardText.nonBlankTitle("  "))
        assertEquals(CardText.BLANK, CardText.nonBlankTitle(null))
        assertEquals("Ok", CardText.nonBlankTitle(" Ok "))
        assertEquals(0, CardText.dayPercent(-5))
        assertEquals(100, CardText.dayPercent(2000))
    }
}
