package dev.atoll.island.domain

import dev.atoll.island.data.model.AdaptivePaddingResult
import dev.atoll.island.data.model.CapsuleScalingMode
import dev.atoll.island.data.model.DeviceDisplayMetrics
import dev.atoll.island.data.model.GlyphBreakdownItem
import dev.atoll.island.data.model.LiveAlertConfig
import dev.atoll.island.data.model.PaddingGlyph
import kotlin.math.abs
import kotlin.math.max

// sceglie il testo che dà la capsula più vicina alla larghezza voluta
object AdaptiveCapsuleSolver {

    const val MAX_PRESET_CHIP_CHARS = 7

    // larghezze rispetto a U+2800 nel font di ColorOS 17
    fun unitWeight(glyph: PaddingGlyph): Float = when (glyph) {
        PaddingGlyph.BRAILLE_BLANK -> 1.000f
        PaddingGlyph.EM_SPACE -> 1.445f
        PaddingGlyph.EN_SPACE -> 0.722f
        PaddingGlyph.THREE_PER_EM_SPACE -> 0.482f
        PaddingGlyph.FOUR_PER_EM_SPACE -> 0.344f
        PaddingGlyph.THIN_SPACE -> 0.172f
        // il font non ha l'hair space e il fallback è molto più largo
        PaddingGlyph.HAIR_SPACE -> 0.826f
        PaddingGlyph.ZERO_WIDTH_SPACE -> 0f
        PaddingGlyph.DEBUG_BULLET, PaddingGlyph.DEBUG_BLOCK -> 1.000f
    }

    fun predictedWidthDp(config: LiveAlertConfig, units: Float): Float =
        max(config.pillFloorDp, config.pillOriginDp + units * config.effectiveDpPerUnit)

    private const val CLAMP_TOLERANCE_DP = 0.5f
    private const val TIE_EPSILON_UNITS = 0.01f

    private val FINE_GLYPHS = listOf(
        PaddingGlyph.EN_SPACE,
        PaddingGlyph.THREE_PER_EM_SPACE,
        PaddingGlyph.FOUR_PER_EM_SPACE,
        PaddingGlyph.THIN_SPACE
    )
    private val FINE_LIMITS = intArrayOf(1, 1, 2, 3)

    fun solve(
        config: LiveAlertConfig,
        metrics: DeviceDisplayMetrics
    ): AdaptivePaddingResult {
        val density = if (metrics.density > 0f) metrics.density else 3.0f
        val xdpi = if (metrics.xdpi > 0f) metrics.xdpi else density * 160f
        val portraitWidthDp = minOf(metrics.screenWidthPx, metrics.screenHeightPx).coerceAtLeast(1) / density
        val model = config.normalizedModel()

        if (config.manualTextEnabled) {
            return buildManualResult(model, density, xdpi, portraitWidthDp)
        }

        val preset = config.selectedPreset
        val targetWidthDp: Float
        val targetMm: Float
        when {
            preset.isCustom -> {
                targetWidthDp = model.customWidthDp.coerceIn(
                    LiveAlertConfig.MIN_CUSTOM_WIDTH_DP,
                    LiveAlertConfig.MAX_CUSTOM_WIDTH_DP
                )
                targetMm = targetWidthDp * density / xdpi * MM_PER_INCH
            }
            config.scalingMode == CapsuleScalingMode.PROPORTIONAL -> {
                targetWidthDp = portraitWidthDp * preset.screenWidthRatio
                targetMm = targetWidthDp * density / xdpi * MM_PER_INCH
            }
            else -> {
                targetMm = preset.physicalWidthMm * config.physicalCalibrationFactor.coerceIn(
                    LiveAlertConfig.MIN_PHYSICAL_CALIBRATION,
                    LiveAlertConfig.MAX_PHYSICAL_CALIBRATION
                )
                targetWidthDp = targetMm / MM_PER_INCH * xdpi / density
            }
        }

        val budget = config.maxChipChars.coerceIn(1, MAX_PRESET_CHIP_CHARS)
        val minUnits = if (config.brailleOnlySafeAnchor) 1f else 0f
        val neededUnits = ((targetWidthDp - model.pillOriginDp) / model.effectiveDpPerUnit).coerceAtLeast(0f)
        val minimumWidthDp = predictedWidthDp(model, minUnits)
        val wasClampedByMin = targetWidthDp + CLAMP_TOLERANCE_DP < minimumWidthDp

        val synthesis = if (targetWidthDp <= minimumWidthDp) {
            synthesize(minUnits, budget, config.brailleOnlySafeAnchor)
        } else {
            synthesize(neededUnits, budget, config.brailleOnlySafeAnchor)
        }
        val estimatedWidthDp = predictedWidthDp(model, synthesis.units)
        val wasClampedByMax = !wasClampedByMin &&
            estimatedWidthDp + CLAMP_TOLERANCE_DP < targetWidthDp &&
            synthesis.text.length >= budget

        return AdaptivePaddingResult(
            text = synthesis.text,
            targetWidthDp = targetWidthDp,
            estimatedWidthDp = estimatedWidthDp,
            targetCapsulePx = targetWidthDp * density,
            estimatedActualPx = estimatedWidthDp * density,
            errorPx = (estimatedWidthDp - targetWidthDp) * density,
            neededUnits = neededUnits,
            achievedUnits = synthesis.units,
            pillFloorDp = model.pillFloorDp,
            pillOriginDp = model.pillOriginDp,
            effectiveDpPerUnit = model.effectiveDpPerUnit,
            wasClampedByMin = wasClampedByMin,
            wasClampedByMax = wasClampedByMax,
            targetMm = targetMm,
            targetScreenPercent = targetWidthDp / portraitWidthDp * 100f,
            breakdown = synthesis.breakdown,
            isManualCustom = false
        )
    }

