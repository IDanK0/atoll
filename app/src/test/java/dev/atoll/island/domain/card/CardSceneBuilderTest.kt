package dev.atoll.island.domain.card

import dev.atoll.island.data.model.CardAction
import dev.atoll.island.data.model.CounterSource
import dev.atoll.island.data.model.CounterState
import dev.atoll.island.data.model.ExpandedCardConfig
import dev.atoll.island.data.model.ExpandedCardMode
import dev.atoll.island.data.model.LargeIconConfig
import dev.atoll.island.data.model.LargeIconType
import dev.atoll.island.data.model.StatMetric
import dev.atoll.island.data.model.TapAction
import dev.atoll.island.data.model.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardSceneBuilderTest {

    private val strings = object : CardStrings, SceneStrings {
        override fun statLabel(metric: StatMetric) = metric.name
        override fun counterValue(count: Int, goal: Int) = "$count / $goal"
        override fun percent(value: Int) = "$value%"
        override fun temperature(celsius: Float) = "$celsius C"
        override fun gigabytes(value: Float) = "$value GB"
        override fun unavailable() = "n/a"
        override fun toolsHint() = "hint"
        override fun toolResult(result: ToolResult) = result.toString()
        override fun counterOf(goal: Int) = "of $goal"
        override fun toolValue(result: ToolResult) = "value"
        override fun toolName(result: ToolResult) = "name"
        override fun toolsChoose() = "choose"
        override fun actionLabel(action: CardAction) = action.toString()
    }

    private val env = CardEnvironment(minuteOfDay = 600, apiLevel = 37, stats = DeviceStats(batteryPercent = 80))

    private fun build(card: ExpandedCardConfig, photo: Boolean = false, e: CardEnvironment = env) =
        CardSceneBuilder.build(card, e, strings, strings, photo)

    @Test
    fun blankModeHasNoScene() {
        assertNull(build(ExpandedCardConfig(mode = ExpandedCardMode.MINIMAL)))
    }

    @Test
    fun onlyAPhotoFillsTheCard() {
        val photo = ExpandedCardConfig(mode = ExpandedCardMode.CUSTOM_NOTE, largeIcon = LargeIconConfig(type = LargeIconType.IMAGE))
        assertTrue((build(photo, photo = true) as CardScene.Note).photoOnly)
        assertFalse((build(photo.copy(noteBody = "latte"), photo = true) as CardScene.Note).photoOnly)
        // senza il file la foto non c'è, anche se era stata scelta
        assertFalse((build(photo, photo = false) as CardScene.Note).photo)
    }

    @Test
    fun counterSwapsPlusForResetAtTheGoal() {
        val card = ExpandedCardConfig(mode = ExpandedCardMode.PROGRESS_COUNTER, counter = CounterState(3, 8), counterLabel = "Acqua")
        val open = build(card) as CardScene.Counter
        assertEquals("3", open.value)
        assertEquals("of 8", open.total)
        assertEquals("Acqua", open.caption)
        assertEquals(listOf(CardAction.CounterMinus, CardAction.CounterPlus), open.buttons.map { it.action })
        assertEquals(8 to 3, open.segments to open.filled)
        val done = build(card.copy(counter = CounterState(8, 8))) as CardScene.Counter
        assertTrue(done.complete)
        assertEquals(CardAction.CounterReset, done.buttons.last().action)
    }

    @Test
    fun longGoalsStayAtTwentySegments() {
        val card = ExpandedCardConfig(mode = ExpandedCardMode.PROGRESS_COUNTER, counter = CounterState(50, 100))
        val scene = build(card) as CardScene.Counter
        assertEquals(20, scene.segments)
        assertEquals(10, scene.filled)
    }

    @Test
    fun counterTimeSourcesShowSegmentsClockAndRemaining() {
        val day = build(
            ExpandedCardConfig(mode = ExpandedCardMode.PROGRESS_COUNTER, counterSource = CounterSource.DAY),
            e = env.copy(minuteOfDay = 840, timeText = "14:00")
        ) as CardScene.Counter
        assertEquals("14:00", day.value)
        assertEquals("58%", day.total)
        assertEquals(24, day.segments)
        assertEquals(14, day.filled)

        val week = build(
            ExpandedCardConfig(mode = ExpandedCardMode.PROGRESS_COUNTER, counterSource = CounterSource.WEEK),
            e = env.copy(dayOfWeekIndex = 3, minuteOfDay = 720)
        ) as CardScene.Counter
        assertEquals(7, week.segments)
        assertEquals(3, week.filled)

        val month = build(
            ExpandedCardConfig(mode = ExpandedCardMode.PROGRESS_COUNTER, counterSource = CounterSource.MONTH),
            e = env.copy(dayOfMonth = 15, daysInMonth = 30, minuteOfDay = 0)
        ) as CardScene.Counter
        assertEquals(30, month.segments)
        assertEquals(15, month.filled)

        val year = build(
            ExpandedCardConfig(mode = ExpandedCardMode.PROGRESS_COUNTER, counterSource = CounterSource.YEAR),
            e = env.copy(dayOfYear = 183, daysInYear = 365, monthOfYear = 7)
        ) as CardScene.Counter
        assertEquals(12, year.segments)
        assertEquals(7, year.filled)
    }

    @Test
    fun statsRingOnlyForPercentages() {
        val card = ExpandedCardConfig(mode = ExpandedCardMode.DEVICE_STATS, stats = listOf(StatMetric.BATTERY, StatMetric.FREE_RAM))
        val cells = (build(card) as CardScene.Stats).cells
        assertEquals(0.8f, cells[0].fraction)
        assertNull(cells[1].fraction)
        assertEquals("n/a", cells[1].value)

        val ext = ExpandedCardConfig(
            mode = ExpandedCardMode.DEVICE_STATS,
            stats = listOf(StatMetric.STORAGE_FREE, StatMetric.THERMAL_STATE, StatMetric.BATTERY_POWER)
        )
        val extCells = (build(
            ext,
            photo = false
        ) as CardScene.Stats).cells
        assertNull(extCells[0].fraction)

        val liveEnv = env.copy(
            stats = DeviceStats(
                batteryPowerWatts = -3.2f,
                storageFreeGb = 128f,
                storageTotalGb = 256f,
                thermalStatus = 2
            )
        )
        val liveCells = (CardSceneBuilder.build(ext, liveEnv, strings, strings, false) as CardScene.Stats).cells
        assertEquals(0.5f, liveCells[0].fraction)
        assertEquals("128.0 GB", liveCells[0].value)
        assertEquals(0.5f, liveCells[1].fraction)
        assertNull(liveCells[2].fraction)
        assertEquals("-3.2W", liveCells[2].value)
    }

    @Test
    fun toolsAskBeforeTheFirstDraw() {
        val scene = build(ExpandedCardConfig(mode = ExpandedCardMode.QUICK_TOOLS)) as CardScene.Tools
        assertNull(scene.value)
        assertEquals("choose", scene.caption)
        assertEquals(3, scene.buttons.size)
    }

    @Test
    fun capsuleKeepsTheSameTextAndCardIsOneCustomView() {
        val text = "\u2800\u2800\u2002\u200b"
        assertEquals("{\"capsuleData\":{\"type\":\"t_sym\",\"lastTitle\":{\"text\":\"$text\"}}}", ColorOsCardJson.capsule(text))
        assertEquals(
            "{\"primaryData\":{\"type\":\"t_custom\",\"rvId\":\"layout/atoll_rv\"},\"attr\":{\"intent\":\"pi/atoll_tap\"}}",
            ColorOsCardJson.card(tappable = true)
        )
        assertEquals(
            "{\"primaryData\":{\"type\":\"t_custom\",\"rvId\":\"layout/atoll_rv\"},\"attr\":{\"background\":\"colorInt/#88142C48\",\"bgLight\":\"false\"}}",
            ColorOsCardJson.card(tappable = false, backgroundArgbHex = "88142C48")
        )
        assertFalse("attr" in ColorOsCardJson.card(tappable = false))
        assertFalse(ColorOsCardJson.tappable(ExpandedCardConfig(tapAction = TapAction.NONE)))
        assertEquals("{\"a\":\"x\\\"y\\n\"}", Json.write(mapOf("a" to "x\"y\n")))
    }
}
