package dev.atoll.island.service.card

import android.content.Context
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import dev.atoll.island.data.model.CardBackground
import dev.atoll.island.data.model.ExpandedCardMode
import dev.atoll.island.data.model.LiveAlertConfig
import dev.atoll.island.domain.card.CardScene
import dev.atoll.island.domain.card.CardSceneBuilder
import dev.atoll.island.domain.card.CardText
import dev.atoll.island.domain.card.ColorOsCardJson
import java.util.Locale

// scheda di ColorOS (extra oplus.livealert.card/capsule) con una vista disegnata da Atoll; altrove viene ignorata
object ColorOsCardDecorator {
    const val EXTRA_CARD = "oplus.livealert.card"
    const val EXTRA_CAPSULE = "oplus.livealert.capsule"

    fun backgroundHex(context: Context, background: CardBackground): String? = when (background) {
        CardBackground.DEFAULT -> null
        CardBackground.SYSTEM -> {
            val rgb = context.getColor(android.R.color.system_accent1_800) and 0x00FFFFFF
            "88%06X".format(Locale.ROOT, rgb)
        }
        else -> background.colorHex
    }

    fun scene(context: Context, config: LiveAlertConfig): CardScene? {
        val env = CardNotificationDecorator.environment(context, CardText.BLANK, CardText.BLANK)
        val strings = AndroidCardStrings(context)
        return CardSceneBuilder.build(config.card, env, strings, strings, CardMedia.hasPhoto(context))
    }

    // anche per l'anteprima nell'app: è la stessa vista della scheda
    fun views(context: Context, config: LiveAlertConfig): RemoteViews? =
        scene(context, config)?.let { CardViews.build(context, config.card, it) }

    fun apply(context: Context, builder: NotificationCompat.Builder, config: LiveAlertConfig, shortCriticalText: String) {
        val card = config.card
        if (!card.useColorOsLayouts || card.mode == ExpandedCardMode.MINIMAL) return
        val scene = scene(context, config) ?: return
        val views = CardViews.build(context, card, scene)
        val bgHex = when {
            card.background != CardBackground.DEFAULT -> backgroundHex(context, card.background)
            scene is CardScene.Maker && scene.customBgHex != null -> scene.customBgHex
            scene is CardScene.Maker && scene.background != null -> backgroundHex(context, scene.background)
            else -> backgroundHex(context, card.background)
        }
        val extras = Bundle()
        extras.putParcelable(ColorOsCardJson.KEY_RV, views)
        val tap = if (ColorOsCardJson.tappable(card)) CardIntents.tap(context, card) else null
        tap?.let { extras.putParcelable(ColorOsCardJson.KEY_TAP, it) }
        extras.putString(EXTRA_CARD, ColorOsCardJson.card(tappable = tap != null, backgroundArgbHex = bgHex))
        extras.putString(EXTRA_CAPSULE, ColorOsCardJson.capsule(shortCriticalText))
        builder.addExtras(extras)
    }
}
