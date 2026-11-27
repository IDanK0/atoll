package dev.atoll.island.ui.sidekey

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.atoll.island.R
import dev.atoll.island.data.model.SideKeyConfig
import dev.atoll.island.data.model.SideKeyDiagnostics

private enum class SideKeyWizardStep(val index: Int) {
    FREE_BUTTON(0),
    UNLOCK_KEY(1),
    PERMISSIONS(2),
    RESTORE(3),
    READY(4);

    companion object {
        fun fromIndex(index: Int): SideKeyWizardStep =
            entries.getOrElse(index.coerceIn(0, entries.lastIndex)) { FREE_BUTTON }
    }
}

@Composable
internal fun SideKeyWizardScreen(
    sideKeyConfig: SideKeyConfig,
    diagnostics: SideKeyDiagnostics,
    adbCommand: String,
    actions: SideKeyUiActions,
    showColorOsGuideInitially: Boolean = false,
    onDismissColorOsGuide: () -> Unit = {},
    onFinishAndEnable: () -> Unit,
    onClose: () -> Unit
) {
    val currentStep = SideKeyWizardStep.fromIndex(sideKeyConfig.wizardStep)
    val pagerState = rememberPagerState(initialPage = currentStep.index, pageCount = { SideKeyWizardStep.entries.size })
    var openedButtonSettings by rememberSaveable { mutableStateOf(false) }
    var openedBattery by rememberSaveable { mutableStateOf(false) }
    var showUnlockHelpDialog by rememberSaveable { mutableStateOf(false) }
    var showShizukuInstallDialog by rememberSaveable { mutableStateOf(false) }
    val isEnglishNow = LocalLocale.current.platformLocale.language.equals("en", ignoreCase = true)

    fun setStep(step: SideKeyWizardStep) {
        actions.updateConfig { it.copy(wizardStep = step.index) }
    }

    LaunchedEffect(currentStep) {
        if (pagerState.currentPage != currentStep.index || pagerState.currentPageOffsetFraction != 0f) {
            pagerState.animateScrollToPage(currentStep.index)
        }
    }

    LaunchedEffect(isEnglishNow, sideKeyConfig.wizardLanguageSwitched) {
        if (!isEnglishNow && sideKeyConfig.wizardLanguageSwitched) {
            actions.updateConfig { it.copy(wizardLanguageSwitched = false) }
        }
    }

    LaunchedEffect(showColorOsGuideInitially) {
        if (showColorOsGuideInitially) {
            setStep(SideKeyWizardStep.UNLOCK_KEY)
            showUnlockHelpDialog = true
        }
    }

    fun goBack() {
        val prev = currentStep.index - 1
        if (prev >= 0) setStep(SideKeyWizardStep.fromIndex(prev)) else onClose()
    }

    BackHandler(onBack = ::goBack)

    val progress by animateFloatAsState(
        targetValue = (currentStep.index + 1) / SideKeyWizardStep.entries.size.toFloat(),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "sideKeyWizardProgress"
    )

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentStep.index > 0) {
                    IconButton(onClick = ::goBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_wizard_back)
                        )
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.weight(1f),
                    drawStopIndicator = {}
                )
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.action_close))
                }
            }

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (SideKeyWizardStep.fromIndex(page)) {
                    SideKeyWizardStep.FREE_BUTTON -> WizardFreeButtonStep(
                        opened = openedButtonSettings,
                        onOpenSettings = {
                            openedButtonSettings = true
                            actions.openButtonSettings()
                        },
                        onNext = { setStep(SideKeyWizardStep.UNLOCK_KEY) }
                    )

                    SideKeyWizardStep.UNLOCK_KEY -> WizardUnlockStep(
                        diagnostics = diagnostics,
                        onGrantShizuku = actions::grantViaShizuku,
                        onOpenShizuku = actions::openShizuku,
                        onShowShizukuGuide = { showShizukuInstallDialog = true },
                        onShowUnlockHelp = { showUnlockHelpDialog = true },
                        onNext = { setStep(SideKeyWizardStep.PERMISSIONS) }
                    )

                    SideKeyWizardStep.PERMISSIONS -> WizardPermissionsStep(
                        hasOverlay = diagnostics.hasOverlayPermission,
                        openedBattery = openedBattery,
                        onOpenOverlay = actions::openOverlaySettings,
                        onOpenBattery = {
                            openedBattery = true
                            actions.openAppInfoSettings()
                        },
                        onNext = { setStep(SideKeyWizardStep.RESTORE) }
                    )

                    SideKeyWizardStep.RESTORE -> WizardRestoreStep(
                        isEnglishNow = isEnglishNow,
                        onOpenLanguageSettings = actions::openLanguageSettings,
                        onOpenDeveloperOptions = actions::openDeveloperOptions,
                        onNext = { setStep(SideKeyWizardStep.READY) }
                    )

                    SideKeyWizardStep.READY -> WizardReadyStep(
                        unlocked = diagnostics.hasReadLogsPermission,
                        onFinishEnable = onFinishAndEnable,
                        onCloseWithoutEnable = onClose
                    )
                }
            }
        }
    }

    if (showShizukuInstallDialog) {
        AlertDialog(
            onDismissRequest = { showShizukuInstallDialog = false },
            title = { Text(stringResource(R.string.sidekey_wiz_shizuku_dl_title)) },
            text = { Text(stringResource(R.string.sidekey_wiz_shizuku_dl_body)) },
            confirmButton = {
                Button(onClick = {
                    showShizukuInstallDialog = false
                    actions.openShizuku()
                }) {
                    Text(stringResource(R.string.sidekey_wiz_shizuku_dl_cta))
                }
            },
            dismissButton = {
                TextButton(onClick = { showShizukuInstallDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    if (showUnlockHelpDialog) {
        ColorOsPermissionGuideDialog(
            diagnostics = diagnostics,
            adbCommand = adbCommand,
            actions = actions,
            onOpenedLanguageSettings = {
                actions.updateConfig { it.copy(wizardLanguageSwitched = true) }
            },
            onDismiss = {
                showUnlockHelpDialog = false
                onDismissColorOsGuide()
            }
        )
    }
}

@Composable
internal fun ColorOsPermissionGuideDialog(
    diagnostics: SideKeyDiagnostics,
    adbCommand: String,
    actions: SideKeyUiActions,
    onOpenedLanguageSettings: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val isEnglish = LocalLocale.current.platformLocale.language.equals("en", ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sidekey_coloros_guide_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(
                        if (isEnglish) R.string.sidekey_coloros_intro_en
                        else R.string.sidekey_coloros_intro_non_en
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )

                HorizontalDivider()

                if (!isEnglish) {
                    Text(
                        stringResource(R.string.sidekey_coloros_step_lang_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        stringResource(R.string.sidekey_coloros_step_lang_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            onOpenedLanguageSettings()
                            actions.openLanguageSettings()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.sidekey_open_language_settings))
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            stringResource(R.string.sidekey_coloros_lang_ready),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                HorizontalDivider()

                Text(
                    stringResource(R.string.sidekey_coloros_step_dev_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    stringResource(R.string.sidekey_coloros_step_dev_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = actions::openDeveloperOptions,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.sidekey_highlight_dev_option))
                }

                HorizontalDivider()

                Text(
                    stringResource(R.string.sidekey_coloros_step_finish_title),
                    style = MaterialTheme.typography.titleSmall
                )
                if (diagnostics.shizukuRunning && !diagnostics.hasReadLogsPermission) {
                    Text(
                        stringResource(R.string.sidekey_coloros_step_finish_shizuku),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = actions::grantViaShizuku,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.sidekey_coloros_retry_shizuku))
                    }
                } else {
                    Text(
                        stringResource(R.string.sidekey_wiz_pc_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            adbCommand,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = actions::copyAdbCommand) {
                            Icon(Icons.Rounded.ContentCopy, stringResource(R.string.sidekey_copy_cmd))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
private fun WizardFreeButtonStep(
    opened: Boolean,
    onOpenSettings: () -> Unit,
    onNext: () -> Unit
) {
    WizardScaffold(
        icon = Icons.Rounded.TouchApp,
        done = opened,
        title = stringResource(R.string.sidekey_wiz_step1_title),
        body = stringResource(R.string.sidekey_wiz_step1_body)
    ) {
        if (opened) {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.wizard_next))
            }
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sidekey_step1_cta))
            }
        } else {
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sidekey_step1_cta))
            }
            TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sidekey_wiz_already_done))
            }
        }
    }
}

