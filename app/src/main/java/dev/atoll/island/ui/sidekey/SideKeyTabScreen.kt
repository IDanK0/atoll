package dev.atoll.island.ui.sidekey

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.atoll.island.R
import dev.atoll.island.data.model.SideKeyAction
import dev.atoll.island.data.model.SideKeyConfig
import dev.atoll.island.data.model.SideKeyDiagnostics
import dev.atoll.island.data.model.SideKeyGesture
import dev.atoll.island.data.model.SideKeyGestureBinding
import dev.atoll.island.data.model.SideKeyStatus
import dev.atoll.island.domain.sidekey.SideKeyLogParser
import dev.atoll.island.ui.components.CardTitle
import dev.atoll.island.ui.components.SectionCard
import dev.atoll.island.ui.components.SectionHeader
import dev.atoll.island.ui.components.SliderRow
import dev.atoll.island.ui.components.StatusRow
import dev.atoll.island.ui.components.SupportingText
import dev.atoll.island.ui.components.SwitchRow
import dev.atoll.island.ui.components.ValueRow
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

interface SideKeyUiActions {
    fun setEnabled(enabled: Boolean)
    fun reconnect()
    fun updateConfig(transform: (SideKeyConfig) -> SideKeyConfig)
    fun testGesture(gesture: SideKeyGesture)
    fun openButtonSettings()
    fun copyAdbCommand()
    fun openLanguageSettings()
    fun openDeveloperOptions()
    fun grantViaShizuku()
    fun openShizuku()
    fun openOverlaySettings()
    fun openAppInfoSettings()
    fun openDndSettings()
}

