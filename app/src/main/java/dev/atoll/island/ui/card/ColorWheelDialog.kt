package dev.atoll.island.ui.card

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import dev.atoll.island.ui.components.AtollSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.atoll.island.R
import dev.atoll.island.service.card.CardBackgroundRenderer
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val QUICK_WHEEL_SWATCHES = listOf(
    0xFF16538A.toInt(),
    0xFF007EA7.toInt(),
    0xFF1C6B4B.toInt(),
    0xFF5D348A.toInt(),
    0xFF8C3B2B.toInt(),
    0xFF8C5A1B.toInt()
)

@Composable
fun ColorWheelDialog(
    initialColor: Int,
    initialGradient: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (colorRgb: Int, gradient: Boolean) -> Unit
) {
    val startHsv = remember(initialColor) {
        FloatArray(3).also { AndroidColor.colorToHSV(initialColor or 0xFF000000.toInt(), it) }
    }
    var hue by remember { mutableFloatStateOf(startHsv[0]) }
    var sat by remember { mutableFloatStateOf(startHsv[1].coerceAtLeast(0.15f)) }
    var value by remember { mutableFloatStateOf(startHsv[2].coerceIn(0.18f, 0.88f)) }
    var gradient by remember { mutableStateOf(initialGradient) }
    var hexText by remember { mutableStateOf(formatHex6(AndroidColor.HSVToColor(floatArrayOf(hue, sat, value)))) }

    val selectedArgb = AndroidColor.HSVToColor(floatArrayOf(hue, sat, value))
    val previewBrush = if (gradient) {
        val stops = CardBackgroundRenderer.gradientStops(selectedArgb)
        Brush.verticalGradient(listOf(Color(stops[0]), Color(stops[1]), Color(stops[2])))
    } else {
        val tint = Color((selectedArgb and 0x00FFFFFF) or 0x9E000000.toInt())
        Brush.verticalGradient(listOf(tint, tint))
    }

    fun syncHexFromHsv() {
        hexText = formatHex6(AndroidColor.HSVToColor(floatArrayOf(hue, sat, value)))
    }

    fun applyColor(rgb: Int) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(rgb or 0xFF000000.toInt(), hsv)
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2].coerceIn(0.15f, 0.92f)
        syncHexFromHsv()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.card_bg_wheel_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF090B0E))
                        .background(previewBrush)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                )
                ColorWheelDisc(
                    hue = hue,
                    saturation = sat,
                    brightness = value,
                    onChange = { h, s ->
                        hue = h
                        sat = s
                        syncHexFromHsv()
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.74f)
                        .aspectRatio(1f)
                        .align(Alignment.CenterHorizontally)
                )
                Column {
                    Text(
                        stringResource(R.string.card_bg_brightness),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AtollSlider(
                        value = value,
                        onValueChange = {
                            value = it
                            syncHexFromHsv()
                        },
                        valueRange = 0.15f..0.92f
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QUICK_WHEEL_SWATCHES.forEach { swatch ->
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(swatch))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable { applyColor(swatch) }
                        )
                    }
                }
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { raw ->
                        val cleaned = raw.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(6)
                        hexText = cleaned
                        if (cleaned.length == 6) {
                            cleaned.toIntOrNull(16)?.let(::applyColor)
                        }
                    },
                    label = { Text(stringResource(R.string.card_bg_hex_label)) },
                    prefix = { Text("#") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = gradient,
                        onClick = { gradient = true },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        label = { Text(stringResource(R.string.card_bg_style_gradient), maxLines = 1) }
                    )
                    SegmentedButton(
                        selected = !gradient,
                        onClick = { gradient = false },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        label = { Text(stringResource(R.string.card_bg_style_solid), maxLines = 1) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedArgb or 0xFF000000.toInt(), gradient) }) {
                Text(stringResource(R.string.card_maker_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun ColorWheelDisc(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    fun pickAt(pos: Offset, width: Float, height: Float) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(cx, cy).coerceAtLeast(1f)
        val dx = pos.x - cx
        val dy = pos.y - cy
        val dist = hypot(dx, dy).coerceAtMost(r)
        val deg = ((atan2(dy, dx) * 180.0 / PI) + 360.0) % 360.0
        onChange(deg.toFloat(), (dist / r).coerceIn(0f, 1f))
    }

    val hueColors = remember {
        listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
    }

    Canvas(
        modifier = modifier
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectTapGestures { pos -> pickAt(pos, size.width.toFloat(), size.height.toFloat()) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    pickAt(change.position, size.width.toFloat(), size.height.toFloat())
                }
            }
    ) {
        val radius = size.minDimension / 2f
        drawCircle(brush = Brush.sweepGradient(hueColors, center), radius = radius)
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.White, Color.Transparent), center, radius),
            radius = radius
        )
        if (brightness < 0.99f) {
            drawCircle(color = Color.Black.copy(alpha = (1f - brightness) * 0.72f), radius = radius)
        }
        val rad = hue * (PI / 180.0)
        val indicator = Offset(
            center.x + (cos(rad) * radius * saturation).toFloat(),
            center.y + (sin(rad) * radius * saturation).toFloat()
        )
        val thumbColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)))
        drawCircle(thumbColor, radius = 10.dp.toPx(), center = indicator)
        drawCircle(Color.White, radius = 10.dp.toPx(), center = indicator, style = Stroke(width = 2.5.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.45f), radius = 11.5.dp.toPx(), center = indicator, style = Stroke(width = 1.dp.toPx()))
    }
}

private fun formatHex6(argb: Int): String = "%06X".format(Locale.ROOT, argb and 0x00FFFFFF)
