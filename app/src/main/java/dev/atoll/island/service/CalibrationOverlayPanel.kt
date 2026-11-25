package dev.atoll.island.service

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.atoll.island.data.model.CapsulePreset
import dev.atoll.island.ui.calibration.CalibrationPanelActions
import dev.atoll.island.ui.calibration.CalibrationPanelContent
import dev.atoll.island.ui.calibration.CalibrationPanelState
import dev.atoll.island.ui.theme.AtollTheme

@SuppressLint("ViewConstructor") // lo crea a mano il WindowManager
internal class CalibrationOverlayPanel(
    context: Context,
    actions: CalibrationPanelActions
) : FrameLayout(context) {
    private var state by mutableStateOf(CalibrationPanelState())
    private val owner = PanelOwner()

    var maxHeight: Int = Int.MAX_VALUE
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    init {
        // un overlay non è un'Activity, gli owner di Compose li metto io
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        val panelActions = object : CalibrationPanelActions by actions {
            override fun advance() {
                post { actions.advance() }
            }

            override fun close() {
                post { actions.close() }
            }
        }
        addView(ComposeView(context).apply {
            setContent { AtollTheme { CalibrationPanelContent(state, panelActions) } }
        }, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
    }

    override fun onDetachedFromWindow() {
        owner.lifecycle.currentState = Lifecycle.State.DESTROYED
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) maxHeight
        else minOf(maxHeight, MeasureSpec.getSize(heightMeasureSpec))
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST))
    }

    fun showStep(index: Int) {
        state = state.copy(step = index, message = null)
    }

    fun showWidth(widthDp: Float) {
        state = state.copy(widthDp = widthDp)
    }

    fun showOptions(color: Int, colorName: Int, below: Boolean) {
        state = state.copy(rulerColor = color, colorName = colorName, below = below)
    }

    fun showSelection(preset: CapsulePreset) {
        state = state.copy(preset = preset)
    }

    fun setBusy(busy: Boolean) {
        state = state.copy(busy = busy)
    }

    fun showMessage(text: Int?) {
        state = state.copy(message = text)
    }

    private class PanelOwner : SavedStateRegistryOwner {
        override val lifecycle = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        override val savedStateRegistry get() = controller.savedStateRegistry

        init {
            controller.performAttach()
            controller.performRestore(null)
        }
    }
}
