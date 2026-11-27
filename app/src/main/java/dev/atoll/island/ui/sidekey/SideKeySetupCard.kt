package dev.atoll.island.ui.sidekey

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.atoll.island.R
import dev.atoll.island.data.model.SideKeyDiagnostics
import dev.atoll.island.ui.components.CardTitle
import dev.atoll.island.ui.components.SectionCard
import dev.atoll.island.ui.components.StatusRow
import dev.atoll.island.ui.components.SupportingText

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SideKeySetupCard(
    adbCommand: String,
    diagnostics: SideKeyDiagnostics,
    canDismiss: Boolean,
    onOpenWizard: () -> Unit,
    onOpenButtonSettings: () -> Unit,
    onCopyAdbCommand: () -> Unit,
    onOpenLanguageSettings: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onGrantViaShizuku: () -> Unit,
    onOpenShizuku: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAppInfoSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    var showAdbTip by remember { mutableStateOf(false) }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(stringResource(R.string.sidekey_setup_title), Modifier.weight(1f))
            TextButton(onClick = onOpenWizard) {
                Text(stringResource(R.string.sidekey_simple_setup_cta))
            }
            if (canDismiss) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.action_close))
                }
            }
        }

        StepHeader(stepNumber = 1, title = stringResource(R.string.sidekey_step1_title))
        SupportingText(stringResource(R.string.sidekey_step1_body))
        OutlinedButton(onClick = onOpenButtonSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.sidekey_step1_cta))
        }

        HorizontalDivider()

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepHeader(
                stepNumber = 2,
                title = stringResource(R.string.sidekey_step2_title),
                modifier = Modifier.weight(1f)
            )
            PermissionBadge(granted = diagnostics.hasReadLogsPermission)
        }
        SupportingText(stringResource(R.string.sidekey_step2_body))

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    adbCommand,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onCopyAdbCommand) {
                    Icon(Icons.Rounded.ContentCopy, stringResource(R.string.sidekey_copy_cmd))
                }
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onCopyAdbCommand) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.sidekey_copy_cmd), modifier = Modifier.padding(start = 8.dp))
            }
            if (!diagnostics.hasReadLogsPermission && diagnostics.shizukuRunning) {
                Button(onClick = onGrantViaShizuku) {
                    Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.sidekey_shizuku_grant), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        if (!diagnostics.hasReadLogsPermission && diagnostics.shizukuInstalled && !diagnostics.shizukuRunning) {
            SupportingText(stringResource(R.string.sidekey_shizuku_not_running))
            OutlinedButton(onClick = onOpenShizuku) {
                Text(stringResource(R.string.sidekey_open_shizuku))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { showAdbTip = !showAdbTip }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.sidekey_adb_troubleshoot_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (showAdbTip) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        AnimatedVisibility(visible = showAdbTip) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SupportingText(stringResource(R.string.sidekey_adb_troubleshoot_body))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenLanguageSettings) {
                        Text(stringResource(R.string.sidekey_open_language_settings))
                    }
                    FilledTonalButton(onClick = onOpenDeveloperOptions) {
                        Text(stringResource(R.string.sidekey_highlight_dev_option))
                    }
                }
            }
        }

        HorizontalDivider()

        StepHeader(stepNumber = 3, title = stringResource(R.string.sidekey_step3_title))
        StatusRow(
            label = stringResource(R.string.sidekey_perm_overlay),
            value = stringResource(if (diagnostics.hasOverlayPermission) R.string.sidekey_badge_granted else R.string.sidekey_badge_missing),
            ok = diagnostics.hasOverlayPermission
        )
        SupportingText(stringResource(R.string.sidekey_step3_body))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!diagnostics.hasOverlayPermission) {
                FilledTonalButton(onClick = onOpenOverlaySettings) {
                    Text(stringResource(R.string.cal_float_permission_open))
                }
            }
            OutlinedButton(onClick = onOpenAppInfoSettings) {
                Text(stringResource(R.string.btn_battery_autolaunch))
            }
        }
    }
}

@Composable
private fun StepHeader(stepNumber: Int, title: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stepNumber.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Text(title, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
internal fun PermissionBadge(granted: Boolean) {
    Surface(
        color = if (granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (granted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                stringResource(if (granted) R.string.sidekey_badge_granted else R.string.sidekey_badge_missing),
                style = MaterialTheme.typography.labelMedium,
                color = if (granted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
