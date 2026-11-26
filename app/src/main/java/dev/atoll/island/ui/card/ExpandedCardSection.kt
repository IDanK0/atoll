package dev.atoll.island.ui.card

import android.animation.ValueAnimator
import android.content.Context
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.atoll.island.R
import dev.atoll.island.data.model.CardAction
import dev.atoll.island.data.model.CardBackground
import dev.atoll.island.data.model.CounterSource
import dev.atoll.island.data.model.ExpandedCardConfig
import dev.atoll.island.data.model.ExpandedCardMode
import dev.atoll.island.data.model.LargeIconType
import dev.atoll.island.data.model.LiveAlertConfig
import dev.atoll.island.data.model.PromotionDiagnostics
import dev.atoll.island.data.model.StatMetric
import dev.atoll.island.data.model.TapAction
import dev.atoll.island.domain.card.ActionSlots
import dev.atoll.island.domain.card.CardBackgroundAppearance
import dev.atoll.island.domain.card.CounterReducer
import dev.atoll.island.domain.card.CustomCardParser
import dev.atoll.island.domain.card.CustomCardPresets
import dev.atoll.island.domain.card.CustomCardTemplate
import dev.atoll.island.domain.card.MakerError
import dev.atoll.island.domain.card.MakerParseResult
import dev.atoll.island.domain.card.StatSelection
import dev.atoll.island.service.card.AndroidCardStrings
import dev.atoll.island.service.card.AppIconLoader
import dev.atoll.island.service.card.CardBackgroundRenderer
import dev.atoll.island.service.card.CardMedia
import dev.atoll.island.service.card.ColorOsCardDecorator
import dev.atoll.island.service.card.ShortcutIcons
import dev.atoll.island.ui.components.SectionCard
import dev.atoll.island.ui.components.StatusRow
import dev.atoll.island.ui.components.SupportingText
import dev.atoll.island.ui.components.SwitchRow
import dev.atoll.island.ui.components.ValueRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

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
            ExpandedCardMode.CUSTOM_TEMPLATE -> MakerSettings(card, onUpdate)
        }
        if (card.mode != ExpandedCardMode.MINIMAL && card.useColorOsLayouts) {
            CardBackgroundPicker(card, onUpdate)
        }
    }
}

private fun previewBrushForCard(context: Context, card: ExpandedCardConfig): Brush {
    val template = if (card.mode == ExpandedCardMode.CUSTOM_TEMPLATE && card.background == CardBackground.DEFAULT) {
        CustomCardPresets.resolveTemplate(card.customTemplateJson)
    } else null
    return previewBrush(CardBackgroundRenderer.appearance(context, card, template?.background, template?.customBgHex))
}

private fun gradientBrush(rgb: Int): Brush {
    val stops = CardBackgroundRenderer.gradientStops(rgb)
    return Brush.verticalGradient(listOf(Color(stops[0]), Color(stops[1]), Color(stops[2])))
}

private fun previewBrushHex(hex: String): Brush {
    val argb = hex.toLongOrNull(16)?.toInt() ?: 0xFF121820.toInt()
    val solid = Color(argb or 0xFF000000.toInt())
    return Brush.verticalGradient(listOf(solid.copy(alpha = 0.92f), solid.copy(alpha = 0.72f)))
}

private fun previewBrush(background: CardBackgroundAppearance): Brush {
    background.gradientRgb?.let { return gradientBrush(it) }
    return when {
        background.preset == CardBackground.PITCH ->
            Brush.verticalGradient(listOf(Color(0xFF090B0E), Color(0xFF0D1014)))
        background.argbHex != null -> previewBrushHex(background.argbHex)
        else -> Brush.verticalGradient(listOf(Color(0xFF020304), Color(0xFF1A212C)))
    }
}