@Composable
private fun WizardUnlockStep(
    diagnostics: SideKeyDiagnostics,
    onGrantShizuku: () -> Unit,
    onOpenShizuku: () -> Unit,
    onShowShizukuGuide: () -> Unit,
    onShowUnlockHelp: () -> Unit,
    onNext: () -> Unit
) {
    val unlocked = diagnostics.hasReadLogsPermission
    val title = when {
        unlocked -> stringResource(R.string.sidekey_wiz_step2_done_title)
        diagnostics.shizukuRunning -> stringResource(R.string.sidekey_wiz_step2_shizuku_ready_title)
        diagnostics.shizukuInstalled -> stringResource(R.string.sidekey_wiz_step2_shizuku_start_title)
        else -> stringResource(R.string.sidekey_wiz_step2_title)
    }
    val body = when {
        unlocked -> stringResource(R.string.sidekey_wiz_step2_done_body)
        diagnostics.shizukuRunning -> stringResource(R.string.sidekey_wiz_step2_shizuku_ready_body)
        diagnostics.shizukuInstalled -> stringResource(R.string.sidekey_wiz_step2_shizuku_start_body)
        else -> stringResource(R.string.sidekey_wiz_step2_body)
    }

    WizardScaffold(
        icon = Icons.Rounded.LockOpen,
        done = unlocked,
        title = title,
        body = body
    ) {
        when {
            unlocked -> {
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.wizard_next))
                }
            }
            diagnostics.shizukuRunning -> {
                Button(onClick = onGrantShizuku, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.sidekey_wiz_unlock_one_tap))
                }
                TextButton(onClick = onShowUnlockHelp, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.sidekey_coloros_guide_cta))
                }
            }
            diagnostics.shizukuInstalled -> {
                Button(onClick = onOpenShizuku, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.sidekey_open_shizuku))
                }
                OutlinedButton(onClick = onShowUnlockHelp, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.sidekey_coloros_guide_cta))
                }
            }
            else -> {
                Button(onClick = onShowShizukuGuide, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.sidekey_wiz_use_phone_shizuku))
                }
                OutlinedButton(onClick = onShowUnlockHelp, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.sidekey_coloros_guide_cta))
                }
                TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.wizard_skip))
                }
            }
        }
    }
}

