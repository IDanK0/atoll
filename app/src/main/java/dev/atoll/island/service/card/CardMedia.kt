package dev.atoll.island.service.card

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import java.io.File

// la foto della nota: salvata una volta a buona risoluzione, poi ridotta per la scheda
object CardMedia {
    private const val DIR = "card"
    private const val PHOTO = "photo.jpg"
    private const val MAX_SIDE = 1600

    @Volatile private var cache: Triple<String, Long, Bitmap>? = null

    fun photoFile(context: Context) = File(File(context.filesDir, DIR), PHOTO)

    fun hasPhoto(context: Context) = photoFile(context).isFile

    fun authority(context: Context) = "${context.packageName}.files"

    fun shareUri(context: Context): Uri? = photoFile(context).takeIf { it.isFile }?.let {
        runCatching { FileProvider.getUriForFile(context, authority(context), it) }.getOrNull()
    }

    fun savePhoto(context: Context, uri: Uri): Boolean = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longSide = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
            val factor = (MAX_SIDE.toFloat() / longSide).coerceAtMost(1f)
            decoder.setTargetSize((info.size.width * factor).toInt().coerceAtLeast(1), (info.size.height * factor).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val file = photoFile(context)
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        cache = null
        // anche l'icona grande della notifica standard
        LargeIconRenderer.saveImage(context, uri)
        true
    }.getOrDefault(false)

    fun deletePhoto(context: Context) {
        photoFile(context).delete()
        cache = null
    }

    // foto per la scheda: lato lungo al massimo maxSide, angoli arrotondati se richiesti
    fun scaled(context: Context, maxSide: Int, cornerPx: Float = 0f): Bitmap? {
        val file = photoFile(context).takeIf { it.isFile } ?: return null
        val key = "$maxSide|$cornerPx"
        cache?.let { (k, stamp, bmp) -> if (k == key && stamp == file.lastModified()) return bmp }
        val full = BitmapFactory.decodeFile(file.path) ?: return null
        val factor = (maxSide.toFloat() / maxOf(full.width, full.height)).coerceAtMost(1f)
        val sized = if (factor < 1f) full.scale((full.width * factor).toInt(), (full.height * factor).toInt()) else full
        val out = if (cornerPx > 0f) rounded(sized, cornerPx) else sized
        cache = Triple(key, file.lastModified(), out)
        return out
    }

    private fun rounded(src: Bitmap, radius: Float): Bitmap {
        val out = createBitmap(src.width, src.height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        Canvas(out).drawRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), radius, radius, paint)
        return out
    }

    // azioni senza icona d'app nel dock: glifo chiaro su un cerchio tenue, come i pulsanti di ColorOS
    fun glyph(context: Context, drawableRes: Int, sizePx: Int = AppIconLoader.DEFAULT_SIZE_PX): Bitmap {
        val out = createBitmap(sizePx, sizePx)
        val canvas = Canvas(out)
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF })
        ContextCompat.getDrawable(context, drawableRes)?.mutate()?.apply {
            setTint(Color.WHITE)
            val inset = (sizePx * 0.28f).toInt()
            setBounds(inset, inset, sizePx - inset, sizePx - inset)
            draw(canvas)
        }
        return out
    }
}
