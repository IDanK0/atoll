package dev.atoll.island.service

import android.Manifest
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import dev.atoll.island.R
import dev.atoll.island.data.model.LiveAlertConfig
import dev.atoll.island.data.prefs.LiveAlertPreferencesRepository
import dev.atoll.island.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AtollTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listenJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val repo = LiveAlertPreferencesRepository.getInstance(applicationContext)
        listenJob?.cancel()
        listenJob = scope.launch {
            combine(
                repo.config,
                AtollLiveAlertService.isRunning
            ) { config, running ->
                config to running
            }.collect { (config, running) ->
                applyTileState(config, running)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        listenJob?.cancel()
        listenJob = null
    }

    override fun onClick() {
        super.onClick()
        val repo = LiveAlertPreferencesRepository.getInstance(applicationContext)
        scope.launch {
            val currentlyRunning = AtollLiveAlertService.isRunning.value
            if (currentlyRunning) {
                repo.setEnabled(false)
                AtollLiveAlertService.stop(applicationContext)
                applyTileState(repo.getLatestConfig().copy(enabled = false), false)
            } else {
                if (!hasNotificationPermission()) {
                    openMainActivity()
                    return@launch
                }
                repo.setEnabled(true)
                AtollLiveAlertService.start(applicationContext)
                applyTileState(repo.getLatestConfig().copy(enabled = true), true)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun applyTileState(config: LiveAlertConfig, running: Boolean) {
        val tile = qsTile ?: return
        val active = running || config.enabled
        tile.label = getString(R.string.qs_tile_label)
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = when {
            !active -> getString(R.string.qs_tile_off)
            !hasNotificationPermission() -> getString(R.string.qs_tile_setup_needed)
            config.selectedPreset.isCustom -> getString(R.string.preset_simple_custom)
            else -> config.selectedPreset.shortName
        }
        tile.updateTile()
    }

    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                101,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            startActivity(intent)
        }
    }

    companion object {
        fun requestTileSync(context: Context) {
            runCatching {
                requestListeningState(
                    context.applicationContext,
                    ComponentName(context.applicationContext, AtollTileService::class.java)
                )
            }
        }
    }
}
