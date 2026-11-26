package dev.atoll.island.service.card

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import dev.atoll.island.R
import dev.atoll.island.data.model.CardAction
import dev.atoll.island.data.model.ExpandedCardConfig
import dev.atoll.island.data.model.ToolResult
import dev.atoll.island.domain.card.CardScene
import dev.atoll.island.domain.card.SceneButton

// le schede disegnate da Atoll: stile scuro e pulsanti tondi come quelli di ColorOS
object CardViews {
    private const val TRACK = 0x29FFFFFF
    private const val ON_ACCENT = 0xFF101418.toInt()

    // 0 = colore d'accento del sistema, come fa ColorOS con lo sfondo
    fun accent(context: Context, color: Int): Int =
        if (color != 0) color else context.getColor(android.R.color.system_accent1_200)

    fun build(context: Context, card: ExpandedCardConfig, scene: CardScene): RemoteViews {
        val accent = accent(context, card.accentColor)
        return when (scene) {
            is CardScene.Note -> note(context, scene)
            is CardScene.Dock -> dock(context, scene)
            is CardScene.Stats -> stats(context, scene, accent)
            is CardScene.Counter -> counter(context, scene, accent)
            is CardScene.Tools -> tools(context, scene)
        }
    }

    private fun rv(context: Context, layout: Int) = RemoteViews(context.packageName, layout)

    private fun RemoteViews.click(context: Context, viewId: Int, button: SceneButton) {
        setContentDescription(viewId, button.label)
        CardIntents.forAction(context, button.action, button.slot, inCard = true)?.let { setOnClickPendingIntent(viewId, it) }
    }

    private fun RemoteViews.tint(viewId: Int, method: String, color: Int) =
        setColorStateList(viewId, method, ColorStateList.valueOf(color))

