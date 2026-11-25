package dev.atoll.island.service.card

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import dev.atoll.island.R
import dev.atoll.island.data.model.BadgeIcon
import dev.atoll.island.data.model.LargeIconConfig
import dev.atoll.island.data.model.LargeIconType
import java.io.File

object LargeIconRenderer {
    const val SIZE_PX = 128
    private const val FILE_NAME = "card_large_icon.png"

    @Volatile private var cached: Pair<LargeIconConfig, Bitmap?>? = null

    fun badgeRes(icon: BadgeIcon): Int = when (icon) {
        BadgeIcon.BOLT -> R.drawable.ic_card_bolt
        BadgeIcon.NOTE -> R.drawable.ic_card_note
        BadgeIcon.TIMER -> R.drawable.ic_card_timer
        BadgeIcon.STAR -> R.drawable.ic_card_star
        BadgeIcon.TERMINAL -> R.drawable.ic_card_terminal
        BadgeIcon.MUSIC -> R.drawable.ic_card_music
        BadgeIcon.PHONE -> R.drawable.ic_card_phone
        BadgeIcon.BOOKMARK -> R.drawable.ic_card_bookmark
    }

    fun imageFile(context: Context) = File(context.filesDir, FILE_NAME)

    fun render(context: Context, config: LargeIconConfig): Bitmap? {
        cached?.let { (key, bitmap) -> if (key == config) return bitmap }
        val bitmap = when (config.type) {
            LargeIconType.NONE -> null
            LargeIconType.VECTOR -> badge(context, config)
            LargeIconType.IMAGE -> imageFile(context).takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path) }
        }
        cached = config to bitmap
        return bitmap
    }

    private fun badge(context: Context, config: LargeIconConfig): Bitmap {
        val bitmap = createBitmap(SIZE_PX, SIZE_PX)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = config.tint }
        canvas.drawCircle(SIZE_PX / 2f, SIZE_PX / 2f, SIZE_PX / 2f, paint)
        ContextCompat.getDrawable(context, badgeRes(config.icon))?.mutate()?.apply {
            setTint(Color.WHITE)
            val inset = SIZE_PX / 4
            setBounds(inset, inset, SIZE_PX - inset, SIZE_PX - inset)
            draw(canvas)
        }
        return bitmap
    }

    // ritaglio centrale, 128 px, angoli arrotondati; niente permessi di archivio
    fun saveImage(context: Context, uri: Uri): Boolean = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val shortSide = minOf(info.size.width, info.size.height).coerceAtLeast(1)
            val scale = (SIZE_PX * 2f / shortSide).coerceAtMost(1f)
            decoder.setTargetSize(
                (info.size.width * scale).toInt().coerceAtLeast(1),
                (info.size.height * scale).toInt().coerceAtLeast(1)
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val side = minOf(decoded.width, decoded.height).toFloat()
        val matrix = Matrix().apply {
            val s = SIZE_PX / side
            setScale(s, s)
            postTranslate(-(decoded.width - side) / 2f * s, -(decoded.height - side) / 2f * s)
        }
        val shader = BitmapShader(decoded, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
        val out = createBitmap(SIZE_PX, SIZE_PX)
        Canvas(out).drawRoundRect(
            RectF(0f, 0f, SIZE_PX.toFloat(), SIZE_PX.toFloat()),
            SIZE_PX * 0.28f, SIZE_PX * 0.28f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        )
        imageFile(context).outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        cached = null
        true
    }.getOrDefault(false)
}
