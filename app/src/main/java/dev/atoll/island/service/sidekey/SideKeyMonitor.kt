package dev.atoll.island.service.sidekey

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import dev.atoll.island.R
import dev.atoll.island.data.model.SideKeyAction
import dev.atoll.island.data.model.SideKeyConfig
import dev.atoll.island.data.model.SideKeyDiagnostics
import dev.atoll.island.data.model.SideKeyGesture
import dev.atoll.island.data.model.SideKeyStatus
import dev.atoll.island.data.prefs.LiveAlertPreferencesRepository
import dev.atoll.island.domain.sidekey.SideKeyGestureDetector
import dev.atoll.island.domain.sidekey.SideKeyLogParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class SideKeyMonitor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val repository: LiveAlertPreferencesRepository
) {
    private val logReader = SideKeyLogReader()
    private val timerJobs = mutableMapOf<Long, Job>()
    private var monitorJob: Job? = null
    private var collectorJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile
    private var currentConfig: SideKeyConfig = repository.sideKeyConfig.value

    private val detector = SideKeyGestureDetector(
        scheduler = object : SideKeyGestureDetector.TimerScheduler {
            override fun schedule(token: Long, delayMs: Long, block: () -> Unit) {
                timerJobs.remove(token)?.cancel()
                timerJobs[token] = scope.launch {
                    delay(delayMs)
                    timerJobs.remove(token)
                    block()
                }
            }

            override fun cancel(token: Long) {
                timerJobs.remove(token)?.cancel()
            }
        },
        onGesture = { gesture -> handleGesture(gesture) }
    )

    fun start() {
        if (collectorJob != null) return
        collectorJob = scope.launch {
            repository.sideKeyConfig.collect { config ->
                applyConfig(config)
            }
        }
    }

    fun ensureStarted() {
        val latest = repository.sideKeyConfig.value
        applyConfig(latest, forceRestart = false)
    }

    fun reconnect() {
        applyConfig(repository.sideKeyConfig.value, forceRestart = true)
    }

    fun applyConfig(config: SideKeyConfig, forceRestart: Boolean = false) {
        val wasEnabled = currentConfig.enabled
        val patternChanged = currentConfig.filterPattern != config.filterPattern
        currentConfig = config
        detector.updateConfig(SideKeyGestureDetector.Config.from(config))

        if (!config.enabled) {
            stopReader(SideKeyStatus.DISABLED, error = null)
            return
        }

        if (!ShizukuGrantHelper.hasReadLogsPermission(context)) {
            stopReader(SideKeyStatus.NEEDS_ADB_GRANT, error = null)
            return
        }

        syncWakeLock(hold = config.workWithScreenOff && _diagnostics.value.status == SideKeyStatus.LISTENING)

        if (!wasEnabled || forceRestart || patternChanged || monitorJob?.isActive != true) {
            startReader()
        } else {
            refreshStaticPermissions(context, _diagnostics.value.status)
        }
    }

    private fun startReader() {
        monitorJob?.cancel()
        detector.reset()
        updateDiagnostics {
            it.copy(
                status = SideKeyStatus.WAITING_CONSENT,
                hasReadLogsPermission = true,
                hasOverlayPermission = Settings.canDrawOverlays(context),
                hasDndAccess = SideKeyActionExecutor.hasDndAccess(context),
                errorMessage = null
            )
        }
        syncWakeLock(hold = false)

        monitorJob = scope.launch {
            try {
                val (exitReason, err) = logReader.streamEvents(
                    patternProvider = { currentConfig.filterPattern },
                    onListening = {
                        withContext(Dispatchers.Main.immediate) {
                            val holdLock = currentConfig.enabled && currentConfig.workWithScreenOff
                            syncWakeLock(hold = holdLock)
                            updateDiagnostics {
                                it.copy(status = SideKeyStatus.LISTENING, errorMessage = null)
                            }
                        }
                    },
                    onEvent = { event ->
                        withContext(Dispatchers.Main.immediate) {
                            if (capturePaused) {
                                detector.reset()
                                return@withContext
                            }
                            if (currentConfig.enabled && currentConfig.workWithScreenOff && wakeLock?.isHeld != true) {
                                syncWakeLock(hold = true)
                            }
                            updateDiagnostics {
                                it.copy(
                                    lastRawEventTimestampMs = event.timestampMs,
                                    sessionPressCount = if (event.edge == SideKeyLogParser.KeyEdge.DOWN) {
                                        it.sessionPressCount + 1
                                    } else it.sessionPressCount
                                )
                            }
                            when (event.edge) {
                                SideKeyLogParser.KeyEdge.DOWN -> detector.onDown(event.timestampMs)
                                SideKeyLogParser.KeyEdge.UP -> detector.onUp(event.timestampMs)
                            }
                        }
                    }
                )

                if (!currentConfig.enabled) {
                    updateDiagnostics { it.copy(status = SideKeyStatus.DISABLED, errorMessage = null) }
                    return@launch
                }
                val nextStatus = when {
                    !ShizukuGrantHelper.hasReadLogsPermission(context) -> SideKeyStatus.NEEDS_ADB_GRANT
                    exitReason == SideKeyLogReader.ExitReason.PERMISSION_DENIED -> SideKeyStatus.NEEDS_ADB_GRANT
                    exitReason == SideKeyLogReader.ExitReason.CONSENT_REQUIRED -> SideKeyStatus.WAITING_CONSENT
                    else -> SideKeyStatus.ERROR
                }
                updateDiagnostics {
                    it.copy(
                        status = nextStatus,
                        hasReadLogsPermission = ShizukuGrantHelper.hasReadLogsPermission(context),
                        errorMessage = err
                    )
                }
            } finally {
                if (isActive) syncWakeLock(hold = false)
            }
        }
    }

    private fun handleGesture(gesture: SideKeyGesture) {
        val config = currentConfig
        val binding = config.bindingFor(gesture)
        if (!binding.action.isAssigned) return

        updateDiagnostics { it.copy(lastParsedGesture = gesture) }
        scope.launch {
            val result = SideKeyActionExecutor.execute(
                context = context,
                action = binding.action,
                unlockedOnly = binding.unlockedOnly,
                ignoreLockCheck = false
            )
            when (result) {
                ActionExecutionResult.Executed -> if (config.hapticFeedback) {
                    SideKeyActionExecutor.vibrateTrigger(context, heavy = gesture == SideKeyGesture.LONG_PRESS)
                }
                ActionExecutionResult.NeedsDndAccess -> {
                    Log.w(TAG, "Action $gesture needs DND policy access")
                    promptDndAccess(context)
                }
                ActionExecutionResult.NeedsShizuku ->
                    Toast.makeText(context, R.string.sidekey_warn_shizuku_title, Toast.LENGTH_SHORT).show()
                ActionExecutionResult.BlockedLocked, ActionExecutionResult.Unavailable -> Unit
            }
        }
    }

    private fun stopReader(status: SideKeyStatus, error: String?) {
        monitorJob?.cancel()
        monitorJob = null
        detector.reset()
        cancelAllTimers()
        syncWakeLock(hold = false)
        refreshStaticPermissions(context, status, error)
    }

    @SuppressLint("Wakelock")
    private fun syncWakeLock(hold: Boolean) {
        try {
            if (hold) {
                val lock = wakeLock ?: context.getSystemService(PowerManager::class.java)
                    ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                    ?.apply { setReferenceCounted(false) }
                    ?.also { wakeLock = it }
                if (lock != null && !lock.isHeld) {
                    lock.acquire(WAKE_LOCK_LEASE_MS)
                }
            } else {
                wakeLock?.let { if (it.isHeld) it.release() }
            }
        } catch (error: Exception) {
            Log.w(TAG, "WakeLock update failed", error)
        }
    }

    private fun cancelAllTimers() {
        timerJobs.values.forEach { it.cancel() }
        timerJobs.clear()
    }

    fun destroy() {
        collectorJob?.cancel()
        collectorJob = null
        stopReader(SideKeyStatus.DISABLED, error = null)
    }

    companion object {
        private const val TAG = "AtollSideKeyMonitor"
        private const val WAKE_LOCK_TAG = "atoll:SideKeyWakeLock"
        private const val WAKE_LOCK_LEASE_MS = 24L * 60L * 60L * 1000L

        @Volatile
        internal var capturePaused: Boolean = false

        private val _diagnostics = MutableStateFlow(SideKeyDiagnostics())
        val diagnostics: StateFlow<SideKeyDiagnostics> = _diagnostics.asStateFlow()

        private fun updateDiagnostics(transform: (SideKeyDiagnostics) -> SideKeyDiagnostics) {
            _diagnostics.value = transform(_diagnostics.value)
        }

        fun refreshStaticPermissions(
            context: Context,
            statusOverride: SideKeyStatus? = null,
            errorOverride: String? = null
        ) {
            val shizuku = ShizukuGrantHelper.inspect(context)
            val hasReadLogs = ShizukuGrantHelper.hasReadLogsPermission(context)
            val enabled = LiveAlertPreferencesRepository.getInstance(context).sideKeyConfig.value.enabled
            val computedStatus = statusOverride ?: when {
                !enabled -> SideKeyStatus.DISABLED
                !hasReadLogs -> SideKeyStatus.NEEDS_ADB_GRANT
                else -> _diagnostics.value.status.let {
                    if (it == SideKeyStatus.DISABLED || it == SideKeyStatus.NEEDS_ADB_GRANT) {
                        SideKeyStatus.WAITING_CONSENT
                    } else it
                }
            }
            updateDiagnostics {
                it.copy(
                    status = computedStatus,
                    hasReadLogsPermission = hasReadLogs,
                    hasOverlayPermission = Settings.canDrawOverlays(context),
                    hasDndAccess = SideKeyActionExecutor.hasDndAccess(context),
                    shizukuInstalled = shizuku.installed,
                    shizukuRunning = shizuku.running,
                    shizukuPermissionGranted = shizuku.permissionGranted,
                    errorMessage = errorOverride ?: if (computedStatus != SideKeyStatus.ERROR) null else it.errorMessage
                )
            }
        }

        fun promptDndAccess(context: Context) {
            Toast.makeText(context, R.string.sidekey_dnd_needed_toast, Toast.LENGTH_LONG).show()
            if (Settings.canDrawOverlays(context)) {
                SideKeyActionExecutor.openDndAccessSettings(context, newTask = true)
            }
        }
    }
}
