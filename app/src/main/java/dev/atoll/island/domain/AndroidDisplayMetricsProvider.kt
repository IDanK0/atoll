package dev.atoll.island.domain

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.WindowManager
import dev.atoll.island.data.model.DeviceDisplayMetrics

object AndroidDisplayMetricsProvider {

    fun readMetrics(
        context: Context,
        persistedCutoutWidthPx: Int = 0
    ): DeviceDisplayMetrics {
        val visualContext = resolveVisualContext(context)
        val resources = visualContext.resources
        val displayMetrics = resources.displayMetrics
        val fontScale = resources.configuration.fontScale.takeIf { it > 0f } ?: 1.0f
        val density = displayMetrics.density.takeIf { it > 0f } ?: 3.0f
        val xdpi = displayMetrics.xdpi.takeIf { it > 0f } ?: (density * 160f)

        var widthPx = displayMetrics.widthPixels
        var heightPx = displayMetrics.heightPixels
        var cutoutWidthPx = persistedCutoutWidthPx

        runCatching {
            val wm = visualContext.getSystemService(WindowManager::class.java)
            if (wm != null) {
                val windowMetrics = wm.currentWindowMetrics
                val bounds = windowMetrics.bounds
                if (bounds.width() > 0 && bounds.height() > 0) {
                    widthPx = bounds.width()
                    heightPx = bounds.height()
                }
                val topCutoutWidth = windowMetrics.windowInsets.displayCutout
                    ?.boundingRectTop
                    ?.width()
                    ?: 0
                if (topCutoutWidth > 0) {
                    cutoutWidthPx = topCutoutWidth
                }
            }
        }

        val portraitWidth = minOf(widthPx, heightPx).coerceAtLeast(1)
        val portraitHeight = maxOf(widthPx, heightPx).coerceAtLeast(1)

        return DeviceDisplayMetrics(
            screenWidthPx = portraitWidth,
            screenHeightPx = portraitHeight,
            xdpi = xdpi,
            density = density,
            fontScale = fontScale,
            detectedCutoutWidthPx = cutoutWidthPx
        )
    }

    private fun resolveVisualContext(context: Context): Context {
        if (context.isUiContext) return context
        val displayManager = context.getSystemService(DisplayManager::class.java) ?: return context
        val defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: return context
        return runCatching {
            context.createDisplayContext(defaultDisplay)
        }.getOrDefault(context)
    }
}
