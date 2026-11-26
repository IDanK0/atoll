package dev.atoll.island.service.card

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Icon
import androidx.core.graphics.createBitmap
import dev.atoll.island.data.model.CardBackground
import dev.atoll.island.data.model.ExpandedCardConfig
import dev.atoll.island.domain.card.resolveCardBackground

// disegna lo sfondo "dal nero al colore" una volta per colore scelto e lo riusa ai tick della scheda
object CardBackgroundRenderer {
    const val TOP_BLACK = 0xF206080B.toInt()
    private const val WIDTH = 24
    private const val HEIGHT = 256

    @Volatile
    private var cached: Triple<Int, Bitmap, Icon>? = null

    internal fun appearance(
        context: Context,
        card: ExpandedCardConfig,
        templatePreset: CardBackground? = null,
        templateHex: String? = null
    ) = resolveCardBackground(card, templatePreset, templateHex) { gradient ->
        context.getColor(if (gradient) android.R.color.system_accent1_600 else android.R.color.system_accent1_800)
    }

    fun gradientStops(colorRgb: Int): IntArray {
        val r = Color.red(colorRgb)
        val g = Color.green(colorRgb)
        val b = Color.blue(colorRgb)
        val mid = Color.argb(0xD8, (r * 0.35f).toInt(), (g * 0.35f).toInt(), (b * 0.35f).toInt())
        val bottom = Color.argb(0xCC, r, g, b)
        return intArrayOf(TOP_BLACK, mid, bottom)
    }

    fun gradientIcon(colorRgb: Int): Icon {
        val opaque = colorRgb and 0x00FFFFFF
        cached?.let { (key, _, icon) -> if (key == opaque) return icon }
        val bmp = createBitmap(WIDTH, HEIGHT)
        val paint = Paint(Paint.DITHER_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, HEIGHT.toFloat(),
                gradientStops(opaque),
                floatArrayOf(0f, 0.38f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        Canvas(bmp).drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        val icon = Icon.createWithBitmap(bmp)
        cached = Triple(opaque, bmp, icon)
        return icon
    }
}
