package dev.atoll.island.domain.sidekey

import dev.atoll.island.data.model.MacroStep
import dev.atoll.island.data.model.SideKeyAction
import dev.atoll.island.data.model.SideKeyConfig
import dev.atoll.island.data.model.SideKeyGesture
import dev.atoll.island.data.model.SideKeyGestureBinding
import dev.atoll.island.data.model.SideKeyRingerMode
import dev.atoll.island.domain.card.ActionSlots
import dev.atoll.island.domain.card.Json
import dev.atoll.island.domain.card.SimpleJson

object SideKeyCodec {

    fun encodeBindings(bindings: Map<SideKeyGesture, SideKeyGestureBinding>): String {
        val payload = linkedMapOf<String, Map<String, Any?>>()
        for (gesture in SideKeyGesture.entries) {
            val binding = bindings[gesture] ?: continue
            if (!binding.action.isAssigned && !binding.unlockedOnly) continue
            payload[gesture.name] = actionFields(binding.action) + ("unlocked" to binding.unlockedOnly)
        }
        return Json.write(payload)
    }

    fun decodeBindings(raw: String?): Map<SideKeyGesture, SideKeyGestureBinding> {
        if (raw.isNullOrBlank()) return emptyMap()
        val parsed = runCatching { SimpleJson.parse(raw) }.getOrNull() as? Map<*, *> ?: return emptyMap()
        val out = linkedMapOf<SideKeyGesture, SideKeyGestureBinding>()
        for ((k, v) in parsed) {
            val gesture = SideKeyGesture.entries.firstOrNull { it.name == k.toString() } ?: continue
            val map = v as? Map<*, *> ?: continue
            val action = parseAction(map) ?: continue
            val unlocked = map["unlocked"] as? Boolean ?: false
            if (action.isAssigned || unlocked) {
                out[gesture] = SideKeyGestureBinding(action = action, unlockedOnly = unlocked)
            }
        }
        return out
    }

    fun isValidAction(action: SideKeyAction): Boolean = when (action) {
        is SideKeyAction.LaunchApp -> action.packageName.isNotBlank()
        is SideKeyAction.AppShortcut -> isValidIntentUri(action.intentUri)
        is SideKeyAction.DialNumber -> ActionSlots.normalizePhone(action.phoneNumber) != null
        is SideKeyAction.StartTimer -> action.seconds in 1..ActionSlots.MAX_TIMER_SECONDS
        is SideKeyAction.CopySnippet -> action.text.isNotEmpty()
        is SideKeyAction.OpenUrl -> ActionSlots.normalizeUrl(action.url) != null
        is SideKeyAction.CustomIntent -> isValidIntentUri(action.intentUri)
        is SideKeyAction.CustomNotification ->
            (action.title.isNotBlank() || action.message.isNotBlank()) &&
                action.delaySeconds in 0..SideKeyConfig.MAX_NOTIF_DELAY_SECONDS
        is SideKeyAction.Macro -> action.steps.isNotEmpty() &&
            action.steps.size <= SideKeyConfig.MAX_MACRO_STEPS &&
            action.steps.all { step ->
                step.action != SideKeyAction.None &&
                    step.action !is SideKeyAction.Macro &&
                    isValidAction(step.action)
            }
        else -> true
    }

    fun isValidIntentUri(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.isNotEmpty() && !trimmed.startsWith("javascript:", ignoreCase = true)
    }

