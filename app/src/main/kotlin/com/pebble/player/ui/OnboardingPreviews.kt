package com.pebble.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pebble.player.data.K
import com.pebble.player.data.Prefs
import com.pebble.player.data.Store
import kotlinx.coroutines.launch

private class Mini(val bg: Color, val surf: Color, val bar: Color, val ink: Color)
private val LightMini = Mini(Color(0xFFFCF8FF), Color(0xFFEDE7F6), Color(0xFFD9D2E3), Color(0xFF49454F))
private val DarkMini = Mini(Color(0xFF141218), Color(0xFF2B2930), Color(0xFF3F3A47), Color(0xFFCAC4D0))

/** A tiny home screen drawn with the given palette (width = 100u, height = 166u). */
private fun DrawScope.mini(c: Mini, primary: Color) {
    val u = size.width / 100f
    fun rr(x: Float, y: Float, w: Float, h: Float, r: Float, col: Color) = drawRoundRect(col, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(r * u))
    drawRect(c.bg)
    drawCircle(primary, 7 * u, Offset(15 * u, 16 * u))
    rr(27f, 10f, 34f, 4.5f, 2.2f, c.ink)
    rr(27f, 18f, 22f, 3.4f, 1.7f, c.bar)
    rr(8f, 30f, 84f, 44f, 9f, c.surf)
    drawCircle(primary, 9 * u, Offset(50 * u, 52 * u))
    drawPath(Path().apply { moveTo(47f * u, 47.5f * u); lineTo(47f * u, 56.5f * u); lineTo(54.5f * u, 52f * u); close() }, Color.White)
    rr(8f, 80f, 40f, 26f, 8f, c.surf); rr(52f, 80f, 40f, 26f, 8f, c.surf)
    rr(8f, 110f, 26f, 3.6f, 1.8f, c.ink); rr(8f, 116f, 18f, 3f, 1.5f, c.bar)
    rr(52f, 110f, 26f, 3.6f, 1.8f, c.ink); rr(52f, 116f, 18f, 3f, 1.5f, c.bar)
    rr(6f, 140f, 88f, 20f, 10f, c.surf)
    rr(11f, 144f, 18f, 12f, 6f, primary.copy(alpha = 0.35f))
    listOf(20f, 41f, 62f, 83f).forEachIndexed { i, x -> drawCircle(if (i == 0) primary else c.ink.copy(alpha = 0.5f), 2.6f * u, Offset(x * u, 150f * u)) }
}

@Composable
private fun PreviewCard(label: String, icon: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit, preview: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val sc by animateFloatAsState(if (selected) 1f else 0.93f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "sc")
    val r by animateDpAsState(if (selected) 30.dp else 20.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "r")
    val bw by animateDpAsState(if (selected) 3.dp else 1.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "bw")
    Column(
        modifier.graphicsLayer { scaleX = sc; scaleY = sc }.clip(RoundedCornerShape(r + 6.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(r)).border(bw, if (selected) cs.primary else cs.outlineVariant, RoundedCornerShape(r))) { preview() }
            AnimatedVisibility(selected, Modifier.align(Alignment.TopEnd).padding(8.dp), enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(), exit = scaleOut() + fadeOut()) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) { Icon("check", null, Modifier.size(16.dp), tint = cs.onPrimary) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = if (selected) cs.primary else cs.onSurfaceVariant)
            Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) cs.primary else cs.onSurface)
        }
    }
}

@Composable
fun ThemeStep(prefs: Prefs, store: Store) {
    val scope = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary
    StepTitle("Light or dark?", "Tap a preview. The whole app follows right away.")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Triple("System", "brightness_auto", 0), Triple("Light", "light_mode", 1), Triple("Dark", "dark_mode", 2)).forEach { (l, ic, v) ->
            PreviewCard(l, ic, prefs.theme == v, Modifier.weight(1f), { scope.launch { store.put(K.theme, v) } }) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(100f / 166f)) {
                    when (v) {
                        1 -> mini(LightMini, primary)
                        2 -> mini(DarkMini, primary)
                        else -> {
                            clipRect(0f, 0f, size.width / 2f, size.height) { mini(LightMini, primary) }
                            clipRect(size.width / 2f, 0f, size.width, size.height) { mini(DarkMini, primary) }
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    AnimatedContent(prefs.theme, label = "tdesc") { t ->
        Text(
            when (t) { 1 -> "Always light."; 2 -> "Always dark, easy on the eyes."; else -> "Follows your phone: light by day, dark by night." },
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun LayoutStep(prefs: Prefs, store: Store) {
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val bg = cs.surfaceContainerHigh; val thumb = cs.surfaceContainerHighest; val ink = cs.onSurfaceVariant; val pr = cs.primary
    StepTitle("How do you browse?", "Pick how your videos are laid out.")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(Triple("Grid", "grid_view", 0), Triple("List", "view_list", 1)).forEach { (l, ic, v) ->
            PreviewCard(l, ic, prefs.layout == v, Modifier.weight(1f), { scope.launch { store.put(K.layout, v) } }) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(0.8f).background(bg)) {
                    val u = size.width / 100f
                    fun rr(x: Float, y: Float, w: Float, h: Float, r: Float, col: Color) = drawRoundRect(col, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(r * u))
                    if (v == 0) {
                        for (row in 0..2) for (col in 0..1) {
                            val x = 8f + col * 44f; val y = 8f + row * 38f
                            rr(x, y, 40f, 23f, 5f, thumb)
                            drawCircle(pr, 4.5f * u, Offset((x + 20f) * u, (y + 11.5f) * u))
                            rr(x, y + 26f, 30f, 3.2f, 1.6f, ink); rr(x, y + 31f, 20f, 2.6f, 1.3f, ink.copy(alpha = 0.45f))
                        }
                    } else {
                        for (i in 0..3) {
                            val y = 8f + i * 29f
                            rr(8f, y, 38f, 22f, 5f, thumb)
                            drawCircle(pr, 4f * u, Offset(27f * u, (y + 11f) * u))
                            rr(51f, y + 3f, 38f, 3.6f, 1.8f, ink); rr(51f, y + 10f, 26f, 2.8f, 1.4f, ink.copy(alpha = 0.45f))
                            rr(51f, y + 16f, 14f, 2.8f, 1.4f, pr.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    AnimatedContent(prefs.layout, label = "ldesc") { t ->
        Text(if (t == 1) "Compact rows with the title and folder." else "Big thumbnails, two per row.", color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
