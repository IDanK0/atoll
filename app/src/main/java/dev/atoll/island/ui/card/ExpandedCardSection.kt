package dev.atoll.island.ui.card

import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.atoll.island.R
import dev.atoll.island.data.model.CardAction
import dev.atoll.island.data.model.CounterSource
import dev.atoll.island.data.model.ExpandedCardConfig
import dev.atoll.island.data.model.ExpandedCardMode
import dev.atoll.island.data.model.LargeIconType
import dev.atoll.island.data.model.LiveAlertConfig
import dev.atoll.island.data.model.PromotionDiagnostics
import dev.atoll.island.data.model.StatMetric
import dev.atoll.island.data.model.TapAction
import dev.atoll.island.domain.card.ActionSlots
import dev.atoll.island.domain.card.CounterReducer
import dev.atoll.island.domain.card.StatSelection
import dev.atoll.island.service.card.AndroidCardStrings
import dev.atoll.island.service.card.AppIconLoader
import dev.atoll.island.service.card.CardMedia
import dev.atoll.island.service.card.ColorOsCardDecorator
import dev.atoll.island.ui.components.SectionCard
import dev.atoll.island.ui.components.StatusRow
import dev.atoll.island.ui.components.SupportingText
import dev.atoll.island.ui.components.SwitchRow
import dev.atoll.island.ui.components.ValueRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

typealias CardUpdate = ((ExpandedCardConfig) -> ExpandedCardConfig) -> Unit

@Composable
fun ExpandedCardSection(config: LiveAlertConfig, onUpdate: CardUpdate, modifier: Modifier = Modifier) {
    val card = config.card
    SectionCard(modifier) {
        CardPreviewFrame(config)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ExpandedCardMode.entries) { mode ->
                FilterChip(
                    selected = mode == card.mode,
                    onClick = { onUpdate { it.copy(mode = mode) } },
                    label = { Text(stringResource(AndroidCardStrings.modeNameRes(mode))) },
                    leadingIcon = {
                        Icon(painterResource(AndroidCardStrings.modeIconRes(mode)), contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
            }
        }
        when (card.mode) {
            ExpandedCardMode.MINIMAL -> SupportingText(stringResource(R.string.card_minimal_help))
            ExpandedCardMode.CUSTOM_NOTE -> NoteSettings(card, onUpdate)
            ExpandedCardMode.SHORTCUT_DOCK -> DockSettings(card, onUpdate)
            ExpandedCardMode.DEVICE_STATS -> StatsSettings(card, onUpdate)
            ExpandedCardMode.PROGRESS_COUNTER -> CounterSettings(card, onUpdate)
            ExpandedCardMode.QUICK_TOOLS -> ToolsSettings(card, onUpdate)
        }
    }
}

// la scheda vera, renderizzata qui: stessa vista che riceve ColorOS, pulsanti compresi
@Composable
private fun CardPreviewFrame(config: LiveAlertConfig) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val minute by produceState(0L) {
        while (true) {
            value = System.currentTimeMillis() / 60_000L
            delay(15_000L)
        }
    }
    val views = remember(config.card, minute, locale) { ColorOsCardDecorator.views(context, config) }
    val description = stringResource(R.string.card_preview_cd)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF020304), Color(0xFF1A212C))))
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .heightIn(min = 56.dp)
            .semantics { contentDescription = description }
    ) {
        if (views != null) {
            AndroidView(
                factory = { FrameLayout(it) },
                update = { frame ->
                    frame.removeAllViews()
                    frame.addView(views.apply(frame.context, frame))
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun NoteSettings(card: ExpandedCardConfig, onUpdate: CardUpdate) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.card_icon_photo_failed)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            if (withContext(Dispatchers.IO) { CardMedia.savePhoto(context, uri) }) {
                onUpdate { it.copy(largeIcon = it.largeIcon.copy(type = LargeIconType.IMAGE, imageVersion = System.currentTimeMillis())) }
            } else {
                Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DebouncedField(R.string.card_field_title, card.noteTitle) { v -> onUpdate { it.copy(noteTitle = v) } }
        DebouncedField(R.string.card_field_body, card.noteBody, minLines = 3) { v -> onUpdate { it.copy(noteBody = v) } }
        val hasPhoto = card.largeIcon.type == LargeIconType.IMAGE && CardMedia.hasPhoto(context)
        if (hasPhoto) {
            val thumb = remember(card.largeIcon.imageVersion) { CardMedia.scaled(context, 240)?.asImageBitmap() }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                thumb?.let {
                    Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
                }
                Text(stringResource(R.string.card_note_photo), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = pick) { Text(stringResource(R.string.card_note_photo_change)) }
                TextButton(onClick = {
                    CardMedia.deletePhoto(context)
                    onUpdate { it.copy(largeIcon = it.largeIcon.copy(type = LargeIconType.NONE)) }
                }) { Text(stringResource(R.string.card_note_photo_remove)) }
            }
        } else {
            OutlinedButton(onClick = pick) {
                Icon(painterResource(R.drawable.ic_card_image), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.card_note_photo_add), modifier = Modifier.padding(start = 8.dp))
            }
        }
        SupportingText(stringResource(R.string.card_note_photo_help))
    }
}

