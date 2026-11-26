package dev.atoll.island.service.card

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.LruCache
import android.util.Xml
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import java.util.Calendar

// icone delle app come le mostra il launcher: se c'è un pacchetto di icone (formato appfilter.xml) usa quello
object AppIconLoader {
    // ColorOS/OxygenOS salvano qui il pacchetto scelto nel launcher
    private const val COLOROS_ICON_PACK = "icon_pack_name"

    private val icons = LruCache<String, Bitmap>(48)
    @Volatile private var pack: IconPack? = null
    @Volatile private var cachedPackName: String? = null
    @Volatile private var packCheckedAtMs = 0L
    @Volatile private var observerRegistered = false

    private const val PACK_CACHE_TTL_MS = 30_000L

    private fun ensureObserver(context: Context) {
        if (observerRegistered) return
        observerRegistered = true
        runCatching {
            val app = context.applicationContext
            app.contentResolver.registerContentObserver(
                Settings.System.getUriFor(COLOROS_ICON_PACK),
                false,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        packCheckedAtMs = 0L
                        cachedPackName = null
                        pack = null
                        icons.evictAll()
                    }
                }
            )
        }
    }

    fun activePackName(context: Context): String? {
        ensureObserver(context)
        val now = android.os.SystemClock.elapsedRealtime()
        if (packCheckedAtMs > 0L && now - packCheckedAtMs < PACK_CACHE_TTL_MS) {
            return cachedPackName
        }
        val resolved = runCatching { Settings.System.getString(context.contentResolver, COLOROS_ICON_PACK) }.getOrNull()
            ?.trim()?.takeIf { it.isNotEmpty() && isInstalled(context, it) }
        cachedPackName = resolved
        packCheckedAtMs = now
        return resolved
    }

    fun load(context: Context, packageName: String, sizePx: Int = DEFAULT_SIZE_PX): Bitmap? {
        val packName = activePackName(context)
        val day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        val key = "$packName|$packageName|$sizePx|$day"
        icons.get(key)?.let { return it }
        val pm = context.packageManager
        val component = launcherComponent(context, packageName)
        val original = runCatching {
            component?.let { pm.getActivityIcon(it) } ?: pm.getApplicationIcon(packageName)
        }.getOrNull() ?: return null
        val themed = packName?.let { name ->
            runCatching { iconPack(context, name).icon(packageName, component, original, sizePx, day) }
                .onFailure { Log.w("AtollIcons", "Icon pack $name failed for $packageName", it) }
                .getOrNull()
        }
        val bitmap = themed ?: original.toBitmap(sizePx, sizePx)
        icons.put(key, bitmap)
        return bitmap
    }

    private fun iconPack(context: Context, name: String): IconPack =
        pack?.takeIf { it.packageName == name } ?: IconPack.load(context, name).also { pack = it; icons.evictAll() }

    private fun launcherComponent(context: Context, packageName: String): ComponentName? =
        context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName), 0
        ).firstOrNull()?.activityInfo?.let { ComponentName(it.packageName, it.name) }

    private fun isInstalled(context: Context, packageName: String) = runCatching {
        context.packageManager.getApplicationInfo(packageName, 0)
    }.isSuccess

    const val DEFAULT_SIZE_PX = 192
}

