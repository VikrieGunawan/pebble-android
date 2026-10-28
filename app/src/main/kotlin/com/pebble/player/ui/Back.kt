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
    tab: Int, count: Int, onTab: (Int) -> Unit, inFolder: Boolean, onFolderUp: () -> Unit,
    modifier: Modifier = Modifier, page: @Composable (Int) -> Unit,
) {
    val pos = remember { Animatable(tab.toFloat()) }
    LaunchedEffect(tab) {
        if (pos.targetValue != tab.toFloat()) {
            val d = abs(pos.value - tab)
            pos.animateTo(tab.toFloat(), tween((200 + 40 * d).toInt().coerceAtMost(420), easing = FastOutSlowInEasing))
        }
    }
    BackHandler(tab != 0) { if (inFolder) onFolderUp() else onTab(0) }
    val lo by remember { derivedStateOf { floor(pos.value).toInt().coerceIn(0, count - 1) } }
    val hi by remember { derivedStateOf { ceil(pos.value).toInt().coerceIn(0, count - 1) } }
    Box(modifier.clipToBounds()) {
        for (i in lo..hi) key(i) {
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = (i - pos.value) * size.width }) { page(i) }
        }
    }
}
