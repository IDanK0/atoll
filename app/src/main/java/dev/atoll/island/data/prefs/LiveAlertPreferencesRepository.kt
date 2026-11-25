package dev.atoll.island.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.atoll.island.data.model.AppUiPreferences
import dev.atoll.island.data.model.CapsulePreset
import dev.atoll.island.data.model.CapsuleScalingMode
import dev.atoll.island.data.model.ExpandedCardConfig
import dev.atoll.island.data.model.LiveAlertConfig
import dev.atoll.island.data.model.NotificationChannelMode
import dev.atoll.island.data.model.NotificationTemplateStyle
import dev.atoll.island.data.model.PaddingGlyph
import dev.atoll.island.data.model.SmallIconMode
import dev.atoll.island.data.model.TitleMode
import dev.atoll.island.domain.CalibrationMath
import dev.atoll.island.domain.CapsulePaddingGenerator
import dev.atoll.island.service.AtollTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

private val Context.liveAlertDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "atoll_live_alert_prefs"
)

typealias IslandPreferencesRepository = LiveAlertPreferencesRepository

class LiveAlertPreferencesRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val dataStore = appContext.liveAlertDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val configFlow: Flow<LiveAlertConfig> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs -> prefs.toLiveAlertConfig() }

    val config: StateFlow<LiveAlertConfig> = configFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = LiveAlertConfig()
    )

    val uiPreferencesFlow: Flow<AppUiPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            AppUiPreferences(
                hasCompletedOnboarding = prefs[KEY_HAS_COMPLETED_ONBOARDING] ?: false,
                isAdvancedMode = prefs[KEY_IS_ADVANCED_MODE] ?: false
            )
        }

    val uiPreferences: StateFlow<AppUiPreferences?> = uiPreferencesFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    suspend fun getLatestConfig(): LiveAlertConfig = configFlow.first()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ENABLED] = enabled }
        AtollTileService.requestTileSync(appContext)
    }

    suspend fun selectPreset(preset: CapsulePreset) {
        dataStore.edit { prefs ->
            prefs[KEY_SELECTED_PRESET] = preset.id
            prefs[KEY_MANUAL_TEXT_ENABLED] = false
        }
        AtollTileService.requestTileSync(appContext)
    }

    suspend fun setCustomWidthDp(widthDp: Float) {
        val clamped = widthDp.coerceIn(LiveAlertConfig.MIN_CUSTOM_WIDTH_DP, LiveAlertConfig.MAX_CUSTOM_WIDTH_DP)
        dataStore.edit { prefs ->
            prefs[KEY_SELECTED_PRESET] = CapsulePreset.CUSTOM.id
            prefs[KEY_MANUAL_TEXT_ENABLED] = false
            prefs[KEY_CUSTOM_WIDTH_DP] = clamped
        }
        AtollTileService.requestTileSync(appContext)
    }

    suspend fun setManualTextEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_MANUAL_TEXT_ENABLED] = enabled }
    }

    suspend fun setScalingMode(mode: CapsuleScalingMode) {
        dataStore.edit { it[KEY_SCALING_MODE] = mode.name }
    }

    suspend fun setPillFloorDp(dp: Float) {
        val clamped = dp.coerceIn(LiveAlertConfig.MIN_PILL_FLOOR_DP, LiveAlertConfig.MAX_PILL_FLOOR_DP)
        dataStore.edit { it[KEY_PILL_FLOOR_DP] = clamped }
    }

    suspend fun setPillOriginDp(dp: Float) {
        val clamped = dp.coerceIn(LiveAlertConfig.MIN_PILL_ORIGIN_DP, LiveAlertConfig.MAX_PILL_ORIGIN_DP)
        dataStore.edit { it[KEY_PILL_ORIGIN_DP] = clamped }
    }

    suspend fun setEffectiveDpPerUnit(dpPerUnit: Float) {
        val clamped = dpPerUnit.coerceIn(
            LiveAlertConfig.MIN_EFFECTIVE_DP_PER_UNIT,
            LiveAlertConfig.MAX_EFFECTIVE_DP_PER_UNIT
        )
        dataStore.edit { it[KEY_EFFECTIVE_DP_PER_UNIT] = clamped }
    }

    internal suspend fun applyVisualCalibration(fit: CalibrationMath.Fit): LiveAlertConfig =
        dataStore.edit { prefs ->
            prefs[KEY_PILL_FLOOR_DP] = fit.floorDp
            prefs[KEY_PILL_ORIGIN_DP] = fit.originDp
            prefs[KEY_EFFECTIVE_DP_PER_UNIT] = fit.dpPerUnit
            prefs[KEY_IS_DEVICE_CALIBRATED] = true
        }.toLiveAlertConfig()

    internal suspend fun getCalibrationOverlayPreferences(): CalibrationOverlayPreferences =
        CalibrationOverlayPreferences.read(dataStore.data.first())

    internal suspend fun setCalibrationYOffsetPx(offsetPx: Int) {
        dataStore.edit { CalibrationOverlayPreferences.writeYOffset(it, offsetPx) }
    }

    internal suspend fun saveCalibrationGeometry(cutoutWidthPx: Int, centerYpx: Int, heightPx: Int) {
        dataStore.edit { prefs ->
            if (cutoutWidthPx > 0) prefs[KEY_DETECTED_CUTOUT_WIDTH_PX] = cutoutWidthPx
            CalibrationOverlayPreferences.writeGeometry(prefs, centerYpx, heightPx)
        }
    }

    suspend fun setBrailleOnlySafeAnchor(enabled: Boolean) {
        dataStore.edit { it[KEY_BRAILLE_ONLY_SAFE_ANCHOR] = enabled }
    }

    suspend fun setPhysicalCalibrationFactor(factor: Float) {
        val clamped = factor.coerceIn(
            LiveAlertConfig.MIN_PHYSICAL_CALIBRATION,
            LiveAlertConfig.MAX_PHYSICAL_CALIBRATION
        )
        dataStore.edit { it[KEY_PHYSICAL_CALIBRATION] = clamped }
    }

    suspend fun setMaxChipChars(maxChars: Int) {
        val clamped = maxChars.coerceIn(1, CapsulePaddingGenerator.MAX_LENGTH)
        dataStore.edit { it[KEY_MAX_CHIP_CHARS] = clamped }
    }

    // se cambi il testo attivo anche la modalità manuale
    suspend fun setCustomLength(length: Int) {
        val clamped = length.coerceIn(CapsulePaddingGenerator.MIN_LENGTH, CapsulePaddingGenerator.MAX_LENGTH)
        dataStore.edit { prefs ->
            prefs[KEY_MANUAL_TEXT_ENABLED] = true
            prefs[KEY_CUSTOM_LENGTH] = clamped
        }
    }

    suspend fun setCustomGlyph(glyph: PaddingGlyph) {
        if (glyph.isVisibleDebug) return
        dataStore.edit { prefs ->
            prefs[KEY_MANUAL_TEXT_ENABLED] = true
            prefs[KEY_CUSTOM_GLYPH] = glyph.id
        }
    }

    suspend fun resetCalibration() {
        dataStore.edit { prefs ->
            listOf(
                KEY_PILL_FLOOR_DP, KEY_PILL_ORIGIN_DP, KEY_EFFECTIVE_DP_PER_UNIT, KEY_IS_DEVICE_CALIBRATED,
                KEY_BRAILLE_ONLY_SAFE_ANCHOR, KEY_PHYSICAL_CALIBRATION, KEY_MAX_CHIP_CHARS,
                KEY_CUSTOM_LENGTH, KEY_CUSTOM_GLYPH, KEY_MANUAL_TEXT_ENABLED
            ).forEach { prefs.remove(it) }
        }
    }

    suspend fun setDebugVisibleMode(enabled: Boolean) {
        dataStore.edit { it[KEY_DEBUG_VISIBLE_MODE] = enabled }
    }

    suspend fun setDebugGlyph(glyph: PaddingGlyph) {
        if (!glyph.isVisibleDebug) return
        dataStore.edit { it[KEY_DEBUG_GLYPH] = glyph.id }
    }

    suspend fun setChannelMode(mode: NotificationChannelMode) {
        dataStore.edit { it[KEY_CHANNEL_MODE] = mode.name }
    }

    suspend fun setTemplateStyle(style: NotificationTemplateStyle) {
        dataStore.edit { it[KEY_TEMPLATE_STYLE] = style.name }
    }

    suspend fun setProgressIndeterminate(indeterminate: Boolean) {
        dataStore.edit { it[KEY_PROGRESS_INDETERMINATE] = indeterminate }
    }

    suspend fun setSmallIconMode(mode: SmallIconMode) {
        dataStore.edit { it[KEY_SMALL_ICON_MODE] = mode.name }
    }

    suspend fun setTitleMode(mode: TitleMode) {
        dataStore.edit { it[KEY_TITLE_MODE] = mode.name }
    }

    suspend fun updateCard(transform: (ExpandedCardConfig) -> ExpandedCardConfig): ExpandedCardConfig {
        var updated = ExpandedCardConfig()
        dataStore.edit { prefs ->
            updated = transform(ExpandedCardPreferences.read(prefs))
            ExpandedCardPreferences.write(prefs, updated)
        }
        return updated
    }

    suspend fun setHasCompletedOnboarding(completed: Boolean) {
        dataStore.edit { it[KEY_HAS_COMPLETED_ONBOARDING] = completed }
    }

    suspend fun setAdvancedMode(advanced: Boolean) {
        dataStore.edit { it[KEY_IS_ADVANCED_MODE] = advanced }
    }

    suspend fun finishSetup(resetToSimpleMode: Boolean = true) {
        dataStore.edit { prefs ->
            prefs[KEY_HAS_COMPLETED_ONBOARDING] = true
            if (resetToSimpleMode) {
                prefs[KEY_IS_ADVANCED_MODE] = false
            }
            prefs[KEY_ENABLED] = true
        }
        AtollTileService.requestTileSync(appContext)
    }

    private fun Preferences.toLiveAlertConfig(): LiveAlertConfig {
        val selectedPreset = CapsulePreset.fromId(this[KEY_SELECTED_PRESET])
        val debugGlyph = PaddingGlyph.fromId(this[KEY_DEBUG_GLYPH]).let {
            if (it.isVisibleDebug) it else PaddingGlyph.DEBUG_BULLET
        }
        val customGlyph = PaddingGlyph.fromId(this[KEY_CUSTOM_GLYPH]).let {
            if (!it.isVisibleDebug) it else PaddingGlyph.BRAILLE_BLANK
        }

        return LiveAlertConfig(
            enabled = this[KEY_ENABLED] ?: false,
            selectedPreset = selectedPreset,
            scalingMode = CapsuleScalingMode.fromName(this[KEY_SCALING_MODE]),
            pillFloorDp = (this[KEY_PILL_FLOOR_DP] ?: LiveAlertConfig.DEFAULT_PILL_FLOOR_DP)
                .coerceIn(LiveAlertConfig.MIN_PILL_FLOOR_DP, LiveAlertConfig.MAX_PILL_FLOOR_DP),
            pillOriginDp = (this[KEY_PILL_ORIGIN_DP] ?: LiveAlertConfig.DEFAULT_PILL_ORIGIN_DP)
                .coerceIn(LiveAlertConfig.MIN_PILL_ORIGIN_DP, LiveAlertConfig.MAX_PILL_ORIGIN_DP),
            effectiveDpPerUnit = (this[KEY_EFFECTIVE_DP_PER_UNIT] ?: LiveAlertConfig.DEFAULT_EFFECTIVE_DP_PER_UNIT)
                .coerceIn(LiveAlertConfig.MIN_EFFECTIVE_DP_PER_UNIT, LiveAlertConfig.MAX_EFFECTIVE_DP_PER_UNIT),
            isDeviceCalibrated = this[KEY_IS_DEVICE_CALIBRATED] ?: false,
            brailleOnlySafeAnchor = this[KEY_BRAILLE_ONLY_SAFE_ANCHOR] ?: false,
            physicalCalibrationFactor = (this[KEY_PHYSICAL_CALIBRATION] ?: LiveAlertConfig.DEFAULT_PHYSICAL_CALIBRATION)
                .coerceIn(LiveAlertConfig.MIN_PHYSICAL_CALIBRATION, LiveAlertConfig.MAX_PHYSICAL_CALIBRATION),
            maxChipChars = (this[KEY_MAX_CHIP_CHARS] ?: CapsulePaddingGenerator.ANDROID_GUARANTEED_CHIP_CHARS)
                .coerceIn(1, CapsulePaddingGenerator.MAX_LENGTH),
            customWidthDp = (this[KEY_CUSTOM_WIDTH_DP] ?: LiveAlertConfig.DEFAULT_CUSTOM_WIDTH_DP)
                .coerceIn(LiveAlertConfig.MIN_CUSTOM_WIDTH_DP, LiveAlertConfig.MAX_CUSTOM_WIDTH_DP),
            manualTextEnabled = this[KEY_MANUAL_TEXT_ENABLED] ?: false,
            customLength = (this[KEY_CUSTOM_LENGTH] ?: 4)
                .coerceIn(CapsulePaddingGenerator.MIN_LENGTH, CapsulePaddingGenerator.MAX_LENGTH),
            customGlyph = customGlyph,
            detectedCutoutWidthPx = (this[KEY_DETECTED_CUTOUT_WIDTH_PX] ?: 0).coerceAtLeast(0),
            debugVisibleMode = this[KEY_DEBUG_VISIBLE_MODE] ?: false,
            debugGlyph = debugGlyph,
            channelMode = NotificationChannelMode.fromName(this[KEY_CHANNEL_MODE]),
            templateStyle = NotificationTemplateStyle.fromName(this[KEY_TEMPLATE_STYLE]),
            progressIndeterminate = this[KEY_PROGRESS_INDETERMINATE] ?: false,
            smallIconMode = SmallIconMode.fromName(this[KEY_SMALL_ICON_MODE]),
            titleMode = TitleMode.fromName(this[KEY_TITLE_MODE]),
            card = ExpandedCardPreferences.read(this)
        )
    }

    companion object {
        @Volatile
        private var instance: LiveAlertPreferencesRepository? = null

        fun getInstance(context: Context): LiveAlertPreferencesRepository =
            instance ?: synchronized(this) {
                instance ?: LiveAlertPreferencesRepository(context).also { instance = it }
            }

        private val KEY_ENABLED = booleanPreferencesKey("live_alert_enabled")
        private val KEY_SELECTED_PRESET = stringPreferencesKey("selected_preset")
        private val KEY_SCALING_MODE = stringPreferencesKey("scaling_mode")
        // v2: con il floor le vecchie calibrazioni non si possono convertire
        private val KEY_PILL_FLOOR_DP = floatPreferencesKey("pill_floor_dp_v2")
        private val KEY_PILL_ORIGIN_DP = floatPreferencesKey("pill_origin_dp_v2")
        private val KEY_EFFECTIVE_DP_PER_UNIT = floatPreferencesKey("dp_per_braille_v2")
        private val KEY_IS_DEVICE_CALIBRATED = booleanPreferencesKey("is_device_calibrated_v2")
        private val KEY_BRAILLE_ONLY_SAFE_ANCHOR = booleanPreferencesKey("braille_only_safe_anchor")
        private val KEY_PHYSICAL_CALIBRATION = floatPreferencesKey("physical_calibration_factor")
        private val KEY_MAX_CHIP_CHARS = intPreferencesKey("max_chip_chars")
        private val KEY_CUSTOM_LENGTH = intPreferencesKey("custom_length")
        private val KEY_CUSTOM_GLYPH = stringPreferencesKey("custom_glyph")
        private val KEY_DETECTED_CUTOUT_WIDTH_PX = intPreferencesKey("detected_cutout_width_px")
        private val KEY_DEBUG_VISIBLE_MODE = booleanPreferencesKey("debug_visible_mode")
        private val KEY_DEBUG_GLYPH = stringPreferencesKey("debug_glyph")
        private val KEY_CHANNEL_MODE = stringPreferencesKey("channel_mode")
        private val KEY_TEMPLATE_STYLE = stringPreferencesKey("template_style")
        private val KEY_PROGRESS_INDETERMINATE = booleanPreferencesKey("progress_indeterminate")
        private val KEY_SMALL_ICON_MODE = stringPreferencesKey("small_icon_mode")
        private val KEY_TITLE_MODE = stringPreferencesKey("title_mode")
        private val KEY_HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        private val KEY_IS_ADVANCED_MODE = booleanPreferencesKey("is_advanced_mode")
        private val KEY_CUSTOM_WIDTH_DP = floatPreferencesKey("custom_width_dp")
        private val KEY_MANUAL_TEXT_ENABLED = booleanPreferencesKey("manual_text_enabled")
    }
}
