package com.pebble.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** Plain back handling: no gesture-driven animation, the screen simply changes when back is pressed. */
@Composable
fun PredictiveBack(enabled: Boolean, onBack: () -> Unit, modifier: Modifier = Modifier.fillMaxSize(), content: @Composable () -> Unit) {
    BackHandler(enabled, onBack)
    Box(modifier) { content() }
}

/**
 * Tabs laid side by side in one strip. Switching slides the strip, and every tab between the two ends slides past
 * (quickly) instead of being skipped. Back goes up one folder first ([inFolder]), then to Home.
 */
@Composable
fun TabPager(
    tab: Int, count: Int, onTab: (Int) -> Unit, inFolder: Boolean, onFolderUp: () -> Unit, active: Boolean = true,
    modifier: Modifier = Modifier, fade: Boolean = true, page: @Composable (Int) -> Unit,
) {
    BackHandler(active && tab != 0) { if (inFolder) onFolderUp() else onTab(0) }
    if (fade) FadePager(tab, modifier, page) else SlidePager(tab, count, modifier, page)
}

/** Tabs cross-fade: the old page fades out while the new one fades in. */
@Composable
private fun FadePager(tab: Int, modifier: Modifier, page: @Composable (Int) -> Unit) {
    var from by remember { mutableIntStateOf(tab) }
    var to by remember { mutableIntStateOf(tab) }
    val prog = remember { Animatable(1f) }
    LaunchedEffect(tab) {
        if (tab != to) {
            from = to; to = tab
            prog.snapTo(0f)
            prog.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
        }
    }
    Box(modifier.clipToBounds()) {
        if (from != to && prog.value < 1f) key(from) { Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - prog.value }) { page(from) } }
        key(to) { Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (from != to) prog.value else 1f }) { page(to) } }
    }
}

/** Tabs sit side by side; switching slides the strip. */
@Composable
private fun SlidePager(tab: Int, count: Int, modifier: Modifier, page: @Composable (Int) -> Unit) {
    val pos = remember { Animatable(tab.toFloat()) }
    LaunchedEffect(tab) {
        if (pos.targetValue != tab.toFloat()) {
            val d = abs(pos.value - tab)
            pos.animateTo(tab.toFloat(), tween((320 + 60 * d).toInt().coerceAtMost(600), easing = FastOutSlowInEasing))
        }
    }
    // every tab between where we are and where we are going stays composed for the whole slide, so none is built mid-motion
    val lo by remember(tab) { derivedStateOf { minOf(floor(pos.value).toInt(), tab).coerceIn(0, count - 1) } }
    val hi by remember(tab) { derivedStateOf { maxOf(ceil(pos.value).toInt(), tab).coerceIn(0, count - 1) } }
    Box(modifier.clipToBounds()) {
        for (i in lo..hi) key(i) {
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = (i - pos.value) * size.width }) { page(i) }
        }
    }
}
