package dev.atoll.island.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.atoll.island.service.AtollLiveAlertService

// adb shell am broadcast -a dev.atoll.island.debug.SAMPLE --es spec 2800x4,2009x1 (senza spec pulisce)
class CapsuleSampleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val spec = intent.getStringExtra("spec")
        val text = spec?.split(',')?.filter { it.isNotBlank() }?.joinToString("") { item ->
            val (code, count) = item.trim().split('x')
            String(Character.toChars(code.toInt(16))).repeat(count.toInt())
        }
        AtollLiveAlertService.setDebugSample(context, text)
    }
}
