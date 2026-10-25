package com.pebble.player.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Predictive back: while the user drags the back gesture the content shrinks a little, rounds its corners and
 * follows the finger. Letting go finishes the motion (keeps shrinking and fades out), then runs [onBack] and fades
 * the next screen in. Cancelling springs the content back.
 */
@Composable
fun PredictiveBack(enabled: Boolean, onBack: () -> Unit, modifier: Modifier = Modifier.fillMaxSize(), content: @Composable () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    var fromRight by remember { mutableStateOf(false) }
    val fade = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    PredictiveBackHandler(enabled) { events ->
        try {
            events.collect { e ->
                progress = e.progress
                fromRight = e.swipeEdge == BackEventCompat.EDGE_RIGHT
            }
            // finger released: carry on to the end instead of freezing where it stopped
            coroutineScope {
                launch { fade.animateTo(0f, tween(150)) }
                animate(progress, 1f, animationSpec = tween(170)) { v, _ -> progress = v }
            }
            onBack()
            progress = 0f
            fade.animateTo(1f, tween(180))
        } catch (_: CancellationException) {
            scope.launch {
                launch { fade.snapTo(1f) }
                animate(progress, 0f, animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)) { v, _ -> progress = v }
            }
        }
    }
    Box(
        modifier.graphicsLayer {
            val s = 1f - 0.1f * progress
            scaleX = s
            scaleY = s
            alpha = fade.value
            translationX = (if (fromRight) -1f else 1f) * size.width * 0.03f * progress
            shape = RoundedCornerShape((32f * progress).dp)
            clip = progress > 0f
        },
    ) { content() }
}
