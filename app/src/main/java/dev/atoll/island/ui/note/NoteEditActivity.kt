package dev.atoll.island.ui.note

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.atoll.island.R
import dev.atoll.island.data.prefs.LiveAlertPreferencesRepository
import dev.atoll.island.service.card.AtollCardActionReceiver
import dev.atoll.island.ui.theme.AtollTheme
import kotlinx.coroutines.launch

// si apre dal pulsante "Modifica" della scheda: solo titolo e testo, sopra quello che stavi facendo
class NoteEditActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = LiveAlertPreferencesRepository.getInstance(applicationContext)
        val card = repository.config.value.card
        setContent {
            AtollTheme {
                var title by rememberSaveable { mutableStateOf(card.noteTitle) }
                var body by rememberSaveable { mutableStateOf(card.noteBody) }
                AlertDialog(
                    onDismissRequest = ::finish,
                    title = { Text(stringResource(R.string.note_edit_title)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it.take(80) },
                                label = { Text(stringResource(R.string.card_field_title)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = body,
                                onValueChange = { body = it.take(AtollCardActionReceiver.MAX_NOTE_CHARS) },
                                label = { Text(stringResource(R.string.card_field_body)) },
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            lifecycleScope.launch {
                                repository.updateCard { it.copy(noteTitle = title.trim(), noteBody = body.trim()) }
                                finish()
                            }
                        }) { Text(stringResource(R.string.note_edit_save)) }
                    },
                    dismissButton = {
                        TextButton(onClick = ::finish) { Text(stringResource(R.string.action_cancel)) }
                    }
                )
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, NoteEditActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
