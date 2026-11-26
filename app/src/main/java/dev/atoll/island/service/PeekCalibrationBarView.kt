package dev.atoll.island.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

internal class PeekCalibrationBarView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    var rulerColor: Int = 0xFF00FF66.toInt()
        set(value) {
            field = value
            invalidate()
        }

    var pointsUp: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val endCap = (3f * d).coerceAtMost(width / 2f)
        val lineThickness = (4f * d).coerceAtMost(height.toFloat())
        val lineTop = if (pointsUp) height - lineThickness else (height - lineThickness) / 2f
        val lineBottom = lineTop + lineThickness
        val pad = d

        // alone scuro così il righello si legge bene anche sopra icone o sfondi chiari
        paint.color = 0xB3000000.toInt()
        canvas.drawRect(0f, (lineTop - pad).coerceAtLeast(0f), width.toFloat(), (lineBottom + pad).coerceAtMost(height.toFloat()), paint)
        canvas.drawRect(0f, 0f, (endCap + pad).coerceAtMost(width / 2f), height.toFloat(), paint)
        canvas.drawRect((width - endCap - pad).coerceAtLeast(width / 2f), 0f, width.toFloat(), height.toFloat(), paint)

        paint.color = rulerColor
        canvas.drawRect(0f, lineTop, width.toFloat(), lineBottom, paint)
        canvas.drawRect(0f, 0f, endCap, height.toFloat(), paint)
        canvas.drawRect(width - endCap, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
