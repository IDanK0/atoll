package dev.atoll.island.ui.calibration

import android.view.MotionEvent
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import dev.atoll.island.ui.components.AtollSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.atoll.island.R
import dev.atoll.island.data.model.CapsulePreset
import dev.atoll.island.domain.CalibrationMath
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt

internal interface CalibrationPanelActions {
    fun changeWidth(width: Int)
    fun cycleColor()
    fun moveRuler(deltaPx: Int)
    fun togglePlacement()
    fun dragPanel(deltaPx: Int)
    fun togglePanelPosition()
    fun selectPreset(preset: CapsulePreset)
    fun advance()
    fun close()
}

internal data class CalibrationPanelState(
    val step: Int = 1,
    val widthDp: Float = 0f,
    val rulerColor: Int = 0,
    @param:StringRes val colorName: Int = R.string.cal_float_green,
    val below: Boolean = false,
    val preset: CapsulePreset? = null,
    val busy: Boolean = false,
    @param:StringRes val message: Int? = null
) {
    val verifying: Boolean get() = step > MEASURE_STEPS

    companion object {
        const val MEASURE_STEPS = 3
    }
}

@Composable
internal fun CalibrationPanelContent(state: CalibrationPanelState, actions: CalibrationPanelActions) {
    var showOptions by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.fillMaxWidth()) {
            PanelHeader(showOptions, { showOptions = !showOptions }, actions)
            val scroll = rememberScrollState()
            LaunchedEffect(state.step, showOptions) { scroll.scrollTo(0) }
            Column(
                Modifier
                    .verticalScroll(scroll)
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, bottom = 14.dp)
            ) {
                if (showOptions) {
                    RulerOptions(state, actions)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { showOptions = false },
                        modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.cal_float_options_back))
                    }
                } else {
                    when {
                        state.verifying -> PresetCheck(state, actions)
                        else -> MeasurePill(state, actions)
                    }
                    state.message?.let {
                        Text(
                            text = stringResource(it),
                            modifier = Modifier.padding(top = 8.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    PanelFooter(state, actions::advance)
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(showOptions: Boolean, toggleOptions: () -> Unit, actions: CalibrationPanelActions) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        IconButton(
            onClick = toggleOptions,
            modifier = Modifier.align(Alignment.CenterStart).pointerHoverIcon(PointerIcon.Hand)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_tune),
                contentDescription = stringResource(
                    if (showOptions) R.string.cal_float_options_back else R.string.cal_float_options
                ),
                tint = if (showOptions) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        PanelGrip(actions, Modifier.align(Alignment.Center))
        IconButton(
            onClick = actions::close,
            modifier = Modifier.align(Alignment.CenterEnd).pointerHoverIcon(PointerIcon.Hand)
        ) {
            val tint = colors.onSurfaceVariant
            val label = stringResource(R.string.action_close)
            Canvas(Modifier.size(18.dp).semantics { contentDescription = label }) {
                val edge = size.width * 0.23f
                val far = size.width - edge
                drawLine(tint, Offset(edge, edge), Offset(far, far), 2.dp.toPx(), StrokeCap.Round)
                drawLine(tint, Offset(far, edge), Offset(edge, far), 2.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun PanelGrip(actions: CalibrationPanelActions, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.cal_float_move_panel)
    val slop = LocalViewConfiguration.current.touchSlop
    val gesture = remember(actions, slop) {
        object : (MotionEvent) -> Boolean {
            private var lastY = 0f
            private var dragging = false

            override fun invoke(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastY = event.rawY
                        dragging = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val delta = event.rawY - lastY
                        if (dragging || abs(delta) > slop) {
                            dragging = true
                            val deltaPx = delta.roundToInt()
                            actions.dragPanel(deltaPx)
                            lastY += deltaPx
                        }
                    }
                    MotionEvent.ACTION_UP -> if (!dragging) actions.togglePanelPosition()
                }
                return true
            }
        }
    }
    Box(
        modifier
            .size(56.dp, 48.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInteropFilter(onTouchEvent = gesture)
            .semantics {
                role = Role.Button
                contentDescription = label
                onClick {
                    actions.togglePanelPosition()
                    true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(28.dp, 4.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f), CircleShape)
        )
    }
}

@Composable
private fun MeasurePill(state: CalibrationPanelState, actions: CalibrationPanelActions) {
    PanelTitle(
        when (state.step) {
            1 -> R.string.cal_visual_step1_title
            2 -> R.string.cal_visual_step2_title
            else -> R.string.cal_visual_step3_title
        }
    )
    PanelDescription(
        when {
            state.below -> R.string.cal_float_below_help
            state.step == 1 -> R.string.cal_visual_step1_body
            else -> R.string.cal_visual_step2_body
        }
    )

    val width = state.widthDp.roundToInt().coerceIn(CalibrationMath.MIN_WIDTH_DP, CalibrationMath.MAX_WIDTH_DP)
    val label = stringResource(R.string.cal_float_ruler_width)
    val formattedWidth = stringResource(R.string.cal_visual_width_badge, width)
    Spacer(Modifier.height(10.dp))
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val showInlineWidth = maxWidth >= 280.dp && LocalDensity.current.fontScale < 1.35f
        if (showInlineWidth) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WidthNudge(-5, width, state.busy, actions)
                WidthNudge(-1, width, state.busy, actions)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { WidthValue(formattedWidth) }
                WidthNudge(1, width, state.busy, actions)
                WidthNudge(5, width, state.busy, actions)
            }
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                WidthValue(formattedWidth)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (delta in listOf(-5, -1, 1, 5)) WidthNudge(delta, width, state.busy, actions)
                }
            }
        }
    }
    AtollSlider(
        value = width.toFloat(),
        onValueChange = { actions.changeWidth(it.roundToInt()) },
        valueRange = CalibrationMath.MIN_WIDTH_DP.toFloat()..CalibrationMath.MAX_WIDTH_DP.toFloat(),
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = label
            stateDescription = formattedWidth
        }
    )
}

