package dev.atoll.island.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.atoll.island.R
import dev.atoll.island.data.model.AdaptivePaddingResult
import dev.atoll.island.data.model.CapsulePreset
import dev.atoll.island.data.model.CapsuleScalingMode
import dev.atoll.island.data.model.DeviceDisplayMetrics
import dev.atoll.island.data.model.LiveAlertConfig
import dev.atoll.island.data.model.PaddingGlyph
import dev.atoll.island.data.model.PromotionDiagnostics
import dev.atoll.island.domain.AdaptiveCapsuleSolver
import dev.atoll.island.domain.CapsulePaddingGenerator
import androidx.compose.runtime.ReadOnlyComposable
import kotlin.math.roundToInt

@Composable
@ReadOnlyComposable
private fun fmt(pattern: String, vararg args: Any): String = formatLocalized(pattern, *args)

@Composable
fun ServiceCard(
    active: Boolean,
    config: LiveAlertConfig,
    result: AdaptivePaddingResult,
    onToggleEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier) {
        SwitchRow(
            title = stringResource(if (active) R.string.master_title_active else R.string.master_title_paused),
            supporting = stringResource(
                R.string.master_subtitle,
                if (config.manualTextEnabled) stringResource(R.string.size_manual_text) else config.selectedPreset.displayName,
                fmt("%.1f", result.estimatedWidthDp)
            ),
            checked = active,
            onCheckedChange = onToggleEnabled
        )
    }
}

@Composable
fun DiagnosticsCard(
    diagnostics: PromotionDiagnostics,
    onRefresh: () -> Unit,
    onOpenLiveAlertSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(stringResource(R.string.diag_title), Modifier.weight(1f))
            TextButton(onClick = onRefresh) { Text(stringResource(R.string.diag_refresh)) }
        }
        Column {
            StatusRow(
                stringResource(R.string.diag_api),
                stringResource(R.string.diag_api_value, diagnostics.apiLevel),
                diagnostics.isAndroid16Plus
            )
            StatusRow(
                stringResource(R.string.diag_notif_perm),
                stringResource(if (diagnostics.notificationsEnabled) R.string.diag_on else R.string.diag_off),
                diagnostics.notificationsEnabled
            )
            StatusRow(
                stringResource(R.string.diag_promoted_perm),
                stringResource(if (diagnostics.canPostPromotedNotifications) R.string.diag_on else R.string.diag_off),
                diagnostics.canPostPromotedNotifications
            )
            StatusRow(
                stringResource(R.string.diag_eligible),
                stringResource(if (diagnostics.builtNotificationEligible) R.string.diag_yes else R.string.diag_no),
                diagnostics.builtNotificationEligible
            )
            StatusRow(
                stringResource(R.string.diag_systemui),
                stringResource(
                    when {
                        !diagnostics.isServiceRunning -> R.string.diag_systemui_idle
                        diagnostics.shownAsLiveAlert -> R.string.diag_yes
                        else -> R.string.diag_no
                    }
                ),
                if (diagnostics.isServiceRunning) diagnostics.shownAsLiveAlert else null
            )
        }
        if (diagnostics.isServiceRunning) {
            SupportingText(
                fmt(
                    stringResource(R.string.diag_flags_meta),
                    diagnostics.activeNotificationFlags,
                    diagnostics.shortCriticalTextLength
                )
            )
        }
        FilledTonalButton(onClick = onOpenLiveAlertSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.btn_enable_live_alerts))
        }
        OutlinedButton(onClick = onOpenBatterySettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.btn_battery_autolaunch))
        }
    }
}

