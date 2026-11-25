package dev.atoll.island.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.atoll.island.R
import dev.atoll.island.data.model.AdaptivePaddingResult
import dev.atoll.island.data.model.CapsulePreset
import dev.atoll.island.data.model.CapsuleScalingMode
import dev.atoll.island.data.model.DeviceDisplayMetrics
import dev.atoll.island.data.model.LiveAlertConfig
import dev.atoll.island.data.model.NotificationChannelMode
import dev.atoll.island.data.model.NotificationTemplateStyle
import dev.atoll.island.data.model.OnboardingStep
import dev.atoll.island.data.model.PaddingGlyph
import dev.atoll.island.data.model.PromotionDiagnostics
import dev.atoll.island.data.model.SmallIconMode
import dev.atoll.island.data.model.TitleMode
import dev.atoll.island.domain.OnboardingCoordinator
import dev.atoll.island.ui.components.AboutAndLicensesDialog
import dev.atoll.island.ui.components.CardGap
import dev.atoll.island.ui.components.CustomWidthCard
import dev.atoll.island.ui.components.DiagnosticsCard
import dev.atoll.island.ui.components.ManualTextCard
import dev.atoll.island.ui.components.MeasurementsCard
import dev.atoll.island.ui.components.NotificationLabSection
import dev.atoll.island.ui.components.PermissionFixCard
import dev.atoll.island.ui.components.PresetGrid
import dev.atoll.island.ui.components.PresetListCard
import dev.atoll.island.ui.components.ScalingModeCard
import dev.atoll.island.ui.components.ScreenPadding
import dev.atoll.island.ui.components.SectionHeader
import dev.atoll.island.ui.components.ServiceCard
import dev.atoll.island.ui.components.SizingModelCard
import dev.atoll.island.ui.components.StatusCard

interface DashboardActions {
    fun setAdvancedMode(advanced: Boolean)
    fun openCalibration()
    fun rerunSetup()
    fun fixPermission(step: OnboardingStep)
    fun setEnabled(enabled: Boolean)
    fun refreshDiagnostics()
    fun openLiveAlertSettings()
    fun openAppInfoSettings()
    fun selectPreset(preset: CapsulePreset)
    fun setCustomWidth(widthDp: Float)
    fun setScalingMode(mode: CapsuleScalingMode)
    fun setPillFloor(dp: Float)
    fun setPillOrigin(dp: Float)
    fun setDpPerUnit(dp: Float)
    fun setPhysicalCalibration(factor: Float)
    fun setMaxChipChars(chars: Int)
    fun setBrailleAnchor(enabled: Boolean)
    fun resetSizing()
    fun setManualText(enabled: Boolean)
    fun setManualLength(length: Int)
    fun setManualGlyph(glyph: PaddingGlyph)
    fun setDebugVisible(enabled: Boolean)
    fun setDebugGlyph(glyph: PaddingGlyph)
    fun setChannelMode(mode: NotificationChannelMode)
    fun setTemplateStyle(style: NotificationTemplateStyle)
    fun setProgressIndeterminate(indeterminate: Boolean)
    fun setSmallIconMode(mode: SmallIconMode)
    fun setTitleMode(mode: TitleMode)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtollDashboardScreen(
    config: LiveAlertConfig,
    isAdvancedMode: Boolean,
    isServiceRunning: Boolean,
    diagnostics: PromotionDiagnostics,
    displayMetrics: DeviceDisplayMetrics,
    adaptiveResult: AdaptivePaddingResult,
    sentText: String,
    actions: DashboardActions
) {
    var showAbout by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val active = config.enabled || isServiceRunning

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    OverflowMenu(
                        onCalibrate = actions::openCalibration,
                        onSetup = actions::rerunSetup,
                        onAbout = { showAbout = true }
                    )
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = ScreenPadding * 2),
            verticalArrangement = Arrangement.spacedBy(CardGap)
        ) {
            item(key = "mode") { ModeSwitch(isAdvancedMode, actions::setAdvancedMode) }
            if (isAdvancedMode) {
                advancedItems(config, active, diagnostics, displayMetrics, adaptiveResult, sentText, actions)
            } else {
                simpleItems(config, active, diagnostics, displayMetrics, adaptiveResult, actions)
            }
        }
    }

    if (showAbout) AboutAndLicensesDialog(onDismiss = { showAbout = false })
}

