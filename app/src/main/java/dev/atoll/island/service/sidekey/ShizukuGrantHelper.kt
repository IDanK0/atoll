package dev.atoll.island.service.sidekey

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

data class ShizukuStatus(
    val installed: Boolean = false,
    val running: Boolean = false,
    val permissionGranted: Boolean = false
)

object ShizukuGrantHelper {
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val REQUEST_CODE_SHIZUKU = 8104

    fun hasReadLogsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED

    fun inspect(context: Context): ShizukuStatus {
        val installed = runCatching {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        }.getOrDefault(false)
        val running = if (installed) {
            runCatching { !Shizuku.isPreV11() && Shizuku.pingBinder() }.getOrDefault(false)
        } else {
            false
        }
        val permitted = running && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        return ShizukuStatus(installed = installed, running = running, permissionGranted = permitted)
    }

    fun requestPermission() {
        runCatching {
            if (!Shizuku.isPreV11() && Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
            ) {
                Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
            }
        }
    }

    suspend fun grantReadLogs(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (hasReadLogsPermission(context)) return@withContext true
        val ok = runShellCommand(context, "pm", "grant", context.packageName, Manifest.permission.READ_LOGS)
        ok && hasReadLogsPermission(context)
    }

    suspend fun setRingerMode(context: Context, modeName: String): Boolean = withContext(Dispatchers.IO) {
        runShellCommand(context, "cmd", "audio", "set-ringer-mode", modeName)
    }

    private fun runShellCommand(context: Context, vararg command: String): Boolean {
        val status = inspect(context)
        if (!status.running || !status.permissionGranted) return false
        return runCatching {
            val service = IShizukuService.Stub.asInterface(Shizuku.getBinder()) ?: return@runCatching false
            val proc = service.newProcess(command, null, null)
            try {
                proc.inputStream?.close()
                proc.errorStream?.close()
                proc.waitFor() == 0
            } finally {
                runCatching { proc.destroy() }
            }
        }.getOrDefault(false)
    }
}
