package dev.atoll.island.data.model

import dev.atoll.island.domain.CapsulePaddingGenerator

enum class CapsuleScalingMode {
    PROPORTIONAL,
    PHYSICAL_MM;

    companion object {
        fun fromName(name: String?): CapsuleScalingMode =
            entries.firstOrNull { it.name == name } ?: PROPORTIONAL
    }
}

data class DeviceDisplayMetrics(
    val screenWidthPx: Int = 1080,
    val screenHeightPx: Int = 2400,
    val xdpi: Float = 440f,
    val density: Float = 3.0f,
    val fontScale: Float = 1.0f,
    val detectedCutoutWidthPx: Int = 0
) {
    val screenWidthDp: Float
        get() = if (density > 0f) screenWidthPx / density else screenWidthPx.toFloat()
}

data class GlyphBreakdownItem(
    val glyph: PaddingGlyph,
    val count: Int,
    val unitWeight: Float
)

data class AdaptivePaddingResult(
    val text: String,
    val targetWidthDp: Float,
    val estimatedWidthDp: Float,
    val targetCapsulePx: Float,
    val estimatedActualPx: Float,
    val errorPx: Float,
    val neededUnits: Float,
    val achievedUnits: Float,
    val pillFloorDp: Float,
    val pillOriginDp: Float,
    val effectiveDpPerUnit: Float,
    val wasClampedByMin: Boolean,
    val wasClampedByMax: Boolean,
    val targetMm: Float,
    val targetScreenPercent: Float,
    val breakdown: List<GlyphBreakdownItem> = emptyList(),
    val isManualCustom: Boolean = false
)

data class LiveAlertConfig(
    val enabled: Boolean = false,
    val selectedPreset: CapsulePreset = CapsulePreset.IPHONE_16_PRO_17,
    val scalingMode: CapsuleScalingMode = CapsuleScalingMode.PROPORTIONAL,
    val pillFloorDp: Float = DEFAULT_PILL_FLOOR_DP,
    val pillOriginDp: Float = DEFAULT_PILL_ORIGIN_DP,
    val effectiveDpPerUnit: Float = DEFAULT_EFFECTIVE_DP_PER_UNIT,
    val isDeviceCalibrated: Boolean = false,
    val brailleOnlySafeAnchor: Boolean = false,
    val physicalCalibrationFactor: Float = DEFAULT_PHYSICAL_CALIBRATION,
    val maxChipChars: Int = CapsulePaddingGenerator.ANDROID_GUARANTEED_CHIP_CHARS,
    // larghezza voluta per CUSTOM
    val customWidthDp: Float = DEFAULT_CUSTOM_WIDTH_DP,
    // modalità test: manda il testo così com'è
    val manualTextEnabled: Boolean = false,
    val customLength: Int = 4,
    val customGlyph: PaddingGlyph = PaddingGlyph.BRAILLE_BLANK,
    val detectedCutoutWidthPx: Int = 0,
    val debugVisibleMode: Boolean = false,
    val debugGlyph: PaddingGlyph = PaddingGlyph.DEBUG_BULLET,
    val channelMode: NotificationChannelMode = NotificationChannelMode.LOW,
    val templateStyle: NotificationTemplateStyle = NotificationTemplateStyle.STANDARD,
    val progressIndeterminate: Boolean = false,
    val smallIconMode: SmallIconMode = SmallIconMode.TRANSPARENT,
    val titleMode: TitleMode = TitleMode.BRAILLE_BLANK,
    val card: ExpandedCardConfig = ExpandedCardConfig()
) {
    companion object {
        // capsula = max(floor, origin + unità * dpPerUnit), valori presi dal OnePlus 15
        const val MIN_PILL_FLOOR_DP = 40f
        const val MAX_PILL_FLOOR_DP = 160f
        const val DEFAULT_PILL_FLOOR_DP = 99.4f

        const val MIN_PILL_ORIGIN_DP = 0f
        const val MAX_PILL_ORIGIN_DP = 160f
        const val DEFAULT_PILL_ORIGIN_DP = 59.3f

        const val MIN_EFFECTIVE_DP_PER_UNIT = 4.0f
        const val MAX_EFFECTIVE_DP_PER_UNIT = 40.0f
        const val DEFAULT_EFFECTIVE_DP_PER_UNIT = 16.6f

        const val MIN_CUSTOM_WIDTH_DP = 40f
        const val MAX_CUSTOM_WIDTH_DP = 240f
        const val DEFAULT_CUSTOM_WIDTH_DP = 115f

        const val MIN_PHYSICAL_CALIBRATION = 0.80f
        const val MAX_PHYSICAL_CALIBRATION = 1.20f
        const val DEFAULT_PHYSICAL_CALIBRATION = 1.00f
    }
}

data class PromotionDiagnostics(
    val apiLevel: Int = 0,
    val isAndroid16Plus: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val canPostPromotedNotifications: Boolean = false,
    val builtNotificationEligible: Boolean = false,
    val isServiceRunning: Boolean = false,
    val shownAsLiveAlert: Boolean = false,
    val activeNotificationFlags: Int = 0,
    val shortCriticalTextLength: Int = 0,
    val activeStyle: String = ""
)
