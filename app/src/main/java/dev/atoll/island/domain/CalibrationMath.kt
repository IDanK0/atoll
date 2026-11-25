package dev.atoll.island.domain

import dev.atoll.island.data.model.LiveAlertConfig

internal object CalibrationMath {
    const val MIN_WIDTH_DP = 40
    const val MAX_WIDTH_DP = 240
    const val Y_NUDGE_PX = 2

    // unità U+2800 di ogni campione
    const val MID_UNITS = 4
    const val WIDE_UNITS = 7

    const val MIN_SAMPLE = "\u200B"
    val MID_SAMPLE = "\u2800".repeat(MID_UNITS)
    val WIDE_SAMPLE = "\u2800".repeat(WIDE_UNITS)

    // se cresce meno di così la pillola era ancora al minimo
    private const val MIN_GROWTH_DP = 2f

    data class Fit(val floorDp: Float, val originDp: Float, val dpPerUnit: Float)

    enum class Problem { MID_NOT_WIDER, WIDE_NOT_WIDER }

    sealed interface Result {
        data class Success(val fit: Fit) : Result
        data class Failure(val problem: Problem) : Result
    }

    // servono due campioni sopra il floor
    fun fit(minDp: Float, midDp: Float, wideDp: Float): Result {
        if (!minDp.isFinite() || !midDp.isFinite() || !wideDp.isFinite()) {
            return Result.Failure(Problem.MID_NOT_WIDER)
        }
        if (midDp < minDp + MIN_GROWTH_DP) return Result.Failure(Problem.MID_NOT_WIDER)
        if (wideDp < midDp + MIN_GROWTH_DP) return Result.Failure(Problem.WIDE_NOT_WIDER)
        val slope = ((wideDp - midDp) / (WIDE_UNITS - MID_UNITS)).coerceIn(
            LiveAlertConfig.MIN_EFFECTIVE_DP_PER_UNIT,
            LiveAlertConfig.MAX_EFFECTIVE_DP_PER_UNIT
        )
        return Result.Success(
            Fit(
                floorDp = minDp.coerceIn(LiveAlertConfig.MIN_PILL_FLOOR_DP, LiveAlertConfig.MAX_PILL_FLOOR_DP),
                originDp = (midDp - MID_UNITS * slope).coerceIn(
                    LiveAlertConfig.MIN_PILL_ORIGIN_DP,
                    LiveAlertConfig.MAX_PILL_ORIGIN_DP
                ),
                dpPerUnit = slope
            )
        )
    }

    fun clampYOffsetPx(offsetPx: Int): Int = offsetPx.coerceIn(-96, 96)

    fun barTopPx(centerYpx: Int, statusBarHeightPx: Int, barHeightPx: Int, offsetPx: Int): Int {
        val center = centerYpx.takeIf { it > 0 } ?: statusBarHeightPx / 2
        return (center - barHeightPx / 2 + clampYOffsetPx(offsetPx)).coerceAtLeast(0)
    }
}
