package dev.atoll.island.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

// 104 px su 1272, misurato su ColorOS 17
private const val PILL_HEIGHT_RATIO = 0.082f

// sul nero l'isola si confonde: come su iPhone le si dà un bordo sottile
internal val IslandRim = Color(0x3DFFFFFF)

// capsuleFraction = larghezza capsula / larghezza schermo
@Composable
fun CapsulePreview(
    capsuleFraction: Float,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val fraction by animateFloatAsState(
        targetValue = if (active) capsuleFraction.coerceIn(0.05f, 1f) else 0f,
        label = "capsuleFraction"
    )
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(6f)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
            )
            .clearAndSetSemantics { },
        contentAlignment = Alignment.TopCenter
    ) {
        val pillHeight = maxWidth * PILL_HEIGHT_RATIO
        val camera = pillHeight * 0.46f
        val top = pillHeight * 0.35f
        Box(
            Modifier
                .offset(y = top)
                .width(maxOf(maxWidth * fraction, pillHeight))
                .height(pillHeight)
                .background(Color.Black, CircleShape)
                .border(1.dp, IslandRim, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .width(camera)
                    .height(camera)
                    .background(Color(0xFF1B1F24), CircleShape)
            )
        }
    }
}