    private fun actionFields(action: SideKeyAction): Map<String, Any?> = when (action) {
        SideKeyAction.None -> mapOf("type" to "none")
        is SideKeyAction.LaunchApp -> mapOf("type" to "app", "pkg" to action.packageName, "label" to action.appLabel)
        is SideKeyAction.AppShortcut -> mapOf(
            "type" to "shortcut",
            "uri" to action.intentUri,
            "label" to action.label,
            "pkg" to action.packageName,
            "iconId" to action.iconId
        )
        is SideKeyAction.DialNumber -> mapOf("type" to "dial", "phone" to action.phoneNumber, "label" to action.label)
        is SideKeyAction.StartTimer -> mapOf("type" to "timer", "sec" to action.seconds, "label" to action.label)
        is SideKeyAction.CopySnippet -> mapOf("type" to "copy", "label" to action.label, "text" to action.text)
        is SideKeyAction.OpenUrl -> mapOf("type" to "url", "url" to action.url, "label" to action.label)
        is SideKeyAction.CustomIntent -> mapOf("type" to "intent", "uri" to action.intentUri, "label" to action.label)
        is SideKeyAction.CustomNotification -> mapOf(
            "type" to "notification",
            "title" to action.title,
            "msg" to action.message,
            "pkg" to action.appPackage,
            "appLabel" to action.appLabel,
            "icon" to action.iconKey,
            "waitSec" to action.delaySeconds,
            "headsUp" to action.headsUp
        )
        SideKeyAction.ToggleFlashlight -> mapOf("type" to "flashlight")
        is SideKeyAction.RingerMode -> mapOf(
            "type" to "ringer",
            "mode" to action.mode.name,
            "toggleBack" to action.toggleBack
        )
        SideKeyAction.ToggleDnd -> mapOf("type" to "dnd")
        SideKeyAction.MediaPlayPause -> mapOf("type" to "media_play_pause")
        SideKeyAction.MediaNext -> mapOf("type" to "media_next")
        SideKeyAction.MediaPrevious -> mapOf("type" to "media_previous")
        SideKeyAction.VolumeUp -> mapOf("type" to "volume_up")
        SideKeyAction.VolumeDown -> mapOf("type" to "volume_down")
        SideKeyAction.ToggleMute -> mapOf("type" to "toggle_mute")
        SideKeyAction.VolumePanel -> mapOf("type" to "volume_panel")
        SideKeyAction.ExpandNotifications -> mapOf("type" to "expand_notifications")
        SideKeyAction.ExpandQuickSettings -> mapOf("type" to "expand_quick_settings")
        SideKeyAction.WifiPanel -> mapOf("type" to "wifi_panel")
        SideKeyAction.BluetoothSettings -> mapOf("type" to "bluetooth_settings")
        SideKeyAction.InternetPanel -> mapOf("type" to "internet_panel")
        SideKeyAction.LaunchCamera -> mapOf("type" to "camera")
        SideKeyAction.LaunchVideoCamera -> mapOf("type" to "video_camera")
        SideKeyAction.LaunchVoiceRecorder -> mapOf("type" to "voice_recorder")
        SideKeyAction.LaunchAssistant -> mapOf("type" to "assistant")
        SideKeyAction.GoHome -> mapOf("type" to "go_home")
        SideKeyAction.ToggleCapsule -> mapOf("type" to "toggle_capsule")
        SideKeyAction.CyclePreset -> mapOf("type" to "cycle_preset")
        SideKeyAction.OpenCalibration -> mapOf("type" to "open_calibration")
        SideKeyAction.CounterIncrement -> mapOf("type" to "counter_plus")
        SideKeyAction.CounterDecrement -> mapOf("type" to "counter_minus")
        SideKeyAction.OpenNoteEditor -> mapOf("type" to "note_editor")
        is SideKeyAction.Macro -> mapOf(
            "type" to "macro",
            "label" to action.label,
            "steps" to action.steps.map { step ->
                actionFields(step.action) + ("delay" to step.delayAfterMs)
            }
        )
    }

