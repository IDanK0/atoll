package dev.atoll.island.service.sidekey

import android.Manifest
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import dev.atoll.island.R
import dev.atoll.island.data.model.CapsulePreset
import dev.atoll.island.data.model.MacroStep
import dev.atoll.island.data.model.SideKeyAction
import dev.atoll.island.data.model.SideKeyConfig
import dev.atoll.island.data.model.SideKeyRingerMode
import dev.atoll.island.data.prefs.LiveAlertPreferencesRepository
import dev.atoll.island.domain.card.ActionSlots
import dev.atoll.island.domain.card.CounterReducer
import dev.atoll.island.domain.sidekey.SideKeyCodec
import dev.atoll.island.service.AtollLiveAlertService
import dev.atoll.island.service.LiveAlertNotificationBuilder
import dev.atoll.island.service.card.MediaKeyDispatcher
import dev.atoll.island.service.card.TorchController
import dev.atoll.island.ui.note.NoteEditActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

sealed interface ActionExecutionResult {
    data object Executed : ActionExecutionResult
    data object BlockedLocked : ActionExecutionResult
    data object NeedsDndAccess : ActionExecutionResult
    data object NeedsShizuku : ActionExecutionResult
    data object Unavailable : ActionExecutionResult
}

object SideKeyActionExecutor {
    private const val TAG = "AtollSideKeyAction"
    private const val CUSTOM_NOTIF_BASE_ID = 9_100
    private const val CUSTOM_NOTIF_SPAN = 100
    private val SOUND_RECORDER_PACKAGES = listOf("com.oneplus.soundrecorder", "com.coloros.soundrecorder")
    private val notifScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val notifCounter = AtomicInteger(0)

    private val ringerLock = Mutex()
    private var lastRingerTarget: Int? = null
    private var previousRingerMode: Int? = null

