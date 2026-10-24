package com.pebble.player.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

/**
 * Predictive back: while the user drags the back gesture the content shrinks a little, rounds its corners and
 * follows the finger; letting go runs [onBack], cancelling springs it back.
 */
@Composable
fun PredictiveBack(enabled: Boolean, onBack: () -> Unit, modifier: Modifier = Modifier.fillMaxSize(), content: @Composable () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    var fromRight by remember { mutableStateOf(false) }
    PredictiveBackHandler(enabled) { events ->
        try {
            events.collect { e ->
                progress = e.progress
                fromRight = e.swipeEdge == BackEventCompat.EDGE_RIGHT
            }
            onBack()
            progress = 0f
        } catch (_: CancellationException) {
            progress = 0f
        }
    }
    Box(
        modifier.graphicsLayer {
            val s = 1f - 0.1f * progress
            scaleX = s
            scaleY = s
            translationX = (if (fromRight) -1f else 1f) * size.width * 0.03f * progress
            shape = RoundedCornerShape((32f * progress).dp)
            clip = progress > 0f
        },
    ) { content() }
}
