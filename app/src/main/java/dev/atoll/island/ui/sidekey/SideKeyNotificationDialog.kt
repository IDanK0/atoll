package dev.atoll.island.ui.sidekey

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.atoll.island.R
import dev.atoll.island.data.model.SideKeyAction
import dev.atoll.island.data.model.SideKeyConfig
import dev.atoll.island.service.sidekey.SideKeyActionExecutor
import dev.atoll.island.ui.card.AppPickerDialog

private data class NotifIconOption(val key: String, val labelRes: Int, val icon: ImageVector)

private val NOTIF_ICON_OPTIONS = listOf(
    NotifIconOption(SideKeyConfig.NOTIF_ICON_CHAT, R.string.sidekey_notif_icon_chat, Icons.AutoMirrored.Rounded.Chat),
    NotifIconOption(SideKeyConfig.NOTIF_ICON_CALL, R.string.sidekey_notif_icon_call, Icons.Rounded.Call),
    NotifIconOption(SideKeyConfig.NOTIF_ICON_MAIL, R.string.sidekey_notif_icon_mail, Icons.Rounded.Mail),
    NotifIconOption(SideKeyConfig.NOTIF_ICON_EVENT, R.string.sidekey_notif_icon_event, Icons.Rounded.Event),
    NotifIconOption(SideKeyConfig.NOTIF_ICON_ALERT, R.string.sidekey_notif_icon_alert, Icons.Rounded.Notifications)
)

private val NOTIF_DELAY_OPTIONS = listOf(0, 3, 5, 10, 30)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SideKeyNotificationDialog(
    initial: SideKeyAction.CustomNotification?,
    onDismiss: () -> Unit,
    onConfirm: (SideKeyAction.CustomNotification) -> Unit
) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var message by rememberSaveable { mutableStateOf(initial?.message.orEmpty()) }
    var appPackage by rememberSaveable { mutableStateOf(initial?.appPackage.orEmpty()) }
    var appLabel by rememberSaveable { mutableStateOf(initial?.appLabel.orEmpty()) }
    var iconKey by rememberSaveable { mutableStateOf(initial?.iconKey ?: SideKeyConfig.NOTIF_ICON_CHAT) }
    var delaySeconds by rememberSaveable { mutableIntStateOf(initial?.delaySeconds ?: 0) }
    var headsUp by rememberSaveable { mutableStateOf(initial?.headsUp ?: true) }
    var showAppPicker by rememberSaveable { mutableStateOf(false) }

    val built = SideKeyAction.CustomNotification(
        title = title.trim(),
        message = message.trim(),
        appPackage = appPackage,
        appLabel = appLabel,
        iconKey = iconKey,
        delaySeconds = delaySeconds,
        headsUp = headsUp
    )
    val canSave = built.title.isNotEmpty() || built.message.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sidekey_action_custom_notif)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAppPicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.sidekey_notif_app_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                appLabel.ifBlank { stringResource(R.string.sidekey_notif_app_none) },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (appPackage.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    appPackage = ""
                                    appLabel = ""
                                }
                            ) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.action_close))
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.sidekey_notif_icon_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NOTIF_ICON_OPTIONS.forEach { option ->
                            FilterChip(
                                selected = iconKey == option.key,
                                onClick = { iconKey = option.key },
                                leadingIcon = {
                                    Icon(option.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text(stringResource(option.labelRes)) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(64) },
                    label = { Text(stringResource(R.string.sidekey_notif_title_field)) },
                    placeholder = { Text(stringResource(R.string.sidekey_notif_title_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(240) },
                    label = { Text(stringResource(R.string.sidekey_notif_body_field)) },
                    placeholder = { Text(stringResource(R.string.sidekey_notif_body_hint)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.sidekey_notif_delay_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NOTIF_DELAY_OPTIONS.forEach { secs ->
                            FilterChip(
                                selected = delaySeconds == secs,
                                onClick = { delaySeconds = secs },
                                label = {
                                    Text(
                                        if (secs == 0) stringResource(R.string.sidekey_notif_delay_now)
                                        else stringResource(R.string.sidekey_notif_delay_secs_fmt, secs)
                                    )
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.sidekey_notif_headsup_label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = headsUp, onCheckedChange = { headsUp = it })
                }

                if (canSave) {
                    OutlinedButton(
                        onClick = {
                            if (!SideKeyActionExecutor.postCustomNotificationNow(context, built)) {
                                Toast.makeText(context, R.string.sidekey_notif_blocked, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.sidekey_notif_preview_cta))
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = canSave, onClick = { onConfirm(built) }) {
                Text(stringResource(R.string.card_maker_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )

    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = { showAppPicker = false },
            onPick = { app ->
                appPackage = app.packageName
                appLabel = app.label
                showAppPicker = false
            }
        )
    }
}
