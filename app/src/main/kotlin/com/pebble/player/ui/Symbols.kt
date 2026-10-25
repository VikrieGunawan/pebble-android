import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity

/**
 * Liquid Glass On/Off Switch (gaya iOS LiquidToggle).
 * Track berupa kapsul kaca, Thumb berupa lensa kaca bulat dengan animasi pegas (spring).
 */
@Composable
fun PebbleSwitch(checked: Boolean, onChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val trackWidth = 54.dp
    val trackHeight = 32.dp
    val thumbSize = 24.dp
    val thumbPadding = 4.dp
    val maxThumbOffset = with(density) { (trackWidth - thumbSize - (thumbPadding * 2)).toPx() }

    val thumbOffsetPx by animateFloatAsState(
        targetValue = if (checked) maxThumbOffset else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "switchThumbOffset"
    )

    val trackTint by animateColorAsState(
        targetValue = if (checked) cs.primary.copy(alpha = 0.85f) else cs.surfaceContainerHighest.copy(alpha = 0.45f),
        label = "switchTrackTint"
    )

    val thumbTint by animateColorAsState(
        targetValue = if (checked) cs.primary else cs.onSurfaceVariant,
        label = "switchThumbTint"
    )

    val isClickable = onChange != null
    Box(
        modifier = modifier
            .size(trackWidth, trackHeight)
            .glass(radius = trackHeight / 2, lens = false, tint = trackTint, borderAlpha = 0.5f)
            .clickable(enabled = isClickable) { onChange?.invoke(!checked) }
            .padding(thumbPadding),
        contentAlignment = Alignment.CenterStart
    ) {
        // Thumb kaca cair melayang
        Box(
            modifier = Modifier
                .graphicsLayer { translationX = thumbOffsetPx }
                .size(thumbSize)
                .glass(radius = thumbSize / 2, lens = true, tint = Color.White, borderAlpha = 0.8f),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (checked) "check" else "close",
                null,
                modifier = Modifier.size(15.dp),
                tint = thumbTint
            )
        }
    }
}