    private fun buildManualResult(
        model: LiveAlertConfig,
        density: Float,
        xdpi: Float,
        portraitWidthDp: Float
    ): AdaptivePaddingResult {
        val length = model.customLength.coerceIn(CapsulePaddingGenerator.MIN_LENGTH, CapsulePaddingGenerator.MAX_LENGTH)
        val glyph = if (model.customGlyph.isVisibleDebug) PaddingGlyph.BRAILLE_BLANK else model.customGlyph
        val units = length * unitWeight(glyph)
        val widthDp = predictedWidthDp(model, units)
        return AdaptivePaddingResult(
            text = CapsulePaddingGenerator.generate(length = length, glyph = glyph),
            targetWidthDp = widthDp,
            estimatedWidthDp = widthDp,
            targetCapsulePx = widthDp * density,
            estimatedActualPx = widthDp * density,
            errorPx = 0f,
            neededUnits = units,
            achievedUnits = units,
            pillFloorDp = model.pillFloorDp,
            pillOriginDp = model.pillOriginDp,
            effectiveDpPerUnit = model.effectiveDpPerUnit,
            wasClampedByMin = model.pillOriginDp + units * model.effectiveDpPerUnit < model.pillFloorDp,
            wasClampedByMax = length > model.maxChipChars,
            targetMm = widthDp * density / xdpi * MM_PER_INCH,
            targetScreenPercent = widthDp / portraitWidthDp * 100f,
            breakdown = if (length == 0) emptyList() else listOf(GlyphBreakdownItem(glyph, length, unitWeight(glyph))),
            isManualCustom = true
        )
    }

    private data class Synthesis(
        val text: String,
        val units: Float,
        val breakdown: List<GlyphBreakdownItem>
    )

    // mai spazi ai bordi, sennò il trim li toglie
    private fun synthesize(targetUnits: Float, budget: Int, brailleAnchor: Boolean): Synthesis {
        var best = IntArray(FINE_GLYPHS.size + 1)
        var bestError = Float.MAX_VALUE
        var bestLength = Int.MAX_VALUE
        val minBraille = if (brailleAnchor) 1 else 0
        val counts = IntArray(FINE_GLYPHS.size + 1)
        fun search(index: Int) {
            if (index == counts.size) {
                val spaces = counts.drop(1).sum()
                val length = textLength(counts[0], spaces)
                if (length > budget) return
                val units = counts[0] + FINE_GLYPHS.indices.sumOf { counts[it + 1] * unitWeight(FINE_GLYPHS[it]).toDouble() }.toFloat()
                val error = abs(units - targetUnits)
                if (error + TIE_EPSILON_UNITS < bestError || (error <= bestError + TIE_EPSILON_UNITS && length < bestLength)) {
                    best = counts.copyOf()
                    bestError = error
                    bestLength = length
                }
                return
            }
            val range = if (index == 0) minBraille..budget else 0..FINE_LIMITS[index - 1]
            for (count in range) {
                counts[index] = count
                search(index + 1)
            }
        }
        search(0)

        val braille = best[0]
        val text = buildString {
            if (braille == 0) append(PaddingGlyph.ZERO_WIDTH_SPACE.char)
            repeat(braille) { append(PaddingGlyph.BRAILLE_BLANK.char) }
            FINE_GLYPHS.forEachIndexed { i, glyph -> repeat(best[i + 1]) { append(glyph.char) } }
            if (best.drop(1).sum() > 0) append(PaddingGlyph.ZERO_WIDTH_SPACE.char)
        }
        val usage = listOf(PaddingGlyph.BRAILLE_BLANK to braille) +
            FINE_GLYPHS.mapIndexed { i, glyph -> glyph to best[i + 1] } +
            listOf(PaddingGlyph.ZERO_WIDTH_SPACE to text.count { it == PaddingGlyph.ZERO_WIDTH_SPACE.char })
        return Synthesis(
            text = text,
            units = usage.sumOf { (glyph, count) -> (count * unitWeight(glyph)).toDouble() }.toFloat(),
            breakdown = usage.filter { it.second > 0 }.map { (glyph, count) ->
                GlyphBreakdownItem(glyph, count, unitWeight(glyph))
            }
        )
    }

    private fun textLength(braille: Int, spaces: Int): Int = when {
        spaces == 0 -> maxOf(braille, 1)
        braille == 0 -> spaces + 2
        else -> braille + spaces + 1
    }

    private fun LiveAlertConfig.normalizedModel(): LiveAlertConfig = copy(
        pillFloorDp = pillFloorDp.coerceIn(LiveAlertConfig.MIN_PILL_FLOOR_DP, LiveAlertConfig.MAX_PILL_FLOOR_DP),
        pillOriginDp = pillOriginDp.coerceIn(LiveAlertConfig.MIN_PILL_ORIGIN_DP, LiveAlertConfig.MAX_PILL_ORIGIN_DP),
        effectiveDpPerUnit = effectiveDpPerUnit.coerceIn(
            LiveAlertConfig.MIN_EFFECTIVE_DP_PER_UNIT,
            LiveAlertConfig.MAX_EFFECTIVE_DP_PER_UNIT
        )
    )

    private const val MM_PER_INCH = 25.4f
}