@Composable
private fun CardBackgroundPicker(card: ExpandedCardConfig, onUpdate: CardUpdate) {
    val context = LocalContext.current
    val selected = card.background
    var showWheel by remember { mutableStateOf(false) }
    val wheelRainbow = remember {
        Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "${stringResource(R.string.card_bg_title)} · ${stringResource(AndroidCardStrings.backgroundNameRes(selected))}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CardBackground.entries.forEach { bg ->
                val isSelected = bg == selected
                val cd = stringResource(AndroidCardStrings.backgroundNameRes(bg))
                val fill = if (bg == CardBackground.CUSTOM) {
                    wheelRainbow
                } else {
                    previewBrush(CardBackgroundRenderer.appearance(context, card.copy(background = bg)))
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (bg == CardBackground.CUSTOM) {
                                showWheel = true
                            } else {
                                onUpdate { it.copy(background = bg) }
                            }
                        }
                        .semantics {
                            role = Role.RadioButton
                            this.selected = isSelected
                            contentDescription = cd
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(fill)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bg == CardBackground.CUSTOM) {
                            Box(
                                Modifier
                                    .size(if (isSelected) 16.dp else 12.dp)
                                    .clip(CircleShape)
                                    .background(Color(card.customBgColor or 0xFF000000.toInt()))
                                    .border(1.5.dp, Color.White, CircleShape)
                            )
                        } else if (isSelected) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }
                    }
                }
            }
        }
        if (selected.isSolidTintCapable) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = card.backgroundGradient,
                    onClick = { onUpdate { it.copy(backgroundGradient = true) } },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    label = { Text(stringResource(R.string.card_bg_style_gradient), maxLines = 1) }
                )
                SegmentedButton(
                    selected = !card.backgroundGradient,
                    onClick = { onUpdate { it.copy(backgroundGradient = false) } },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    label = { Text(stringResource(R.string.card_bg_style_solid), maxLines = 1) }
                )
            }
        }
    }
    if (showWheel) {
        ColorWheelDialog(
            initialColor = card.customBgColor,
            initialGradient = card.backgroundGradient || selected != CardBackground.CUSTOM,
            onDismiss = { showWheel = false },
            onConfirm = { rgb, gradient ->
                onUpdate { it.copy(background = CardBackground.CUSTOM, customBgColor = rgb, backgroundGradient = gradient) }
                showWheel = false
            }
        )
    }
}

