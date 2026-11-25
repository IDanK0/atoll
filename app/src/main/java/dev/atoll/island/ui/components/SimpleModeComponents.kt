package dev.atoll.island.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.atoll.island.R
import dev.atoll.island.data.model.AdaptivePaddingResult
import dev.atoll.island.data.model.CapsulePreset
import dev.atoll.island.data.model.CapsuleScalingMode
import dev.atoll.island.data.model.DeviceDisplayMetrics
import dev.atoll.island.data.model.LiveAlertConfig
import dev.atoll.island.data.model.OnboardingStep
import dev.atoll.island.domain.AdaptiveCapsuleSolver

@Composable
fun PermissionFixCard(
    missingStep: OnboardingStep,
    onFixSetup: (OnboardingStep) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle(stringResource(R.string.simple_fix_title))
            Text(
                stringResource(
                    if (missingStep == OnboardingStep.NOTIFICATIONS) R.string.simple_fix_body_notif
                    else R.string.simple_fix_body_live_alerts
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = { onFixSetup(missingStep) }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.simple_fix_cta))
            }
        }
    }
}

@Composable
fun StatusCard(
    config: LiveAlertConfig,
    active: Boolean,
    result: AdaptivePaddingResult,
    metrics: DeviceDisplayMetrics,
    onToggleEnabled: (Boolean) -> Unit,
    onOpenCalibration: () -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier) {
        CapsulePreview(capsuleFraction = result.estimatedWidthDp / metrics.screenWidthDp, active = active)
        SwitchRow(
            title = stringResource(if (active) R.string.simple_status_active else R.string.simple_status_off),
            supporting = sizeSummary(config, result),
            checked = active,
            onCheckedChange = onToggleEnabled
        )
        FilledTonalButton(onClick = onOpenCalibration, modifier = Modifier.fillMaxWidth()) {
            Icon(painterResource(R.drawable.ic_tune), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(
                    if (config.isDeviceCalibrated) R.string.simple_calibrate_done_cta else R.string.simple_calibrate_cta
                ),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun sizeSummary(config: LiveAlertConfig, result: AdaptivePaddingResult): String {
    val name = if (config.manualTextEnabled) stringResource(R.string.size_manual_text)
    else stringResource(config.selectedPreset.simpleTitleRes())
    return when {
        result.wasClampedByMin -> stringResource(R.string.size_summary_minimum, name)
        result.wasClampedByMax -> stringResource(R.string.size_summary_maximum, name)
        else -> name
    }
}

@Composable
fun PresetGrid(
    config: LiveAlertConfig,
    metrics: DeviceDisplayMetrics,
    onSelectPreset: (CapsulePreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CapsulePreset.entries.chunked(2).forEach { pair ->
            // le due tile della riga hanno la stessa altezza
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { preset ->
                    val solved = remember(config, metrics, preset) {
                        AdaptiveCapsuleSolver.solve(
                            config.copy(selectedPreset = preset, manualTextEnabled = false),
                            metrics
                        )
                    }
                    PresetTile(
                        preset = preset,
                        result = solved,
                        screenWidthDp = metrics.screenWidthDp,
                        selected = !config.manualTextEnabled && config.selectedPreset == preset,
                        onClick = { onSelectPreset(preset) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PresetTile(
    preset: CapsulePreset,
    result: AdaptivePaddingResult,
    screenWidthDp: Float,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) colors.secondaryContainer else colors.surfaceContainer,
            contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface
        )
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .heightIn(min = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(12.dp)) {
                // la tile fa da schermo, così la barra ha le proporzioni vere
                Box(
                    Modifier
                        .width(maxWidth * (result.estimatedWidthDp / screenWidthDp).coerceIn(0.1f, 1f))
                        .height(12.dp)
                        .background(Color.Black, CircleShape)
                )
            }
            Text(stringResource(preset.simpleTitleRes()), style = MaterialTheme.typography.titleSmall)
            Text(
                text = when {
                    preset.isCustom -> stringResource(R.string.preset_sub_custom)
                    result.wasClampedByMin -> stringResource(R.string.preset_sub_minimum)
                    else -> formatLocalized("%.1f\u2033", preset.diagonalInches)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ScalingModeCard(
    scalingMode: CapsuleScalingMode,
    onScalingModeChange: (CapsuleScalingMode) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier, title = stringResource(R.string.simple_scaling_title)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            CapsuleScalingMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = scalingMode == mode,
                    onClick = { onScalingModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, CapsuleScalingMode.entries.size)
                ) {
                    Text(
                        stringResource(
                            if (mode == CapsuleScalingMode.PROPORTIONAL) R.string.scaling_simple_proportional
                            else R.string.scaling_simple_physical
                        )
                    )
                }
            }
        }
        SupportingText(
            stringResource(
                if (scalingMode == CapsuleScalingMode.PROPORTIONAL) R.string.scaling_simple_proportional_help
                else R.string.scaling_simple_physical_help
            )
        )
    }
}

// salva quando alzi il dito, non a ogni frame
@Composable
fun CustomWidthCard(
    config: LiveAlertConfig,
    metrics: DeviceDisplayMetrics,
    onCustomWidthChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val minDp = AdaptiveCapsuleSolver.predictedWidthDp(config, 0f)
    val maxDp = AdaptiveCapsuleSolver.predictedWidthDp(config, AdaptiveCapsuleSolver.MAX_PRESET_CHIP_CHARS.toFloat())
    var dragging by remember { mutableStateOf(false) }
    var draft by remember { mutableFloatStateOf(config.customWidthDp) }
    val shown = if (dragging) draft else config.customWidthDp
    val mm = shown * metrics.density / metrics.xdpi * 25.4f
    SectionCard(modifier) {
        SliderRow(
            title = stringResource(R.string.simple_size_title),
            valueText = formatLocalized("%.1f mm", mm),
            value = shown,
            valueRange = minDp..maxDp,
            onValueChange = {
                dragging = true
                draft = it
            },
            onValueChangeFinished = {
                dragging = false
                onCustomWidthChange(draft)
            },
            supporting = stringResource(R.string.simple_size_help)
        )
    }
}

@StringRes
fun CapsulePreset.simpleTitleRes(): Int = when (this) {
    CapsulePreset.IPHONE_18_PRO_MAX -> R.string.preset_simple_18_pro_max
    CapsulePreset.IPHONE_18_PRO -> R.string.preset_simple_18_pro
    CapsulePreset.IPHONE_16_17_PRO_MAX -> R.string.preset_simple_16_17_pro_max
    CapsulePreset.IPHONE_14_16_PLUS_MAX -> R.string.preset_simple_14_16_plus_max
    CapsulePreset.IPHONE_AIR -> R.string.preset_simple_air
    CapsulePreset.IPHONE_16_PRO_17 -> R.string.preset_simple_16_pro_17
    CapsulePreset.IPHONE_14_PRO_16 -> R.string.preset_simple_14_pro_16
    CapsulePreset.CUSTOM -> R.string.preset_simple_custom
}