private enum class DockDialog { NONE, PICK, APP, URL, COPY }

@Composable
private fun DockSettings(card: ExpandedCardConfig, onUpdate: CardUpdate) {
    val context = LocalContext.current
    val current = ActionSlots.resolve(card)
    var dialog by remember { mutableStateOf(DockDialog.NONE) }
    fun set(transform: (List<CardAction>) -> List<CardAction>) = onUpdate {
        val next = ActionSlots.sanitize(transform(ActionSlots.resolve(it)), ExpandedCardMode.SHORTCUT_DOCK)
        it.copy(actionOverrides = it.actionOverrides + (ExpandedCardMode.SHORTCUT_DOCK to next))
    }
    fun add(action: CardAction) {
        set { it + action }
        dialog = DockDialog.NONE
    }
    Column {
        current.forEach { action ->
            val label = actionLabel(action)
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                val app = (action as? CardAction.LaunchApp)?.let { a -> remember(a.packageName) { AppIconLoader.load(context, a.packageName, 120) } }
                if (app != null) {
                    Image(app.asImageBitmap(), null, modifier = Modifier.size(36.dp))
                } else {
                    Icon(painterResource(AndroidCardStrings.actionIconRes(action)), contentDescription = null, modifier = Modifier.padding(6.dp))
                }
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 16.dp))
                IconButton(onClick = { set { list -> list - action } }) {
                    Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.card_button_remove, label))
                }
            }
        }
        if (current.size < ActionSlots.MAX_SLOTS) {
            OutlinedButton(onClick = { dialog = DockDialog.PICK }) {
                Icon(painterResource(R.drawable.ic_act_plus), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.card_buttons_add), modifier = Modifier.padding(start = 8.dp))
            }
        }
        SupportingText(stringResource(R.string.card_dock_help), Modifier.padding(top = 8.dp))
    }
    when (dialog) {
        DockDialog.NONE -> Unit
        DockDialog.PICK -> ActionPickerDialog(
            options = listOf(CardAction.ToggleFlashlight, CardAction.MediaPlayPause, CardAction.MediaNext),
            exclude = current,
            onDismiss = { dialog = DockDialog.NONE },
            onPick = { action ->
                dialog = when (action) {
                    is CardAction.LaunchApp -> DockDialog.APP
                    is CardAction.OpenUrl -> DockDialog.URL
                    is CardAction.CopySnippet -> DockDialog.COPY
                    else -> {
                        add(action)
                        DockDialog.NONE
                    }
                }
            }
        )
        DockDialog.APP -> AppPickerDialog(onDismiss = { dialog = DockDialog.NONE }, onPick = { add(CardAction.LaunchApp(it.packageName, it.label)) })
        DockDialog.URL -> UrlDialog(onDismiss = { dialog = DockDialog.NONE }, onAdd = ::add)
        DockDialog.COPY -> CopyDialog(onDismiss = { dialog = DockDialog.NONE }, onAdd = ::add)
    }
}

@Composable
private fun StatsSettings(card: ExpandedCardConfig, onUpdate: CardUpdate) {
    Column {
        SupportingText(stringResource(R.string.card_stats_pick))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatMetric.entries.forEach { metric ->
                val selected = metric in card.stats
                FilterChip(
                    selected = selected,
                    enabled = selected || card.stats.size < ExpandedCardConfig.MAX_STATS,
                    onClick = { onUpdate { it.copy(stats = StatSelection.toggle(it.stats, metric)) } },
                    label = { Text(stringResource(AndroidCardStrings.statLabelRes(metric))) }
                )
            }
        }
    }
}

@Composable
private fun CounterSettings(card: ExpandedCardConfig, onUpdate: CardUpdate) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            CounterSource.entries.forEachIndexed { i, source ->
                SegmentedButton(
                    selected = card.counterSource == source,
                    onClick = { onUpdate { it.copy(counterSource = source) } },
                    shape = SegmentedButtonDefaults.itemShape(i, CounterSource.entries.size)
                ) {
                    Text(stringResource(if (source == CounterSource.CUSTOM) R.string.card_counter_custom else R.string.card_counter_day))
                }
            }
        }
        if (card.counterSource == CounterSource.CUSTOM) {
            DebouncedField(R.string.card_field_label, card.counterLabel) { v -> onUpdate { it.copy(counterLabel = v) } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.card_goal), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { onUpdate { it.copy(counter = CounterReducer.setGoal(it.counter, it.counter.goal - 1)) } }) {
                    Icon(painterResource(R.drawable.ic_act_minus), stringResource(R.string.card_action_minus))
                }
                Text("${card.counter.goal}", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { onUpdate { it.copy(counter = CounterReducer.setGoal(it.counter, it.counter.goal + 1)) } }) {
                    Icon(painterResource(R.drawable.ic_act_plus), stringResource(R.string.card_action_plus))
                }
            }
        }
    }
}

