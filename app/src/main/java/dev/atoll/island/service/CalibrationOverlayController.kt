package dev.atoll.island.service

import android.app.AppOpsManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import dev.atoll.island.R
import dev.atoll.island.data.model.CapsulePreset
import dev.atoll.island.data.model.LiveAlertConfig
import dev.atoll.island.data.prefs.CalibrationOverlayPreferences
import dev.atoll.island.data.prefs.LiveAlertPreferencesRepository
import dev.atoll.island.domain.AdaptiveCapsuleSolver
import dev.atoll.island.domain.CalibrationMath
import dev.atoll.island.ui.calibration.CalibrationPanelActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.roundToInt

internal class CalibrationOverlayController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val repository: LiveAlertPreferencesRepository,
    private var config: LiveAlertConfig,
    private val geometry: CalibrationOverlayPreferences,
    private val onSample: (LiveAlertConfig, String?) -> Unit,
    private val onClose: (returnToApp: Boolean) -> Unit
) : CalibrationPanelActions {
    private val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    private val display = checkNotNull(context.getSystemService(DisplayManager::class.java)
        ?.getDisplay(Display.DEFAULT_DISPLAY))
    private val ui = context.createWindowContext(display, type, null)
    private val windows = checkNotNull(ui.getSystemService(WindowManager::class.java))
    private val appOps = context.getSystemService(AppOpsManager::class.java)
    private val appOpListener = AppOpsManager.OnOpChangedListener { _, _ ->
        ui.mainExecutor.execute {
            if (!closed && !Settings.canDrawOverlays(ui)) onClose(false)
        }
    }
    private val bar = PeekCalibrationBarView(ui)
    private val panel = CalibrationOverlayPanel(ui, this)
    private val barParams = WindowManager.LayoutParams(
        1, dp(12), type,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        fitInsetsTypes = 0
        // i tocchi fuori dal righello devono arrivare al launcher
        alpha = ui.getSystemService(InputManager::class.java)?.maximumObscuringOpacityForTouch ?: 0.8f
        title = "Atoll calibration ruler"
    }
    private val panelParams = WindowManager.LayoutParams(
        1, WindowManager.LayoutParams.WRAP_CONTENT, type,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        fitInsetsTypes = 0
        title = "Atoll calibration controls"
    }

    private val offsetUpdates = Channel<Int>(Channel.CONFLATED)
    private var openBar = false
    private var openPanel = false
    private var watchingAppOps = false
    private var closed = false
    private var work: Job? = null
    private var step = 1
    private var widthDp = config.pillFloorDp
    private var measuredMinDp = 0f
    private var measuredMidDp = 0f
    private var color = 0
    private var below = false
    private var panelBottomPx = dp(96)
    private var yOffsetPx = geometry.yOffsetPx

    fun open() {
        check(Settings.canDrawOverlays(ui))
        check(ui.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE)
        setPanelBounds()
        panel.showStep(step)
        showOptions()
        resolveBarPosition()
        try {
            windows.addView(bar, barParams)
            openBar = true
            windows.addView(panel, panelParams)
            openPanel = true
            appOps?.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, ui.packageName, appOpListener)
            watchingAppOps = appOps != null
        } catch (error: RuntimeException) {
            dispose()
            throw error
        }
        scope.launch {
            for (offset in offsetUpdates) {
                try {
                    repository.setCalibrationYOffsetPx(offset)
                } catch (error: IOException) {
                    Log.w(TAG, "Could not save ruler vertical offset", error)
                    if (!closed) panel.showMessage(R.string.cal_float_save_failed)
                }
            }
        }
        onSample(config, CalibrationMath.MIN_SAMPLE)
    }

    fun dispose() {
        if (closed) return
        closed = true
        work?.cancel()
        offsetUpdates.close()
        if (watchingAppOps) appOps?.stopWatchingMode(appOpListener)
        if (openPanel) remove(panel)
        if (openBar) remove(bar)
    }

    fun onConfigurationChanged() {
        if (closed) return
        if (ui.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Toast.makeText(ui, R.string.cal_float_portrait, Toast.LENGTH_SHORT).show()
            onClose(false)
            return
        }
        if (step == VERIFY_STEP) resolveTarget()
        setPanelBounds()
        update(panel, panelParams)
        updateBar()
    }

    override fun changeWidth(width: Int) {
        if (step == VERIFY_STEP || work?.isActive == true || closed) return
        widthDp = width.coerceIn(CalibrationMath.MIN_WIDTH_DP, CalibrationMath.MAX_WIDTH_DP).toFloat()
        showWarning(null)
        updateBar()
    }

    override fun cycleColor() {
        color = (color + 1) % COLORS.size
        showOptions()
    }

    override fun moveRuler(deltaPx: Int) {
        yOffsetPx = CalibrationMath.clampYOffsetPx(yOffsetPx + deltaPx)
        offsetUpdates.trySend(yOffsetPx)
        updateBar()
    }

    override fun togglePlacement() {
        below = !below
        showOptions()
        updateBar()
    }

    override fun dragPanel(deltaPx: Int) = movePanel(panelBottomPx - deltaPx)

    override fun togglePanelPosition() {
        val higher = getPanelMaxBottom() - dp(16)
        val lower = dp(96)
        movePanel(if (panelBottomPx < (higher + lower) / 2) higher else lower)
    }

    override fun selectPreset(preset: CapsulePreset) {
        updateSavedConfig {
            repository.selectPreset(preset)
            repository.getLatestConfig()
        }
    }

    override fun advance() {
        when (step) {
            1 -> {
                if (widthDp > LiveAlertConfig.MAX_PILL_FLOOR_DP) {
                    showWarning(R.string.cal_float_min_out_of_range)
                    return
                }
                measuredMinDp = widthDp
                nextSample(CalibrationMath.MID_UNITS, CalibrationMath.MID_SAMPLE)
            }
            2 -> {
                if (widthDp < measuredMinDp + 2f) {
                    showWarning(R.string.cal_float_not_wider)
                    return
                }
                measuredMidDp = widthDp
                nextSample(CalibrationMath.WIDE_UNITS, CalibrationMath.WIDE_SAMPLE)
            }
            3 -> when (val result = CalibrationMath.fit(measuredMinDp, measuredMidDp, widthDp)) {
                is CalibrationMath.Result.Failure -> showWarning(R.string.cal_float_not_wider)
                is CalibrationMath.Result.Success -> updateSavedConfig {
                    repository.applyVisualCalibration(result.fit)
                }
            }
            else -> onClose(true)
        }
    }

    // il righello parte dalla larghezza prevista
    private fun nextSample(units: Int, sample: String) {
        widthDp = AdaptiveCapsuleSolver.predictedWidthDp(config, units.toFloat())
            .coerceIn(CalibrationMath.MIN_WIDTH_DP.toFloat(), CalibrationMath.MAX_WIDTH_DP.toFloat())
        step++
        panel.showStep(step)
        updateBar()
        if (!closed) onSample(config, sample)
    }

    override fun close() = onClose(false)

    private fun updateSavedConfig(save: suspend () -> LiveAlertConfig) {
        if (closed || work?.isActive == true) return
        panel.setBusy(true)
        work = scope.launch {
            try {
                config = save()
                if (closed) return@launch
                if (step != VERIFY_STEP) {
                    step = VERIFY_STEP
                    panel.showStep(step)
                }
                resolveTarget()
                updateBar()
                if (!closed) onSample(config, null)
            } catch (error: IOException) {
                Log.w(TAG, "Could not save calibration", error)
                showWarning(R.string.cal_float_save_failed)
            } finally {
                if (!closed) panel.setBusy(false)
            }
        }
    }

    private fun resolveTarget() {
        val solved = LiveAlertNotificationBuilder.solveForDevice(context, config).second
        widthDp = solved.targetWidthDp
        panel.showSelection(config.selectedPreset)
        showWarning(when {
            config.manualTextEnabled -> R.string.cal_float_select_preset
            solved.wasClampedByMin -> R.string.simple_smallest_supported_note
            solved.wasClampedByMax -> R.string.cal_float_max_size
            else -> null
        })
    }

    private fun showWarning(text: Int?) {
        panel.showMessage(text)
    }

    private fun showOptions() {
        val (colorValue, name) = COLORS[color]
        bar.rulerColor = colorValue
        panel.showOptions(colorValue, name, below)
    }

    private fun updateBar() {
        if (closed) return
        resolveBarPosition()
        update(bar, barParams)
    }

    private fun resolveBarPosition() {
        val current = windows.maximumWindowMetrics
        val bounds = current.bounds
        val insets = current.windowInsets
        val rawStatusBarHeight = insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
        val statusBarHeight = rawStatusBarHeight.takeIf { it > 0 }
            ?: geometry.statusBarHeightPx.takeIf { it > 0 } ?: dp(24)
        val topCutout = insets.displayCutout?.boundingRectTop?.takeUnless { it.isEmpty }
        val centerY = topCutout?.centerY() ?: geometry.cutoutCenterYpx
        val maxWidth = bounds.width().coerceAtLeast(1)
        barParams.width = dp(widthDp).coerceIn(1, maxWidth)
        barParams.height = dp(12)
        barParams.x = topCutout?.centerX()?.minus(maxWidth / 2) ?: 0
        barParams.y = CalibrationMath.barTopPx(centerY, statusBarHeight, barParams.height, yOffsetPx) +
            if (below) dp(24) else 0
        panel.showWidth(widthDp)
    }

    private fun setPanelBounds() {
        val current = windows.maximumWindowMetrics
        panelParams.width = (minOf(dp(370), current.bounds.width() - dp(24))).coerceAtLeast(1)
        val navigationSize = current.windowInsets
            .getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()).bottom
        panel.maxHeight = (current.bounds.height() - maxOf(dp(108), navigationSize + dp(24)) - dp(72))
            .coerceAtLeast(dp(96))
        panelParams.y = clampPanelBottom(panelBottomPx)
    }

    private fun movePanel(bottom: Int) {
        panelBottomPx = clampPanelBottom(bottom)
        panelParams.y = panelBottomPx
        update(panel, panelParams)
    }

    private fun clampPanelBottom(bottom: Int): Int {
        val inset = windows.maximumWindowMetrics.windowInsets
            .getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()).bottom
        return bottom.coerceIn(inset + dp(8), getPanelMaxBottom().coerceAtLeast(inset + dp(8)))
    }

    private fun getPanelMaxBottom(): Int = windows.maximumWindowMetrics.bounds.height() -
        maxOf(panel.height, panel.measuredHeight) - dp(72)

    private fun update(view: View, params: WindowManager.LayoutParams) {
        if (closed || !view.isAttachedToWindow) return
        try {
            windows.updateViewLayout(view, params)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Calibration window no longer available", error)
            onClose(false)
        }
    }

    private fun remove(view: View) {
        try {
            windows.removeViewImmediate(view)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Calibration window already removed", error)
        }
    }

    private fun dp(value: Number): Int = (value.toFloat() * ui.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val TAG = "AtollCalibration"
        const val VERIFY_STEP = 4
        val COLORS = listOf(
            0xFF00FF66.toInt() to R.string.cal_float_green,
            0xFFFF007F.toInt() to R.string.cal_float_magenta,
            0xFF00E5FF.toInt() to R.string.cal_float_cyan
        )
    }
}
