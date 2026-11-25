package dev.atoll.island.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Display
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dev.atoll.island.BuildConfig
import dev.atoll.island.R
import dev.atoll.island.data.model.AdaptivePaddingResult
import dev.atoll.island.data.model.CardAction
import dev.atoll.island.data.model.CounterSource
import dev.atoll.island.data.model.DeviceDisplayMetrics
import dev.atoll.island.data.model.ExpandedCardMode
import dev.atoll.island.data.model.LiveAlertConfig
import dev.atoll.island.data.model.PromotionDiagnostics
import dev.atoll.island.data.model.StatMetric
import dev.atoll.island.data.prefs.LiveAlertPreferencesRepository
import dev.atoll.island.domain.AndroidDisplayMetricsProvider
import dev.atoll.island.domain.card.ActionSlots
import dev.atoll.island.domain.card.CardContent
import dev.atoll.island.domain.card.ExpandedCardContentResolver
import dev.atoll.island.domain.card.TimerReducer
import dev.atoll.island.service.card.TorchController
import dev.atoll.island.ui.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

// tiene viva una sola notifica promossa, ColorOS la mette nella capsula
class AtollLiveAlertService : LifecycleService() {

    private var notificationManager: NotificationManager? = null
    private var displayManager: DisplayManager? = null
    private var hasEnteredForeground = false
    private var lastAppliedConfig: LiveAlertConfig? = null
    private var lastAppliedMetrics: DeviceDisplayMetrics? = null
    private var calibrationText: String? = null
    private var calibration: CalibrationOverlayController? = null
    private var calibrationLaunch: Job? = null
    private var verificationJob: Job? = null
    private var lastRecheckMs = 0L
    private var lastDisplayState: Int = Display.STATE_UNKNOWN
    private var lastCardContent: CardContent? = null
    private var batteryRegistered = false
    private var tickRegistered = false
    private var timerJob: Job? = null
    private var timerEndScheduled = 0L

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshCardIfChanged()
    }

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshCardIfChanged()
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY || !hasEnteredForeground) return
            val config = LiveAlertPreferencesRepository.getInstance(applicationContext).config.value
            val currentMetrics = AndroidDisplayMetricsProvider.readMetrics(
                context = this@AtollLiveAlertService,
                persistedCutoutWidthPx = config.detectedCutoutWidthPx
            )
            // refresh rate e luminosità non contano, solo dimensioni, dpi e font
            if (metricsDiffer(lastAppliedMetrics, currentMetrics)) {
                postOrUpdateForeground(config, force = true)
                return
            }
            val currentDisplayState = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.state
                ?: Display.STATE_UNKNOWN
            val stateChanged = currentDisplayState != lastDisplayState
            val elapsed = SystemClock.elapsedRealtime()
            if (stateChanged || elapsed - lastRecheckMs >= RECHECK_INTERVAL_MS) {
                lastDisplayState = currentDisplayState
                lastRecheckMs = elapsed
                repairIfNeeded(config)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        notificationManager = getSystemService(NotificationManager::class.java)
        displayManager = getSystemService(DisplayManager::class.java)
        lastDisplayState = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.state
            ?: Display.STATE_UNKNOWN
        LiveAlertNotificationBuilder.ensureChannels(this)

        displayManager?.registerDisplayListener(
            displayListener,
            Handler(Looper.getMainLooper())
        )

        val repo = LiveAlertPreferencesRepository.getInstance(applicationContext)
        val initialConfig = repo.config.value
        postOrUpdateForeground(initialConfig, force = true)

        lifecycleScope.launch {
            repo.config.collect { config ->
                // appena accesa lo StateFlow può dire ancora off, ricontrollo il valore salvato
                if (!config.enabled && hasEnteredForeground && !repo.getLatestConfig().enabled) {
                    stopSelf()
                } else if (config.enabled || !hasEnteredForeground) {
                    postOrUpdateForeground(config, force = false)
                }
            }
        }

        AtollTileService.requestTileSync(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START_CALIBRATION -> openCalibration()
            ACTION_STOP_CALIBRATION -> closeCalibration()
            ACTION_DEBUG_SAMPLE -> if (BuildConfig.DEBUG && calibration == null) {
                calibrationText = intent.getStringExtra(EXTRA_SAMPLE)
                val config = LiveAlertPreferencesRepository.getInstance(applicationContext).config.value
                if (config.enabled) postOrUpdateForeground(config, force = true)
            }
            ACTION_REFRESH_CARD -> if (hasEnteredForeground) {
                lifecycleScope.launch {
                    val config = LiveAlertPreferencesRepository.getInstance(applicationContext).getLatestConfig()
                    if (config.enabled) postOrUpdateForeground(config, force = true, scheduleVerification = false)
                }
            }
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun openCalibration() {
        if (calibration != null || calibrationLaunch?.isActive == true) return
        _calibrationState.value = CalibrationState.OPENING
        calibrationLaunch = lifecycleScope.launch {
            val repository = LiveAlertPreferencesRepository.getInstance(applicationContext)
            try {
                val config = repository.getLatestConfig()
                check(config.enabled && hasEnteredForeground && Settings.canDrawOverlays(this@AtollLiveAlertService))
                calibration = CalibrationOverlayController(
                    this@AtollLiveAlertService,
                    lifecycleScope,
                    repository,
                    config,
                    repository.getCalibrationOverlayPreferences(),
                    onSample = { updated, sample ->
                        calibrationText = sample
                        postOrUpdateForeground(updated, force = true)
                    },
                    onClose = { returnToApp ->
                        if (returnToApp) {
                            runCatching {
                                startActivity(Intent(this@AtollLiveAlertService, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                })
                            }.onFailure {
                                android.widget.Toast.makeText(
                                    this@AtollLiveAlertService, R.string.cal_float_return_manual, android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        closeCalibration()
                    }
                )
                calibration?.open()
                _calibrationState.value = CalibrationState.OPEN
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("AtollCalibration", "Could not open calibration windows", error)
                closeCalibration()
                _calibrationState.value = CalibrationState.FAILED
            }
        }
    }

    private fun closeCalibration(restoreNotification: Boolean = true) {
        calibrationLaunch?.cancel()
        calibrationLaunch = null
        calibration?.dispose()
        calibration = null
        val hadSample = calibrationText != null
        calibrationText = null
        _calibrationState.value = CalibrationState.IDLE
        if (restoreNotification && hadSample && hasEnteredForeground) {
            val config = LiveAlertPreferencesRepository.getInstance(applicationContext).config.value
            if (config.enabled) postOrUpdateForeground(config, force = true)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!hasEnteredForeground) return
        val config = LiveAlertPreferencesRepository.getInstance(applicationContext).config.value
        postOrUpdateForeground(config, force = true)
        calibration?.onConfigurationChanged()
    }

    override fun onDestroy() {
        closeCalibration(restoreNotification = false)
        verificationJob?.cancel()
        verificationJob = null
        stopCardMonitors()
        displayManager?.unregisterDisplayListener(displayListener)
        _isRunning.value = false
        hasEnteredForeground = false
        lastAppliedConfig = null
        lastAppliedMetrics = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        notificationManager?.cancel(NOTIFICATION_ID)
        refreshDiagnostics(
            context = applicationContext,
            config = LiveAlertPreferencesRepository.getInstance(applicationContext).config.value
        )
        AtollTileService.requestTileSync(applicationContext)
        super.onDestroy()
    }

    private fun postOrUpdateForeground(
        config: LiveAlertConfig,
        force: Boolean,
        scheduleVerification: Boolean = true
    ) {
        val (metrics, solvedResult) = LiveAlertNotificationBuilder.solveForDevice(this, config)
        if (hasEnteredForeground && !force &&
            lastAppliedConfig == config &&
            !metricsDiffer(lastAppliedMetrics, metrics)
        ) {
            return
        }

        lastAppliedConfig = config
        lastAppliedMetrics = metrics
        val activeOverride = calibrationText
        _displayMetrics.value = metrics
        _adaptiveResult.value = solvedResult

        val cardContent = LiveAlertNotificationBuilder.resolveCard(this, config)
        lastCardContent = cardContent
        val notification = LiveAlertNotificationBuilder.build(
            context = this,
            config = if (activeOverride != null) config.copy(debugVisibleMode = false) else config,
            solvedResult = solvedResult,
            calibrationOverrideText = activeOverride,
            cardContent = cardContent
        )
        if (!hasEnteredForeground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            hasEnteredForeground = true
        } else {
            notificationManager?.notify(NOTIFICATION_ID, notification)
        }
        if (scheduleVerification) {
            scheduleRecheck()
        }
        updateCardMonitors(config)
        refreshDiagnostics(this, config, metrics, solvedResult)
    }

    // batteria e minuti solo quando la scheda li mostra, così il consumo resta quasi zero
    private fun updateCardMonitors(config: LiveAlertConfig) {
        val card = config.card
        val usesMetric = ExpandedCardContentResolver.usesMetricStyle(card, Build.VERSION.SDK_INT)
        val wantBattery = config.enabled && card.mode == ExpandedCardMode.DEVICE_STATS
        val wantTick = config.enabled && when (card.mode) {
            ExpandedCardMode.DEVICE_STATS -> card.stats.any { it == StatMetric.DAY_PROGRESS || it == StatMetric.DATE_TIME }
            ExpandedCardMode.PROGRESS_COUNTER -> card.counterSource == CounterSource.DAY
            ExpandedCardMode.FOCUS_TIMER -> card.timer.isRunning && !usesMetric
            else -> false
        }
        if (wantBattery != batteryRegistered) {
            if (wantBattery) {
                ContextCompat.registerReceiver(
                    this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
                )
            } else {
                runCatching { unregisterReceiver(batteryReceiver) }
            }
            batteryRegistered = wantBattery
        }
        if (wantTick != tickRegistered) {
            if (wantTick) {
                ContextCompat.registerReceiver(
                    this, tickReceiver, IntentFilter(Intent.ACTION_TIME_TICK), ContextCompat.RECEIVER_NOT_EXPORTED
                )
            } else {
                runCatching { unregisterReceiver(tickReceiver) }
            }
            tickRegistered = wantTick
        }

        val endAt = if (config.enabled && card.mode == ExpandedCardMode.FOCUS_TIMER && card.timer.isRunning) {
            card.timer.endAtMs
        } else {
            0L
        }
        if (endAt != timerEndScheduled) {
            timerJob?.cancel()
            timerEndScheduled = endAt
            if (endAt > 0L) {
                timerJob = lifecycleScope.launch {
                    delay((endAt - System.currentTimeMillis()).coerceAtLeast(0L))
                    LiveAlertPreferencesRepository.getInstance(applicationContext).updateCard {
                        it.copy(timer = TimerReducer.finishIfDue(it.timer, System.currentTimeMillis()))
                    }
                }
            }
        }
        if (CardAction.ToggleFlashlight in ActionSlots.resolve(card)) TorchController.ensureRegistered(this)
    }

    private fun stopCardMonitors() {
        if (batteryRegistered) runCatching { unregisterReceiver(batteryReceiver) }
        if (tickRegistered) runCatching { unregisterReceiver(tickReceiver) }
        batteryRegistered = false
        tickRegistered = false
        timerJob?.cancel()
        timerJob = null
        timerEndScheduled = 0L
    }

    private fun refreshCardIfChanged() {
        if (!hasEnteredForeground) return
        val config = LiveAlertPreferencesRepository.getInstance(applicationContext).config.value
        if (!config.enabled) return
        if (LiveAlertNotificationBuilder.resolveCard(this, config) != lastCardContent) {
            postOrUpdateForeground(config, force = true, scheduleVerification = false)
        }
    }

    private fun scheduleRecheck() {
        verificationJob?.cancel()
        verificationJob = lifecycleScope.launch {
            for (delayMs in VERIFICATION_DELAYS_MS) {
                delay(delayMs)
                if (!hasEnteredForeground) return@launch
                val config = LiveAlertPreferencesRepository.getInstance(applicationContext).config.value
                if (!config.enabled) return@launch
                repairIfNeeded(config)
            }
        }
    }

    private fun repairIfNeeded(config: LiveAlertConfig) {
        if (!hasEnteredForeground || !config.enabled) return
        val nm = notificationManager ?: return
        val activeNotification = runCatching {
            nm.activeNotifications.firstOrNull { it.id == NOTIFICATION_ID }?.notification
        }.getOrNull()
        val requirePromotion = LiveAlertNotificationBuilder.canPostPromotedNotifications(this)
        if (LiveAlertNotificationBuilder.needsRepost(
                notification = activeNotification,
                config = config,
                appPackageName = packageName,
                requirePromotion = requirePromotion
            )
        ) {
            postOrUpdateForeground(config, force = true, scheduleVerification = false)
        }
    }

    private fun metricsDiffer(a: DeviceDisplayMetrics?, b: DeviceDisplayMetrics): Boolean {
        if (a == null) return true
        return a.screenWidthPx != b.screenWidthPx ||
            a.screenHeightPx != b.screenHeightPx ||
            a.xdpi != b.xdpi ||
            a.density != b.density ||
            a.fontScale != b.fontScale ||
            a.detectedCutoutWidthPx != b.detectedCutoutWidthPx
    }

    internal enum class CalibrationState { IDLE, OPENING, OPEN, FAILED }

    companion object {
        const val NOTIFICATION_ID = 7001
        private const val ACTION_STOP = "dev.atoll.island.action.STOP_LIVE_ALERT"
        private const val ACTION_START_CALIBRATION = "dev.atoll.island.action.START_CALIBRATION"
        private const val ACTION_STOP_CALIBRATION = "dev.atoll.island.action.STOP_CALIBRATION"
        private const val ACTION_DEBUG_SAMPLE = "dev.atoll.island.action.DEBUG_SAMPLE"
        private const val ACTION_REFRESH_CARD = "dev.atoll.island.action.REFRESH_CARD"
        private const val EXTRA_SAMPLE = "sample"
        private val VERIFICATION_DELAYS_MS = longArrayOf(350L, 1_200L, 9_500L)
        private const val RECHECK_INTERVAL_MS = 30_000L

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _diagnostics = MutableStateFlow(PromotionDiagnostics())
        val diagnostics: StateFlow<PromotionDiagnostics> = _diagnostics.asStateFlow()

        private val _displayMetrics = MutableStateFlow(DeviceDisplayMetrics())
        val displayMetrics: StateFlow<DeviceDisplayMetrics> = _displayMetrics.asStateFlow()

        private val _adaptiveResult = MutableStateFlow<AdaptivePaddingResult?>(null)
        val adaptiveResult: StateFlow<AdaptivePaddingResult?> = _adaptiveResult.asStateFlow()

        private val _calibrationState = MutableStateFlow(CalibrationState.IDLE)
        internal val calibrationState = _calibrationState.asStateFlow()

        internal fun startCalibration(context: Context) {
            if (_calibrationState.value == CalibrationState.OPEN) return
            _calibrationState.value = CalibrationState.OPENING
            val intent = Intent(context, AtollLiveAlertService::class.java).setAction(ACTION_START_CALIBRATION)
            if (isRunning.value) context.startService(intent) else context.startForegroundService(intent)
        }

        internal fun stopCalibration(context: Context) {
            if (!isRunning.value) return
            context.startService(
                Intent(context, AtollLiveAlertService::class.java).setAction(ACTION_STOP_CALIBRATION)
            )
        }

        internal fun setDebugSample(context: Context, text: String?) {
            if (!BuildConfig.DEBUG || !isRunning.value) return
            context.startService(
                Intent(context, AtollLiveAlertService::class.java)
                    .setAction(ACTION_DEBUG_SAMPLE)
                    .putExtra(EXTRA_SAMPLE, text)
            )
        }

        fun refreshCard(context: Context) {
            if (!isRunning.value) return
            runCatching {
                context.startService(Intent(context, AtollLiveAlertService::class.java).setAction(ACTION_REFRESH_CARD))
            }
        }

        fun start(context: Context) {
            val intent = Intent(context, AtollLiveAlertService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AtollLiveAlertService::class.java))
        }

        fun refreshDiagnostics(
            context: Context,
            config: LiveAlertConfig,
            metricsOverride: DeviceDisplayMetrics? = null,
            solvedOverride: AdaptivePaddingResult? = null
        ) {
            val (metrics, solved) = if (metricsOverride != null && solvedOverride != null) {
                metricsOverride to solvedOverride
            } else {
                LiveAlertNotificationBuilder.solveForDevice(context, config)
            }
            _displayMetrics.value = metrics
            _adaptiveResult.value = solved
            _diagnostics.value = LiveAlertNotificationBuilder.evaluateDiagnostics(
                context = context,
                config = config,
                isServiceRunning = _isRunning.value,
                solvedResult = solved
            )
        }
    }
}
