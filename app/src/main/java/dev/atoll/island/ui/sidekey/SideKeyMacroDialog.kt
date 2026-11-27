package dev.atoll.island.ui.sidekey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import dev.atoll.island.R
import dev.atoll.island.data.model.MacroStep
import dev.atoll.island.data.model.SideKeyAction
import dev.atoll.island.data.model.SideKeyConfig
import dev.atoll.island.data.model.SideKeyGesture

private val MACRO_DELAY_OPTIONS = listOf(0L, 250L, 500L, 1_000L, 2_000L)

@Composable
internal fun SideKeyMacroDialog(
    gesture: SideKeyGesture,
    initialMacro: SideKeyAction.Macro?,
    onDismiss: () -> Unit,
    onSave: (SideKeyAction.Macro) -> Unit
) {
    var label by remember(initialMacro) { mutableStateOf(initialMacro?.label.orEmpty()) }
    var steps by remember(initialMacro) { mutableStateOf(initialMacro?.steps.orEmpty()) }
    var addingStep by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sidekey_macro_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(32) },
                    label = { Text(stringResource(R.string.sidekey_macro_name_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (steps.isEmpty()) {
                    Text(
                        stringResource(R.string.sidekey_macro_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                steps.forEachIndexed { index, step ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "${index + 1}.",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(actionIcon(step.action), contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(
                                    actionSummary(step.action),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (index > 0) {
                                    IconButton(
                                        onClick = { steps = steps.swap(index, index - 1) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.ArrowUpward,
                                            contentDescription = stringResource(R.string.sidekey_macro_move_up),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                if (index < steps.lastIndex) {
                                    IconButton(
                                        onClick = { steps = steps.swap(index, index + 1) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.ArrowDownward,
                                            contentDescription = stringResource(R.string.sidekey_macro_move_down),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { steps = steps.filterIndexed { i, _ -> i != index } },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = stringResource(R.string.sidekey_macro_remove),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            if (index < steps.lastIndex) {
                                FilterChip(
                                    selected = step.delayAfterMs > 0L,
                                    onClick = {
                                        val nextDelay = nextMacroDelay(step.delayAfterMs)
                                        steps = steps.mapIndexed { i, item ->
                                            if (i == index) item.copy(delayAfterMs = nextDelay) else item
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    label = { Text(stringResource(R.string.sidekey_macro_wait_fmt, step.delayAfterMs)) }
                                )
                            }
                        }
                    }
                }

                if (steps.size < SideKeyConfig.MAX_MACRO_STEPS) {
                    OutlinedButton(
                        onClick = { addingStep = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.sidekey_macro_add_step), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = steps.isNotEmpty(),
                onClick = { onSave(SideKeyAction.Macro(label = label.trim(), steps = steps)) }
            ) {
                Text(stringResource(R.string.card_maker_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )

    if (addingStep) {
        SideKeyActionPickerSheet(
            gesture = gesture,
            currentAction = SideKeyAction.None,
            allowMacro = false,
            titleOverride = stringResource(R.string.sidekey_macro_pick_step),
            onDismiss = { addingStep = false },
            onSelect = { chosen ->
                addingStep = false
                if (chosen.isAssigned && chosen !is SideKeyAction.Macro && steps.size < SideKeyConfig.MAX_MACRO_STEPS) {
                    steps = steps + MacroStep(chosen, SideKeyConfig.DEFAULT_MACRO_DELAY_MS)
                }
            }
        )
    }
}

private fun nextMacroDelay(current: Long): Long {
    val idx = MACRO_DELAY_OPTIONS.indexOfFirst { it == current }
    return MACRO_DELAY_OPTIONS[(idx + 1) % MACRO_DELAY_OPTIONS.size]
}

private fun <T> List<T>.swap(a: Int, b: Int): List<T> {
    val copy = toMutableList()
    val tmp = copy[a]
    copy[a] = copy[b]
    copy[b] = tmp
    return copy
}
