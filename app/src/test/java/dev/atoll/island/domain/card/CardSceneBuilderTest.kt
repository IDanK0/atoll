package dev.atoll.island.domain.card

import dev.atoll.island.data.model.CardAction
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

    private fun build(card: ExpandedCardConfig, photo: Boolean = false) = CardSceneBuilder.build(card, env, strings, strings, photo)

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
    fun statsRingOnlyForPercentages() {
        val card = ExpandedCardConfig(mode = ExpandedCardMode.DEVICE_STATS, stats = listOf(StatMetric.BATTERY, StatMetric.FREE_RAM))
        val cells = (build(card) as CardScene.Stats).cells
        assertEquals(0.8f, cells[0].fraction)
        assertNull(cells[1].fraction)
        assertEquals("n/a", cells[1].value)
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
        assertFalse("attr" in ColorOsCardJson.card(tappable = false))
        assertFalse(ColorOsCardJson.tappable(ExpandedCardConfig(tapAction = TapAction.NONE)))
        assertEquals("{\"a\":\"x\\\"y\\n\"}", Json.write(mapOf("a" to "x\"y\n")))
    }
}
