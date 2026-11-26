package dev.atoll.island.service.card

import android.content.Context
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import dev.atoll.island.data.model.CardBackground
import dev.atoll.island.data.model.ExpandedCardConfig
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

    fun backgroundHex(
        context: Context,
        background: CardBackground,
        customBgColor: Int = 0
    ): String? = when (background) {
        CardBackground.DEFAULT -> null
        CardBackground.SYSTEM -> {
            val rgb = context.getColor(android.R.color.system_accent1_800) and 0x00FFFFFF
            "88%06X".format(Locale.ROOT, rgb)
        }
        CardBackground.CUSTOM -> "9E%06X".format(Locale.ROOT, customBgColor and 0x00FFFFFF)
        else -> background.colorHex
    }

    // colore più saturo per la base del gradiente quando si sfuma dal nero
    fun gradientColorRgb(
        context: Context,
        background: CardBackground,
        customBgColor: Int
    ): Int? = when (background) {
        CardBackground.DEFAULT, CardBackground.PITCH -> null
        CardBackground.SYSTEM -> context.getColor(android.R.color.system_accent1_600) and 0x00FFFFFF
        CardBackground.OCEAN -> 0x1A497F
        CardBackground.FOREST -> 0x1A5940
        CardBackground.PLUM -> 0x4E2978
        CardBackground.AMBER -> 0x6B421A
        CardBackground.CUSTOM -> customBgColor and 0x00FFFFFF
    }

    internal fun effectiveBackground(card: ExpandedCardConfig, scene: CardScene?): CardBackground = when {
        card.background != CardBackground.DEFAULT -> card.background
        scene is CardScene.Maker && scene.background != null -> scene.background
        else -> card.background
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
        val effBg = effectiveBackground(card, scene)
        val gradientRgb = if (card.backgroundGradient) {
            if (card.background == CardBackground.DEFAULT && scene is CardScene.Maker && scene.customBgHex != null) {
                scene.customBgHex.toLongOrNull(16)?.toInt()?.and(0x00FFFFFF)
            } else {
                gradientColorRgb(context, effBg, card.customBgColor)
            }
        } else null
        val bgHex = when {
            card.background != CardBackground.DEFAULT -> backgroundHex(context, card.background, card.customBgColor)
            scene is CardScene.Maker && scene.customBgHex != null -> scene.customBgHex
            scene is CardScene.Maker && scene.background != null -> backgroundHex(context, scene.background, card.customBgColor)
            else -> backgroundHex(context, card.background, card.customBgColor)
        }
        val extras = Bundle()
        extras.putParcelable(ColorOsCardJson.KEY_RV, views)
        val tap = if (ColorOsCardJson.tappable(card)) CardIntents.tap(context, card) else null
        tap?.let { extras.putParcelable(ColorOsCardJson.KEY_TAP, it) }
        val bgIconKey = if (gradientRgb != null) {
            extras.putParcelable(ColorOsCardJson.KEY_BG_ICON, CardBackgroundRenderer.gradientIcon(gradientRgb))
            ColorOsCardJson.KEY_BG_ICON
        } else null
        extras.putString(
            EXTRA_CARD,
            ColorOsCardJson.card(tappable = tap != null, backgroundArgbHex = bgHex, backgroundIconKey = bgIconKey)
        )
        extras.putString(EXTRA_CAPSULE, ColorOsCardJson.capsule(shortCriticalText))
        builder.addExtras(extras)
    }
}
