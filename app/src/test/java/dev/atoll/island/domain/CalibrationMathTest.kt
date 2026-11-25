package dev.atoll.island.domain

import dev.atoll.island.data.model.LiveAlertConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationMathTest {
    private fun success(result: CalibrationMath.Result) = (result as CalibrationMath.Result.Success).fit

    @Test
    fun `fits the phone measurements`() {
        // 348, 440 e 614 px a 3.5x: vuota, 4 e 7 x U+2800
        val fit = success(CalibrationMath.fit(348 / 3.5f, 440 / 3.5f, 614 / 3.5f))
        assertEquals(99.43f, fit.floorDp, 0.01f)
        assertEquals(16.57f, fit.dpPerUnit, 0.01f)
        assertEquals(59.43f, fit.originDp, 0.01f)
        val model = LiveAlertConfig(pillFloorDp = fit.floorDp, pillOriginDp = fit.originDp, effectiveDpPerUnit = fit.dpPerUnit)
        // deve tornare anche il campione che non ha visto: 3 x U+2800 = 382 px
        assertEquals(382f, AdaptiveCapsuleSolver.predictedWidthDp(model, 3f) * 3.5f, 3f)
        assertEquals(348f, AdaptiveCapsuleSolver.predictedWidthDp(model, 2f) * 3.5f, 0.5f)
    }

    @Test
    fun `rejects samples that did not grow`() {
        assertEquals(
            CalibrationMath.Result.Failure(CalibrationMath.Problem.MID_NOT_WIDER),
            CalibrationMath.fit(100f, 101f, 150f)
        )
        assertEquals(
            CalibrationMath.Result.Failure(CalibrationMath.Problem.WIDE_NOT_WIDER),
            CalibrationMath.fit(100f, 126f, 127f)
        )
        assertTrue(CalibrationMath.fit(Float.NaN, 126f, 175f) is CalibrationMath.Result.Failure)
    }

    @Test
    fun `clamps every parameter to its range`() {
        val wide = success(CalibrationMath.fit(200f, 220f, 400f))
        assertEquals(LiveAlertConfig.MAX_PILL_FLOOR_DP, wide.floorDp, 0f)
        assertEquals(LiveAlertConfig.MAX_EFFECTIVE_DP_PER_UNIT, wide.dpPerUnit, 0f)
        // una retta ripida da un campione medio basso partirebbe sotto zero
        val steep = success(CalibrationMath.fit(50f, 60f, 200f))
        assertEquals(LiveAlertConfig.MIN_PILL_ORIGIN_DP, steep.originDp, 0f)
    }

    @Test
    fun `positions the ruler at the camera or the status bar center`() {
        assertEquals(38, CalibrationMath.barTopPx(52, 120, 40, 6))
        assertEquals(34, CalibrationMath.barTopPx(0, 120, 40, -6))
        assertEquals(0, CalibrationMath.barTopPx(1, 120, 40, -200))
    }
}