@Composable
fun PresetListCard(
    config: LiveAlertConfig,
    metrics: DeviceDisplayMetrics,
    onSelectPreset: (CapsulePreset) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier, title = stringResource(R.string.presets_title)) {
        Column(Modifier.selectableGroup()) {
            CapsulePreset.devicePresets.forEach { preset ->
                val solved = remember(config, metrics, preset) {
                    AdaptiveCapsuleSolver.solve(config.copy(selectedPreset = preset, manualTextEnabled = false), metrics)
                }
                val selected = !config.manualTextEnabled && config.selectedPreset == preset
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(selected, role = Role.RadioButton, onClick = { onSelectPreset(preset) }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Column(Modifier.weight(1f).padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
                        Text(preset.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            fmt(
                                "%.2f pt · %.2f%% · %.2f mm",
                                preset.widthPt,
                                preset.screenWidthRatio * 100f,
                                preset.physicalWidthMm
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        fmt("%.1f dp", solved.targetWidthDp),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (solved.wasClampedByMin) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
        SupportingText(stringResource(R.string.presets_help))
    }
}

@Composable
fun MeasurementsCard(
    config: LiveAlertConfig,
    metrics: DeviceDisplayMetrics,
    result: AdaptivePaddingResult,
    onOpenCalibration: () -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier, title = stringResource(R.string.measure_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ValueRow(
                stringResource(R.string.measure_screen),
                fmt("%d px · %.0f dp · %.0f dpi", metrics.screenWidthPx, metrics.screenWidthDp, metrics.xdpi)
            )
            ValueRow(
                stringResource(R.string.measure_target),
                fmt("%.1f dp · %.2f mm · %.1f%%", result.targetWidthDp, result.targetMm, result.targetScreenPercent)
            )
            ValueRow(stringResource(R.string.measure_expected), fmt("%.1f dp", result.estimatedWidthDp))
            ValueRow(
                stringResource(R.string.measure_text),
                result.breakdown.joinToString(" + ") { "${it.count}× ${it.glyph.codePoint}" }.ifEmpty { stringResource(R.string.measure_text_empty) }
            )
            ValueRow(
                stringResource(if (config.isDeviceCalibrated) R.string.measure_model_calibrated else R.string.measure_model_default),
                fmt(stringResource(R.string.measure_model_value), config.pillFloorDp, config.pillOriginDp, config.effectiveDpPerUnit)
            )
        }
        if (result.wasClampedByMin) SupportingText(stringResource(R.string.measure_clamped_min))
        if (result.wasClampedByMax) SupportingText(stringResource(R.string.measure_clamped_max, config.maxChipChars))
        FilledTonalButton(onClick = onOpenCalibration, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cal_visual_launch_adv))
        }
    }
}

@Composable
fun SizingModelCard(
    config: LiveAlertConfig,
    onScalingModeChange: (CapsuleScalingMode) -> Unit,
    onPillFloorChange: (Float) -> Unit,
    onPillOriginChange: (Float) -> Unit,
    onDpPerUnitChange: (Float) -> Unit,
    onPhysicalCalibrationChange: (Float) -> Unit,
    onMaxChipCharsChange: (Int) -> Unit,
    onBrailleAnchorChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(stringResource(R.string.lab_title), Modifier.weight(1f))
            TextButton(onClick = onReset) { Text(stringResource(R.string.lab_reset)) }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            CapsuleScalingMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = config.scalingMode == mode,
                    onClick = { onScalingModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, CapsuleScalingMode.entries.size)
                ) {
                    Text(
                        stringResource(
                            if (mode == CapsuleScalingMode.PROPORTIONAL) R.string.scaling_adv_proportional
                            else R.string.scaling_adv_physical
                        )
                    )
                }
            }
        }
        SliderRow(
            title = stringResource(R.string.cal_floor_label),
            valueText = fmt("%.1f dp", config.pillFloorDp),
            value = config.pillFloorDp,
            valueRange = LiveAlertConfig.MIN_PILL_FLOOR_DP..LiveAlertConfig.MAX_PILL_FLOOR_DP,
            onValueChange = { onPillFloorChange((it * 2).roundToInt() / 2f) },
            supporting = stringResource(R.string.cal_floor_help)
        )
        SliderRow(
            title = stringResource(R.string.cal_origin_label),
            valueText = fmt("%.1f dp", config.pillOriginDp),
            value = config.pillOriginDp,
            valueRange = LiveAlertConfig.MIN_PILL_ORIGIN_DP..LiveAlertConfig.MAX_PILL_ORIGIN_DP,
            onValueChange = { onPillOriginChange((it * 2).roundToInt() / 2f) },
            supporting = stringResource(R.string.cal_origin_help)
        )
        SliderRow(
            title = stringResource(R.string.cal_dp_per_unit_label),
            valueText = fmt("%.2f dp", config.effectiveDpPerUnit),
            value = config.effectiveDpPerUnit,
            valueRange = LiveAlertConfig.MIN_EFFECTIVE_DP_PER_UNIT..LiveAlertConfig.MAX_EFFECTIVE_DP_PER_UNIT,
            onValueChange = { onDpPerUnitChange((it * 10).roundToInt() / 10f) }
        )
        SliderRow(
            title = stringResource(R.string.cal_phys_scale_label),
            valueText = fmt("%.2f×", config.physicalCalibrationFactor),
            value = config.physicalCalibrationFactor,
            valueRange = LiveAlertConfig.MIN_PHYSICAL_CALIBRATION..LiveAlertConfig.MAX_PHYSICAL_CALIBRATION,
            onValueChange = { onPhysicalCalibrationChange((it * 100).roundToInt() / 100f) },
            supporting = stringResource(R.string.cal_phys_scale_help)
        )
        SliderRow(
            title = stringResource(R.string.cal_max_chars_label),
            valueText = config.maxChipChars.toString(),
            value = config.maxChipChars.toFloat(),
            valueRange = 1f..AdaptiveCapsuleSolver.MAX_PRESET_CHIP_CHARS.toFloat(),
            steps = AdaptiveCapsuleSolver.MAX_PRESET_CHIP_CHARS - 2,
            onValueChange = { onMaxChipCharsChange(it.roundToInt()) },
            supporting = stringResource(R.string.lab_length_hint)
        )
        SwitchRow(
            title = stringResource(R.string.cal_braille_anchor_title),
            supporting = stringResource(R.string.cal_braille_anchor_sub),
            checked = config.brailleOnlySafeAnchor,
            onCheckedChange = onBrailleAnchorChange
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualTextCard(
    config: LiveAlertConfig,
    sentText: String,
    onManualTextChange: (Boolean) -> Unit,
    onLengthChange: (Int) -> Unit,
    onGlyphChange: (PaddingGlyph) -> Unit,
    onDebugVisibleChange: (Boolean) -> Unit,
    onDebugGlyphChange: (PaddingGlyph) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier, title = stringResource(R.string.lab_manual_title)) {
        SwitchRow(
            title = stringResource(R.string.lab_manual_switch),
            supporting = stringResource(R.string.lab_manual_sub),
            checked = config.manualTextEnabled,
            onCheckedChange = onManualTextChange
        )
        SliderRow(
            title = stringResource(R.string.lab_manual_length),
            valueText = config.customLength.toString(),
            value = config.customLength.toFloat(),
            valueRange = CapsulePaddingGenerator.MIN_LENGTH.toFloat()..CapsulePaddingGenerator.MAX_LENGTH.toFloat(),
            steps = CapsulePaddingGenerator.MAX_LENGTH - 1,
            onValueChange = { onLengthChange(it.roundToInt()) }
        )
        Text(stringResource(R.string.lab_glyph_label), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaddingGlyph.invisibleGlyphs.forEach { glyph ->
                FilterChip(
                    selected = config.manualTextEnabled && config.customGlyph == glyph,
                    onClick = { onGlyphChange(glyph) },
                    label = { Text("${glyph.codePoint} ${glyph.shortLabel}") }
                )
            }
        }
        HorizontalDivider()
        SwitchRow(
            title = stringResource(R.string.lab_debug_title),
            supporting = stringResource(R.string.lab_debug_sub),
            checked = config.debugVisibleMode,
            onCheckedChange = onDebugVisibleChange
        )
        if (config.debugVisibleMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaddingGlyph.debugGlyphs.forEach { glyph ->
                    FilterChip(
                        selected = config.debugGlyph == glyph,
                        onClick = { onDebugGlyphChange(glyph) },
                        label = { Text(glyph.shortLabel) }
                    )
                }
            }
        }
        Text(
            stringResource(R.string.lab_preview_stats, sentText.length, sentText.trim().length),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
