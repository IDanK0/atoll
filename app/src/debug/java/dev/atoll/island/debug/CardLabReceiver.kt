package dev.atoll.island.debug

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Base64
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import dev.atoll.island.R
import dev.atoll.island.service.card.AppIconLoader
import dev.atoll.island.service.AtollLiveAlertService
import dev.atoll.island.service.LiveAlertNotificationBuilder
import dev.atoll.island.ui.MainActivity

// adb shell am broadcast -n dev.atoll.island.debug/dev.atoll.island.debug.CardLabReceiver --es card <base64> [--es capsule <base64>] [--ez clear true]
class CardLabReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("clear", false)) {
            LiveAlertNotificationBuilder.debugHook = null
        } else {
            val card = intent.getStringExtra("card")?.let(::decode)
            val capsule = intent.getStringExtra("capsule")?.let(::decode)
            val w = intent.getIntExtra("w", 1200)
            val h = intent.getIntExtra("h", 800)
            LiveAlertNotificationBuilder.debugHook = { ctx, builder ->
                builder.addExtras(Bundle().apply {
                    card?.let { putString("oplus.livealert.card", it) }
                    capsule?.let { putString("oplus.livealert.capsule", it) }
                    val bitmap = photo(w, h)
                    putParcelable("atoll_photo", Icon.createWithBitmap(bitmap))
                    val dock = RemoteViews(ctx.packageName, R.layout.card_dock)
                    val slots = listOf(
                        Triple(R.id.dock_slot_0, R.id.dock_icon_0, R.id.dock_label_0),
                        Triple(R.id.dock_slot_1, R.id.dock_icon_1, R.id.dock_label_1),
                        Triple(R.id.dock_slot_2, R.id.dock_icon_2, R.id.dock_label_2)
                    )
                    listOf("com.android.settings", "com.oneplus.camera", "com.whatsapp").forEachIndexed { i, pkg ->
                        val pm = ctx.packageManager
                        val icon = AppIconLoader.load(ctx, pkg) ?: return@forEachIndexed
                        android.util.Log.i("AtollLab", "pack=${AppIconLoader.activePackName(ctx)} $pkg ${icon.width}")
                        putParcelable("app_$i", Icon.createWithBitmap(icon))
                        val (slot, image, label) = slots[i]
                        dock.setViewVisibility(slot, android.view.View.VISIBLE)
                        dock.setImageViewBitmap(image, icon)
                        dock.setTextViewText(label, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)))
                        pm.getLaunchIntentForPackage(pkg)?.let { launch ->
                            dock.setOnClickPendingIntent(
                                slot,
                                PendingIntent.getActivity(ctx, 910 + i, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                            )
                        }
                    }
                    putParcelable("atoll_dock", dock)
                    putParcelable("atoll_rv", RemoteViews(ctx.packageName, R.layout.card_photo).apply {
                        setImageViewBitmap(R.id.card_photo, bitmap)
                        setOnClickPendingIntent(
                            R.id.card_photo,
                            PendingIntent.getActivity(
                                ctx, 901, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                            )
                        )
                    })
                    putParcelable(
                        "atoll_open",
                        PendingIntent.getActivity(
                            ctx, 900, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    )
                })
            }
        }
        AtollLiveAlertService.refreshCard(context)
    }

    private fun decode(value: String) = String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)

    // un'immagine riconoscibile: sfumatura con una banda e un cerchio, per vedere ritagli e proporzioni
    private fun photo(w: Int, h: Int) = createBitmap(w, h).also { bitmap ->
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), Color.rgb(29, 79, 110), Color.rgb(127, 198, 207), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
        paint.color = Color.rgb(240, 226, 196)
        canvas.drawCircle(w * 0.5f, h * 0.5f, minOf(w, h) * 0.3f, paint)
        paint.color = Color.BLACK
        canvas.drawRect(0f, 0f, w.toFloat(), h * 0.06f, paint)
        canvas.drawRect(0f, h * 0.94f, w.toFloat(), h.toFloat(), paint)
    }
}
