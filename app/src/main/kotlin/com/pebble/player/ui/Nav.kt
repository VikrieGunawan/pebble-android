package com.pebble.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Same navigation as the website: Home, Library, Favorites, History. */
private data class NavItem(val id: Int, val label: String, val icon: String)
private val NAV = listOf(NavItem(0, "Home", "home"), NavItem(1, "Library", "video_library"), NavItem(2, "Favorites", "favorite"), NavItem(3, "History", "history"))

@Composable
fun LogoMark(size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.34f)).background(Brush.linearGradient(listOf(Color(0xFFFF8A5C), Color(0xFF7B61FF)))),
        contentAlignment = Alignment.Center,
    ) { Icon("play_arrow", null, Modifier.size(size * 0.58f), tint = Color.White) }
}

/** The website's `.nv`: an icon in a soft pill, with a bold label under it. */
@Composable
private fun NavButton(icon: String, label: String, selected: Boolean, pillW: Dp, pillH: Dp, labelSp: Float, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.92f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "navsc")
    val bg by animateColorAsState(if (selected) cs.primaryContainer else Color.Transparent, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "navbg")
    val tint by animateColorAsState(if (selected) cs.primary else cs.onSurfaceVariant, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "navtint")
    val txt by animateColorAsState(if (selected) cs.onSurface else cs.onSurfaceVariant, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "navtxt")
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).clickable(interactionSource = src, indication = null, onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.graphicsLayer { scaleX = sc; scaleY = sc }.size(pillW, pillH).clip(RoundedCornerShape(pillH / 2)).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(24.dp), tint = tint)
        }
        Text(label, fontSize = labelSp.sp, fontWeight = FontWeight.ExtraBold, color = txt, maxLines = 1)
    }
}

@Composable
fun PebbleTabBar(tab: Int, onTab: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val line = cs.outlineVariant
    Row(
        Modifier.fillMaxWidth().background(cs.surfaceContainer).drawBehind { drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) { NAV.forEach { n -> NavButton(n.icon, n.label, tab == n.id, 58.dp, 34.dp, 11f, Modifier.weight(1f)) { onTab(n.id) } } }
}

@Composable
fun PebbleRail(tab: Int, onTab: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val line = cs.outlineVariant
    Column(
        Modifier.width(92.dp).fillMaxHeight().background(cs.surfaceContainer)
            .drawBehind { drawLine(line, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx()) }
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LogoMark(46.dp)
        Text("Pebble", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        NAV.forEach { n -> NavButton(n.icon, n.label, tab == n.id, 58.dp, 34.dp, 11f, Modifier.fillMaxWidth()) { onTab(n.id) } }
        Spacer(Modifier.weight(1f))
        NavButton("settings", "Settings", tab == 4, 58.dp, 34.dp, 11f, Modifier.fillMaxWidth()) { onTab(4) }
    }
}

/** Phone header like the website's `.top`: logo on the left, settings button on the right. */
@Composable
fun PebbleTopBar(settingsOn: Boolean, onSettings: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tint by animateColorAsState(if (settingsOn) cs.primary else cs.onSurfaceVariant, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "gear")
    val bg by animateColorAsState(if (settingsOn) cs.primaryContainer else Color.Transparent, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "gearbg")
    Row(Modifier.fillMaxWidth().background(cs.background).statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        LogoMark(38.dp)
        Spacer(Modifier.width(10.dp))
        Text("Pebble", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Box(Modifier.size(42.dp).clip(CircleShape).background(bg).clickable(onClick = onSettings), contentAlignment = Alignment.Center) {
            Icon("settings", "Settings", Modifier.size(24.dp), tint = tint)
        }
    }
}
