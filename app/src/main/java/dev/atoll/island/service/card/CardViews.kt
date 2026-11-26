package dev.atoll.island.service.card

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BulletSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import dev.atoll.island.R
import dev.atoll.island.data.model.CardAction
import dev.atoll.island.data.model.ExpandedCardConfig
import dev.atoll.island.data.model.ToolResult
import dev.atoll.island.domain.card.CardScene
import dev.atoll.island.domain.card.NoteMarkdown
import dev.atoll.island.domain.card.NoteSpanStyle
import dev.atoll.island.domain.card.ResolvedMakerBlock
import dev.atoll.island.domain.card.SceneButton
import kotlin.math.roundToInt

// le schede disegnate da Atoll: stile scuro e pulsanti tondi come quelli di ColorOS
object CardViews {
    private const val TRACK = 0x29FFFFFF
    private const val ON_ACCENT = 0xFF101418.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val WHITE_90 = 0xE6FFFFFF.toInt()
    private const val WHITE_70 = 0xB3FFFFFF.toInt()
    private const val WHITE_60 = 0x99FFFFFF.toInt()

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
            is CardScene.Maker -> maker(context, scene, accent)
        }
    }

    private fun rv(context: Context, layout: Int) = RemoteViews(context.packageName, layout)

    // colori espliciti per ridurre i problemi di contrasto su ColorOS
    private fun RemoteViews.text(viewId: Int, value: CharSequence, color: Int) {
        setTextViewText(viewId, value)
        setTextColor(viewId, color)
    }

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
                    text(R.id.nt_title, context.getString(R.string.card_note_empty), WHITE_70)
                    setViewVisibility(R.id.nt_body, View.GONE)
                }
                scene.title.isEmpty() -> {
                    setViewVisibility(R.id.nt_title, View.GONE)
                    text(R.id.nt_body, styledNoteBody(scene, density, baseWhite = true), WHITE)
                    setTextViewTextSize(R.id.nt_body, TypedValue.COMPLEX_UNIT_SP, 18f)
                }
                else -> {
                    text(R.id.nt_title, scene.title, WHITE)
                    if (scene.body.isEmpty()) setViewVisibility(R.id.nt_body, View.GONE)
                    else text(R.id.nt_body, styledNoteBody(scene, density, baseWhite = false), WHITE_70)
                }
            }
            click(context, R.id.nt_btn, SceneButton(0, CardAction.EditNoteInline, context.getString(R.string.card_action_edit)))
        }
    }

    private fun styledNoteBody(scene: CardScene.Note, density: Float, baseWhite: Boolean): CharSequence {
        if (scene.spans.isEmpty()) return scene.body
        val src = scene.body
        val bulletStarts = scene.spans
            .filter { it.style == NoteSpanStyle.BULLET }
            .map { it.start }
            .filter { src.startsWith(NoteMarkdown.BULLET_PREFIX, startIndex = it) }
            .toSet()

        val indexMap = IntArray(src.length + 1)
        val cleaned = StringBuilder(src.length)
        var i = 0
        while (i < src.length) {
            if (i in bulletStarts) {
                indexMap[i] = cleaned.length
                indexMap[i + 1] = cleaned.length
                i += NoteMarkdown.BULLET_PREFIX.length
            } else {
                indexMap[i] = cleaned.length
                cleaned.append(src[i])
                i++
            }
        }
        indexMap[src.length] = cleaned.length

        val ssb = SpannableStringBuilder(cleaned.toString())
        val gapPx = (6 * density).roundToInt()
        for (span in scene.spans) {
            val s = indexMap[span.start]
            val e = indexMap[span.end]
            if (s >= e || e > ssb.length) continue
            when (span.style) {
                NoteSpanStyle.BOLD -> ssb.setSpan(StyleSpan(Typeface.BOLD), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                NoteSpanStyle.ITALIC -> ssb.setSpan(StyleSpan(Typeface.ITALIC), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                NoteSpanStyle.STRIKE -> ssb.setSpan(StrikethroughSpan(), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                NoteSpanStyle.CODE -> ssb.setSpan(TypefaceSpan("monospace"), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                NoteSpanStyle.HEADING -> {
                    ssb.setSpan(StyleSpan(Typeface.BOLD), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    ssb.setSpan(RelativeSizeSpan(1.16f), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (!baseWhite) {
                        ssb.setSpan(ForegroundColorSpan(0xFFFFFFFF.toInt()), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                NoteSpanStyle.BULLET -> ssb.setSpan(BulletSpan(gapPx), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return ssb
    }

    private val dockSlots = listOf(
        Triple(R.id.dock_slot_0, R.id.dock_icon_0, R.id.dock_label_0),
        Triple(R.id.dock_slot_1, R.id.dock_icon_1, R.id.dock_label_1),
        Triple(R.id.dock_slot_2, R.id.dock_icon_2, R.id.dock_label_2),
        Triple(R.id.dock_slot_3, R.id.dock_icon_3, R.id.dock_label_3)
    )

    private fun dock(context: Context, scene: CardScene.Dock): RemoteViews = rv(context, R.layout.card_dock).apply {
        val title = scene.title ?: if (scene.slots.isEmpty()) context.getString(R.string.card_dock_empty) else null
        title?.let {
            text(R.id.dock_title, it, WHITE)
            setViewVisibility(R.id.dock_title, View.VISIBLE)
        }
        scene.slots.take(dockSlots.size).forEachIndexed { i, button ->
            val (slot, icon, label) = dockSlots[i]
            val shortcutBmp = (button.action as? CardAction.AppShortcut)
                ?.iconId?.takeIf { it.isNotBlank() }?.let { ShortcutIcons.load(context, it) }
            val appPkg = when (val a = button.action) {
                is CardAction.LaunchApp -> a.packageName
                is CardAction.AppShortcut -> a.packageName.takeIf { it.isNotBlank() }
                else -> null
            }
            val bitmap = shortcutBmp
                ?: appPkg?.let { AppIconLoader.load(context, it) }
                ?: CardMedia.glyph(context, AndroidCardStrings.actionIconRes(button.action))
            setViewVisibility(slot, View.VISIBLE)
            setImageViewBitmap(icon, bitmap)
            text(label, button.label, WHITE_90)
            click(context, slot, button)
        }
    }

    private fun stats(context: Context, scene: CardScene.Stats, accent: Int): RemoteViews = rv(context, R.layout.card_rv_stats).apply {
        val size = (72 * context.resources.displayMetrics.density).toInt()
        scene.cells.forEach { cell ->
            val child = rv(context, R.layout.card_rv_stat_cell)
            child.setImageViewBitmap(R.id.sc_ring, ring(size, cell.fraction, accent, context.resources.displayMetrics.density))
            child.text(R.id.sc_value, cell.value, WHITE)
            val textSp = if (cell.value.length >= 6) 13f else 17f
            child.setTextViewTextSize(R.id.sc_value, TypedValue.COMPLEX_UNIT_SP, textSp)
            child.text(R.id.sc_caption, cell.caption, WHITE_70)
            addView(R.id.st_row, child)
        }
    }

    private fun counter(context: Context, scene: CardScene.Counter, accent: Int): RemoteViews = rv(context, R.layout.card_rv_counter).apply {
        text(R.id.cv_value, scene.value, WHITE)
        if (scene.value.length >= 7) {
            setTextViewTextSize(R.id.cv_value, TypedValue.COMPLEX_UNIT_SP, 32f)
        }
        scene.total?.let { text(R.id.cv_total, it, WHITE_60) } ?: setViewVisibility(R.id.cv_total, View.GONE)
        scene.caption?.let {
            text(R.id.cv_caption, it, WHITE_70)
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
        addSegments(context, R.id.cv_bar, scene.segments, scene.filled, accent)
    }

    private fun RemoteViews.addSegments(context: Context, viewId: Int, segments: Int, filled: Int, accent: Int) {
        repeat(segments) { i ->
            val seg = rv(context, R.layout.card_rv_segment)
            seg.tint(R.id.seg, "setBackgroundTintList", if (i < filled) accent else TRACK)
            addView(viewId, seg)
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
            text(R.id.tl_value, it, WHITE)
            setViewVisibility(R.id.tl_value, View.VISIBLE)
        }
        text(R.id.tl_caption, scene.caption, WHITE_70)
        listOf(R.id.tl_btn_0, R.id.tl_btn_1, R.id.tl_btn_2).forEachIndexed { i, id ->
            scene.buttons.getOrNull(i)?.let { b ->
                text(id, b.label, WHITE)
                click(context, id, b)
            }
        }
    }

    private fun maker(context: Context, scene: CardScene.Maker, accent: Int): RemoteViews = rv(context, R.layout.card_rv_maker).apply {
        val density = context.resources.displayMetrics.density
        val ringSize = (52 * density).toInt()
        scene.blocks.forEach { block ->
            val child = when (block) {
                is ResolvedMakerBlock.Value -> rv(context, R.layout.card_mk_value).apply {
                    text(R.id.mk_val, block.text, WHITE)
                    block.sub?.let {
                        text(R.id.mk_sub, it, WHITE_60)
                        setViewVisibility(R.id.mk_sub, View.VISIBLE)
                    }
                    block.caption?.let {
                        text(R.id.mk_val_cap, it, WHITE_70)
                        setViewVisibility(R.id.mk_val_cap, View.VISIBLE)
                    }
                }
                is ResolvedMakerBlock.Text -> rv(context, R.layout.card_mk_text).apply {
                    text(R.id.mk_txt, block.text, if (block.muted) WHITE_70 else WHITE_90)
                }
                is ResolvedMakerBlock.Bar -> rv(context, R.layout.card_mk_bar).apply {
                    addSegments(context, R.id.mk_bar, block.bar.segments, block.bar.filled, accent)
                }
                is ResolvedMakerBlock.Rings -> rv(context, R.layout.card_mk_rings).apply {
                    block.cells.forEach { cell ->
                        val c = rv(context, R.layout.card_rv_maker_cell)
                        c.setImageViewBitmap(R.id.mk_ring, ring(ringSize, cell.fraction, accent, density))
                        c.text(R.id.mk_ring_val, cell.value, WHITE)
                        if (cell.value.length >= 6) {
                            c.setTextViewTextSize(R.id.mk_ring_val, TypedValue.COMPLEX_UNIT_SP, 10f)
                        }
                        c.text(R.id.mk_ring_cap, cell.caption, WHITE_70)
                        addView(R.id.mk_rings, c)
                    }
                }
                is ResolvedMakerBlock.Buttons -> rv(context, R.layout.card_mk_buttons).apply {
                    val ids = listOf(R.id.mk_btn_0, R.id.mk_btn_1, R.id.mk_btn_2, R.id.mk_btn_3)
                    ids.forEachIndexed { i, id ->
                        block.buttons.getOrNull(i)?.let { b ->
                            setViewVisibility(id, View.VISIBLE)
                            text(id, b.label, WHITE)
                            click(context, id, b)
                        }
                    }
                }
            }
            addView(R.id.mk_root, child)
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
