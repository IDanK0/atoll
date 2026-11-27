package dev.atoll.island.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.atoll.island.R
import dev.atoll.island.data.model.OnboardingStep
import dev.atoll.island.data.model.PromotionDiagnostics
import dev.atoll.island.data.model.WizardLaunchRequest
import dev.atoll.island.domain.OnboardingCoordinator
import dev.atoll.island.ui.components.CapsulePreview
import kotlinx.coroutines.delay

@Composable
fun SetupWizardScreen(
    launchRequest: WizardLaunchRequest,
    diagnostics: PromotionDiagnostics,
    onRefreshPermissions: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenLiveAlertSettings: () -> Unit,
    onOpenAppInfoSettings: () -> Unit,
    onFinishAndEnable: () -> Unit,
    onDismissRerun: (() -> Unit)?
) {
    val pagerState = rememberPagerState(
        initialPage = launchRequest.initialStep.index,
        pageCount = { OnboardingStep.entries.size }
    )
    var targetStep by remember(launchRequest) { mutableStateOf(launchRequest.initialStep) }
    var openedAppInfoOnKeepAlive by remember { mutableStateOf(false) }

    // ricontrollo i permessi quando torni dalle impostazioni
    LifecycleResumeEffect(Unit) {
        onRefreshPermissions()
        onPauseOrDispose { }
    }

    // tiene lo scorrimento su uno stato stabile così il cambio pagina non lo cancella a metà
    LaunchedEffect(targetStep) {
        if (pagerState.currentPage != targetStep.index || pagerState.currentPageOffsetFraction != 0f) {
            pagerState.animateScrollToPage(targetStep.index)
        }
    }

    // riallinea lo scorrimento se un cambio finestra di sistema lo interrompe
    LaunchedEffect(pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress &&
            (pagerState.currentPage != targetStep.index || pagerState.currentPageOffsetFraction != 0f)
        ) {
            pagerState.animateScrollToPage(targetStep.index)
        }
    }

    // se il permesso arriva mentre sei al passo 1 va avanti da solo
    var waitingForPermission by remember(targetStep) {
        mutableStateOf(
            targetStep == OnboardingStep.NOTIFICATIONS && !diagnostics.notificationsEnabled
        )
    }
    LaunchedEffect(targetStep, diagnostics.notificationsEnabled) {
        if (targetStep == OnboardingStep.NOTIFICATIONS) {
            if (!diagnostics.notificationsEnabled) {
                waitingForPermission = true
            } else if (waitingForPermission) {
                waitingForPermission = false
                delay(750)
                if (targetStep == OnboardingStep.NOTIFICATIONS) {
                    targetStep = OnboardingStep.LIVE_ALERTS
                }
            }
        }
    }

    fun goToStep(step: OnboardingStep) {
        targetStep = step
    }

    fun handleBack() {
        val prev = targetStep.index - 1
        when {
            launchRequest.isRerun && targetStep == launchRequest.initialStep && onDismissRerun != null ->
                onDismissRerun()
            prev >= 0 -> goToStep(OnboardingStep.fromIndex(prev))
            onDismissRerun != null -> onDismissRerun()
        }
    }

    BackHandler(enabled = targetStep.index > 0 || (launchRequest.isRerun && onDismissRerun != null)) {
        handleBack()
    }

    val animatedProgress by animateFloatAsState(
        targetValue = OnboardingCoordinator.stepProgressFraction(targetStep),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "wizardProgress"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
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
                val showBack = targetStep.index > 0 || (launchRequest.isRerun && onDismissRerun != null)
                if (showBack) {
                    IconButton(onClick = ::handleBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.cd_wizard_back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.weight(1f),
                    drawStopIndicator = {}
                )

                if (launchRequest.isRerun && onDismissRerun != null) {
                    TextButton(onClick = onDismissRerun) {
                        Text(stringResource(R.string.action_close))
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (OnboardingStep.fromIndex(page)) {
                    OnboardingStep.WELCOME -> WelcomeStepPage(
                        onGetStarted = { goToStep(OnboardingStep.NOTIFICATIONS) }
                    )

                    OnboardingStep.NOTIFICATIONS -> NotificationsStepPage(
                        enabled = diagnostics.notificationsEnabled,
                        onAllowClick = onRequestNotifications,
                        onNextClick = { goToStep(OnboardingStep.LIVE_ALERTS) }
                    )

                    OnboardingStep.LIVE_ALERTS -> LiveAlertsStepPage(
                        enabled = diagnostics.canPostPromotedNotifications,
                        onOpenSettingsClick = onOpenLiveAlertSettings,
                        onContinueClick = { goToStep(OnboardingStep.KEEP_ALIVE) }
                    )

                    OnboardingStep.KEEP_ALIVE -> KeepAliveStepPage(
                        openedSettings = openedAppInfoOnKeepAlive,
                        onOpenAppInfoClick = {
                            openedAppInfoOnKeepAlive = true
                            onOpenAppInfoSettings()
                        },
                        onDoneOrSkipClick = { goToStep(OnboardingStep.ALL_SET) }
                    )

                    OnboardingStep.ALL_SET -> AllSetStepPage(
                        onFinishClick = onFinishAndEnable
                    )
                }
            }
        }
    }
}