// la scheda vera, renderizzata qui: stessa vista che riceve ColorOS, pulsanti compresi
@Composable
private fun CardPreviewFrame(config: LiveAlertConfig) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val makerNeeds = if (config.card.mode == ExpandedCardMode.CUSTOM_TEMPLATE) {
        CustomCardParser.monitorNeeds(config.card.customTemplateJson)
    } else null
    val fastStats = (config.card.mode == ExpandedCardMode.DEVICE_STATS &&
        StatSelection.sanitize(config.card.stats).any { it.needsFastRefresh }) ||
        makerNeeds?.fastStats == true
    val autoTicks = config.card.mode == ExpandedCardMode.DEVICE_STATS ||
        config.card.mode == ExpandedCardMode.PROGRESS_COUNTER ||
        config.card.mode == ExpandedCardMode.CUSTOM_TEMPLATE
    val refreshTick by produceState(0L, config.card.mode, fastStats) {
        while (true) {
            value++
            delay(if (fastStats) 4_000L else 15_000L)
        }
    }
    val sceneKey = config.card to (if (autoTicks) refreshTick else 0L) to locale
    val views by produceState<android.widget.RemoteViews?>(initialValue = null, sceneKey) {
        value = withContext(Dispatchers.Default) { ColorOsCardDecorator.views(context, config) }
    }
    val description = stringResource(R.string.card_preview_cd)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF090B0E))
            .background(previewBrushForCard(context, config.card))
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .heightIn(min = 56.dp)
            .semantics { contentDescription = description }
    ) {
        AnimatedContent(
            targetState = sceneKey to views,
            transitionSpec = {
                if (ValueAnimator.areAnimatorsEnabled()) {
                    (fadeIn(tween(180)) + scaleIn(initialScale = 0.96f, animationSpec = tween(180))) togetherWith fadeOut(tween(120))
                } else {
                    EnterTransition.None togetherWith ExitTransition.None
                }
            },
            label = "cardPreview"
        ) { (_, rv) ->
            if (rv != null) {
                AndroidView(
                    factory = { FrameLayout(it) },
                    update = { frame ->
                        frame.removeAllViews()
                        frame.addView(rv.apply(frame.context, frame))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
        SupportingText(stringResource(R.string.card_note_markdown_help))
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

private enum class DockDialog { NONE, PICK, APP, SHORTCUT, URL, COPY, DIAL, TIMER }

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
                val dockIcon by produceState<android.graphics.Bitmap?>(initialValue = null, action) {
                    value = withContext(Dispatchers.IO) {
                        val shortcut = (action as? CardAction.AppShortcut)?.iconId?.takeIf { it.isNotBlank() }
                        val pkg = when (action) {
                            is CardAction.LaunchApp -> action.packageName
                            is CardAction.AppShortcut -> action.packageName.takeIf { it.isNotBlank() }
                            else -> null
                        }
                        shortcut?.let { ShortcutIcons.load(context, it) } ?: pkg?.let { AppIconLoader.load(context, it, 120) }
                    }
                }
                val icon = dockIcon
                if (icon != null) {
                    Image(icon.asImageBitmap(), null, modifier = Modifier.size(36.dp))
                } else {
                    Icon(painterResource(AndroidCardStrings.actionIconRes(action)), contentDescription = null, modifier = Modifier.padding(6.dp))
                }
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 16.dp))
                IconButton(onClick = { set { list -> list - action } }) {
                    Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.card_button_remove, label))
                }
            }
        }
        if (current.size < ActionSlots.maxSlots(ExpandedCardMode.SHORTCUT_DOCK)) {
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
            options = listOf(
                CardAction.ToggleFlashlight,
                CardAction.Camera,
                CardAction.VolumePanel,
                CardAction.InternetPanel,
                CardAction.WifiPanel,
                CardAction.MediaPlayPause,
                CardAction.MediaNext,
                CardAction.MediaPrevious
            ),
            exclude = current,
            onDismiss = { dialog = DockDialog.NONE },
            onPick = { action ->
                dialog = when (action) {
                    is CardAction.LaunchApp -> DockDialog.APP
                    is CardAction.AppShortcut -> DockDialog.SHORTCUT
                    is CardAction.OpenUrl -> DockDialog.URL
                    is CardAction.CopySnippet -> DockDialog.COPY
                    is CardAction.DialNumber -> DockDialog.DIAL
                    is CardAction.ClockTimer -> DockDialog.TIMER
                    else -> {
                        add(action)
                        DockDialog.NONE
                    }
                }
            }
        )
        DockDialog.APP -> AppPickerDialog(onDismiss = { dialog = DockDialog.NONE }, onPick = { add(CardAction.LaunchApp(it.packageName, it.label)) })
        DockDialog.SHORTCUT -> ShortcutPickerDialog(onDismiss = { dialog = DockDialog.NONE }, onAdd = ::add)
        DockDialog.URL -> UrlDialog(onDismiss = { dialog = DockDialog.NONE }, onAdd = ::add)
        DockDialog.COPY -> CopyDialog(onDismiss = { dialog = DockDialog.NONE }, onAdd = ::add)
        DockDialog.DIAL -> DialDialog(onDismiss = { dialog = DockDialog.NONE }, onAdd = ::add)
        DockDialog.TIMER -> ClockTimerDialog(onDismiss = { dialog = DockDialog.NONE }, onAdd = ::add)
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CounterSource.entries.forEach { source ->
                FilterChip(
                    selected = card.counterSource == source,
                    onClick = { onUpdate { it.copy(counterSource = source) } },
                    label = { Text(stringResource(AndroidCardStrings.counterSourceRes(source))) }
                )
            }
        }
        if (card.counterSource == CounterSource.CUSTOM) {
            DebouncedField(R.string.card_field_label, card.counterLabel) { v -> onUpdate { it.copy(counterLabel = v) } }
            CounterStepperRow(
                label = stringResource(R.string.card_goal),
                value = "${card.counter.goal}",
                onMinus = { onUpdate { it.copy(counter = CounterReducer.setGoal(it.counter, it.counter.goal - 1)) } },
                onPlus = { onUpdate { it.copy(counter = CounterReducer.setGoal(it.counter, it.counter.goal + 1)) } }
            )
            CounterStepperRow(
                label = stringResource(R.string.card_step),
                value = "${card.counter.step}",
                onMinus = { onUpdate { it.copy(counter = CounterReducer.setStep(it.counter, it.counter.step - 1)) } },
                onPlus = { onUpdate { it.copy(counter = CounterReducer.setStep(it.counter, it.counter.step + 1)) } }
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.card_counter_midnight), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = card.counter.resetAtMidnight,
                    onCheckedChange = { enabled ->
                        val today = LocalDate.now().toEpochDay()
                        onUpdate { it.copy(counter = CounterReducer.setResetAtMidnight(it.counter, enabled, today)) }
                    }
                )
            }
        }
    }
}

