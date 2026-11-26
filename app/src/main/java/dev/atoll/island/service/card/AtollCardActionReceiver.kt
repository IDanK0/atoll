package dev.atoll.island.service.card

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.core.app.RemoteInput
import dev.atoll.island.R
import dev.atoll.island.data.model.CardAction
import dev.atoll.island.data.prefs.LiveAlertPreferencesRepository
import dev.atoll.island.domain.card.CardActionReducer
import dev.atoll.island.domain.card.CardCodec
import dev.atoll.island.service.AtollLiveAlertService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// pulsanti della scheda che non aprono niente: aggiornano DataStore e la notifica sul posto
class AtollCardActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CARD) return
        val action = CardCodec.decodeActions(intent.getStringExtra(EXTRA_ACTION)).firstOrNull() ?: return
        val app = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                handle(app, action, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, action: CardAction, intent: Intent) {
        val repo = LiveAlertPreferencesRepository.getInstance(context)
        if (CardActionReducer.changesState(action)) {
            repo.updateCard { CardActionReducer.reduce(it, action, Random.Default) ?: it }
            return
        }
        when (action) {
            CardAction.EditNoteInline -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_NOTE_TEXT)?.toString()
                // anche se il testo non cambia va ripubblicata, sennò resta la rotella di invio
                if (text != null) repo.updateCard { it.copy(noteBody = text.take(MAX_NOTE_CHARS)) }
                AtollLiveAlertService.refreshCard(context)
            }
            CardAction.ToggleFlashlight -> {
                if (!TorchController.toggle(context)) confirm(context, context.getString(R.string.card_confirm_torch_unavailable))
            }
            CardAction.MediaPlayPause -> mediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            CardAction.MediaNext -> mediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
            is CardAction.CopySnippet -> {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText(action.label, action.text))
                confirm(context, context.getString(R.string.card_confirm_copied, action.label.ifBlank { action.text.take(24) }))
            }
            CardAction.RefreshStats -> AtollLiveAlertService.refreshCard(context)
            else -> Unit
        }
    }

    private suspend fun confirm(context: Context, message: String) {
        CardTransientState.show(message)
        AtollLiveAlertService.refreshCard(context)
        delay(CardTransientState.SHOW_MS + 100)
        AtollLiveAlertService.refreshCard(context)
    }

    private fun mediaKey(context: Context, code: Int) {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        val time = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, code, 0))
    }

    companion object {
        const val ACTION_CARD = "dev.atoll.island.action.CARD"
        const val EXTRA_ACTION = "card_action"
        const val KEY_NOTE_TEXT = "note_text"
        const val MAX_NOTE_CHARS = 2_000
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
