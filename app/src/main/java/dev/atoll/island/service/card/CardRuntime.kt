package dev.atoll.island.service.card

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import dev.atoll.island.domain.card.DeviceStats
import kotlinx.coroutines.delay

object DeviceStatsReader {
    // il broadcast della batteria è sticky: con receiver null si legge senza registrarsi
    fun read(context: Context): DeviceStats {
        val battery = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val memory = runCatching {
            ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(it) }
        }.getOrNull()
        return DeviceStats(
            batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else null,
            batteryTempC = if (temp != Int.MIN_VALUE) temp / 10f else null,
            freeRamGb = memory?.availMem?.takeIf { it > 0 }?.let { it / 1_000_000_000f }
        )
    }
}

// conferma breve nel sottotitolo ("Copiato: ..."), vive solo in memoria
object CardTransientState {
    @Volatile private var text: String? = null
    @Volatile private var untilMs = 0L

    const val SHOW_MS = 2_500L

    fun show(message: String) {
        text = message
        untilMs = SystemClock.elapsedRealtime() + SHOW_MS
    }

    fun current(): String? = text?.takeIf { SystemClock.elapsedRealtime() < untilMs }
}

object TorchController {
    @Volatile private var cameraId: String? = null
    @Volatile private var torchOn: Boolean? = null
    @Volatile private var registered = false

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) torchOn = enabled
        }

        override fun onTorchModeUnavailable(id: String) {
            if (id == cameraId) torchOn = false
        }
    }

    fun ensureRegistered(context: Context) {
        if (registered) return
        val manager = context.getSystemService(CameraManager::class.java) ?: return
        cameraId = runCatching {
            manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
        if (cameraId == null) return
        runCatching { manager.registerTorchCallback(callback, Handler(Looper.getMainLooper())) }
            .onSuccess { registered = true }
    }

    // false = torcia occupata o assente; non serve il permesso della fotocamera
    suspend fun toggle(context: Context): Boolean {
        ensureRegistered(context)
        val id = cameraId ?: return false
        if (torchOn == null) delay(200)
        val manager = context.getSystemService(CameraManager::class.java) ?: return false
        return try {
            manager.setTorchMode(id, torchOn != true)
            true
        } catch (error: CameraAccessException) {
            Log.w("AtollTorch", "Torch unavailable", error)
            false
        } catch (error: IllegalArgumentException) {
            Log.w("AtollTorch", "Torch unavailable", error)
            false
        }
    }
}