internal fun LazyListScope.sideKeyTabItems(
    config: SideKeyConfig,
    diagnostics: SideKeyDiagnostics,
    adbCommand: String,
    isAdvancedMode: Boolean,
    actions: SideKeyUiActions,
    onPickGesture: (SideKeyGesture) -> Unit,
    onOpenDetectKey: () -> Unit,
    onOpenWizard: () -> Unit
) {
    if (!isAdvancedMode) {
        if (!diagnostics.hasReadLogsPermission) {
            item(key = "sk_simple_prompt") {
                SideKeySimpleSetupCard(onOpenWizard = onOpenWizard)
            }
        } else {
            item(key = "sk_status") {
                SideKeyStatusCard(config, diagnostics, isAdvancedMode = false, actions = actions)
            }
        }
    } else {
        item(key = "sk_status") {
            SideKeyStatusCard(config, diagnostics, isAdvancedMode = true, actions = actions)
        }
        val showSetup = !diagnostics.hasReadLogsPermission || !config.setupDismissed
        if (showSetup) {
            item(key = "sk_setup") {
                SideKeySetupCard(
                    adbCommand = adbCommand,
                    diagnostics = diagnostics,
                    canDismiss = diagnostics.hasReadLogsPermission,
                    onOpenWizard = onOpenWizard,
                    onOpenButtonSettings = actions::openButtonSettings,
                    onCopyAdbCommand = actions::copyAdbCommand,
                    onOpenLanguageSettings = {
                        actions.updateConfig { it.copy(wizardLanguageSwitched = true) }
                        actions.openLanguageSettings()
                    },
                    onOpenDeveloperOptions = actions::openDeveloperOptions,
                    onGrantViaShizuku = actions::grantViaShizuku,
                    onOpenShizuku = actions::openShizuku,
                    onOpenOverlaySettings = actions::openOverlaySettings,
                    onOpenAppInfoSettings = actions::openAppInfoSettings,
                    onDismiss = { actions.updateConfig { it.copy(setupDismissed = true) } }
                )
            }
        }
    }

    if (config.hasAnyBackgroundLaunchAction() && !diagnostics.hasOverlayPermission) {
        item(key = "sk_overlay_warn") {
            SideKeyWarningBanner(
                title = stringResource(R.string.sidekey_warn_overlay_title),
                body = stringResource(R.string.sidekey_warn_overlay_body),
                cta = stringResource(R.string.simple_fix_cta),
                onAction = actions::openOverlaySettings
            )
        }
    }

    val shizukuReady = diagnostics.shizukuRunning && diagnostics.shizukuPermissionGranted
    val needsDnd = config.bindings.values.any { it.action.needsDndAccess }
    if (needsDnd && !diagnostics.hasDndAccess) {
        item(key = "sk_dnd_warn") {
            SideKeyWarningBanner(
                title = stringResource(R.string.sidekey_warn_dnd_title),
                body = stringResource(R.string.sidekey_warn_dnd_body),
                cta = stringResource(R.string.simple_fix_cta),
                onAction = actions::openDndSettings
            )
        }
    }
    if (!shizukuReady && config.bindings.values.any { it.action.needsShizukuForSilent }) {
        item(key = "sk_shizuku_warn") {
            SideKeyWarningBanner(
                title = stringResource(R.string.sidekey_warn_shizuku_title),
                body = stringResource(R.string.sidekey_warn_shizuku_body),
                cta = stringResource(R.string.simple_fix_cta),
                onAction = if (diagnostics.shizukuRunning) actions::grantViaShizuku else actions::openShizuku
            )
        }
    }

    item(key = "sk_gestures_header") {
        SectionHeader(stringResource(R.string.sidekey_gestures_header))
    }

    SideKeyGesture.entries.forEach { gesture ->
        item(key = "sk_gesture_${gesture.name}") {
            val binding = config.bindingFor(gesture)
            GestureBindingCard(
                gesture = gesture,
                binding = binding,
                onPickAction = { onPickGesture(gesture) },
                onToggleUnlockedOnly = { unlocked ->
                    actions.updateConfig { current ->
                        val updated = current.bindingFor(gesture).copy(unlockedOnly = unlocked)
                        current.copy(bindings = current.bindings + (gesture to updated))
                    }
                },
                onTest = { actions.testGesture(gesture) }
            )
        }
    }

    if (isAdvancedMode) {
        item(key = "sk_adv_header") {
            SectionHeader(stringResource(R.string.sidekey_adv_header))
        }
        item(key = "sk_adv_tuning") {
            SideKeyAdvancedTuningCard(config, actions)
        }
        item(key = "sk_adv_pattern") {
            SideKeyPatternCard(config, actions, onOpenDetectKey)
        }
        item(key = "sk_adv_diag") {
            SideKeyDiagnosticsCard(diagnostics, actions::reconnect)
        }
        if (diagnostics.hasReadLogsPermission && config.setupDismissed) {
            item(key = "sk_reopen_setup") {
                OutlinedButton(
                    onClick = { actions.updateConfig { it.copy(setupDismissed = false) } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.sidekey_reopen_setup))
                }
            }
        }
    } else if (diagnostics.hasReadLogsPermission) {
        item(key = "sk_simple_guide_btn") {
            TextButton(onClick = onOpenWizard, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sidekey_reopen_setup))
            }
        }
    }
}

@Composable
private fun SideKeySimpleSetupCard(onOpenWizard: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle(stringResource(R.string.sidekey_tab_label), Modifier.weight(1f))
            StatusPill(SideKeyStatus.NEEDS_ADB_GRANT)
        }
        SupportingText(stringResource(R.string.sidekey_simple_setup_body))
        Button(onClick = onOpenWizard, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.sidekey_simple_setup_cta))
        }
    }
}

@Composable
private fun SideKeyStatusCard(
    config: SideKeyConfig,
    diagnostics: SideKeyDiagnostics,
    isAdvancedMode: Boolean,
    actions: SideKeyUiActions
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle(stringResource(R.string.sidekey_tab_label), Modifier.weight(1f))
            StatusPill(diagnostics.status)
        }
        SwitchRow(
            title = stringResource(if (config.enabled) R.string.sidekey_master_on else R.string.sidekey_master_off),
            supporting = stringResource(R.string.sidekey_master_sub),
            checked = config.enabled,
            onCheckedChange = actions::setEnabled
        )
        if (config.enabled && diagnostics.status == SideKeyStatus.WAITING_CONSENT) {
            SupportingText(stringResource(R.string.sidekey_consent_hint))
            Button(onClick = actions::reconnect, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.sidekey_reconnect_cta), modifier = Modifier.padding(start = 8.dp))
            }
        } else if (config.enabled && diagnostics.status == SideKeyStatus.ERROR) {
            if (isAdvancedMode) {
                diagnostics.errorMessage?.let { SupportingText(it) }
            }
            OutlinedButton(onClick = actions::reconnect, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sidekey_reconnect_cta))
            }
        }
    }
}

