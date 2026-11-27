package dev.atoll.island.data.model

enum class SideKeyGesture {
    SINGLE_PRESS,
    DOUBLE_PRESS,
    TRIPLE_PRESS,
    LONG_PRESS
}

enum class SideKeyRingerMode {
    CYCLE,
    SILENT,
    VIBRATE,
    RING;

    companion object {
        fun fromName(name: String?): SideKeyRingerMode =
            entries.firstOrNull { it.name == name } ?: CYCLE
    }
}

enum class SideKeyStatus {
    DISABLED,
    NEEDS_ADB_GRANT,
    WAITING_CONSENT,
    LISTENING,
    ERROR
}

data class MacroStep(
    val action: SideKeyAction,
    val delayAfterMs: Long = SideKeyConfig.DEFAULT_MACRO_DELAY_MS
)

sealed interface SideKeyAction {
    data object None : SideKeyAction
    data class LaunchApp(val packageName: String, val appLabel: String) : SideKeyAction
    data class AppShortcut(
        val intentUri: String,
        val label: String,
        val packageName: String = "",
        val iconId: String = ""
    ) : SideKeyAction
    data class DialNumber(val phoneNumber: String, val label: String) : SideKeyAction
    data class StartTimer(val seconds: Int, val label: String) : SideKeyAction
    data class CopySnippet(val label: String, val text: String) : SideKeyAction
    data class OpenUrl(val url: String, val label: String) : SideKeyAction
    data class CustomIntent(val intentUri: String, val label: String) : SideKeyAction
    data class CustomNotification(
        val title: String,
        val message: String,
        val appPackage: String = "",
        val appLabel: String = "",
        val iconKey: String = SideKeyConfig.NOTIF_ICON_CHAT,
        val delaySeconds: Int = 0,
        val headsUp: Boolean = true
    ) : SideKeyAction
    data object ToggleFlashlight : SideKeyAction
    data class RingerMode(
        val mode: SideKeyRingerMode = SideKeyRingerMode.CYCLE,
        val toggleBack: Boolean = false
    ) : SideKeyAction
    data object ToggleDnd : SideKeyAction
    data object MediaPlayPause : SideKeyAction
    data object MediaNext : SideKeyAction
    data object MediaPrevious : SideKeyAction
    data object VolumeUp : SideKeyAction
    data object VolumeDown : SideKeyAction
    data object ToggleMute : SideKeyAction
    data object VolumePanel : SideKeyAction
    data object ExpandNotifications : SideKeyAction
    data object ExpandQuickSettings : SideKeyAction
    data object WifiPanel : SideKeyAction
    data object BluetoothSettings : SideKeyAction
    data object InternetPanel : SideKeyAction
    data object LaunchCamera : SideKeyAction
    data object LaunchVideoCamera : SideKeyAction
    data object LaunchVoiceRecorder : SideKeyAction
    data object LaunchAssistant : SideKeyAction
    data object GoHome : SideKeyAction
    data object ToggleCapsule : SideKeyAction
    data object CyclePreset : SideKeyAction
    data object OpenCalibration : SideKeyAction
    data object CounterIncrement : SideKeyAction
    data object CounterDecrement : SideKeyAction
    data object OpenNoteEditor : SideKeyAction
    data class Macro(val label: String, val steps: List<MacroStep>) : SideKeyAction

    val isAssigned: Boolean
        get() = when (this) {
            None -> false
            is Macro -> steps.any { it.action.isAssigned }
            else -> true
        }

    val needsDndAccess: Boolean
        get() = when (this) {
            ToggleDnd -> true
            is Macro -> steps.any { it.action.needsDndAccess }
            else -> false
        }

    val needsShizukuForSilent: Boolean
        get() = when (this) {
            is RingerMode -> mode == SideKeyRingerMode.SILENT || mode == SideKeyRingerMode.CYCLE
            is Macro -> steps.any { it.action.needsShizukuForSilent }
            else -> false
        }

    val needsOverlayForBackgroundLaunch: Boolean
        get() = when (this) {
            is LaunchApp,
            is AppShortcut,
            is DialNumber,
            is StartTimer,
            is OpenUrl,
            is CustomIntent,
            VolumePanel,
            WifiPanel,
            BluetoothSettings,
            InternetPanel,
            LaunchCamera,
            LaunchVideoCamera,
            LaunchVoiceRecorder,
            LaunchAssistant,
            GoHome,
            OpenCalibration,
            OpenNoteEditor -> true
            is Macro -> steps.any { it.action.needsOverlayForBackgroundLaunch }
            else -> false
        }
}

data class SideKeyGestureBinding(
    val action: SideKeyAction = SideKeyAction.None,
    val unlockedOnly: Boolean = false
)

data class SideKeyConfig(
    val enabled: Boolean = false,
    val setupDismissed: Boolean = false,
    val wizardStep: Int = WIZARD_NONE,
    val wizardLanguageSwitched: Boolean = false,
    val bindings: Map<SideKeyGesture, SideKeyGestureBinding> = emptyMap(),
    val longPressTimeoutMs: Long = DEFAULT_LONG_PRESS_MS,
    val multiTapTimeoutMs: Long = DEFAULT_MULTI_TAP_MS,
    val workWithScreenOff: Boolean = false,
    val hapticFeedback: Boolean = true,
    val filterPattern: String = DEFAULT_FILTER_PATTERN
) {
    val isWizardActive: Boolean
        get() = wizardStep in 0..WIZARD_MAX_STEP

    fun bindingFor(gesture: SideKeyGesture): SideKeyGestureBinding =
        bindings[gesture] ?: SideKeyGestureBinding()

    fun actionFor(gesture: SideKeyGesture): SideKeyAction = bindingFor(gesture).action

    fun hasAnyBackgroundLaunchAction(): Boolean =
        bindings.values.any { it.action.needsOverlayForBackgroundLaunch }

    companion object {
        const val WIZARD_NONE = -1
        const val WIZARD_MAX_STEP = 4

        const val MIN_LONG_PRESS_MS = 300L
        const val MAX_LONG_PRESS_MS = 1_000L
        const val DEFAULT_LONG_PRESS_MS = 500L

        const val MIN_MULTI_TAP_MS = 180L
        const val MAX_MULTI_TAP_MS = 450L
        const val DEFAULT_MULTI_TAP_MS = 280L

        const val DEFAULT_MACRO_DELAY_MS = 250L
        const val MAX_MACRO_DELAY_MS = 10_000L
        const val MAX_MACRO_STEPS = 8

        const val NOTIF_ICON_CHAT = "chat"
        const val NOTIF_ICON_CALL = "call"
        const val NOTIF_ICON_MAIL = "mail"
        const val NOTIF_ICON_EVENT = "event"
        const val NOTIF_ICON_ALERT = "alert"
        const val MAX_NOTIF_DELAY_SECONDS = 60

        const val DEFAULT_FILTER_PATTERN = "KEYCODE_ACTION_BUTTON_CLICK|scanCode=735"
        const val DEDUP_WINDOW_MS = 25L
    }
}

data class SideKeyDiagnostics(
    val status: SideKeyStatus = SideKeyStatus.DISABLED,
    val hasReadLogsPermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasDndAccess: Boolean = false,
    val shizukuInstalled: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuPermissionGranted: Boolean = false,
    val lastRawEventTimestampMs: Long? = null,
    val lastParsedGesture: SideKeyGesture? = null,
    val sessionPressCount: Int = 0,
    val errorMessage: String? = null
)