@Composable
private fun WidthNudge(delta: Int, width: Int, busy: Boolean, actions: CalibrationPanelActions) {
    val fine = abs(delta) == 1
    val enabled = !busy && if (delta < 0) width > CalibrationMath.MIN_WIDTH_DP else width < CalibrationMath.MAX_WIDTH_DP
    val action by rememberUpdatedState { actions.changeWidth(width + delta) }
    val label = stringResource(if (delta < 0) R.string.cal_float_narrow else R.string.cal_float_widen, abs(delta))
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var repeating by remember { mutableStateOf(false) }
    LaunchedEffect(pressed, repeating, enabled) {
        if (!pressed || !enabled) {
            repeating = false
        } else if (repeating) {
            while (isActive) {
                action()
                delay(75)
            }
        }
    }
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (fine) colors.secondaryContainer else Color.Transparent,
        modifier = Modifier
            .size(48.dp)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .combinedClickable(
                interactionSource = source,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onLongClick = if (fine) {
                    {
                        // così va anche il long click senza touch
                        if (pressed) repeating = true else action()
                    }
                } else null,
                onClick = { action() }
            )
            .semantics { contentDescription = label }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.cal_float_delta, delta),
                style = MaterialTheme.typography.titleSmall,
                color = (if (fine) colors.onSecondaryContainer else colors.onSurfaceVariant)
                    .let { if (enabled) it else it.copy(alpha = 0.38f) }
            )
        }
    }
}

@Composable
private fun PresetCheck(state: CalibrationPanelState, actions: CalibrationPanelActions) {
    PanelTitle(R.string.cal_float_try_presets)
    PanelDescription(R.string.cal_float_verify_body)
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.cal_float_target_width, state.widthDp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (preset in CapsulePreset.devicePresets) {
            val title = preset.shortName
            FilterChip(
                selected = state.preset == preset,
                onClick = { actions.selectPreset(preset) },
                enabled = !state.busy,
                modifier = Modifier.heightIn(min = 48.dp).pointerHoverIcon(PointerIcon.Hand),
                label = { Text(title) }
            )
        }
    }
}

@Composable
private fun RulerOptions(state: CalibrationPanelState, actions: CalibrationPanelActions) {
    PanelTitle(R.string.cal_float_options)
    val belowLabel = stringResource(R.string.cal_float_below)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(belowLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = state.below,
            onCheckedChange = { actions.togglePlacement() },
            modifier = Modifier.semantics { contentDescription = belowLabel }
        )
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.cal_float_color_label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        FilledTonalButton(onClick = actions::cycleColor) {
            Box(Modifier.size(8.dp).background(Color(state.rulerColor), CircleShape))
            Text(stringResource(state.colorName), Modifier.padding(start = 8.dp))
        }
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.cal_float_vertical_position), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        for (up in listOf(true, false)) {
            IconButton(onClick = {
                actions.moveRuler(if (up) -CalibrationMath.Y_NUDGE_PX else CalibrationMath.Y_NUDGE_PX)
            }) {
                Icon(
                    painterResource(R.drawable.ic_arrow_back),
                    stringResource(if (up) R.string.cal_float_up else R.string.cal_float_down),
                    Modifier.size(20.dp).rotate(if (up) 90f else -90f),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun PanelFooter(state: CalibrationPanelState, advance: () -> Unit) {
    val action = when {
        state.busy -> R.string.cal_float_saving
        state.step < CalibrationPanelState.MEASURE_STEPS -> R.string.wizard_next
        state.step == CalibrationPanelState.MEASURE_STEPS -> R.string.cal_visual_save
        else -> R.string.cal_visual_finish
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            if (!state.verifying) {
                Text(
                    text = stringResource(R.string.cal_visual_step_badge, state.step, CalibrationPanelState.MEASURE_STEPS),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Button(
            onClick = advance,
            enabled = !state.busy,
            modifier = Modifier.weight(if (state.verifying) 4f else 2f)
                .heightIn(min = 48.dp)
                .pointerHoverIcon(if (state.busy) PointerIcon.Default else PointerIcon.Hand)
        ) {
            Text(stringResource(action))
        }
    }
}

@Composable
private fun PanelTitle(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineSmall
    )
}

@Composable
private fun PanelDescription(@StringRes description: Int) {
    Text(
        text = stringResource(description),
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun WidthValue(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurface
    )
}