@Composable
private fun StatusPill(status: SideKeyStatus) {
    val container = when (status) {
        SideKeyStatus.LISTENING -> MaterialTheme.colorScheme.primaryContainer
        SideKeyStatus.NEEDS_ADB_GRANT, SideKeyStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
        SideKeyStatus.WAITING_CONSENT -> MaterialTheme.colorScheme.secondaryContainer
        SideKeyStatus.DISABLED -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = when (status) {
        SideKeyStatus.LISTENING -> MaterialTheme.colorScheme.onPrimaryContainer
        SideKeyStatus.NEEDS_ADB_GRANT, SideKeyStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        SideKeyStatus.WAITING_CONSENT -> MaterialTheme.colorScheme.onSecondaryContainer
        SideKeyStatus.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = container, shape = RoundedCornerShape(999.dp)) {
        Text(
            stringResource(statusLabelRes(status)),
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SideKeyWarningBanner(title: String, body: String, cta: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle(title)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onAction, modifier = Modifier.align(Alignment.End)) {
                Text(cta)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GestureBindingCard(
    gesture: SideKeyGesture,
    binding: SideKeyGestureBinding,
    onPickAction: () -> Unit,
    onToggleUnlockedOnly: (Boolean) -> Unit,
    onTest: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPickAction),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(gestureIcon(gesture), contentDescription = null, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(gestureTitleRes(gesture)), style = MaterialTheme.typography.titleMedium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            actionIcon(binding.action),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (binding.action.isAssigned) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (binding.action.isAssigned) actionSummary(binding.action)
                            else stringResource(R.string.sidekey_tap_to_choose),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (binding.action.isAssigned) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (binding.action.isAssigned) {
                    IconButton(onClick = onTest) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.sidekey_test_action)
                        )
                    }
                }
            }
            if (binding.action.isAssigned) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = binding.unlockedOnly,
                        onClick = { onToggleUnlockedOnly(!binding.unlockedOnly) },
                        leadingIcon = {
                            Icon(
                                if (binding.unlockedOnly) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(stringResource(R.string.sidekey_unlocked_only)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SideKeyAdvancedTuningCard(config: SideKeyConfig, actions: SideKeyUiActions) {
    SectionCard(title = stringResource(R.string.sidekey_adv_timing_title)) {
        SliderRow(
            title = stringResource(R.string.sidekey_long_press_label),
            valueText = stringResource(R.string.sidekey_ms_value, config.longPressTimeoutMs),
            value = config.longPressTimeoutMs.toFloat(),
            valueRange = SideKeyConfig.MIN_LONG_PRESS_MS.toFloat()..SideKeyConfig.MAX_LONG_PRESS_MS.toFloat(),
            steps = ((SideKeyConfig.MAX_LONG_PRESS_MS - SideKeyConfig.MIN_LONG_PRESS_MS) / 50L).toInt() - 1,
            onValueChange = { raw ->
                val snapped = ((raw / 50f).roundToInt() * 50L)
                actions.updateConfig { it.copy(longPressTimeoutMs = snapped) }
            }
        )
        SliderRow(
            title = stringResource(R.string.sidekey_multi_tap_label),
            valueText = stringResource(R.string.sidekey_ms_value, config.multiTapTimeoutMs),
            value = config.multiTapTimeoutMs.toFloat(),
            valueRange = SideKeyConfig.MIN_MULTI_TAP_MS.toFloat()..SideKeyConfig.MAX_MULTI_TAP_MS.toFloat(),
            onValueChange = { raw ->
                val snapped = ((raw / 10f).roundToInt() * 10L)
                actions.updateConfig { it.copy(multiTapTimeoutMs = snapped) }
            }
        )
        HorizontalDivider()
        SwitchRow(
            title = stringResource(R.string.sidekey_haptic_title),
            checked = config.hapticFeedback,
            onCheckedChange = { v -> actions.updateConfig { it.copy(hapticFeedback = v) } }
        )
        SwitchRow(
            title = stringResource(R.string.sidekey_screen_off_title),
            supporting = stringResource(R.string.sidekey_screen_off_sub),
            checked = config.workWithScreenOff,
            onCheckedChange = { v -> actions.updateConfig { it.copy(workWithScreenOff = v) } }
        )
    }
}

@Composable
private fun SideKeyPatternCard(
    config: SideKeyConfig,
    actions: SideKeyUiActions,
    onOpenDetectKey: () -> Unit
) {
    var draft by remember(config.filterPattern) { mutableStateOf(config.filterPattern) }
    val valid = SideKeyLogParser.isValidRegex(draft)
    val dirty = draft.trim() != config.filterPattern

    SectionCard(title = stringResource(R.string.sidekey_adv_pattern_title)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.take(180) },
            label = { Text(stringResource(R.string.sidekey_pattern_field)) },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            singleLine = true,
            isError = !valid,
            supportingText = if (!valid) {
                { Text(stringResource(R.string.sidekey_pattern_invalid)) }
            } else null,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onOpenDetectKey) {
                Icon(Icons.Rounded.Sensors, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.sidekey_detect_cta), modifier = Modifier.padding(start = 8.dp))
            }
            if (dirty && valid) {
                Button(onClick = { actions.updateConfig { it.copy(filterPattern = draft.trim()) } }) {
                    Text(stringResource(R.string.card_maker_save))
                }
            }
            if (config.filterPattern != SideKeyConfig.DEFAULT_FILTER_PATTERN) {
                TextButton(onClick = {
                    draft = SideKeyConfig.DEFAULT_FILTER_PATTERN
                    actions.updateConfig { it.copy(filterPattern = SideKeyConfig.DEFAULT_FILTER_PATTERN) }
                }) {
                    Text(stringResource(R.string.lab_reset))
                }
            }
        }
    }
}

@Composable
private fun SideKeyDiagnosticsCard(diagnostics: SideKeyDiagnostics, onReconnect: () -> Unit) {
    val lastEventText = diagnostics.lastRawEventTimestampMs?.let { ts ->
        DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(ts))
    } ?: stringResource(R.string.measure_text_empty)

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(stringResource(R.string.sidekey_diag_title), Modifier.weight(1f))
            TextButton(onClick = onReconnect) { Text(stringResource(R.string.sidekey_reconnect_short)) }
        }
        StatusRow(
            label = stringResource(R.string.sidekey_diag_read_logs),
            value = stringResource(if (diagnostics.hasReadLogsPermission) R.string.sidekey_badge_granted else R.string.sidekey_badge_missing),
            ok = diagnostics.hasReadLogsPermission
        )
        StatusRow(
            label = stringResource(R.string.sidekey_perm_overlay),
            value = stringResource(if (diagnostics.hasOverlayPermission) R.string.sidekey_badge_granted else R.string.sidekey_badge_missing),
            ok = diagnostics.hasOverlayPermission
        )
        StatusRow(
            label = stringResource(R.string.sidekey_diag_shizuku),
            value = stringResource(
                when {
                    !diagnostics.shizukuInstalled -> R.string.sidekey_shizuku_absent
                    !diagnostics.shizukuRunning -> R.string.sidekey_shizuku_stopped
                    diagnostics.shizukuPermissionGranted -> R.string.sidekey_badge_granted
                    else -> R.string.sidekey_shizuku_needs_auth
                }
            ),
            ok = if (diagnostics.shizukuInstalled) diagnostics.shizukuPermissionGranted else null
        )
        ValueRow(stringResource(R.string.sidekey_diag_status), stringResource(statusLabelRes(diagnostics.status)))
        ValueRow(stringResource(R.string.sidekey_diag_presses), diagnostics.sessionPressCount.toString())
        ValueRow(stringResource(R.string.sidekey_diag_last_event), lastEventText)
        ValueRow(
            stringResource(R.string.sidekey_diag_last_gesture),
            diagnostics.lastParsedGesture?.let { stringResource(gestureTitleRes(it)) } ?: stringResource(R.string.measure_text_empty)
        )
    }
}