    private fun note(context: Context, scene: CardScene.Note): RemoteViews {
        val density = context.resources.displayMetrics.density
        if (scene.photoOnly) {
            return rv(context, R.layout.card_photo).apply {
                CardMedia.scaled(context, 1080, cornerPx = 20f * density)?.let { setImageViewBitmap(R.id.card_photo, it) }
                setContentDescription(R.id.card_photo, context.getString(R.string.card_photo_cd))
                CardIntents.viewPhoto(context)?.let { setOnClickPendingIntent(R.id.card_photo, it) }
            }
        }
        return rv(context, R.layout.card_rv_note).apply {
            if (scene.photo) {
                CardMedia.scaled(context, 420, cornerPx = 14f * density)?.let {
                    setImageViewBitmap(R.id.nt_photo, it)
                    setViewVisibility(R.id.nt_photo, View.VISIBLE)
                    setContentDescription(R.id.nt_photo, context.getString(R.string.card_photo_cd))
                    CardIntents.viewPhoto(context)?.let { pi -> setOnClickPendingIntent(R.id.nt_photo, pi) }
                }
            }
            val empty = scene.title.isEmpty() && scene.body.isEmpty()
            when {
                empty -> {
                    setTextViewText(R.id.nt_title, context.getString(R.string.card_note_empty))
                    setTextColor(R.id.nt_title, 0xB3FFFFFF.toInt())
                    setViewVisibility(R.id.nt_body, View.GONE)
                }
                scene.title.isEmpty() -> {
                    setViewVisibility(R.id.nt_title, View.GONE)
                    setTextViewText(R.id.nt_body, scene.body)
                    setTextColor(R.id.nt_body, 0xFFFFFFFF.toInt())
                    setTextViewTextSize(R.id.nt_body, android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
                }
                else -> {
                    setTextViewText(R.id.nt_title, scene.title)
                    if (scene.body.isEmpty()) setViewVisibility(R.id.nt_body, View.GONE) else setTextViewText(R.id.nt_body, scene.body)
                }
            }
            click(context, R.id.nt_btn, SceneButton(0, CardAction.EditNoteInline, context.getString(R.string.card_action_edit)))
        }
    }

    private val dockSlots = listOf(
        Triple(R.id.dock_slot_0, R.id.dock_icon_0, R.id.dock_label_0),
        Triple(R.id.dock_slot_1, R.id.dock_icon_1, R.id.dock_label_1),
        Triple(R.id.dock_slot_2, R.id.dock_icon_2, R.id.dock_label_2)
    )

    private fun dock(context: Context, scene: CardScene.Dock): RemoteViews = rv(context, R.layout.card_dock).apply {
        val title = scene.title ?: if (scene.slots.isEmpty()) context.getString(R.string.card_dock_empty) else null
        title?.let {
            setTextViewText(R.id.dock_title, it)
            setViewVisibility(R.id.dock_title, View.VISIBLE)
        }
        scene.slots.take(dockSlots.size).forEachIndexed { i, button ->
            val (slot, icon, label) = dockSlots[i]
            val bitmap = (button.action as? CardAction.LaunchApp)?.let { AppIconLoader.load(context, it.packageName) }
                ?: CardMedia.glyph(context, AndroidCardStrings.actionIconRes(button.action))
            setViewVisibility(slot, View.VISIBLE)
            setImageViewBitmap(icon, bitmap)
            setTextViewText(label, button.label)
            click(context, slot, button)
        }
    }

    private fun stats(context: Context, scene: CardScene.Stats, accent: Int): RemoteViews = rv(context, R.layout.card_rv_stats).apply {
        val size = (72 * context.resources.displayMetrics.density).toInt()
        scene.cells.forEach { cell ->
            val child = rv(context, R.layout.card_rv_stat_cell)
            child.setImageViewBitmap(R.id.sc_ring, ring(size, cell.fraction, accent, context.resources.displayMetrics.density))
            child.setTextViewText(R.id.sc_value, cell.value)
            child.setTextViewText(R.id.sc_caption, cell.caption)
            addView(R.id.st_row, child)
        }
    }

    private fun counter(context: Context, scene: CardScene.Counter, accent: Int): RemoteViews = rv(context, R.layout.card_rv_counter).apply {
        setTextViewText(R.id.cv_value, scene.value)
        scene.total?.let { setTextViewText(R.id.cv_total, it) } ?: setViewVisibility(R.id.cv_total, View.GONE)
        scene.caption?.let {
            setTextViewText(R.id.cv_caption, it)
            setViewVisibility(R.id.cv_caption, View.VISIBLE)
        }
        val ids = listOf(R.id.cv_btn_0 to R.id.cv_btn_0_icon, R.id.cv_btn_1 to R.id.cv_btn_1_icon)
        ids.forEachIndexed { i, (button, icon) ->
            val b = scene.buttons.getOrNull(i)
            if (b == null) {
                setViewVisibility(button, View.GONE)
                return@forEachIndexed
            }
            if (b.primary) {
                setInt(button, "setBackgroundResource", R.drawable.card_btn_circle_solid)
                tint(button, "setBackgroundTintList", accent)
                setImageViewResource(icon, AndroidCardStrings.actionIconRes(b.action))
                tint(icon, "setImageTintList", ON_ACCENT)
            } else {
                setImageViewResource(icon, lightIcon(b.action))
            }
            click(context, button, b)
        }
        repeat(scene.segments) { i ->
            val seg = rv(context, R.layout.card_rv_segment)
            seg.tint(R.id.seg, "setBackgroundTintList", if (i < scene.filled) accent else TRACK)
            addView(R.id.cv_bar, seg)
        }
    }

    private fun tools(context: Context, scene: CardScene.Tools): RemoteViews = rv(context, R.layout.card_rv_tools).apply {
        val result = scene.result
        val badge = (44 * context.resources.displayMetrics.density).toInt()
        when (result) {
            is ToolResult.Dice -> die(result.value, badge)
            is ToolResult.Coin -> coin(scene.value.orEmpty().take(1).uppercase(), badge)
            is ToolResult.Number -> tile("#", badge)
            null -> null
        }?.let {
            setImageViewBitmap(R.id.tl_die, it)
            setViewVisibility(R.id.tl_die, View.VISIBLE)
        }
        scene.value?.let {
            setTextViewText(R.id.tl_value, it)
            setViewVisibility(R.id.tl_value, View.VISIBLE)
        }
        setTextViewText(R.id.tl_caption, scene.caption)
        listOf(R.id.tl_btn_0, R.id.tl_btn_1, R.id.tl_btn_2).forEachIndexed { i, id ->
            scene.buttons.getOrNull(i)?.let { b ->
                setTextViewText(id, b.label)
                click(context, id, b)
            }
        }
    }

    // icone chiare (ic_cbtn_*) per i pulsanti tondi trasparenti
    private fun lightIcon(action: CardAction): Int = when (action) {
        CardAction.CounterPlus -> R.drawable.ic_cbtn_plus
        CardAction.CounterMinus -> R.drawable.ic_cbtn_minus
        CardAction.CounterReset -> R.drawable.ic_cbtn_reset
        else -> R.drawable.ic_cbtn_launch
    }

    // anello: traccia tenue sempre, arco d'accento solo se c'è una percentuale
    fun ring(size: Int, fraction: Float?, accent: Int, density: Float): Bitmap {
        val out = createBitmap(size, size)
        val stroke = 5f * density
        val box = RectF(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            color = TRACK
        }
        val canvas = Canvas(out)
        canvas.drawOval(box, paint)
        if (fraction != null && fraction > 0f) {
            paint.color = accent
            canvas.drawArc(box, -90f, 360f * fraction.coerceIn(0f, 1f), false, paint)
        }
        return out
    }

    // moneta dorata con l'iniziale del lato uscito
    fun coin(letter: String, size: Int): Bitmap {
        val out = createBitmap(size, size)
        val canvas = Canvas(out)
        val c = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE9C46A.toInt() }
        canvas.drawCircle(c, c, c, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.05f
        paint.color = 0xFFB8892F.toInt()
        canvas.drawCircle(c, c, c * 0.8f, paint)
        paint.style = Paint.Style.FILL
        paint.color = 0xFF4A3608.toInt()
        drawCentered(canvas, letter, size, paint)
        return out
    }

    fun tile(symbol: String, size: Int): Bitmap {
        val out = createBitmap(size, size)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2FFFFFF.toInt() }
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size * 0.24f, size * 0.24f, paint)
        paint.color = ON_ACCENT
        drawCentered(canvas, symbol, size, paint)
        return out
    }

    private fun drawCentered(canvas: Canvas, text: String, size: Int, paint: Paint) {
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size * 0.5f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(text, size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2, paint)
    }

    // faccia del dado con i punti, più leggibile di un numero da solo
    fun die(value: Int, size: Int): Bitmap {
        val out = createBitmap(size, size)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2FFFFFF.toInt() }
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size * 0.24f, size * 0.24f, paint)
        paint.color = ON_ACCENT
        val r = size * 0.09f
        val lo = size * 0.27f
        val mid = size * 0.5f
        val hi = size * 0.73f
        val pips = when (value) {
            1 -> listOf(mid to mid)
            2 -> listOf(lo to lo, hi to hi)
            3 -> listOf(lo to lo, mid to mid, hi to hi)
            4 -> listOf(lo to lo, hi to lo, lo to hi, hi to hi)
            5 -> listOf(lo to lo, hi to lo, mid to mid, lo to hi, hi to hi)
            else -> listOf(lo to lo, hi to lo, lo to mid, hi to mid, lo to hi, hi to hi)
        }
        pips.forEach { (x, y) -> canvas.drawCircle(x, y, r, paint) }
        return out
    }
}
