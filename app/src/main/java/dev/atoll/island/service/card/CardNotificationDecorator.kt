package dev.atoll.island.service.card

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import dev.atoll.island.R
import dev.atoll.island.data.model.CardAction
import dev.atoll.island.data.model.ExpandedCardMode
import dev.atoll.island.data.model.LiveAlertConfig
import dev.atoll.island.domain.card.CardContent
import dev.atoll.island.domain.card.CardEnvironment
import dev.atoll.island.domain.card.CardStyle
import dev.atoll.island.domain.card.ExpandedCardContentResolver
import java.time.LocalDate
import java.time.LocalTime

object CardNotificationDecorator {

    fun environment(context: Context, minimalTitle: String, minimalText: String): CardEnvironment {
        val now = System.currentTimeMillis()
        val date = LocalDate.now()
        val time = LocalTime.now()
        val strings = AndroidCardStrings(context)
        return CardEnvironment(
            minuteOfDay = time.hour * 60 + time.minute,
            apiLevel = Build.VERSION.SDK_INT,
            stats = DeviceStatsReader.read(context),
            dateText = strings.date(now),
            timeText = strings.clock(now),
            confirmation = CardTransientState.current(),
            minimalTitle = minimalTitle,
            minimalText = minimalText,
            dayOfWeekIndex = date.dayOfWeek.value,
            dayOfMonth = date.dayOfMonth,
            daysInMonth = date.lengthOfMonth(),
            dayOfYear = date.dayOfYear,
            daysInYear = date.lengthOfYear(),
            monthOfYear = date.monthValue
        )
    }

    fun resolve(context: Context, config: LiveAlertConfig, minimalTitle: String, minimalText: String): CardContent =
        ExpandedCardContentResolver.resolve(
            config.card,
            environment(context, minimalTitle, minimalText),
            AndroidCardStrings(context)
        )

    fun styleName(style: CardStyle): String = when (style) {
        CardStyle.STANDARD -> "Standard"
        CardStyle.BIG_TEXT -> "BigTextStyle"
        CardStyle.PROGRESS -> "ProgressStyle"
        CardStyle.METRIC -> "MetricStyle"
    }

    // MINIMAL resta com'era: il builder ci mette solo titolo e testo vuoti
    fun apply(context: Context, builder: NotificationCompat.Builder, config: LiveAlertConfig, content: CardContent) {
        builder.setContentIntent(CardIntents.tap(context, config.card))
        if (config.card.mode == ExpandedCardMode.MINIMAL) return

        builder.setContentTitle(content.title)
        builder.setContentText(content.body)
        builder.setSubText(content.subText)
        LargeIconRenderer.render(context, config.card.largeIcon)?.let(builder::setLargeIcon)

        when (content.style) {
            CardStyle.STANDARD -> Unit
            CardStyle.BIG_TEXT -> builder.setStyle(
                NotificationCompat.BigTextStyle().setBigContentTitle(content.title).bigText(content.body.orEmpty())
            )
            CardStyle.PROGRESS -> applyProgress(context, builder, content)
            // il resolver sceglie METRIC solo da API 37, ma il controllo resta qui
            CardStyle.METRIC -> if (Build.VERSION.SDK_INT >= 37) {
                applyMetrics(builder, content)
            } else {
                builder.setStyle(
                    NotificationCompat.BigTextStyle().setBigContentTitle(content.title).bigText(content.body.orEmpty())
                )
            }
        }

        content.actions.forEachIndexed { slot, resolved ->
            val action = resolved.action
            val pending = CardIntents.forAction(context, action, slot) ?: return@forEachIndexed
            val icon = IconCompat.createWithResource(context, AndroidCardStrings.actionIconRes(action))
            val compat = NotificationCompat.Action.Builder(icon, resolved.label, pending)
            if (action == CardAction.EditNoteInline) {
                compat.addRemoteInput(
                    RemoteInput.Builder(AtollCardActionReceiver.KEY_NOTE_TEXT)
                        .setLabel(context.getString(R.string.card_note_reply_label))
                        .build()
                )
                compat.setAllowGeneratedReplies(false)
            }
            builder.addAction(compat.build())
        }
    }

    private fun applyProgress(context: Context, builder: NotificationCompat.Builder, content: CardContent) {
        val progress = content.progress ?: return
        val color = CardViews.accent(context, progress.color)
        if (Build.VERSION.SDK_INT >= 36) {
            val style = NotificationCompat.ProgressStyle().setStyledByProgress(true)
            progress.segments.forEach { length ->
                style.addProgressSegment(NotificationCompat.ProgressStyle.Segment(length).setColor(color))
            }
            style.setProgress(progress.value)
            builder.setStyle(style)
        } else {
            builder.setProgress(progress.max, progress.value, false)
        }
    }

    @RequiresApi(37)
    private fun applyMetrics(builder: NotificationCompat.Builder, content: CardContent) {
        val style = NotificationCompat.MetricStyle()
        content.metrics.forEach { metric ->
            // ColorOS scrive l'unità tra parentesi dopo l'etichetta e la taglia: va nel valore
            val value = NotificationCompat.Metric.FixedText(metric.valueText)
            style.addMetric(NotificationCompat.Metric(value, metric.label))
        }
        builder.setStyle(style)
    }
}
