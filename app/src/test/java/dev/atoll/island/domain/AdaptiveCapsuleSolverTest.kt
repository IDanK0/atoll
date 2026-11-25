package dev.atoll.island.domain

import dev.atoll.island.data.model.CapsulePreset
import dev.atoll.island.data.model.CapsuleScalingMode
import dev.atoll.island.data.model.DeviceDisplayMetrics
import dev.atoll.island.data.model.LiveAlertConfig
import dev.atoll.island.data.model.PaddingGlyph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AdaptiveCapsuleSolverTest {

    // OnePlus 15 (CPH2747) a 1.5K, risoluzione di default
    private val onePlus15 = DeviceDisplayMetrics(
        screenWidthPx = 1272,
        screenHeightPx = 2772,
        xdpi = 455f,
        density = 3.5f
    )
    private val defaults = LiveAlertConfig()

    @Test
    fun `presets match the published iPhone geometry`() {
        // fonti: safearea.info (iOS 27) e schede tecniche Apple
        data class Spec(val preset: CapsulePreset, val widthPt: Float, val screenPt: Float, val mm: Float)
        val specs = listOf(
            Spec(CapsulePreset.IPHONE_18_PRO_MAX, 94.67f, 440f, 15.68f),
            Spec(CapsulePreset.IPHONE_18_PRO, 94.67f, 402f, 15.68f),
            Spec(CapsulePreset.IPHONE_16_17_PRO_MAX, 125.33f, 440f, 20.76f),
            Spec(CapsulePreset.IPHONE_14_16_PLUS_MAX, 125.33f, 430f, 20.76f),
            Spec(CapsulePreset.IPHONE_AIR, 125.33f, 420f, 20.76f),
            Spec(CapsulePreset.IPHONE_16_PRO_17, 125.33f, 402f, 20.76f),
            Spec(CapsulePreset.IPHONE_14_PRO_16, 125.00f, 393f, 20.71f)
        )
        assertEquals(specs.map { it.preset }, CapsulePreset.devicePresets)
        specs.forEach { spec ->
            assertEquals(spec.widthPt, spec.preset.widthPt, 0.001f)
            assertEquals(36.67f, spec.preset.heightPt, 0.001f)
            // tutti @3x a 460 ppi: mm = pt * 3 / 460 * 25.4
            assertEquals(spec.widthPt * 3f / 460f * 25.4f, spec.preset.physicalWidthMm, 0.01f)
            assertEquals(spec.mm, spec.preset.physicalWidthMm, 0.001f)
            assertEquals(spec.screenPt, spec.preset.screenWidthPt, 0f)
            assertEquals(spec.widthPt / spec.screenPt, spec.preset.screenWidthRatio, 0.0001f)
        }
        val ratios = CapsulePreset.devicePresets.map { it.screenWidthRatio }
        assertEquals(ratios.sorted(), ratios)
    }

    @Test
    fun `preset names survived the file encoding`() {
        // era già successo: UTF-8 letto come Windows-1252 rompeva i nomi
        CapsulePreset.entries.forEach { preset ->
            listOf(preset.displayName, preset.shortName).forEach { name ->
                assertFalse(name, name.contains('\u00E2') || name.contains('\u00C3') || name.contains('\uFFFD'))
            }
        }
        assertEquals("15 / 16 Plus", CapsulePreset.IPHONE_14_16_PLUS_MAX.shortName)
        // niente trattini nei testi
        CapsulePreset.entries.forEach {
            assertFalse(it.displayName, Regex("[\u2013\u2014]").containsMatchIn(it.displayName + it.shortName))
        }
    }

    @Test
    fun `default model reproduces the pill widths measured on the phone`() {
        // px misurati in Home per 0..8 x U+2800
        val measuredPx = listOf(348, 348, 348, 382, 440, 498, 556, 614, 673)
        measuredPx.forEachIndexed { units, px ->
            val predictedPx = AdaptiveCapsuleSolver.predictedWidthDp(defaults, units.toFloat()) * 3.5f
            assertEquals("$units x U+2800", px.toFloat(), predictedPx, 3f)
        }
    }

    @Test
    fun `glyph weights reproduce the measured widths`() {
        // 4 x U+2800, un altro carattere, poi U+200B
        val measuredPx = mapOf(
            PaddingGlyph.THIN_SPACE to 450,
            PaddingGlyph.FOUR_PER_EM_SPACE to 460,
            PaddingGlyph.THREE_PER_EM_SPACE to 468,
            PaddingGlyph.EN_SPACE to 482,
            PaddingGlyph.HAIR_SPACE to 488,
            PaddingGlyph.EM_SPACE to 524,
            PaddingGlyph.ZERO_WIDTH_SPACE to 440
        )
        measuredPx.forEach { (glyph, px) ->
            val units = 4f + AdaptiveCapsuleSolver.unitWeight(glyph)
            val predictedPx = AdaptiveCapsuleSolver.predictedWidthDp(defaults, units) * 3.5f
            assertEquals(glyph.name, px.toFloat(), predictedPx, 3f)
        }
    }

    @Test
    fun `every preset lands within one fine step of its target or at the phone minimum`() {
        val fineStepDp = AdaptiveCapsuleSolver.unitWeight(PaddingGlyph.THIN_SPACE) * defaults.effectiveDpPerUnit
        var previous = 0f
        CapsulePreset.devicePresets.forEach { preset ->
            val result = AdaptiveCapsuleSolver.solve(defaults.copy(selectedPreset = preset), onePlus15)
            val expectedTarget = 1272 / 3.5f * preset.screenWidthRatio
            assertEquals(expectedTarget, result.targetWidthDp, 0.01f)
            if (result.wasClampedByMin) {
                assertTrue("$preset target is above the floor", result.targetWidthDp < defaults.pillFloorDp)
                assertEquals(defaults.pillFloorDp, result.estimatedWidthDp, 0.01f)
                assertEquals("\u200B", result.text)
            } else {
                assertTrue(
                    "$preset: expected ${result.estimatedWidthDp}, target ${result.targetWidthDp}",
                    abs(result.estimatedWidthDp - result.targetWidthDp) <= fineStepDp / 2 + 0.01f
                )
            }
            assertTrue(result.text.length in 1..AdaptiveCapsuleSolver.MAX_PRESET_CHIP_CHARS)
            assertEquals("trim() must not change $preset", result.text, result.text.trim())
            assertTrue(result.estimatedWidthDp >= previous)
            previous = result.estimatedWidthDp
        }
        // qui solo le isole di iPhone 18 sono più strette della pillola minima
        val clamped = CapsulePreset.devicePresets.filter {
            AdaptiveCapsuleSolver.solve(defaults.copy(selectedPreset = it), onePlus15).wasClampedByMin
        }
        assertEquals(listOf(CapsulePreset.IPHONE_18_PRO_MAX, CapsulePreset.IPHONE_18_PRO), clamped)
    }

    @Test
    fun `text is measured to the units it claims`() {
        CapsulePreset.devicePresets.forEach { preset ->
            val result = AdaptiveCapsuleSolver.solve(defaults.copy(selectedPreset = preset), onePlus15)
            val counted = result.text.sumOf { char ->
                val glyph = PaddingGlyph.entries.first { it.char == char }
                AdaptiveCapsuleSolver.unitWeight(glyph).toDouble()
            }.toFloat()
            assertEquals(preset.name, result.achievedUnits, counted, 0.001f)
            assertFalse(result.text.contains(PaddingGlyph.HAIR_SPACE.char))
        }
    }

    @Test
    fun `physical mode converts millimeters through xdpi`() {
        val result = AdaptiveCapsuleSolver.solve(
            defaults.copy(selectedPreset = CapsulePreset.IPHONE_16_PRO_17, scalingMode = CapsuleScalingMode.PHYSICAL_MM),
            onePlus15
        )
        assertEquals(20.76f / 25.4f * 455f / 3.5f, result.targetWidthDp, 0.01f)
        assertEquals(20.76f, result.targetMm, 0.001f)
    }

    @Test
    fun `custom width is solved like a preset`() {
        val result = AdaptiveCapsuleSolver.solve(
            defaults.copy(selectedPreset = CapsulePreset.CUSTOM, customWidthDp = 140f),
            onePlus15
        )
        assertEquals(140f, result.targetWidthDp, 0.001f)
        val halfFineStep = AdaptiveCapsuleSolver.unitWeight(PaddingGlyph.THIN_SPACE) * defaults.effectiveDpPerUnit / 2
        assertEquals(140f, result.estimatedWidthDp, halfFineStep)
        assertTrue(result.text.length <= AdaptiveCapsuleSolver.MAX_PRESET_CHIP_CHARS)
        assertFalse(result.isManualCustom)
    }

    @Test
    fun `manual text is sent as is and overrides the preset`() {
        val result = AdaptiveCapsuleSolver.solve(
            defaults.copy(
                selectedPreset = CapsulePreset.IPHONE_14_PRO_16,
                manualTextEnabled = true,
                customLength = 5,
                customGlyph = PaddingGlyph.EN_SPACE
            ),
            onePlus15
        )
        assertEquals("\u2002".repeat(5), result.text)
        assertTrue(result.isManualCustom)
        assertEquals(5 * 0.722f, result.achievedUnits, 0.001f)
    }

    @Test
    fun `changing resolution keeps the same text`() {
        // da 1.5K a FHD+ cambiano px, density e xdpi insieme, i dp no
        val scale = 1080f / 1272f
        val fhd = DeviceDisplayMetrics(1080, 2353, 455f * scale, 3.5f * scale)
        CapsuleScalingMode.entries.forEach { mode ->
            CapsulePreset.devicePresets.forEach { preset ->
                val config = defaults.copy(selectedPreset = preset, scalingMode = mode)
                val a = AdaptiveCapsuleSolver.solve(config, onePlus15)
                val b = AdaptiveCapsuleSolver.solve(config, fhd)
                assertEquals("$mode $preset", a.text, b.text)
                assertEquals(a.targetWidthDp, b.targetWidthDp, 0.3f)
            }
        }
    }

    @Test
    fun `character budget and Braille anchor are respected`() {
        val wide = DeviceDisplayMetrics(1800, 3200, 420f, 3f)
        val tight = AdaptiveCapsuleSolver.solve(
            defaults.copy(selectedPreset = CapsulePreset.IPHONE_14_PRO_16, maxChipChars = 3),
            wide
        )
        assertTrue(tight.text.length <= 3)
        assertTrue(tight.wasClampedByMax)

        val anchored = AdaptiveCapsuleSolver.solve(
            defaults.copy(selectedPreset = CapsulePreset.IPHONE_18_PRO_MAX, brailleOnlySafeAnchor = true),
            onePlus15
        )
        assertEquals("\u2800", anchored.text)
        assertTrue(anchored.wasClampedByMin)
    }
}