@Composable
private fun ToolsSettings(card: ExpandedCardConfig, onUpdate: CardUpdate) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(R.string.card_range_min, card.randomMin, Modifier.weight(1f)) { v -> onUpdate { it.copy(randomMin = v) } }
        NumberField(R.string.card_range_max, card.randomMax, Modifier.weight(1f)) { v -> onUpdate { it.copy(randomMax = v) } }
    }
}

// salva mezzo secondo dopo l'ultima lettera, non a ogni tasto
@Composable
private fun DebouncedField(label: Int, value: String, minLines: Int = 1, onCommit: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(text) {
        delay(500)
        if (text != value) onCommit(text)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(stringResource(label)) },
        singleLine = minLines == 1,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun NumberField(label: Int, value: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw.filterIndexed { i, c -> c.isDigit() || (i == 0 && c == '-') }.take(7)
            text.toIntOrNull()?.let(onChange)
        },
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
fun AdvancedCardSection(config: LiveAlertConfig, diagnostics: PromotionDiagnostics, onUpdate: CardUpdate, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val card = config.card
    val metricSupported = diagnostics.apiLevel >= 37
    val running = diagnostics.isServiceRunning
    var pickingApp by remember { mutableStateOf(false) }
    SectionCard(modifier, title = stringResource(R.string.card_adv_title)) {
        ValueRow(stringResource(R.string.card_adv_mode), stringResource(AndroidCardStrings.modeNameRes(card.mode)))
        ValueRow(stringResource(R.string.card_adv_style), diagnostics.activeStyle)
        StatusRow(
            label = stringResource(R.string.card_adv_promotable),
            value = stringResource(if (diagnostics.builtNotificationEligible) R.string.diag_yes else R.string.diag_no),
            ok = if (diagnostics.isAndroid16Plus) diagnostics.builtNotificationEligible else null
        )
        StatusRow(
            label = stringResource(R.string.card_adv_flag),
            value = stringResource(
                when {
                    !running -> R.string.diag_systemui_idle
                    diagnostics.shownAsLiveAlert -> R.string.diag_yes
                    else -> R.string.diag_no
                }
            ),
            ok = if (running) diagnostics.shownAsLiveAlert else null
        )
        val pack = remember { AppIconLoader.activePackName(context) }
        val packLabel = remember(pack) {
            pack?.let { runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(it, 0)).toString() }.getOrNull() }
        }
        ValueRow(stringResource(R.string.card_adv_icon_pack), packLabel ?: stringResource(R.string.card_adv_icon_pack_none))
        SwitchRow(
            title = stringResource(R.string.card_adv_coloros),
            supporting = stringResource(R.string.card_adv_coloros_help),
            checked = card.useColorOsLayouts,
            onCheckedChange = { v -> onUpdate { it.copy(useColorOsLayouts = v) } }
        )
        SwitchRow(
            title = stringResource(R.string.card_adv_compat),
            supporting = stringResource(if (metricSupported) R.string.card_adv_compat_help else R.string.card_adv_compat_unsupported),
            checked = card.useMetricStyle && metricSupported,
            onCheckedChange = { v -> if (metricSupported) onUpdate { it.copy(useMetricStyle = v) } }
        )
        Text(stringResource(R.string.card_tap_title), style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            TapAction.entries.forEachIndexed { i, tap ->
                SegmentedButton(
                    selected = card.tapAction == tap,
                    onClick = { if (tap == TapAction.LAUNCH_APP) pickingApp = true else onUpdate { it.copy(tapAction = tap) } },
                    shape = SegmentedButtonDefaults.itemShape(i, TapAction.entries.size)
                ) {
                    Text(
                        stringResource(
                            when (tap) {
                                TapAction.OPEN_ATOLL -> R.string.card_tap_atoll
                                TapAction.LAUNCH_APP -> R.string.card_tap_app
                                TapAction.NONE -> R.string.card_tap_none
                            }
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
    if (pickingApp) {
        AppPickerDialog(
            onDismiss = { pickingApp = false },
            onPick = { app ->
                onUpdate { it.copy(tapAction = TapAction.LAUNCH_APP, tapPackage = app.packageName) }
                pickingApp = false
            }
        )
    }
}

@Composable
fun actionLabel(action: CardAction): String = when (action) {
    is CardAction.LaunchApp -> action.appLabel.ifBlank { action.packageName }
    is CardAction.OpenUrl -> action.label.ifBlank { action.url }
    is CardAction.CopySnippet -> action.label.ifBlank { action.text }
    else -> stringResource(AndroidCardStrings.actionLabelRes(action))
}