    private fun parseAction(map: Map<*, *>, allowMacro: Boolean = true): SideKeyAction? {
        val action = when (map["type"] as? String) {
            "none" -> SideKeyAction.None
            "app" -> SideKeyAction.LaunchApp(
                packageName = (map["pkg"] as? String).orEmpty(),
                appLabel = (map["label"] as? String).orEmpty()
            )
            "shortcut" -> SideKeyAction.AppShortcut(
                intentUri = (map["uri"] as? String).orEmpty(),
                label = (map["label"] as? String).orEmpty(),
                packageName = (map["pkg"] as? String).orEmpty(),
                iconId = (map["iconId"] as? String).orEmpty()
            )
            "dial" -> SideKeyAction.DialNumber(
                phoneNumber = (map["phone"] as? String).orEmpty(),
                label = (map["label"] as? String).orEmpty()
            )
            "timer" -> SideKeyAction.StartTimer(
                seconds = (map["sec"] as? Number)?.toInt() ?: 0,
                label = (map["label"] as? String).orEmpty()
            )
            "copy" -> SideKeyAction.CopySnippet(
                label = (map["label"] as? String).orEmpty(),
                text = (map["text"] as? String).orEmpty()
            )
            "url" -> SideKeyAction.OpenUrl(
                url = (map["url"] as? String).orEmpty(),
                label = (map["label"] as? String).orEmpty()
            )
            "intent" -> SideKeyAction.CustomIntent(
                intentUri = (map["uri"] as? String).orEmpty(),
                label = (map["label"] as? String).orEmpty()
            )
            "notification" -> SideKeyAction.CustomNotification(
                title = (map["title"] as? String).orEmpty(),
                message = (map["msg"] as? String).orEmpty(),
                appPackage = (map["pkg"] as? String).orEmpty(),
                appLabel = (map["appLabel"] as? String).orEmpty(),
                iconKey = (map["icon"] as? String).orEmpty().ifBlank { SideKeyConfig.NOTIF_ICON_CHAT },
                delaySeconds = ((map["waitSec"] as? Number)?.toInt() ?: 0).coerceIn(0, SideKeyConfig.MAX_NOTIF_DELAY_SECONDS),
                headsUp = map["headsUp"] as? Boolean ?: true
            )
            "flashlight" -> SideKeyAction.ToggleFlashlight
            "ringer" -> SideKeyAction.RingerMode(
                mode = SideKeyRingerMode.fromName(map["mode"] as? String),
                toggleBack = map["toggleBack"] as? Boolean ?: false
            )
            "dnd" -> SideKeyAction.ToggleDnd
            "media_play_pause" -> SideKeyAction.MediaPlayPause
            "media_next" -> SideKeyAction.MediaNext
            "media_previous" -> SideKeyAction.MediaPrevious
            "volume_up" -> SideKeyAction.VolumeUp
            "volume_down" -> SideKeyAction.VolumeDown
            "toggle_mute" -> SideKeyAction.ToggleMute
            "volume_panel" -> SideKeyAction.VolumePanel
            "expand_notifications" -> SideKeyAction.ExpandNotifications
            "expand_quick_settings" -> SideKeyAction.ExpandQuickSettings
            "wifi_panel" -> SideKeyAction.WifiPanel
            "bluetooth_settings" -> SideKeyAction.BluetoothSettings
            "internet_panel" -> SideKeyAction.InternetPanel
            "camera" -> SideKeyAction.LaunchCamera
            "video_camera" -> SideKeyAction.LaunchVideoCamera
            "voice_recorder" -> SideKeyAction.LaunchVoiceRecorder
            "assistant" -> SideKeyAction.LaunchAssistant
            "go_home" -> SideKeyAction.GoHome
            "toggle_capsule" -> SideKeyAction.ToggleCapsule
            "cycle_preset" -> SideKeyAction.CyclePreset
            "open_calibration" -> SideKeyAction.OpenCalibration
            "counter_plus" -> SideKeyAction.CounterIncrement
            "counter_minus" -> SideKeyAction.CounterDecrement
            "note_editor" -> SideKeyAction.OpenNoteEditor
            "macro" -> {
                if (!allowMacro) return null
                val rawSteps = map["steps"] as? List<*> ?: return null
                val parsedSteps = rawSteps.mapNotNull { entry ->
                    val stepMap = entry as? Map<*, *> ?: return@mapNotNull null
                    val stepAction = parseAction(stepMap, allowMacro = false) ?: return@mapNotNull null
                    if (stepAction == SideKeyAction.None) return@mapNotNull null
                    val delay = ((stepMap["delay"] as? Number)?.toLong() ?: SideKeyConfig.DEFAULT_MACRO_DELAY_MS)
                        .coerceIn(0L, SideKeyConfig.MAX_MACRO_DELAY_MS)
                    MacroStep(action = stepAction, delayAfterMs = delay)
                }.take(SideKeyConfig.MAX_MACRO_STEPS)
                SideKeyAction.Macro(label = (map["label"] as? String).orEmpty(), steps = parsedSteps)
            }
            else -> return null
        }
        return action.takeIf(::isValidAction)
    }
}
