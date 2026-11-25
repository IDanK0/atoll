package dev.atoll.island.service.card

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dev.atoll.island.data.model.CardAction
import dev.atoll.island.data.model.ExpandedCardConfig
import dev.atoll.island.data.model.TapAction
import dev.atoll.island.domain.card.ActionSlots
import dev.atoll.island.domain.card.CardCodec
import dev.atoll.island.ui.MainActivity
import dev.atoll.island.ui.note.NoteEditActivity

// niente trampolini: le activity si aprono direttamente, il resto passa dal receiver
object CardIntents {
    private const val IMMUTABLE = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    private const val REQUEST_TAP = 100
    private const val REQUEST_BROADCAST = 200
    private const val REQUEST_ACTIVITY = 300
    private const val REQUEST_NOTE_EDITOR = 400
    private const val REQUEST_PHOTO = 500

    // inCard: nella scheda di ColorOS non c'è la risposta inline, quindi "Modifica" apre l'editor
    fun forAction(context: Context, action: CardAction, slot: Int, inCard: Boolean = false): PendingIntent? {
        if (action == CardAction.EditNoteInline && inCard) {
            return PendingIntent.getActivity(context, REQUEST_NOTE_EDITOR, NoteEditActivity.intent(context), IMMUTABLE)
        }
        if (action.opensUi) {
            val intent = when (action) {
                is CardAction.LaunchApp -> context.packageManager.getLaunchIntentForPackage(action.packageName)
                is CardAction.OpenUrl -> ActionSlots.normalizeUrl(action.url)?.let { Intent(Intent.ACTION_VIEW, it.toUri()) }
                else -> null
            }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return null
            return PendingIntent.getActivity(context, REQUEST_ACTIVITY + slot, intent, IMMUTABLE)
        }
        val encoded = CardCodec.encodeActions(listOf(action))
        val intent = Intent(context, AtollCardActionReceiver::class.java)
            .setAction(AtollCardActionReceiver.ACTION_CARD)
            .setData("atoll://card/$slot/${encoded.substringBefore('\t')}".toUri())
            .putExtra(AtollCardActionReceiver.EXTRA_ACTION, encoded)
        // la risposta inline deve poter scrivere nell'intent
        val mutability = if (action == CardAction.EditNoteInline) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_BROADCAST + slot, intent, mutability or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun tap(context: Context, card: ExpandedCardConfig): PendingIntent? {
        val target = when (card.tapAction) {
            TapAction.NONE -> return null
            TapAction.LAUNCH_APP -> context.packageManager.getLaunchIntentForPackage(card.tapPackage)
            TapAction.OPEN_ATOLL -> null
        } ?: Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, REQUEST_TAP, target, IMMUTABLE)
    }

    // apre la foto della nota nel visualizzatore di sistema
    fun viewPhoto(context: Context): PendingIntent? {
        val uri = CardMedia.shareUri(context) ?: return null
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "image/jpeg")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, REQUEST_PHOTO, intent, IMMUTABLE)
    }
}