    fun isDeviceLocked(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked ?: false

    fun hasDndAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted ?: false

    fun openDndAccessSettings(context: Context, newTask: Boolean = false) {
        val pkgUri = "package:${context.packageName}".toUri()
        val candidates = listOf(
            Intent("android.settings.NOTIFICATION_POLICY_ACCESS_DETAIL_SETTINGS", pkgUri),
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
        )
        for (intent in candidates) {
            if (newTask) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
    }

    fun vibrateTrigger(context: Context, heavy: Boolean = false) {
        val manager = context.getSystemService(VibratorManager::class.java) ?: return
        val effect = VibrationEffect.createPredefined(
            if (heavy) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_CLICK
        )
        runCatching { manager.defaultVibrator.vibrate(effect) }
    }

    suspend fun execute(
        context: Context,
        action: SideKeyAction,
        unlockedOnly: Boolean = false,
        ignoreLockCheck: Boolean = false
    ): ActionExecutionResult {
        if (!action.isAssigned) return ActionExecutionResult.Unavailable
        if (!ignoreLockCheck && unlockedOnly && isDeviceLocked(context)) {
            return ActionExecutionResult.BlockedLocked
        }
        return if (action is SideKeyAction.Macro) {
            executeMacro(context, action.steps)
        } else {
            executeSingle(context, action)
        }
    }

    private suspend fun executeMacro(context: Context, steps: List<MacroStep>): ActionExecutionResult {
        if (steps.isEmpty()) return ActionExecutionResult.Unavailable
        var anyExecuted = false
        var needsDnd = false
        var needsShizuku = false
        for ((index, step) in steps.withIndex()) {
            when (executeSingle(context, step.action)) {
                ActionExecutionResult.Executed -> anyExecuted = true
                ActionExecutionResult.NeedsDndAccess -> needsDnd = true
                ActionExecutionResult.NeedsShizuku -> needsShizuku = true
                ActionExecutionResult.BlockedLocked, ActionExecutionResult.Unavailable -> Unit
            }
            if (index < steps.lastIndex && step.delayAfterMs > 0L) {
                delay(step.delayAfterMs)
            }
        }
        return when {
            needsDnd -> ActionExecutionResult.NeedsDndAccess
            needsShizuku -> ActionExecutionResult.NeedsShizuku
            anyExecuted -> ActionExecutionResult.Executed
            else -> ActionExecutionResult.Unavailable
        }
    }

    private suspend fun executeSingle(context: Context, action: SideKeyAction): ActionExecutionResult {
        return when (action) {
            SideKeyAction.None, is SideKeyAction.Macro -> ActionExecutionResult.Unavailable
            is SideKeyAction.LaunchApp -> launchPackage(context, action.packageName)
            is SideKeyAction.CustomIntent -> {
                val uri = action.intentUri
                if (!SideKeyCodec.isValidIntentUri(uri)) return ActionExecutionResult.Unavailable
                val parsed = runCatching { Intent.parseUri(uri, Intent.URI_INTENT_SCHEME) }.getOrNull()
                    ?: return ActionExecutionResult.Unavailable
                startActivitySafe(context, parsed)
            }
            is SideKeyAction.AppShortcut -> {
                val uri = action.intentUri
                if (!SideKeyCodec.isValidIntentUri(uri)) return ActionExecutionResult.Unavailable
                val parsed = runCatching { Intent.parseUri(uri, Intent.URI_INTENT_SCHEME) }.getOrNull()
                    ?: return ActionExecutionResult.Unavailable
                startActivitySafe(context, parsed)
            }
            is SideKeyAction.CustomNotification -> triggerCustomNotification(context, action)
            is SideKeyAction.DialNumber -> {
                val phone = ActionSlots.normalizePhone(action.phoneNumber) ?: return ActionExecutionResult.Unavailable
                startActivitySafe(context, Intent(Intent.ACTION_DIAL, "tel:$phone".toUri()))
            }
            is SideKeyAction.StartTimer -> {
                val secs = action.seconds.coerceIn(1, ActionSlots.MAX_TIMER_SECONDS)
                val intent = Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, secs).apply {
                    action.label.trim().takeIf { it.isNotEmpty() }?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
                }
                startActivitySafe(context, intent)
            }
            is SideKeyAction.CopySnippet -> copyText(context, action)
            is SideKeyAction.OpenUrl -> {
                val normalized = ActionSlots.normalizeUrl(action.url) ?: return ActionExecutionResult.Unavailable
                startActivitySafe(context, Intent(Intent.ACTION_VIEW, normalized.toUri()))
            }
            SideKeyAction.ToggleFlashlight -> {
                if (TorchController.toggle(context)) ActionExecutionResult.Executed else ActionExecutionResult.Unavailable
            }
            is SideKeyAction.RingerMode -> applyRingerMode(context, action)
            SideKeyAction.ToggleDnd -> toggleDnd(context)
            SideKeyAction.MediaPlayPause -> dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            SideKeyAction.MediaNext -> dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
            SideKeyAction.MediaPrevious -> dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            SideKeyAction.VolumeUp -> adjustVolume(context, AudioManager.ADJUST_RAISE)
            SideKeyAction.VolumeDown -> adjustVolume(context, AudioManager.ADJUST_LOWER)
            SideKeyAction.ToggleMute -> adjustVolume(context, AudioManager.ADJUST_TOGGLE_MUTE)
            SideKeyAction.VolumePanel -> launchPanelOrSettings(context, Settings.Panel.ACTION_VOLUME, Settings.ACTION_SOUND_SETTINGS)
            SideKeyAction.ExpandNotifications -> expandStatusBar(context, "expandNotificationsPanel")
            SideKeyAction.ExpandQuickSettings -> expandStatusBar(context, "expandSettingsPanel")
            SideKeyAction.WifiPanel -> launchPanelOrSettings(context, Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS)
            SideKeyAction.BluetoothSettings -> startActivitySafe(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            SideKeyAction.InternetPanel -> launchPanelOrSettings(
                context, Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_WIRELESS_SETTINGS
            )
            SideKeyAction.LaunchCamera -> launchCamera(context, video = false)
            SideKeyAction.LaunchVideoCamera -> launchCamera(context, video = true)
            SideKeyAction.LaunchVoiceRecorder -> launchVoiceRecorder(context)
            SideKeyAction.LaunchAssistant -> launchAssistant(context)
            SideKeyAction.GoHome -> startActivitySafe(
                context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            )
            SideKeyAction.ToggleCapsule -> toggleCapsule(context)
            SideKeyAction.CyclePreset -> cyclePreset(context)
            SideKeyAction.OpenCalibration -> openCalibration(context)
            SideKeyAction.CounterIncrement -> updateCounter(context, plus = true)
            SideKeyAction.CounterDecrement -> updateCounter(context, plus = false)
            SideKeyAction.OpenNoteEditor -> startActivitySafe(context, NoteEditActivity.intent(context))
        }
    }

    private fun dispatchMediaKey(context: Context, keyCode: Int): ActionExecutionResult {
        MediaKeyDispatcher.dispatch(context, keyCode)
        return ActionExecutionResult.Executed
    }

    private fun adjustVolume(context: Context, direction: Int): ActionExecutionResult {
        val audio = context.getSystemService(AudioManager::class.java) ?: return ActionExecutionResult.Unavailable
        return runCatching {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            ActionExecutionResult.Executed
        }.getOrElse { ActionExecutionResult.Unavailable }
    }

    private fun copyText(context: Context, snippet: SideKeyAction.CopySnippet): ActionExecutionResult {
        if (snippet.text.isEmpty()) return ActionExecutionResult.Unavailable
        val clipLabel = snippet.label.ifBlank { context.getString(R.string.card_action_copy) }
        return runCatching {
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText(clipLabel, snippet.text))
                ?: return ActionExecutionResult.Unavailable
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, context.getString(R.string.sidekey_copied_toast, clipLabel), Toast.LENGTH_SHORT).show()
            }
            ActionExecutionResult.Executed
        }.getOrElse { ActionExecutionResult.Unavailable }
    }

    private fun launchPanelOrSettings(context: Context, panelAction: String, fallbackAction: String): ActionExecutionResult {
        val panel = Intent(panelAction)
        val chosen = if (panel.resolveActivity(context.packageManager) != null) panel else Intent(fallbackAction)
        return startActivitySafe(context, chosen)
    }

    private fun launchAssistant(context: Context): ActionExecutionResult {
        if (startActivitySafe(context, Intent(Intent.ACTION_ASSIST)) == ActionExecutionResult.Executed) {
            return ActionExecutionResult.Executed
        }
        return startActivitySafe(context, Intent(Intent.ACTION_WEB_SEARCH))
    }

    private fun launchPackage(context: Context, packageName: String): ActionExecutionResult {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return ActionExecutionResult.Unavailable
        return startActivitySafe(context, intent)
    }

    private fun launchCamera(context: Context, video: Boolean): ActionExecutionResult {
        val intent = when {
            video -> Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA)
            isDeviceLocked(context) -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE)
            else -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        }
        val firstAttempt = startActivitySafe(context, intent)
        if (firstAttempt == ActionExecutionResult.Executed) return firstAttempt
        return launchPackage(context, "com.oplus.camera")
    }

    private fun launchVoiceRecorder(context: Context): ActionExecutionResult {
        val actionIntent = Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)
        if (actionIntent.resolveActivity(context.packageManager) != null) {
            if (startActivitySafe(context, actionIntent) == ActionExecutionResult.Executed) {
                return ActionExecutionResult.Executed
            }
        }
        for (pkg in SOUND_RECORDER_PACKAGES) {
            val result = launchPackage(context, pkg)
            if (result == ActionExecutionResult.Executed) return result
        }
        return ActionExecutionResult.Unavailable
    }

    private fun startActivitySafe(context: Context, intent: Intent): ActionExecutionResult {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
            ActionExecutionResult.Executed
        } catch (error: Exception) {
            Log.w(TAG, "Failed to launch intent", error)
            ActionExecutionResult.Unavailable
        }
    }

    internal fun resolveRingerTarget(
        action: SideKeyAction.RingerMode,
        currentMode: Int,
        previousMode: Int?,
        canUseSilent: Boolean
    ): Pair<Int, Int?> {
        if (action.mode == SideKeyRingerMode.CYCLE) {
            val next = when (currentMode) {
                AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                AudioManager.RINGER_MODE_VIBRATE ->
                    if (canUseSilent) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
                else -> AudioManager.RINGER_MODE_NORMAL
            }
            return next to currentMode
        }
        val desiredMode = when (action.mode) {
            SideKeyRingerMode.RING -> AudioManager.RINGER_MODE_NORMAL
            SideKeyRingerMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
            SideKeyRingerMode.SILENT -> AudioManager.RINGER_MODE_SILENT
            SideKeyRingerMode.CYCLE -> AudioManager.RINGER_MODE_NORMAL
        }
        if (action.toggleBack && currentMode == desiredMode) {
            val restored = previousMode?.takeIf { it != desiredMode }
            val fallback = if (desiredMode == AudioManager.RINGER_MODE_SILENT) {
                // dal silenzioso senza stato precedente passiamo a vibrazione senza riattivare la suoneria
                AudioManager.RINGER_MODE_VIBRATE
            } else {
                AudioManager.RINGER_MODE_NORMAL
            }
            return (restored ?: fallback) to null
        }
        val updatedPrev = if (currentMode != desiredMode) currentMode else previousMode
        return desiredMode to updatedPrev
    }

    private suspend fun applyRingerMode(
        context: Context,
        action: SideKeyAction.RingerMode
    ): ActionExecutionResult = ringerLock.withLock {
        val audio = context.getSystemService(AudioManager::class.java) ?: return ActionExecutionResult.Unavailable
        val shizukuReady = ShizukuGrantHelper.inspect(context).permissionGranted
        val currentMode = audio.ringerMode
        val (targetMode, nextPrevious) = resolveRingerTarget(
            action = action,
            currentMode = currentMode,
            previousMode = previousRingerMode.takeIf { currentMode == lastRingerTarget },
            canUseSilent = shizukuReady
        )
        if (targetMode == currentMode) return ActionExecutionResult.Executed
        val crossesSilent = currentMode == AudioManager.RINGER_MODE_SILENT ||
            targetMode == AudioManager.RINGER_MODE_SILENT
        if (crossesSilent && !shizukuReady) return ActionExecutionResult.NeedsShizuku

        val modeName = when (targetMode) {
            AudioManager.RINGER_MODE_SILENT -> "SILENT"
            AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
            else -> "NORMAL"
        }
        // Su ColorOS il passaggio da o verso Silenzioso via AudioManager può cambiare Non disturbare.
        val changedViaShizuku = shizukuReady && ShizukuGrantHelper.setRingerMode(context, modeName)
        if (!changedViaShizuku) {
            if (crossesSilent) return ActionExecutionResult.Unavailable
            try {
                audio.ringerMode = targetMode
            } catch (error: SecurityException) {
                Log.w(TAG, "Ringer mode change blocked by Android", error)
                return ActionExecutionResult.NeedsShizuku
            }
        }
        lastRingerTarget = targetMode
        previousRingerMode = nextPrevious
        ActionExecutionResult.Executed
    }

    fun postCustomNotificationNow(context: Context, action: SideKeyAction.CustomNotification): Boolean {
        if (!SideKeyCodec.isValidAction(action)) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val appContext = context.applicationContext
        LiveAlertNotificationBuilder.ensureChannels(appContext)
        val channelId = if (action.headsUp) {
            LiveAlertNotificationBuilder.CHANNEL_CUSTOM_ALERT_HIGH
        } else {
            LiveAlertNotificationBuilder.CHANNEL_CUSTOM_ALERT_SILENT
        }
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return false
        if (appContext.getSystemService(NotificationManager::class.java)
                ?.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE
        ) return false

        val launchIntent = action.appPackage.takeIf { it.isNotBlank() }?.let { pkg ->
            appContext.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
        val notifId = CUSTOM_NOTIF_BASE_ID + (notifCounter.getAndIncrement() % CUSTOM_NOTIF_SPAN)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                appContext,
                notifId,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val largeIcon = loadAppIconBitmap(appContext, action.appPackage)
        val bodyText = action.message.trim()
        val titleText = action.title.trim().ifBlank { appContext.getString(R.string.sidekey_action_custom_notif) }
        val builder = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(notifSmallIconRes(action.iconKey))
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(if (action.headsUp) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        if (largeIcon != null) builder.setLargeIcon(largeIcon)
        if (contentIntent != null) builder.setContentIntent(contentIntent)

        return try {
            NotificationManagerCompat.from(appContext).notify(notifId, builder.build())
            true
        } catch (error: SecurityException) {
            Log.w(TAG, "Notification permission missing", error)
            false
        }
    }

    private fun triggerCustomNotification(
        context: Context,
        action: SideKeyAction.CustomNotification
    ): ActionExecutionResult {
        if (!SideKeyCodec.isValidAction(action)) return ActionExecutionResult.Unavailable
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return ActionExecutionResult.Unavailable
        }
        val appContext = context.applicationContext
        if (action.delaySeconds <= 0) {
            return if (postCustomNotificationNow(appContext, action)) {
                ActionExecutionResult.Executed
            } else {
                ActionExecutionResult.Unavailable
            }
        }
        notifScope.launch {
            delay(action.delaySeconds * 1_000L)
            postCustomNotificationNow(appContext, action)
        }
        return ActionExecutionResult.Executed
    }

    private fun notifSmallIconRes(iconKey: String): Int = when (iconKey) {
        SideKeyConfig.NOTIF_ICON_CALL -> R.drawable.ic_notif_call
        SideKeyConfig.NOTIF_ICON_MAIL -> R.drawable.ic_notif_mail
        SideKeyConfig.NOTIF_ICON_EVENT -> R.drawable.ic_notif_event
        SideKeyConfig.NOTIF_ICON_ALERT -> R.drawable.ic_notifications_bell
        else -> R.drawable.ic_notif_chat
    }

    private fun loadAppIconBitmap(context: Context, packageName: String): Bitmap? {
        if (packageName.isBlank()) return null
        val drawable = runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull() ?: return null
        val sizePx = (64 * context.resources.displayMetrics.density).toInt().coerceIn(96, 256)
        return drawable.toBitmap(width = sizePx, height = sizePx)
    }

    private fun toggleDnd(context: Context): ActionExecutionResult {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return ActionExecutionResult.Unavailable
        if (!nm.isNotificationPolicyAccessGranted) return ActionExecutionResult.NeedsDndAccess
        val next = if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY
        } else {
            NotificationManager.INTERRUPTION_FILTER_ALL
        }
        return runCatching {
            nm.setInterruptionFilter(next)
            ActionExecutionResult.Executed
        }.getOrElse {
            Log.w(TAG, "Failed to toggle DND", it)
            ActionExecutionResult.Unavailable
        }
    }

    @SuppressLint("WrongConstant")
    private fun expandStatusBar(context: Context, methodName: String): ActionExecutionResult {
        return runCatching {
            val statusBar = context.getSystemService("statusbar") ?: return ActionExecutionResult.Unavailable
            val cls = Class.forName("android.app.StatusBarManager")
            cls.getMethod(methodName).invoke(statusBar)
            ActionExecutionResult.Executed
        }.getOrElse {
            Log.w(TAG, "Could not expand status bar panel: $methodName", it)
            ActionExecutionResult.Unavailable
        }
    }

    private suspend fun toggleCapsule(context: Context): ActionExecutionResult {
        val repo = LiveAlertPreferencesRepository.getInstance(context)
        val currentlyOn = repo.getLatestConfig().enabled
        val nextOn = !currentlyOn
        repo.setEnabled(nextOn)
        if (nextOn) {
            AtollLiveAlertService.start(context)
        } else {
            AtollLiveAlertService.stopCapsule(context)
        }
        return ActionExecutionResult.Executed
    }

    private suspend fun cyclePreset(context: Context): ActionExecutionResult {
        val repo = LiveAlertPreferencesRepository.getInstance(context)
        val presets = CapsulePreset.devicePresets
        val current = repo.getLatestConfig().selectedPreset
        val currentIdx = presets.indexOf(current)
        val next = presets[(currentIdx + 1) % presets.size]
        repo.selectPreset(next)
        return ActionExecutionResult.Executed
    }

    private suspend fun openCalibration(context: Context): ActionExecutionResult {
        if (Build.VERSION.SDK_INT < 36 || !Settings.canDrawOverlays(context)) {
            return ActionExecutionResult.Unavailable
        }
        val repo = LiveAlertPreferencesRepository.getInstance(context)
        if (!repo.getLatestConfig().enabled) {
            repo.setEnabled(true)
            AtollLiveAlertService.start(context)
        }
        AtollLiveAlertService.startCalibration(context)
        return ActionExecutionResult.Executed
    }

    private suspend fun updateCounter(context: Context, plus: Boolean): ActionExecutionResult {
        val repo = LiveAlertPreferencesRepository.getInstance(context)
        repo.updateCard { card ->
            card.copy(counter = if (plus) CounterReducer.plus(card.counter) else CounterReducer.minus(card.counter))
        }
        return ActionExecutionResult.Executed
    }
}
