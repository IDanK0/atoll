package dev.atoll.island.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowInsets
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.atoll.island.R
import dev.atoll.island.data.model.CapsulePreset
import dev.atoll.island.data.model.CapsuleScalingMode
import dev.atoll.island.data.model.NotificationChannelMode
import dev.atoll.island.data.model.NotificationTemplateStyle
import dev.atoll.island.data.model.OnboardingStep
import dev.atoll.island.data.model.PaddingGlyph
import dev.atoll.island.data.model.SmallIconMode
import dev.atoll.island.data.model.TitleMode
import dev.atoll.island.data.model.WizardLaunchRequest
import dev.atoll.island.data.prefs.LiveAlertPreferencesRepository
import dev.atoll.island.domain.CapsulePaddingGenerator
import dev.atoll.island.service.AtollLiveAlertService
import dev.atoll.island.service.LiveAlertNotificationBuilder
import dev.atoll.island.ui.onboarding.SetupWizardScreen
import dev.atoll.island.ui.theme.AtollTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {

    private lateinit var repository: LiveAlertPreferencesRepository
    private var shownRationale = false
    private var showOverlayPermission by mutableStateOf(false)
    private var waitingForOverlaySettings = false
    private var launchingCalibration = false
    private var wizardRequest by mutableStateOf<WizardLaunchRequest?>(null)

    private fun save(block: suspend LiveAlertPreferencesRepository.() -> Unit) {
        lifecycleScope.launch { repository.block() }
    }

    private val dashboardActions = object : DashboardActions {
        override fun setAdvancedMode(advanced: Boolean) = save { setAdvancedMode(advanced) }
        override fun openCalibration() = requestFloatingCalibration()
        override fun rerunSetup() {
            wizardRequest = WizardLaunchRequest(initialStep = OnboardingStep.WELCOME, isRerun = true)
        }
        override fun fixPermission(step: OnboardingStep) {
            wizardRequest = WizardLaunchRequest(initialStep = step, isRerun = true)
        }
        override fun setEnabled(enabled: Boolean) {
            // prima salvo: il servizio all'avvio legge il valore salvato e se è off si ferma
            lifecycleScope.launch {
                repository.setEnabled(enabled)
                when {
                    enabled && !hasNotificationPermission() -> askNotificationPermission()
                    enabled -> AtollLiveAlertService.start(this@MainActivity)
                    else -> AtollLiveAlertService.stop(this@MainActivity)
                }
            }
        }
        override fun refreshDiagnostics() {
            captureDisplayCutoutWidth()
            AtollLiveAlertService.refreshDiagnostics(this@MainActivity, repository.config.value)
        }
        override fun openLiveAlertSettings() = openLiveAlertPromotionSettings()
        override fun openAppInfoSettings() = this@MainActivity.openAppInfoSettings()
        override fun selectPreset(preset: CapsulePreset) = save { selectPreset(preset) }
        override fun setCustomWidth(widthDp: Float) = save { setCustomWidthDp(widthDp) }
        override fun setScalingMode(mode: CapsuleScalingMode) = save { setScalingMode(mode) }
        override fun setPillFloor(dp: Float) = save { setPillFloorDp(dp) }
        override fun setPillOrigin(dp: Float) = save { setPillOriginDp(dp) }
        override fun setDpPerUnit(dp: Float) = save { setEffectiveDpPerUnit(dp) }
        override fun setPhysicalCalibration(factor: Float) = save { setPhysicalCalibrationFactor(factor) }
        override fun setMaxChipChars(chars: Int) = save { setMaxChipChars(chars) }
        override fun setBrailleAnchor(enabled: Boolean) = save { setBrailleOnlySafeAnchor(enabled) }
        override fun resetSizing() = save { resetCalibration() }
        override fun setManualText(enabled: Boolean) = save { setManualTextEnabled(enabled) }
        override fun setManualLength(length: Int) = save { setCustomLength(length) }
        override fun setManualGlyph(glyph: PaddingGlyph) = save { setCustomGlyph(glyph) }
        override fun setDebugVisible(enabled: Boolean) = save { setDebugVisibleMode(enabled) }
        override fun setDebugGlyph(glyph: PaddingGlyph) = save { setDebugGlyph(glyph) }
        override fun setChannelMode(mode: NotificationChannelMode) = save { setChannelMode(mode) }
        override fun setTemplateStyle(style: NotificationTemplateStyle) = save { setTemplateStyle(style) }
        override fun setProgressIndeterminate(indeterminate: Boolean) = save { setProgressIndeterminate(indeterminate) }
        override fun setSmallIconMode(mode: SmallIconMode) = save { setSmallIconMode(mode) }
        override fun setTitleMode(mode: TitleMode) = save { setTitleMode(mode) }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val config = repository.config.value
        val canShowRationaleNow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            false
        }
        if (!granted && !shownRationale && !canShowRationaleNow) {
            openNotificationSettings()
        } else if (granted && config.enabled) {
            AtollLiveAlertService.start(this)
        }
        AtollLiveAlertService.refreshDiagnostics(this, config)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        repository = LiveAlertPreferencesRepository.getInstance(applicationContext)
        waitingForOverlaySettings = savedInstanceState?.getBoolean("waitingForOverlaySettings") ?: false
        AtollLiveAlertService.refreshDiagnostics(this, repository.config.value)

        setContent {
            AtollTheme {
                val config by repository.config.collectAsStateWithLifecycle()
                val uiPrefs by repository.uiPreferences.collectAsStateWithLifecycle()
                val isServiceRunning by AtollLiveAlertService.isRunning.collectAsStateWithLifecycle()
                val diagnostics by AtollLiveAlertService.diagnostics.collectAsStateWithLifecycle()
                val displayMetrics by AtollLiveAlertService.displayMetrics.collectAsStateWithLifecycle()
                val solvedState by AtollLiveAlertService.adaptiveResult.collectAsStateWithLifecycle()


                LaunchedEffect(config) {
                    AtollLiveAlertService.refreshDiagnostics(this@MainActivity, config)
                    if (config.enabled && !isServiceRunning && !launchingCalibration && hasNotificationPermission()) {
                        AtollLiveAlertService.start(this@MainActivity)
                    } else if (!config.enabled && isServiceRunning) {
                        AtollLiveAlertService.stop(this@MainActivity)
                    }
                }

                // aspetto un attimo che SystemUI aggiorni i flag
                LaunchedEffect(isServiceRunning, config) {
                    delay(400)
                    AtollLiveAlertService.refreshDiagnostics(this@MainActivity, config)
                    if (isServiceRunning) {
                        delay(800)
                        AtollLiveAlertService.refreshDiagnostics(this@MainActivity, config)
                    }
                }

                val effectiveSolved = solvedState ?: remember(config, displayMetrics) {
                    LiveAlertNotificationBuilder.solveForDevice(this@MainActivity, config).second
                }

                val loadedPrefs = uiPrefs
                if (loadedPrefs == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                } else if (!loadedPrefs.hasCompletedOnboarding || wizardRequest != null) {
                    val activeRequest = wizardRequest ?: WizardLaunchRequest(
                        initialStep = OnboardingStep.WELCOME,
                        isRerun = false
                    )
                    SetupWizardScreen(
                        launchRequest = activeRequest,
                        diagnostics = diagnostics,
                        onRefreshPermissions = {
                            AtollLiveAlertService.refreshDiagnostics(this@MainActivity, config)
                        },
                        onRequestNotifications = ::askNotificationPermission,
                        onOpenLiveAlertSettings = ::openLiveAlertPromotionSettings,
                        onOpenAppInfoSettings = ::openAppInfoSettings,
                        onFinishAndEnable = {
                            lifecycleScope.launch {
                                val resetToSimple = !activeRequest.isRerun || !loadedPrefs.isAdvancedMode
                                repository.finishSetup(resetToSimpleMode = resetToSimple)
                                if (hasNotificationPermission()) {
                                    AtollLiveAlertService.start(this@MainActivity)
                                }
                                AtollLiveAlertService.refreshDiagnostics(
                                    this@MainActivity,
                                    repository.getLatestConfig()
                                )
                                wizardRequest = null
                            }
                        },
                        onDismissRerun = if (activeRequest.isRerun) {
                            { wizardRequest = null }
                        } else {
                            null
                        }
                    )
                } else {
                    AtollDashboardScreen(
                        config = config,
                        isAdvancedMode = loadedPrefs.isAdvancedMode,
                        isServiceRunning = isServiceRunning,
                        diagnostics = diagnostics,
                        displayMetrics = displayMetrics,
                        adaptiveResult = effectiveSolved,
                        sentText = CapsulePaddingGenerator.applyDebugOverlay(
                            effectiveSolved.text,
                            config.debugVisibleMode,
                            config.debugGlyph
                        ),
                        actions = dashboardActions
                    )
                }

                if (showOverlayPermission) {
                    AlertDialog(
                        onDismissRequest = { showOverlayPermission = false },
                        title = { Text(stringResource(R.string.cal_float_permission_title)) },
                        text = { Text(stringResource(R.string.cal_float_permission_body)) },
                        confirmButton = {
                            TextButton(onClick = ::openOverlaySettings) {
                                Text(stringResource(R.string.cal_float_permission_open))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showOverlayPermission = false }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        captureDisplayCutoutWidth()
    }

    override fun onResume() {
        super.onResume()
        captureDisplayCutoutWidth()
        AtollLiveAlertService.refreshDiagnostics(this, repository.config.value)
        if (waitingForOverlaySettings) {
            waitingForOverlaySettings = false
            if (Settings.canDrawOverlays(this)) {
                // ON_RESUME arriva prima che lo stato sia RESUMED
                window.decorView.post { requestFloatingCalibration() }
            }
        } else if (!launchingCalibration &&
            AtollLiveAlertService.calibrationState.value == AtollLiveAlertService.CalibrationState.OPEN
        ) {
            AtollLiveAlertService.stopCalibration(this)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("waitingForOverlaySettings", waitingForOverlaySettings)
        super.onSaveInstanceState(outState)
    }

    private fun captureDisplayCutoutWidth() {
        if (!::repository.isInitialized) return
        lifecycleScope.launch { persistCalibrationGeometry() }
    }

    private suspend fun persistCalibrationGeometry() {
        if (resources.configuration.orientation != Configuration.ORIENTATION_PORTRAIT) return
        val fallback = windowManager.maximumWindowMetrics.windowInsets
        val root = window.decorView.rootWindowInsets
        val cutout = root?.displayCutout?.boundingRectTop?.takeUnless { it.isEmpty }
            ?: fallback.displayCutout?.boundingRectTop?.takeUnless { it.isEmpty }
        val barHeight = (root ?: fallback).getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
        repository.saveCalibrationGeometry(cutout?.width() ?: 0, cutout?.centerY() ?: 0, barHeight)
    }

    private fun requestFloatingCalibration() {
        if (launchingCalibration || isFinishing) return
        when {
            Build.VERSION.SDK_INT < 36 -> calibrationMessage(R.string.cal_float_android_required)
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ->
                calibrationMessage(R.string.cal_float_portrait)
            !NotificationManagerCompat.from(this).areNotificationsEnabled() -> {
                calibrationMessage(R.string.cal_float_notifications_required)
                askNotificationPermission()
            }
            !LiveAlertNotificationBuilder.canPostPromotedNotifications(this) &&
                !AtollLiveAlertService.diagnostics.value.shownAsLiveAlert -> {
                calibrationMessage(R.string.cal_float_live_alerts_required)
                openLiveAlertPromotionSettings()
            }
            !Settings.canDrawOverlays(this) -> showOverlayPermission = true
            else -> launchFloatingCalibration()
        }
    }

    private fun openOverlaySettings() {
        showOverlayPermission = false
        waitingForOverlaySettings = true
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
        } catch (_: ActivityNotFoundException) {
            waitingForOverlaySettings = false
            calibrationMessage(R.string.cal_float_start_failed)
        }
    }

    private fun launchFloatingCalibration() {
        launchingCalibration = true
        lifecycleScope.launch {
            try {
                val opened = withTimeoutOrNull(5_000L) {
                    persistCalibrationGeometry()
                    if (!repository.getLatestConfig().enabled) {
                        repository.setEnabled(true)
                        repository.config.first { it.enabled }
                    }
                    if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@withTimeoutOrNull false
                    AtollLiveAlertService.startCalibration(this@MainActivity)
                    val state = AtollLiveAlertService.calibrationState.first {
                        it != AtollLiveAlertService.CalibrationState.OPENING
                    }
                    state == AtollLiveAlertService.CalibrationState.OPEN
                } == true
                if (opened && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    AtollLiveAlertService.stopCalibration(this@MainActivity)
                    calibrationMessage(R.string.cal_float_start_failed)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                runCatching { AtollLiveAlertService.stopCalibration(this@MainActivity) }
                calibrationMessage(R.string.cal_float_start_failed)
            } finally {
                launchingCalibration = false
            }
        }
    }

    private fun calibrationMessage(text: Int) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
            shownRationale =
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings()
        }
    }

    private fun openLiveAlertPromotionSettings() {
        val promotionIntent = if (Build.VERSION.SDK_INT >= 36) {
            Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        }

        val fallbackNotifIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }

        runCatching {
            startActivity(promotionIntent)
        }.onFailure {
            runCatching {
                startActivity(fallbackNotifIntent)
            }.onFailure {
                openAppInfoSettings()
            }
        }
    }

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        runCatching {
            startActivity(intent)
        }.onFailure {
            openAppInfoSettings()
        }
    }

    private fun openAppInfoSettings() {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:$packageName".toUri()
                )
            )
        }
    }
}