@Composable
private fun WizardPermissionsStep(
    hasOverlay: Boolean,
    openedBattery: Boolean,
    onOpenOverlay: () -> Unit,
    onOpenBattery: () -> Unit,
    onNext: () -> Unit
) {
    WizardScaffold(
        icon = Icons.Rounded.BatteryChargingFull,
        done = hasOverlay,
        title = stringResource(R.string.sidekey_wiz_step3_title),
        body = stringResource(R.string.sidekey_wiz_step3_body)
    ) {
        when {
            !hasOverlay -> {
                Button(onClick = onOpenOverlay, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.sidekey_wiz_allow_overlay))
                }
                OutlinedButton(onClick = onOpenBattery, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.btn_battery_autolaunch))
                }
                TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.wizard_skip))
                }
            }
            !openedBattery -> {
                Button(onClick = onOpenBattery, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.btn_battery_autolaunch))
                }
                TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.wizard_next))
                }
            }
            else -> {
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.wizard_next))
                }
                OutlinedButton(onClick = onOpenBattery, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.btn_battery_autolaunch))
                }
            }
        }
    }
}

@Composable
private fun WizardRestoreStep(
    isEnglishNow: Boolean,
    onOpenLanguageSettings: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onNext: () -> Unit
) {
    val body = stringResource(
        if (isEnglishNow) R.string.sidekey_wiz_restore_step_body_en
        else R.string.sidekey_wiz_restore_step_body_ok
    )
    WizardScaffold(
        icon = Icons.Rounded.LockOpen,
        done = !isEnglishNow,
        title = stringResource(R.string.sidekey_wiz_restore_step_title),
        body = body
    ) {
        if (isEnglishNow) {
            Button(onClick = onOpenLanguageSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sidekey_wiz_restore_lang_cta))
            }
            OutlinedButton(onClick = onOpenDeveloperOptions, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sidekey_wiz_restore_dev_cta))
            }
            TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.wizard_next))
            }
        } else {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.wizard_next))
            }
            OutlinedButton(onClick = onOpenDeveloperOptions, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sidekey_wiz_restore_dev_cta))
            }
            TextButton(onClick = onOpenLanguageSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sidekey_open_language_settings))
            }
        }
    }
}

@Composable
private fun WizardReadyStep(
    unlocked: Boolean,
    onFinishEnable: () -> Unit,
    onCloseWithoutEnable: () -> Unit
) {
    val body = stringResource(
        if (unlocked) R.string.sidekey_wiz_done_body else R.string.sidekey_wiz_partial_body
    )
    WizardScaffold(
        icon = Icons.Rounded.TouchApp,
        done = unlocked,
        title = stringResource(if (unlocked) R.string.sidekey_wiz_done_title else R.string.sidekey_wiz_partial_title),
        body = body
    ) {
        if (unlocked) {
            Button(onClick = onFinishEnable, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sidekey_wiz_done_cta))
            }
        } else {
            Button(onClick = onCloseWithoutEnable, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sidekey_wiz_partial_cta))
            }
        }
    }
}

@Composable
private fun WizardScaffold(
    icon: ImageVector,
    done: Boolean,
    title: String,
    body: String,
    actions: @Composable () -> Unit
) {
    val container by animateColorAsState(
        if (done) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "sideKeyStepContainer"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(container, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(done, label = "sideKeyStepIcon") { ok ->
                Icon(
                    if (ok) Icons.Rounded.CheckCircle else icon,
                    contentDescription = null,
                    tint = if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            actions()
        }
    }
}
