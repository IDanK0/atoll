package dev.atoll.island.service.card

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import androidx.core.graphics.createBitmap
import java.io.File
import java.util.UUID

// icone proprie delle scorciatoie (es. avatar chat WhatsApp), salvate in PNG e ripulite se rimosse
object ShortcutIcons {
    private const val DIR = "card/shortcuts"
    private val cache = LruCache<String, Bitmap>(16)

    private fun dir(context: Context): File = File(context.filesDir, DIR)

    private fun isSafeId(id: String): Boolean =
        id.isNotEmpty() && id.length <= 64 && id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }

    private fun fileFor(context: Context, id: String): File? =
        if (isSafeId(id)) File(dir(context), "$id.png") else null

    fun save(context: Context, source: Bitmap): String? = runCatching {
        val id = UUID.randomUUID().toString()
        val size = AppIconLoader.DEFAULT_SIZE_PX
        val normalized = normalize(source, size)
        val folder = dir(context)
        folder.mkdirs()
        File(folder, "$id.png").outputStream().use { normalized.compress(Bitmap.CompressFormat.PNG, 100, it) }
        cache.put(id, normalized)
        id
    }.getOrNull()

    fun load(context: Context, id: String): Bitmap? {
        if (!isSafeId(id)) return null
        cache.get(id)?.let { return it }
        val file = fileFor(context, id)?.takeIf { it.isFile } ?: return null
        return BitmapFactory.decodeFile(file.path)?.also { cache.put(id, it) }
    }

    fun prune(context: Context, keepIds: Set<String>) {
        val folder = dir(context)
        if (!folder.isDirectory) return
        folder.listFiles()?.forEach { file ->
            val id = file.nameWithoutExtension
            if (file.extension == "png" && id !in keepIds) {
                file.delete()
                cache.remove(id)
            }
        }
    }

    private fun normalize(source: Bitmap, targetSize: Int): Bitmap {
        val src = if (source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, false)
        } else source
        val out = createBitmap(targetSize, targetSize)
        val scale = minOf(targetSize.toFloat() / src.width.coerceAtLeast(1), targetSize.toFloat() / src.height.coerceAtLeast(1))
        val drawW = src.width * scale
        val drawH = src.height * scale
        val left = (targetSize - drawW) / 2f
        val top = (targetSize - drawH) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        Canvas(out).drawBitmap(src, null, RectF(left, top, left + drawW, top + drawH), paint)
        return out
    }
}
