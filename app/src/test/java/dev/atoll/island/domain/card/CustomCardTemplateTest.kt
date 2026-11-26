package dev.atoll.island.domain.card

import dev.atoll.island.data.model.CardAction
import dev.atoll.island.data.model.CardBackground
import dev.atoll.island.data.model.CounterState
import dev.atoll.island.data.model.ExpandedCardConfig
import dev.atoll.island.data.model.ExpandedCardMode
import dev.atoll.island.data.model.StatMetric
import dev.atoll.island.data.model.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomCardTemplateTest {

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

    private val env = CardEnvironment(
        minuteOfDay = 720,
        apiLevel = 37,
        stats = DeviceStats(batteryPercent = 82, cpuMaxMhz = 1900, freeRamGb = 6.4f, thermalStatus = 0),
        dateText = "26 Nov",
        timeText = "12:00"
    )

    @Test
    fun allStarterPresetsParseCleanlyAndDetectMonitors() {
        listOf(
            CustomCardPresets.CLOCK_AND_CONTROLS,
            CustomCardPresets.HARDWARE_DECK,
            CustomCardPresets.FOCUS_NOTE
        ).forEach { json ->
            val res = CustomCardParser.parse(json)
            assertTrue("Preset failed: $res", res is MakerParseResult.Ok)
        }
        val telemetryNeeds = CustomCardParser.monitorNeeds(CustomCardPresets.HARDWARE_DECK)
        assertTrue(telemetryNeeds.battery)
        assertTrue(telemetryNeeds.fastStats)
        assertFalse(telemetryNeeds.tick)
    }

    @Test
    fun invalidTemplatesReturnSpecificErrors() {
        assertEquals(MakerError.EMPTY, (CustomCardParser.parse("   ") as MakerParseResult.Invalid).reason)
        assertEquals(MakerError.SYNTAX, (CustomCardParser.parse("{bad") as MakerParseResult.Invalid).reason)
        assertEquals(MakerError.NO_BLOCKS, (CustomCardParser.parse("{\"name\":\"x\"}") as MakerParseResult.Invalid).reason)
        assertEquals(
            MakerError.UNKNOWN_BLOCK,
            (CustomCardParser.parse("{\"blocks\":[{\"type\":\"script\"}]}") as MakerParseResult.Invalid).reason
        )
    }

    @Test
    fun bindsPlaceholdersActionsAndCustomHexBackground() {
        val raw = """
            {
              "name": "Desk Mod",
              "background": "#1E3A5F",
              "blocks": [
                { "type": "value", "text": "{time}", "sub": "{battery}", "caption": "Step {counter}/{goal}" },
                { "type": "rings", "items": [{"label": "Power", "value": "{power}"}, {"metric": "CPU_FREQUENCY"}] },
                { "type": "bar", "value": "{day_pct}", "max": 100, "segments": 10 },
                { "type": "buttons", "items": ["camera", "wifi", "timer_app:300:Tea", "invalid_internal"] }
              ]
            }
        """.trimIndent()
        val ok = CustomCardParser.parse(raw) as MakerParseResult.Ok
        assertEquals("991E3A5F", ok.template.customBgHex)
        val cfg = ExpandedCardConfig(
            mode = ExpandedCardMode.CUSTOM_TEMPLATE,
            customTemplateJson = raw,
            counter = CounterState(3, 8)
        )
        val scene = CardSceneBuilder.build(cfg, env, strings, strings, false) as CardScene.Maker
        val valBlock = scene.blocks[0] as ResolvedMakerBlock.Value
        assertEquals("12:00", valBlock.text)
        assertEquals("82%", valBlock.sub)
        assertEquals("Step 3/8", valBlock.caption)

        val rings = scene.blocks[1] as ResolvedMakerBlock.Rings
        assertEquals(2, rings.cells.size)

        val bar = scene.blocks[2] as ResolvedMakerBlock.Bar
        assertEquals(10, bar.bar.segments)
        assertEquals(5, bar.bar.filled)

        val btns = scene.blocks[3] as ResolvedMakerBlock.Buttons
        assertEquals(
            listOf(CardAction.Camera, CardAction.WifiPanel, CardAction.ClockTimer(300, "Tea")),
            btns.buttons.map { it.action }
        )

        val namedBg = CustomCardParser.parse("""{"background":"plum","blocks":[{"type":"text","text":"Hi"}]}""") as MakerParseResult.Ok
        assertEquals(CardBackground.PLUM, namedBg.template.background)
    }
}
