package dev.atoll.island.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

internal class PeekCalibrationBarView(context: Context) : View(context) {
    private val paint = Paint()

    var rulerColor: Int = 0xFF00FF66.toInt()
        set(value) {
            field = value
            invalidate()
        }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        val endCap = (3f * resources.displayMetrics.density).coerceAtMost(width / 2f)
        val half = height / 2f
        paint.color = rulerColor
        canvas.drawRect(0f, half * 0.67f, width.toFloat(), half * 1.33f, paint)
        canvas.drawRect(0f, 0f, endCap, height.toFloat(), paint)
        canvas.drawRect(width - endCap, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
