package dev.atoll.island.data.prefs

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import dev.atoll.island.domain.CalibrationMath

internal data class CalibrationOverlayPreferences(
    val cutoutCenterYpx: Int = 0,
    val statusBarHeightPx: Int = 0,
    val yOffsetPx: Int = 0
) {
    companion object {
        private val centerY = intPreferencesKey("calibration_cutout_center_y_px")
        private val barHeight = intPreferencesKey("calibration_status_bar_height_px")
        private val offsetY = intPreferencesKey("calibration_y_offset_px")

        fun read(prefs: Preferences) = CalibrationOverlayPreferences(
            cutoutCenterYpx = (prefs[centerY] ?: 0).coerceAtLeast(0),
            statusBarHeightPx = (prefs[barHeight] ?: 0).coerceAtLeast(0),
            yOffsetPx = CalibrationMath.clampYOffsetPx(prefs[offsetY] ?: 0)
        )

        fun writeGeometry(prefs: MutablePreferences, centerYpx: Int, heightPx: Int) {
            prefs[centerY] = centerYpx.coerceAtLeast(0)
            prefs[barHeight] = heightPx.coerceAtLeast(0)
        }

        fun writeYOffset(prefs: MutablePreferences, offsetPx: Int) {
            prefs[offsetY] = CalibrationMath.clampYOffsetPx(offsetPx)
        }
    }
}
