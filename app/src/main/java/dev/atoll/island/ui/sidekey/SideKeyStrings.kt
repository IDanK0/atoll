package dev.atoll.island.ui.sidekey

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.Shortcut
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdsClick
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
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.atoll.island.R
import dev.atoll.island.data.model.SideKeyAction
import dev.atoll.island.data.model.SideKeyGesture
import dev.atoll.island.data.model.SideKeyRingerMode
import dev.atoll.island.data.model.SideKeyStatus
import dev.atoll.island.service.card.AndroidCardStrings

internal fun gestureTitleRes(gesture: SideKeyGesture): Int = when (gesture) {
    SideKeyGesture.SINGLE_PRESS -> R.string.sidekey_gesture_single
    SideKeyGesture.DOUBLE_PRESS -> R.string.sidekey_gesture_double
    SideKeyGesture.TRIPLE_PRESS -> R.string.sidekey_gesture_triple
    SideKeyGesture.LONG_PRESS -> R.string.sidekey_gesture_long
}

internal fun gestureIcon(gesture: SideKeyGesture): ImageVector = when (gesture) {
    SideKeyGesture.SINGLE_PRESS -> Icons.Rounded.TouchApp
    SideKeyGesture.DOUBLE_PRESS -> Icons.Rounded.AdsClick
    SideKeyGesture.TRIPLE_PRESS -> Icons.Rounded.Repeat
    SideKeyGesture.LONG_PRESS -> Icons.Rounded.PanTool
}

internal fun ringerModeRes(mode: SideKeyRingerMode): Int = when (mode) {
    SideKeyRingerMode.CYCLE -> R.string.sidekey_ringer_cycle
    SideKeyRingerMode.SILENT -> R.string.sidekey_ringer_silent
    SideKeyRingerMode.VIBRATE -> R.string.sidekey_ringer_vibrate
    SideKeyRingerMode.RING -> R.string.sidekey_ringer_ring
}

internal fun actionIcon(action: SideKeyAction): ImageVector = when (action) {
    SideKeyAction.None -> Icons.Rounded.RemoveCircleOutline
    is SideKeyAction.LaunchApp -> Icons.Rounded.Apps
    is SideKeyAction.AppShortcut -> Icons.AutoMirrored.Rounded.Shortcut
    is SideKeyAction.DialNumber -> Icons.Rounded.Call
    is SideKeyAction.StartTimer -> Icons.Rounded.Timer
    is SideKeyAction.CopySnippet -> Icons.Rounded.ContentCopy
    is SideKeyAction.OpenUrl -> Icons.Rounded.Link
    is SideKeyAction.CustomIntent -> Icons.Rounded.Terminal
    is SideKeyAction.CustomNotification -> Icons.Rounded.Notifications
    SideKeyAction.ToggleFlashlight -> Icons.Rounded.FlashlightOn
    is SideKeyAction.RingerMode -> Icons.AutoMirrored.Rounded.VolumeUp
    SideKeyAction.ToggleDnd -> Icons.Rounded.DoNotDisturbOn
    SideKeyAction.MediaPlayPause -> Icons.Rounded.PlayArrow
    SideKeyAction.MediaNext -> Icons.Rounded.SkipNext
    SideKeyAction.MediaPrevious -> Icons.Rounded.SkipPrevious
    SideKeyAction.VolumeUp -> Icons.AutoMirrored.Rounded.VolumeUp
    SideKeyAction.VolumeDown -> Icons.AutoMirrored.Rounded.VolumeDown
    SideKeyAction.ToggleMute -> Icons.AutoMirrored.Rounded.VolumeOff
    SideKeyAction.VolumePanel -> Icons.AutoMirrored.Rounded.VolumeUp
    SideKeyAction.ExpandNotifications -> Icons.Rounded.Notifications
    SideKeyAction.ExpandQuickSettings -> Icons.Rounded.Tune
    SideKeyAction.WifiPanel -> Icons.Rounded.Wifi
    SideKeyAction.BluetoothSettings -> Icons.Rounded.Bluetooth
    SideKeyAction.InternetPanel -> Icons.Rounded.Public
    SideKeyAction.LaunchCamera -> Icons.Rounded.PhotoCamera
    SideKeyAction.LaunchVideoCamera -> Icons.Rounded.Videocam
    SideKeyAction.LaunchVoiceRecorder -> Icons.Rounded.Mic
    SideKeyAction.LaunchAssistant -> Icons.Rounded.AutoAwesome
    SideKeyAction.GoHome -> Icons.Rounded.Home
    SideKeyAction.ToggleCapsule -> Icons.Rounded.Smartphone
    SideKeyAction.CyclePreset -> Icons.Rounded.Smartphone
    SideKeyAction.OpenCalibration -> Icons.Rounded.Tune
    SideKeyAction.CounterIncrement -> Icons.Rounded.Add
    SideKeyAction.CounterDecrement -> Icons.Rounded.Remove
    SideKeyAction.OpenNoteEditor -> Icons.Rounded.EditNote
    is SideKeyAction.Macro -> Icons.AutoMirrored.Rounded.PlaylistPlay
}

