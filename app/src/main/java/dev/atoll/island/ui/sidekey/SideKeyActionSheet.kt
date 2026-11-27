package dev.atoll.island.ui.sidekey

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.Shortcut
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.atoll.island.R
import dev.atoll.island.data.model.CardAction
import dev.atoll.island.data.model.SideKeyAction
import dev.atoll.island.data.model.SideKeyGesture
import dev.atoll.island.data.model.SideKeyRingerMode
import dev.atoll.island.domain.card.ActionSlots
import dev.atoll.island.domain.sidekey.SideKeyCodec
import dev.atoll.island.ui.card.AppPickerDialog
import dev.atoll.island.ui.card.ClockTimerDialog
import dev.atoll.island.ui.card.CopyDialog
import dev.atoll.island.ui.card.DialDialog
import dev.atoll.island.ui.card.ShortcutPickerDialog

private enum class PickerSubDialog {
    NONE, APP, SHORTCUT, DIAL, TIMER, COPY, NOTIFICATION, URL, INTENT, MACRO
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SideKeyActionPickerSheet(
    gesture: SideKeyGesture,
    currentAction: SideKeyAction,
    allowMacro: Boolean = true,
    titleOverride: String? = null,
    onDismiss: () -> Unit,
    onSelect: (SideKeyAction) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var subDialog by rememberSaveable { mutableStateOf(PickerSubDialog.NONE) }
    val selectedRingerMode = (currentAction as? SideKeyAction.RingerMode)?.mode ?: SideKeyRingerMode.CYCLE
    var ringerToggleBack by rememberSaveable {
        mutableStateOf((currentAction as? SideKeyAction.RingerMode)?.toggleBack ?: false)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                titleOverride ?: stringResource(gestureTitleRes(gesture)),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            if (currentAction.isAssigned) {
                ActionSheetRow(
                    icon = Icons.Rounded.RemoveCircleOutline,
                    label = stringResource(R.string.sidekey_action_none),
                    onClick = { onSelect(SideKeyAction.None) }
                )
                HorizontalDivider()
            }

            if (allowMacro) {
                CategoryHeader(stringResource(R.string.sidekey_cat_macros))
                ActionSheetRow(
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    label = stringResource(
                        if (currentAction is SideKeyAction.Macro) R.string.sidekey_macro_edit
                        else R.string.sidekey_action_macro
                    ),
                    onClick = { subDialog = PickerSubDialog.MACRO }
                )
                HorizontalDivider()
            }

            CategoryHeader(stringResource(R.string.sidekey_cat_apps))
            ActionSheetRow(Icons.Rounded.Apps, stringResource(R.string.sidekey_action_launch_app)) {
                subDialog = PickerSubDialog.APP
            }
            ActionSheetRow(Icons.AutoMirrored.Rounded.Shortcut, stringResource(R.string.sidekey_action_shortcut)) {
                subDialog = PickerSubDialog.SHORTCUT
            }
            ActionSheetRow(Icons.Rounded.Call, stringResource(R.string.card_action_dial)) {
                subDialog = PickerSubDialog.DIAL
            }
            ActionSheetRow(Icons.Rounded.Timer, stringResource(R.string.card_action_timer)) {
                subDialog = PickerSubDialog.TIMER
            }
            ActionSheetRow(Icons.Rounded.ContentCopy, stringResource(R.string.card_action_copy)) {
                subDialog = PickerSubDialog.COPY
            }
            ActionSheetRow(Icons.Rounded.Notifications, stringResource(R.string.sidekey_action_custom_notif)) {
                subDialog = PickerSubDialog.NOTIFICATION
            }

            HorizontalDivider()
            CategoryHeader(stringResource(R.string.sidekey_cat_controls))
            ActionSheetRow(Icons.Rounded.FlashlightOn, stringResource(R.string.sidekey_action_flashlight)) {
                onSelect(SideKeyAction.ToggleFlashlight)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ActionSheetRow(Icons.AutoMirrored.Rounded.VolumeUp, stringResource(R.string.sidekey_action_ringer)) {
                    val toggle = if (selectedRingerMode == SideKeyRingerMode.CYCLE) false else ringerToggleBack
                    onSelect(SideKeyAction.RingerMode(selectedRingerMode, toggle))
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 36.dp, bottom = 4.dp)
                ) {
                    SideKeyRingerMode.entries.forEach { mode ->
                        FilterChip(
                            selected = currentAction is SideKeyAction.RingerMode && currentAction.mode == mode,
                            onClick = {
                                val toggle = if (mode == SideKeyRingerMode.CYCLE) false else ringerToggleBack
                                onSelect(SideKeyAction.RingerMode(mode, toggle))
                            },
                            label = { Text(stringResource(ringerModeRes(mode))) }
                        )
                    }
                }
                FilterChip(
                    selected = ringerToggleBack,
                    onClick = { ringerToggleBack = !ringerToggleBack },
                    label = { Text(stringResource(R.string.sidekey_ringer_toggle_back)) },
                    modifier = Modifier.padding(start = 36.dp)
                )
                Text(
                    stringResource(R.string.sidekey_silent_dnd_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 36.dp, bottom = 4.dp)
                )
            }
            ActionSheetRow(Icons.Rounded.DoNotDisturbOn, stringResource(R.string.sidekey_action_dnd)) {
                onSelect(SideKeyAction.ToggleDnd)
            }
            ActionSheetRow(Icons.Rounded.PhotoCamera, stringResource(R.string.sidekey_action_camera)) {
                onSelect(SideKeyAction.LaunchCamera)
            }
            ActionSheetRow(Icons.Rounded.Videocam, stringResource(R.string.sidekey_action_video_camera)) {
                onSelect(SideKeyAction.LaunchVideoCamera)
            }
            ActionSheetRow(Icons.Rounded.Mic, stringResource(R.string.sidekey_action_voice_recorder)) {
                onSelect(SideKeyAction.LaunchVoiceRecorder)
            }
            ActionSheetRow(Icons.Rounded.AutoAwesome, stringResource(R.string.sidekey_action_assistant)) {
                onSelect(SideKeyAction.LaunchAssistant)
            }
            ActionSheetRow(Icons.Rounded.Home, stringResource(R.string.sidekey_action_home)) {
                onSelect(SideKeyAction.GoHome)
            }

            HorizontalDivider()
            CategoryHeader(stringResource(R.string.sidekey_cat_media))
            ActionSheetRow(Icons.Rounded.PlayArrow, stringResource(R.string.sidekey_action_media_play_pause)) {
                onSelect(SideKeyAction.MediaPlayPause)
            }
            ActionSheetRow(Icons.Rounded.SkipNext, stringResource(R.string.sidekey_action_media_next)) {
                onSelect(SideKeyAction.MediaNext)
            }
            ActionSheetRow(Icons.Rounded.SkipPrevious, stringResource(R.string.sidekey_action_media_previous)) {
                onSelect(SideKeyAction.MediaPrevious)
            }
            ActionSheetRow(Icons.AutoMirrored.Rounded.VolumeUp, stringResource(R.string.sidekey_action_volume_up)) {
                onSelect(SideKeyAction.VolumeUp)
            }
            ActionSheetRow(Icons.AutoMirrored.Rounded.VolumeDown, stringResource(R.string.sidekey_action_volume_down)) {
                onSelect(SideKeyAction.VolumeDown)
            }
            ActionSheetRow(Icons.AutoMirrored.Rounded.VolumeOff, stringResource(R.string.sidekey_action_mute_media)) {
                onSelect(SideKeyAction.ToggleMute)
            }
            ActionSheetRow(Icons.AutoMirrored.Rounded.VolumeUp, stringResource(R.string.card_action_volume)) {
                onSelect(SideKeyAction.VolumePanel)
            }

            HorizontalDivider()
            CategoryHeader(stringResource(R.string.sidekey_cat_panels))
            ActionSheetRow(Icons.Rounded.Notifications, stringResource(R.string.sidekey_action_notifications)) {
                onSelect(SideKeyAction.ExpandNotifications)
            }
            ActionSheetRow(Icons.Rounded.Tune, stringResource(R.string.sidekey_action_quick_settings)) {
                onSelect(SideKeyAction.ExpandQuickSettings)
            }
            ActionSheetRow(Icons.Rounded.Wifi, stringResource(R.string.card_action_wifi)) {
                onSelect(SideKeyAction.WifiPanel)
            }
            ActionSheetRow(Icons.Rounded.Bluetooth, stringResource(R.string.sidekey_action_bluetooth)) {
                onSelect(SideKeyAction.BluetoothSettings)
            }
            ActionSheetRow(Icons.Rounded.Public, stringResource(R.string.card_action_internet)) {
                onSelect(SideKeyAction.InternetPanel)
            }

            HorizontalDivider()
            CategoryHeader(stringResource(R.string.sidekey_cat_atoll))
            ActionSheetRow(Icons.Rounded.Smartphone, stringResource(R.string.sidekey_action_toggle_capsule)) {
                onSelect(SideKeyAction.ToggleCapsule)
            }
            ActionSheetRow(Icons.Rounded.Smartphone, stringResource(R.string.sidekey_action_cycle_preset)) {
                onSelect(SideKeyAction.CyclePreset)
            }
            ActionSheetRow(Icons.Rounded.Tune, stringResource(R.string.sidekey_action_open_calibration)) {
                onSelect(SideKeyAction.OpenCalibration)
            }
            ActionSheetRow(Icons.Rounded.Add, stringResource(R.string.sidekey_action_counter_plus)) {
                onSelect(SideKeyAction.CounterIncrement)
            }
            ActionSheetRow(Icons.Rounded.Remove, stringResource(R.string.sidekey_action_counter_minus)) {
                onSelect(SideKeyAction.CounterDecrement)
            }
            ActionSheetRow(Icons.Rounded.EditNote, stringResource(R.string.sidekey_action_note_editor)) {
                onSelect(SideKeyAction.OpenNoteEditor)
            }

            HorizontalDivider()
            CategoryHeader(stringResource(R.string.sidekey_cat_links))
            ActionSheetRow(Icons.Rounded.Link, stringResource(R.string.sidekey_action_open_url)) {
                subDialog = PickerSubDialog.URL
            }
            ActionSheetRow(Icons.Rounded.Terminal, stringResource(R.string.sidekey_action_custom_intent)) {
                subDialog = PickerSubDialog.INTENT
            }
        }
    }

    when (subDialog) {
        PickerSubDialog.NONE -> Unit
        PickerSubDialog.APP -> AppPickerDialog(
            onDismiss = { subDialog = PickerSubDialog.NONE },
            onPick = { app ->
                subDialog = PickerSubDialog.NONE
                onSelect(SideKeyAction.LaunchApp(app.packageName, app.label))
            }
        )
        PickerSubDialog.SHORTCUT -> ShortcutPickerDialog(
            onDismiss = { subDialog = PickerSubDialog.NONE },
            onAdd = { cardAction ->
                subDialog = PickerSubDialog.NONE
                if (cardAction is CardAction.AppShortcut) {
                    onSelect(
                        SideKeyAction.AppShortcut(
                            intentUri = cardAction.intentUri,
                            label = cardAction.label,
                            packageName = cardAction.packageName,
                            iconId = cardAction.iconId
                        )
                    )
                }
            }
        )
        PickerSubDialog.DIAL -> DialDialog(
            onDismiss = { subDialog = PickerSubDialog.NONE },
            onAdd = { cardAction ->
                subDialog = PickerSubDialog.NONE
                if (cardAction is CardAction.DialNumber) {
                    onSelect(SideKeyAction.DialNumber(cardAction.phoneNumber, cardAction.label))
                }
            }
        )
        PickerSubDialog.TIMER -> ClockTimerDialog(
            onDismiss = { subDialog = PickerSubDialog.NONE },
            onAdd = { cardAction ->
                subDialog = PickerSubDialog.NONE
                if (cardAction is CardAction.ClockTimer) {
                    onSelect(SideKeyAction.StartTimer(cardAction.seconds, cardAction.label))
                }
            }
        )
        PickerSubDialog.COPY -> CopyDialog(
            onDismiss = { subDialog = PickerSubDialog.NONE },
            onAdd = { cardAction ->
                subDialog = PickerSubDialog.NONE
                if (cardAction is CardAction.CopySnippet) {
                    onSelect(SideKeyAction.CopySnippet(cardAction.label, cardAction.text))
                }
            }
        )
        PickerSubDialog.NOTIFICATION -> SideKeyNotificationDialog(
            initial = currentAction as? SideKeyAction.CustomNotification,
            onDismiss = { subDialog = PickerSubDialog.NONE },
            onConfirm = { notif ->
                subDialog = PickerSubDialog.NONE
                onSelect(notif)
            }
        )
        PickerSubDialog.URL -> LinkInputDialog(
            titleRes = R.string.sidekey_action_open_url,
            fieldRes = R.string.card_url_field,
            errorRes = R.string.card_url_invalid,
            monospace = false,
            validate = ActionSlots::normalizeUrl,
            onDismiss = { subDialog = PickerSubDialog.NONE },
            onConfirm = { url, label ->
                subDialog = PickerSubDialog.NONE
                onSelect(SideKeyAction.OpenUrl(url, label))
            }
        )
        PickerSubDialog.INTENT -> LinkInputDialog(
            titleRes = R.string.sidekey_action_custom_intent,
            fieldRes = R.string.sidekey_intent_field,
            errorRes = R.string.sidekey_intent_invalid,
            monospace = true,
            validate = { raw -> raw.trim().takeIf(SideKeyCodec::isValidIntentUri) },
            onDismiss = { subDialog = PickerSubDialog.NONE },
            onConfirm = { uri, label ->
                subDialog = PickerSubDialog.NONE
                onSelect(SideKeyAction.CustomIntent(uri, label))
            }
        )
        PickerSubDialog.MACRO -> SideKeyMacroDialog(
            gesture = gesture,
            initialMacro = currentAction as? SideKeyAction.Macro,
            onDismiss = { subDialog = PickerSubDialog.NONE },
            onSave = { macro ->
                subDialog = PickerSubDialog.NONE
                onSelect(macro)
            }
        )
    }
}

@Composable
private fun CategoryHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun ActionSheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LinkInputDialog(
    titleRes: Int,
    fieldRes: Int,
    errorRes: Int,
    monospace: Boolean,
    validate: (String) -> String?,
    onDismiss: () -> Unit,
    onConfirm: (value: String, label: String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    val normalized = validate(input)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(512) },
                    label = { Text(stringResource(fieldRes)) },
                    textStyle = if (monospace) {
                        MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                    } else MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    isError = input.isNotBlank() && normalized == null,
                    supportingText = if (input.isNotBlank() && normalized == null) {
                        { Text(stringResource(errorRes)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(32) },
                    label = { Text(stringResource(R.string.card_label_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = normalized != null,
                onClick = { normalized?.let { onConfirm(it, label.trim()) } }
            ) {
                Text(stringResource(R.string.card_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
