package dev.atoll.island.ui.sidekey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.atoll.island.R
import dev.atoll.island.domain.sidekey.SideKeyCaptureAnalyzer
import dev.atoll.island.service.sidekey.SideKeyLogReader
import dev.atoll.island.service.sidekey.SideKeyMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun SideKeyDetectKeyDialog(
    onDismiss: () -> Unit,
    onApplyPattern: (String) -> Unit
) {
    var captureAttempt by remember { mutableIntStateOf(0) }
    var pressCount by remember { mutableIntStateOf(0) }
    var lastLine by remember { mutableStateOf<String?>(null) }
    var candidate by remember { mutableStateOf<SideKeyCaptureAnalyzer.CaptureCandidate?>(null) }

    DisposableEffect(Unit) {
        SideKeyMonitor.capturePaused = true
        onDispose { SideKeyMonitor.capturePaused = false }
    }

    LaunchedEffect(captureAttempt) {
        pressCount = 0
        lastLine = null
        candidate = null
        val reader = SideKeyLogReader()
        candidate = reader.captureCandidate { count, preview ->
            withContext(Dispatchers.Main) {
                pressCount = count
                lastLine = preview
            }
        }
    }

    val found = candidate
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sidekey_detect_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (found == null) {
                    Text(
                        stringResource(R.string.sidekey_detect_prompt),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LinearProgressIndicator(
                        progress = { (pressCount.coerceIn(0, 3) / 3f) },
                        modifier = Modifier.fillMaxWidth(),
                        drawStopIndicator = {}
                    )
                    Text(
                        stringResource(R.string.sidekey_detect_progress, pressCount.coerceAtMost(3), 3),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    lastLine?.let { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.sidekey_detect_found),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(R.string.sidekey_detect_pattern_value, found.tag, found.token, found.regexPattern),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        found.sampleLine,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4
                    )
                }
            }
        },
        confirmButton = {
            if (found != null) {
                TextButton(onClick = { onApplyPattern(found.regexPattern) }) {
                    Text(stringResource(R.string.sidekey_detect_apply))
                }
            }
        },
        dismissButton = {
            if (found != null) {
                TextButton(onClick = { captureAttempt += 1 }) {
                    Text(stringResource(R.string.sidekey_detect_retry))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}