@Composable
internal fun actionSummary(action: SideKeyAction): String = when (action) {
    SideKeyAction.None -> stringResource(R.string.sidekey_action_none)
    is SideKeyAction.LaunchApp -> action.appLabel.ifBlank { action.packageName }
    is SideKeyAction.AppShortcut -> action.label.ifBlank { stringResource(R.string.sidekey_action_shortcut) }
    is SideKeyAction.DialNumber -> action.label.ifBlank { action.phoneNumber }
    is SideKeyAction.StartTimer -> {
        val duration = AndroidCardStrings.formatTimerDuration(LocalContext.current, action.seconds)
        if (action.label.isNotBlank()) "${action.label} ($duration)"
        else stringResource(R.string.sidekey_action_timer_fmt, duration)
    }
    is SideKeyAction.CopySnippet -> action.label.ifBlank { stringResource(R.string.card_action_copy) }
    is SideKeyAction.OpenUrl -> action.label.ifBlank { action.url }
    is SideKeyAction.CustomIntent -> action.label.ifBlank { stringResource(R.string.sidekey_action_custom_intent) }
    is SideKeyAction.CustomNotification -> {
        val base = action.title.ifBlank { action.message }.ifBlank { stringResource(R.string.sidekey_action_custom_notif) }
        val withApp = if (action.appLabel.isNotBlank()) "${action.appLabel}: $base" else base
        if (action.delaySeconds > 0) "$withApp (+${action.delaySeconds} s)" else withApp
    }
    SideKeyAction.ToggleFlashlight -> stringResource(R.string.sidekey_action_flashlight)
    is SideKeyAction.RingerMode -> {
        val modeLabel = stringResource(ringerModeRes(action.mode))
        if (action.toggleBack && action.mode != SideKeyRingerMode.CYCLE) {
            stringResource(R.string.sidekey_action_ringer_toggle_fmt, modeLabel)
        } else {
            stringResource(R.string.sidekey_action_ringer_fmt, modeLabel)
        }
    }
    SideKeyAction.ToggleDnd -> stringResource(R.string.sidekey_action_dnd)
    SideKeyAction.MediaPlayPause -> stringResource(R.string.sidekey_action_media_play_pause)
    SideKeyAction.MediaNext -> stringResource(R.string.sidekey_action_media_next)
    SideKeyAction.MediaPrevious -> stringResource(R.string.sidekey_action_media_previous)
    SideKeyAction.VolumeUp -> stringResource(R.string.sidekey_action_volume_up)
    SideKeyAction.VolumeDown -> stringResource(R.string.sidekey_action_volume_down)
    SideKeyAction.ToggleMute -> stringResource(R.string.sidekey_action_mute_media)
    SideKeyAction.VolumePanel -> stringResource(R.string.card_action_volume)
    SideKeyAction.ExpandNotifications -> stringResource(R.string.sidekey_action_notifications)
    SideKeyAction.ExpandQuickSettings -> stringResource(R.string.sidekey_action_quick_settings)
    SideKeyAction.WifiPanel -> stringResource(R.string.card_action_wifi)
    SideKeyAction.BluetoothSettings -> stringResource(R.string.sidekey_action_bluetooth)
    SideKeyAction.InternetPanel -> stringResource(R.string.card_action_internet)
    SideKeyAction.LaunchCamera -> stringResource(R.string.sidekey_action_camera)
    SideKeyAction.LaunchVideoCamera -> stringResource(R.string.sidekey_action_video_camera)
    SideKeyAction.LaunchVoiceRecorder -> stringResource(R.string.sidekey_action_voice_recorder)
    SideKeyAction.LaunchAssistant -> stringResource(R.string.sidekey_action_assistant)
    SideKeyAction.GoHome -> stringResource(R.string.sidekey_action_home)
    SideKeyAction.ToggleCapsule -> stringResource(R.string.sidekey_action_toggle_capsule)
    SideKeyAction.CyclePreset -> stringResource(R.string.sidekey_action_cycle_preset)
    SideKeyAction.OpenCalibration -> stringResource(R.string.sidekey_action_open_calibration)
    SideKeyAction.CounterIncrement -> stringResource(R.string.sidekey_action_counter_plus)
    SideKeyAction.CounterDecrement -> stringResource(R.string.sidekey_action_counter_minus)
    SideKeyAction.OpenNoteEditor -> stringResource(R.string.sidekey_action_note_editor)
    is SideKeyAction.Macro -> if (action.label.isNotBlank()) {
        pluralStringResource(R.plurals.sidekey_macro_labeled, action.steps.size, action.label, action.steps.size)
    } else {
        pluralStringResource(R.plurals.sidekey_macro_steps, action.steps.size, action.steps.size)
    }
}

internal fun statusLabelRes(status: SideKeyStatus): Int = when (status) {
    SideKeyStatus.DISABLED -> R.string.sidekey_status_disabled
    SideKeyStatus.NEEDS_ADB_GRANT -> R.string.sidekey_status_needs_setup
    SideKeyStatus.WAITING_CONSENT -> R.string.sidekey_status_waiting_consent
    SideKeyStatus.LISTENING -> R.string.sidekey_status_listening
    SideKeyStatus.ERROR -> R.string.sidekey_status_error
}
