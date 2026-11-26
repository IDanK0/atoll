package dev.atoll.island.service.card

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.util.Log
import dev.atoll.island.data.model.ChargeState
import dev.atoll.island.domain.card.DeviceStats
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.abs

object DeviceStatsReader {
    // il broadcast della batteria è sticky: con receiver null si legge senza registrarsi
    fun read(context: Context): DeviceStats {
        val battery = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val voltageMv = battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1

        val bm = context.getSystemService(BatteryManager::class.java)
        val rawCurrent = runCatching { bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }
            .getOrNull()?.takeIf { it != Int.MIN_VALUE }
        val chargeState = resolveChargeState(status, plugged)
        val powerWatts = if (rawCurrent != null && voltageMv > 0) {
            val mag = (abs(rawCurrent) / 1_000_000f) * (voltageMv / 1000f)
            when {
                mag < 0.05f -> 0f
                status == BatteryManager.BATTERY_STATUS_CHARGING -> mag
                status == BatteryManager.BATTERY_STATUS_DISCHARGING -> -mag
                // con carica piena o stato ignoto manteniamo il segno letto dal driver
                else -> (rawCurrent / 1_000_000f) * (voltageMv / 1000f)
            }
        } else null

        val chargeTimeMinutes = if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
            runCatching { bm?.computeChargeTimeRemaining() ?: -1L }.getOrDefault(-1L)
                .takeIf { it > 0L }
                ?.let { ((it + 30_000L) / 60_000L).toInt().coerceAtLeast(1) }
        } else null

        val cycles = if (Build.VERSION.SDK_INT >= 34) {
            battery?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)?.takeIf { it >= 0 }
        } else null

        val memory = runCatching {
            ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(it) }
        }.getOrNull()

        val storage = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        val storageFreeGb = storage?.availableBytes?.takeIf { it > 0 }?.let { it / 1_000_000_000f }
        val storageTotalGb = storage?.totalBytes?.takeIf { it > 0 }?.let { it / 1_000_000_000f }

        val thermal = runCatching { context.getSystemService(PowerManager::class.java)?.currentThermalStatus }
            .getOrNull()?.takeIf { it >= 0 }

        val uptimeMinutes = (SystemClock.elapsedRealtime() / 60_000L).coerceAtLeast(0L)

        return DeviceStats(
            batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else null,
            batteryPowerWatts = powerWatts,
            chargeState = chargeState,
            chargeTimeMinutes = chargeTimeMinutes,
            batteryTempC = if (temp != Int.MIN_VALUE) temp / 10f else null,
            batteryCycles = cycles,
            freeRamGb = memory?.availMem?.takeIf { it > 0 }?.let { it / 1_000_000_000f },
            storageFreeGb = storageFreeGb,
            storageTotalGb = storageTotalGb,
            cpuMaxMhz = readCpuMaxMhz(),
            thermalStatus = thermal,
            uptimeMinutes = uptimeMinutes
        )
    }

    private fun resolveChargeState(status: Int, plugged: Int): ChargeState? = when {
        status == -1 -> null
        status == BatteryManager.BATTERY_STATUS_FULL -> ChargeState.FULL
        (plugged and BatteryManager.BATTERY_PLUGGED_AC) != 0 -> ChargeState.AC
        (plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0 -> ChargeState.WIRELESS
        (plugged and BatteryManager.BATTERY_PLUGGED_USB) != 0 -> ChargeState.USB
        else -> ChargeState.DISCHARGING
    }

    private fun readCpuMaxMhz(): Int? {
        var maxKhz = -1
        for (cpu in 0 until 16) {
            val file = File("/sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_cur_freq")
            if (!file.exists()) {
                if (cpu == 0) return null else break
            }
            val khz = runCatching { file.readText().trim().toIntOrNull() }.getOrNull()
            if (khz != null && khz > maxKhz) maxKhz = khz
        }
        return if (maxKhz > 0) maxKhz / 1000 else null
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