@Composable
private fun CounterStepperRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onMinus) {
            Icon(painterResource(R.drawable.ic_act_minus), stringResource(R.string.card_action_minus))
        }
        Text(value, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onPlus) {
            Icon(painterResource(R.drawable.ic_act_plus), stringResource(R.string.card_action_plus))
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

@Composable
private fun MakerSettings(card: ExpandedCardConfig, onUpdate: CardUpdate) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val effective = CustomCardPresets.effectiveJson(card.customTemplateJson)
    val presets = listOf(
        R.string.card_maker_preset_clock to CustomCardPresets.CLOCK_AND_CONTROLS,
        R.string.card_maker_preset_telemetry to CustomCardPresets.HARDWARE_DECK,
        R.string.card_maker_preset_focus to CustomCardPresets.FOCUS_NOTE
    )
    val importedMsg = stringResource(R.string.card_maker_imported)
    val exportedMsg = stringResource(R.string.card_maker_exported)
    val readFailedMsg = stringResource(R.string.card_maker_import_failed)
    var editing by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val raw = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        val buf = CharArray(CustomCardTemplate.MAX_JSON_BYTES + 2)
                        val count = reader.read(buf)
                        if (count <= 0) "" else String(buf, 0, count)
                    }
                }.getOrNull()
            }
            if (raw == null) {
                Toast.makeText(context, readFailedMsg, Toast.LENGTH_SHORT).show()
                return@launch
            }
            when (val res = CustomCardParser.parse(raw)) {
                is MakerParseResult.Ok -> {
                    onUpdate {
                        it.copy(
                            customTemplateJson = raw,
                            background = res.template.background ?: CardBackground.DEFAULT
                        )
                    }
                    Toast.makeText(context, importedMsg, Toast.LENGTH_SHORT).show()
                }
                is MakerParseResult.Invalid -> {
                    Toast.makeText(context, makerErrorRes(res.reason), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(effective) }
                }.isSuccess
            }
            if (ok) Toast.makeText(context, exportedMsg, Toast.LENGTH_SHORT).show()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.card_maker_presets), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { (labelRes, presetJson) ->
                FilterChip(
                    selected = effective == presetJson,
                    onClick = {
                        val bg = CustomCardPresets.resolveTemplate(presetJson).background ?: CardBackground.DEFAULT
                        onUpdate { it.copy(customTemplateJson = presetJson, background = bg) }
                    },
                    label = { Text(stringResource(labelRes)) }
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                Text(stringResource(R.string.card_maker_import))
            }
            OutlinedButton(onClick = { exportLauncher.launch("atoll-card.json") }) {
                Text(stringResource(R.string.card_maker_export))
            }
            OutlinedButton(onClick = { editing = true }) {
                Text(stringResource(R.string.card_maker_edit))
            }
        }
        SupportingText(stringResource(R.string.card_maker_help))
    }

    if (editing) {
        MakerJsonDialog(
            initial = effective,
            onDismiss = { editing = false },
            onSave = { raw, template ->
                onUpdate {
                    it.copy(
                        customTemplateJson = raw,
                        background = template.background ?: it.background
                    )
                }
                editing = false
            }
        )
    }
}

@Composable
private fun MakerJsonDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String, CustomCardTemplate) -> Unit
) {
    var draft by remember { mutableStateOf(initial) }
    val result = remember(draft) { CustomCardParser.parse(draft) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.card_maker_editor_title)) },        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(CustomCardTemplate.MAX_JSON_BYTES) },
                    label = { Text(stringResource(R.string.card_maker_json_field)) },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    minLines = 6,
                    maxLines = 12,
                    isError = result is MakerParseResult.Invalid,
                    supportingText = (result as? MakerParseResult.Invalid)?.let { err ->
                        { Text(stringResource(makerErrorRes(err.reason))) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = result is MakerParseResult.Ok,
                onClick = { (result as? MakerParseResult.Ok)?.let { onSave(draft, it.template) } }
            ) { Text(stringResource(R.string.card_maker_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

private fun makerErrorRes(error: MakerError): Int = when (error) {
    MakerError.EMPTY -> R.string.card_maker_err_empty
    MakerError.TOO_LARGE -> R.string.card_maker_err_large
    MakerError.SYNTAX -> R.string.card_maker_err_syntax
    MakerError.NO_BLOCKS -> R.string.card_maker_err_blocks
    MakerError.UNKNOWN_BLOCK -> R.string.card_maker_err_block_type
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
    is CardAction.AppShortcut -> action.label.ifBlank { stringResource(R.string.card_btn_shortcut) }
    is CardAction.OpenUrl -> action.label.ifBlank { action.url }
    is CardAction.CopySnippet -> action.label.ifBlank { action.text }
    is CardAction.DialNumber -> action.label.ifBlank { action.phoneNumber }
    is CardAction.ClockTimer -> action.label.ifBlank { AndroidCardStrings.formatTimerDuration(LocalContext.current, action.seconds) }
    else -> stringResource(AndroidCardStrings.actionLabelRes(action))
}