private fun LazyListScope.simpleItems(
    config: LiveAlertConfig,
    active: Boolean,
    diagnostics: PromotionDiagnostics,
    metrics: DeviceDisplayMetrics,
    result: AdaptivePaddingResult,
    actions: DashboardActions
) {
    OnboardingCoordinator.resolveMissingPermissionStep(diagnostics)?.let { step ->
        item(key = "fix") { PermissionFixCard(step, actions::fixPermission) }
    }
    item(key = "status") {
        StatusCard(config, active, result, metrics, actions::setEnabled, actions::openCalibration)
    }
    item(key = "presetsHeader") { SectionHeader(stringResource(R.string.simple_styles_title)) }
    item(key = "presets") { PresetGrid(config, metrics, actions::selectPreset) }
    // la larghezza custom è assoluta, il confronto iPhone vale solo per i preset
    if (config.selectedPreset.isCustom && !config.manualTextEnabled) {
        item(key = "custom") { CustomWidthCard(config, metrics, actions::setCustomWidth) }
    } else {
        item(key = "scaling") { ScalingModeCard(config.scalingMode, actions::setScalingMode) }
    }
}

private fun LazyListScope.advancedItems(
    config: LiveAlertConfig,
    active: Boolean,
    diagnostics: PromotionDiagnostics,
    metrics: DeviceDisplayMetrics,
    result: AdaptivePaddingResult,
    sentText: String,
    actions: DashboardActions
) {
    item(key = "service") { ServiceCard(active, config, result, actions::setEnabled) }
    item(key = "diagnostics") {
        DiagnosticsCard(diagnostics, actions::refreshDiagnostics, actions::openLiveAlertSettings, actions::openAppInfoSettings)
    }
    item(key = "measurements") { MeasurementsCard(config, metrics, result, actions::openCalibration) }
    item(key = "presetList") { PresetListCard(config, metrics, actions::selectPreset) }
    item(key = "model") {
        SizingModelCard(
            config = config,
            onScalingModeChange = actions::setScalingMode,
            onPillFloorChange = actions::setPillFloor,
            onPillOriginChange = actions::setPillOrigin,
            onDpPerUnitChange = actions::setDpPerUnit,
            onPhysicalCalibrationChange = actions::setPhysicalCalibration,
            onMaxChipCharsChange = actions::setMaxChipChars,
            onBrailleAnchorChange = actions::setBrailleAnchor,
            onReset = actions::resetSizing
        )
    }
    item(key = "manual") {
        ManualTextCard(
            config = config,
            sentText = sentText,
            onManualTextChange = actions::setManualText,
            onLengthChange = actions::setManualLength,
            onGlyphChange = actions::setManualGlyph,
            onDebugVisibleChange = actions::setDebugVisible,
            onDebugGlyphChange = actions::setDebugGlyph
        )
    }
    item(key = "notification") {
        NotificationLabSection(
            config = config,
            onChannelModeChange = actions::setChannelMode,
            onTemplateStyleChange = actions::setTemplateStyle,
            onProgressIndeterminateChange = actions::setProgressIndeterminate,
            onSmallIconModeChange = actions::setSmallIconMode,
            onTitleModeChange = actions::setTitleMode
        )
    }
}

@Composable
private fun ModeSwitch(advanced: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(false, true).forEachIndexed { index, value ->
            SegmentedButton(
                selected = advanced == value,
                onClick = { onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index, 2)
            ) {
                Text(stringResource(if (value) R.string.mode_advanced else R.string.mode_simple))
            }
        }
    }
}

@Composable
private fun OverflowMenu(onCalibrate: () -> Unit, onSetup: () -> Unit, onAbout: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.cd_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
                R.string.simple_calibrate_cta to onCalibrate,
                R.string.menu_setup_guide to onSetup,
                R.string.about_card_button to onAbout
            ).forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label), style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        expanded = false
                        action()
                    }
                )
            }
        }
    }
}
