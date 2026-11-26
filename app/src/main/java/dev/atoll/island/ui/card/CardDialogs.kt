package dev.atoll.island.ui.card

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.graphics.drawable.toBitmap
import dev.atoll.island.R
import dev.atoll.island.data.model.CardAction
import dev.atoll.island.domain.card.ActionSlots
import dev.atoll.island.service.card.AndroidCardStrings
import dev.atoll.island.service.card.AppIconLoader
import dev.atoll.island.service.card.ShortcutIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

data class AppEntry(val packageName: String, val label: String)

@Composable
fun ActionPickerDialog(
    options: List<CardAction>,
    exclude: List<CardAction>,
    onDismiss: () -> Unit,
    onPick: (CardAction) -> Unit
) {
    val all = listOf(
        CardAction.LaunchApp("", ""),
        CardAction.AppShortcut("", ""),
        CardAction.OpenUrl("", ""),
        CardAction.CopySnippet("", ""),
        CardAction.DialNumber("", ""),
        CardAction.ClockTimer(300, "")
    ) + options.filter { it !in exclude }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.card_buttons_add)) },
        text = {
            LazyColumn {
                items(all) { action ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onPick(action) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(painterResource(AndroidCardStrings.actionIconRes(action)), contentDescription = null)
                        Text(
                            stringResource(AndroidCardStrings.actionLabelRes(action)),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
fun AppPickerDialog(onDismiss: () -> Unit, onPick: (AppEntry) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val collator = Collator.getInstance()
            pm.queryIntentActivities(launcher, 0)
                .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .filter { it.packageName != context.packageName }
                .distinctBy { it.packageName }
                .sortedWith { a, b -> collator.compare(a.label, b.label) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.card_pick_app)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.card_search_apps)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val list = apps
                if (list == null) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val shown = list.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(shown, key = { it.packageName }) { app ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clickable { onPick(app) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcon(app.packageName)
                                Text(app.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            AppIconLoader.load(context, packageName, 120)?.asImageBitmap()
        }
    }
    Box(Modifier.size(40.dp)) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(40.dp)) }
    }
}

@Composable
fun UrlDialog(onDismiss: () -> Unit, onAdd: (CardAction) -> Unit) {
    ValidatedLabeledDialog(
        titleRes = R.string.card_action_url,
        fieldRes = R.string.card_url_field,
        errorRes = R.string.card_url_invalid,
        keyboardType = KeyboardType.Uri,
        maxInputChars = 512,
        validate = ActionSlots::normalizeUrl,
        onDismiss = onDismiss,
        onAdd = { value, label -> onAdd(CardAction.OpenUrl(value, label)) }
    )
}

@Composable
fun CopyDialog(onDismiss: () -> Unit, onAdd: (CardAction) -> Unit) {
    var text by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.card_action_copy)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(1_000) },
                    label = { Text(stringResource(R.string.card_copy_field)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(24) },
                    label = { Text(stringResource(R.string.card_label_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotEmpty(),
                onClick = { onAdd(CardAction.CopySnippet(label.trim(), text)) }
            ) { Text(stringResource(R.string.card_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

private data class ShortcutCreatorEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val appLabel: String
)

@Suppress("DEPRECATION")
private fun extractShortcutBitmap(context: Context, data: Intent): Bitmap? {
    IntentCompat.getParcelableExtra(data, Intent.EXTRA_SHORTCUT_ICON, Bitmap::class.java)?.let { return it }
    val res = IntentCompat.getParcelableExtra(data, Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource::class.java)
        ?: return null
    return runCatching {
        val appRes = context.packageManager.getResourcesForApplication(res.packageName)
        @Suppress("DiscouragedApi")
        val id = appRes.getIdentifier(res.resourceName, null, null)
        if (id != 0) appRes.getDrawable(id, null)?.toBitmap(AppIconLoader.DEFAULT_SIZE_PX, AppIconLoader.DEFAULT_SIZE_PX) else null
    }.getOrNull()
}

@Composable
fun ShortcutPickerDialog(onDismiss: () -> Unit, onAdd: (CardAction) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.card_shortcut_failed)
    var pendingEntry by remember { mutableStateOf<ShortcutCreatorEntry?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val entry = pendingEntry
        pendingEntry = null
        if (result.resultCode != Activity.RESULT_OK || data == null) return@rememberLauncherForActivityResult
        @Suppress("DEPRECATION")
        val target = IntentCompat.getParcelableExtra(data, Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
        if (target == null) {
            Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        @Suppress("DEPRECATION")
        val name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)?.trim()
            ?.takeIf { it.isNotEmpty() } ?: entry?.label.orEmpty()
        val pkg = target.`package` ?: entry?.packageName.orEmpty()
        val uri = target.toUri(Intent.URI_INTENT_SCHEME)
        val rawBitmap = extractShortcutBitmap(context, data)
        if (rawBitmap == null) {
            onAdd(CardAction.AppShortcut(uri, name, pkg))
        } else {
            scope.launch {
                val iconId = withContext(Dispatchers.IO) { ShortcutIcons.save(context, rawBitmap) }.orEmpty()
                onAdd(CardAction.AppShortcut(uri, name, pkg, iconId))
            }
        }
    }
    val creators by produceState<List<ShortcutCreatorEntry>?>(null) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val collator = Collator.getInstance()
            pm.queryIntentActivities(Intent(Intent.ACTION_CREATE_SHORTCUT), 0)
                .map { info ->
                    val pkg = info.activityInfo.packageName
                    val appLabel = info.activityInfo.applicationInfo.loadLabel(pm).toString()
                    val label = info.loadLabel(pm).toString()
                    ShortcutCreatorEntry(pkg, info.activityInfo.name, label, appLabel)
                }
                .filter { it.packageName != context.packageName }
                .sortedWith { a, b -> collator.compare("${a.appLabel} ${a.label}", "${b.appLabel} ${b.label}") }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.card_pick_shortcut)) },
        text = {
            val list = creators
            when {
                list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                list.isEmpty() -> Text(stringResource(R.string.card_shortcut_empty))
                else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(list, key = { "${it.packageName}/${it.activityName}" }) { item ->
                        val title = if (item.appLabel.equals(item.label, ignoreCase = true)) item.label else "${item.appLabel} · ${item.label}"
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable {
                                    pendingEntry = item
                                    launcher.launch(
                                        Intent(Intent.ACTION_CREATE_SHORTCUT)
                                            .setComponent(ComponentName(item.packageName, item.activityName))
                                    )
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(item.packageName)
                            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
fun DialDialog(onDismiss: () -> Unit, onAdd: (CardAction) -> Unit) {
    ValidatedLabeledDialog(
        titleRes = R.string.card_action_dial,
        fieldRes = R.string.card_dial_field,
        errorRes = R.string.card_dial_invalid,
        keyboardType = KeyboardType.Phone,
        maxInputChars = 32,
        validate = ActionSlots::normalizePhone,
        onDismiss = onDismiss,
        onAdd = { value, label -> onAdd(CardAction.DialNumber(value, label)) }
    )
}

@Composable
private fun ValidatedLabeledDialog(
    titleRes: Int,
    fieldRes: Int,
    errorRes: Int,
    keyboardType: KeyboardType,
    maxInputChars: Int,
    validate: (String) -> String?,
    onDismiss: () -> Unit,
    onAdd: (value: String, label: String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    val normalized = validate(input)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(maxInputChars) },
                    label = { Text(stringResource(fieldRes)) },
                    singleLine = true,
                    isError = input.isNotBlank() && normalized == null,
                    supportingText = if (input.isNotBlank() && normalized == null) {
                        { Text(stringResource(errorRes)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(24) },
                    label = { Text(stringResource(R.string.card_label_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = normalized != null,
                onClick = { normalized?.let { onAdd(it, label.trim()) } }
            ) { Text(stringResource(R.string.card_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
fun ClockTimerDialog(onDismiss: () -> Unit, onAdd: (CardAction) -> Unit) {
    val context = LocalContext.current
    val presets = listOf(60, 300, 600, 900, 1800, 3600)
    var minutes by remember { mutableStateOf("5") }
    var seconds by remember { mutableStateOf("0") }
    var label by remember { mutableStateOf("") }
    val totalSeconds = ((minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0))
    val valid = totalSeconds in 1..ActionSlots.MAX_TIMER_SECONDS
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.card_action_timer)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = totalSeconds == preset,
                            onClick = {
                                minutes = (preset / 60).toString()
                                seconds = (preset % 60).toString()
                            },
                            label = { Text(AndroidCardStrings.formatTimerDuration(context, preset)) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.card_timer_minutes)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = seconds,
                        onValueChange = { seconds = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(R.string.card_timer_seconds)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(24) },
                    label = { Text(stringResource(R.string.card_label_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onAdd(CardAction.ClockTimer(totalSeconds, label.trim())) }
            ) { Text(stringResource(R.string.card_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