@Composable
private fun WelcomeStepPage(onGetStarted: () -> Unit) {
    WizardPageScaffold(
        illustration = { CapsulePreview(capsuleFraction = WIZARD_PREVIEW_FRACTION, active = true) },
        title = stringResource(R.string.wizard_welcome_title),
        body = stringResource(R.string.wizard_welcome_body)
    ) {
        Button(onClick = onGetStarted, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.wizard_welcome_cta))
        }
    }
}

@Composable
private fun NotificationsStepPage(enabled: Boolean, onAllowClick: () -> Unit, onNextClick: () -> Unit) {
    WizardPageScaffold(
        illustration = { StepIcon(granted = enabled, iconRes = R.drawable.ic_notifications_bell) },
        title = stringResource(R.string.wizard_notif_title),
        body = stringResource(R.string.wizard_notif_body)
    ) {
        Button(onClick = if (enabled) onNextClick else onAllowClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (enabled) R.string.wizard_next else R.string.wizard_notif_cta))
        }
    }
}

@Composable
private fun LiveAlertsStepPage(enabled: Boolean, onOpenSettingsClick: () -> Unit, onContinueClick: () -> Unit) {
    WizardPageScaffold(
        illustration = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                StepIcon(granted = enabled, iconRes = R.drawable.ic_qs_atoll)
                SettingsToggleHint(stringResource(R.string.wizard_live_alerts_hint_toggle))
            }
        },
        title = stringResource(R.string.wizard_live_alerts_title),
        body = stringResource(R.string.wizard_live_alerts_body)
    ) {
        if (enabled) {
            Button(onClick = onContinueClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.wizard_next))
            }
        } else {
            Button(onClick = onOpenSettingsClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.wizard_live_alerts_cta))
            }
            TextButton(onClick = onContinueClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.wizard_continue_anyway))
            }
        }
    }
}

@Composable
private fun KeepAliveStepPage(openedSettings: Boolean, onOpenAppInfoClick: () -> Unit, onDoneOrSkipClick: () -> Unit) {
    WizardPageScaffold(
        illustration = { StepIcon(granted = openedSettings, iconRes = R.drawable.ic_battery_shield) },
        title = stringResource(R.string.wizard_keep_alive_title),
        body = stringResource(R.string.wizard_keep_alive_body)
    ) {
        if (openedSettings) {
            Button(onClick = onDoneOrSkipClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.wizard_next))
            }
            OutlinedButton(onClick = onOpenAppInfoClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.wizard_keep_alive_cta))
            }
        } else {
            Button(onClick = onOpenAppInfoClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.wizard_keep_alive_cta))
            }
            TextButton(onClick = onDoneOrSkipClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.wizard_skip))
            }
        }
    }
}

@Composable
private fun AllSetStepPage(onFinishClick: () -> Unit) {
    WizardPageScaffold(
        illustration = { CapsulePreview(capsuleFraction = WIZARD_PREVIEW_FRACTION, active = true) },
        title = stringResource(R.string.wizard_done_title),
        body = stringResource(R.string.wizard_done_body)
    ) {
        Button(onClick = onFinishClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.wizard_done_cta))
        }
    }
}

// proporzioni di 16 Pro / 17, le più comuni
private const val WIZARD_PREVIEW_FRACTION = 0.3118f

@Composable
private fun WizardPageScaffold(
    illustration: @Composable () -> Unit,
    title: String,
    body: String,
    actions: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        illustration()
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
        ) { actions() }
    }
}

@Composable
private fun StepIcon(granted: Boolean, @DrawableRes iconRes: Int) {
    val container by animateColorAsState(
        if (granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "stepIconContainer"
    )
    val doneLabel = stringResource(R.string.wizard_status_enabled)
    Box(
        modifier = Modifier
            .size(96.dp)
            .background(container, CircleShape)
            .semantics { if (granted) contentDescription = doneLabel },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(granted, label = "stepIcon") { done ->
            Icon(
                painter = painterResource(if (done) R.drawable.ic_check_circle else iconRes),
                contentDescription = null,
                tint = if (done) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

// solo un disegno, non si clicca
@Composable
private fun SettingsToggleHint(label: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 16.dp))
            Switch(checked = true, onCheckedChange = null)
        }
    }
}