// i pacchetti di icone si leggono solo per nome
@SuppressLint("DiscouragedApi")
private class IconPack(
    val packageName: String,
    private val res: Resources,
    private val items: Map<String, String>,
    private val calendars: Map<String, String>,
    private val backs: List<String>,
    private val mask: String?,
    private val upon: String?,
    private val scale: Float
) {
    fun icon(pkg: String, component: ComponentName?, original: Drawable, size: Int, day: Int): Bitmap? {
        val exact = component?.let { "${it.packageName}/${it.className}" }
        calendars[exact]?.let { prefix -> drawable("$prefix$day")?.let { return it.toBitmap(size, size) } }
        val name = items[exact] ?: items.entries.firstOrNull { it.key.substringBefore('/') == pkg }?.value
        name?.let { drawable(it) }?.let { return it.toBitmap(size, size) }
        return masked(pkg, original, size)
    }

    // app non coperte dal pacchetto: sfondo + icona rimpicciolita e ritagliata, come fanno i launcher
    private fun masked(pkg: String, original: Drawable, size: Int): Bitmap? {
        if (backs.isEmpty() && mask == null && upon == null) return null
        val out = createBitmap(size, size)
        val canvas = Canvas(out)
        val inner = (size * scale).toInt().coerceAtLeast(1)
        val offset = (size - inner) / 2f
        canvas.drawBitmap(original.toBitmap(inner, inner), offset, offset, null)
        mask?.let(::drawable)?.let { m ->
            canvas.drawBitmap(m.toBitmap(size, size), 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
        }
        backs.getOrNull(Math.floorMod(pkg.hashCode(), backs.size.coerceAtLeast(1)))?.let(::drawable)?.let { b ->
            canvas.drawBitmap(b.toBitmap(size, size), 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER) })
        }
        upon?.let(::drawable)?.let { canvas.drawBitmap(it.toBitmap(size, size), 0f, 0f, null) }
        return out
    }

    private fun drawable(name: String): Drawable? {
        val id = res.getIdentifier(name, "drawable", packageName).takeIf { it != 0 }
            ?: res.getIdentifier(name, "mipmap", packageName).takeIf { it != 0 }
            ?: return null
        return runCatching { res.getDrawable(id, null) }.getOrNull()
    }

    companion object {
        fun load(context: Context, packageName: String): IconPack {
            val res = context.packageManager.getResourcesForApplication(packageName)
            val items = HashMap<String, String>(4096)
            val calendars = HashMap<String, String>()
            val backs = mutableListOf<String>()
            var mask: String? = null
            var upon: String? = null
            var scale = 1f
            val parser = openAppFilter(context, res, packageName)
            try {
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType != XmlPullParser.START_TAG) continue
                    fun attr(name: String): String? = parser.getAttributeValue(null, name)
                    fun images() = (0 until parser.attributeCount)
                        .filter { parser.getAttributeName(it).startsWith("img") }
                        .map { parser.getAttributeValue(it) }
                    when (parser.name) {
                        "item" -> component(attr("component"))?.let { c -> attr("drawable")?.let { items.putIfAbsent(c, it) } }
                        "calendar" -> component(attr("component"))?.let { c -> attr("prefix")?.let { calendars[c] = it } }
                        "iconback" -> backs += images()
                        "iconmask" -> mask = images().firstOrNull()
                        "iconupon" -> upon = images().firstOrNull()
                        "scale" -> scale = attr("factor")?.toFloatOrNull()?.coerceIn(0.1f, 1f) ?: 1f
                    }
                }
            } finally {
                (parser as? android.content.res.XmlResourceParser)?.close()
            }
            return IconPack(packageName, res, items, calendars, backs, mask, upon, scale)
        }

        private fun openAppFilter(context: Context, res: Resources, packageName: String): XmlPullParser {
            val id = res.getIdentifier("appfilter", "xml", packageName)
            if (id != 0) return res.getXml(id)
            val stream = context.createPackageContext(packageName, 0).assets.open("appfilter.xml")
            return Xml.newPullParser().apply { setInput(stream, null) }
        }

        // "ComponentInfo{pkg/.Main}" diventa "pkg/pkg.Main"
        fun component(raw: String?): String? {
            val inner = raw?.substringAfter('{', "")?.substringBefore('}')?.trim().orEmpty()
            val pkg = inner.substringBefore('/', "")
            val cls = inner.substringAfter('/', "")
            if (pkg.isEmpty() || cls.isEmpty()) return null
            return "$pkg/" + if (cls.startsWith('.')) pkg + cls else cls
        }
    }
}
