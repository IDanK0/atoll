package dev.atoll.island.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.atoll.island.R
import dev.atoll.island.data.model.LiveAlertConfig
import dev.atoll.island.data.model.NotificationChannelMode
import dev.atoll.island.data.model.NotificationTemplateStyle
import dev.atoll.island.data.model.SmallIconMode
import dev.atoll.island.data.model.TitleMode

@Composable
fun NotificationLabSection(
    config: LiveAlertConfig,
    onChannelModeChange: (NotificationChannelMode) -> Unit,
    onTemplateStyleChange: (NotificationTemplateStyle) -> Unit,
    onProgressIndeterminateChange: (Boolean) -> Unit,
    onSmallIconModeChange: (SmallIconMode) -> Unit,
    onTitleModeChange: (TitleMode) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier, title = stringResource(R.string.notif_lab_title)) {
        Choice(
            R.string.notif_channel_label,
            NotificationChannelMode.entries,
            config.channelMode,
            onChannelModeChange
        ) { if (it == NotificationChannelMode.LOW) R.string.notif_channel_low else R.string.notif_channel_default }
        Choice(
            R.string.notif_style_label,
            NotificationTemplateStyle.entries,
            config.templateStyle,
            onTemplateStyleChange
        ) {
            if (it == NotificationTemplateStyle.STANDARD) R.string.notif_style_standard else R.string.notif_style_progress
        }
        if (config.templateStyle == NotificationTemplateStyle.PROGRESS_STYLE) {
            SwitchRow(
                title = stringResource(R.string.notif_progress_indet),
                supporting = stringResource(R.string.notif_progress_indet_sub),
                checked = config.progressIndeterminate,
                onCheckedChange = onProgressIndeterminateChange
            )
        }
        Choice(R.string.notif_icon_label, SmallIconMode.entries, config.smallIconMode, onSmallIconModeChange) {
            when (it) {
                SmallIconMode.TRANSPARENT -> R.string.notif_icon_transparent
                SmallIconMode.DOT -> R.string.notif_icon_dot
                SmallIconMode.CAPSULE -> R.string.notif_icon_capsule
            }
        }
        Choice(R.string.notif_title_label, TitleMode.entries, config.titleMode, onTitleModeChange) {
            if (it == TitleMode.BRAILLE_BLANK) R.string.notif_title_blank else R.string.notif_title_text
        }
    }
}

@Composable
private fun <T> Choice(
    @StringRes label: Int,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    optionLabel: (T) -> Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size)
                ) { Text(stringResource(optionLabel(option))) }
            }
        }
    }
}
