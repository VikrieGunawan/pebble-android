package com.pebble.player.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Flat back animation: while the back gesture is dragged the screen simply slides with the finger. Letting go slides
 * it out, runs [onBack] while it is invisible, then slides the screen that is now showing in. Cancelling springs back.
 */
@Composable
fun PredictiveBack(enabled: Boolean, onBack: () -> Unit, modifier: Modifier = Modifier.fillMaxSize(), content: @Composable () -> Unit) {
    val p = remember { Animatable(0f) }   // slide, in screen widths (negative = arriving from the other side)
    val a = remember { Animatable(1f) }
    var dir by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    PredictiveBackHandler(enabled) { events ->
        try {
            events.collect { e ->
                dir = if (e.swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                p.snapTo(e.progress * 0.3f)
                a.snapTo(1f - 0.25f * e.progress)
            }
            // released: finish sliding out, swap while invisible, then slide the new screen in
            kotlinx.coroutines.coroutineScope {
                launch { a.animateTo(0f, tween(170)) }
                p.animateTo(1f, tween(170, easing = FastOutSlowInEasing))
            }
            onBack()
            p.snapTo(-0.12f)
            a.snapTo(0f)
            kotlinx.coroutines.coroutineScope {
                launch { a.animateTo(1f, tween(200)) }
                p.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
            }
        } catch (_: CancellationException) {
            scope.launch {
                launch { a.animateTo(1f, tween(150)) }
                p.animateTo(0f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium))
            }
        }
    }
    Box(modifier.graphicsLayer { translationX = dir * p.value * size.width; alpha = a.value }) { content() }
}

/**
 * Tabs laid side by side in one strip. Switching slides the strip, and every tab between the two ends slides past
 * (quickly) instead of being skipped. The back gesture drags the strip toward Home; letting go carries on through
 * the tabs in between. [inFolder] makes back go up one folder first ([onFolderUp]) with no tab motion.
 */
@Composable
fun TabPager(
    tab: Int, count: Int, onTab: (Int) -> Unit, inFolder: Boolean, onFolderUp: () -> Unit,
    modifier: Modifier = Modifier, page: @Composable (Int) -> Unit,
) {
    val pos = remember { Animatable(tab.toFloat()) }
    val scope = rememberCoroutineScope()
    fun spec(d: Float) = tween<Float>((200 + 40 * d).toInt().coerceAtMost(420), easing = FastOutSlowInEasing)
    LaunchedEffect(tab) {
        if (pos.targetValue != tab.toFloat()) pos.animateTo(tab.toFloat(), spec(kotlin.math.abs(pos.value - tab)))
    }
    PredictiveBackHandler(tab != 0) { events ->
        if (inFolder) {
            events.collect { }
            onFolderUp()
        } else {
            val from = tab
            val reach = minOf(from, 1).toFloat()
            try {
                events.collect { e -> pos.snapTo(from - reach * e.progress) }
                pos.animateTo(0f, spec(pos.value))
                onTab(0)
            } catch (_: CancellationException) {
                scope.launch { pos.animateTo(from.toFloat(), spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)) }
            }
        }
    }
    val lo by remember { derivedStateOf { floor(pos.value).toInt().coerceIn(0, count - 1) } }
    val hi by remember { derivedStateOf { ceil(pos.value).toInt().coerceIn(0, count - 1) } }
    Box(modifier.clipToBounds()) {
        for (i in lo..hi) key(i) {
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = (i - pos.value) * size.width }) { page(i) }
        }
    }
}
