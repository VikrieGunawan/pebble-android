package com.pebble.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import com.pebble.player.data.Profile
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
import androidx.compose.runtime.setValue
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
fun LogoMark(size: Dp) = StaticMark(size)

/** The website's `.nv`: an icon in a soft pill, with a bold label under it. */
@Composable
private fun NavButton(icon: String, label: String, selected: Boolean, pillW: Dp, pillH: Dp, labelSp: Float, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.92f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "navsc")
    val tint by animateColorAsState(if (selected) cs.primary else cs.onSurfaceVariant, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "navtint")
    val txt by animateColorAsState(if (selected) cs.onSurface else cs.onSurfaceVariant, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "navtxt")
    val bg by animateColorAsState(if (selected) cs.primaryContainer else Color.Transparent, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "navbg")
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
    val short = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 500
    // the rail background runs under the camera notch; only its content stays clear of it
    val dir = androidx.compose.ui.platform.LocalLayoutDirection.current
    val insetPad = WindowInsets.displayCutout.only(WindowInsetsSides.Start).asPaddingValues()
    val startInset = maxOf(insetPad.calculateLeftPadding(dir), insetPad.calculateRightPadding(dir))
    val line = cs.outlineVariant
    Column(
        Modifier.width(92.dp + startInset).fillMaxHeight().background(cs.surfaceContainer).windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start))
            .drawBehind { drawLine(line, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx()) }
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 8.dp, vertical = if (short) 6.dp else 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (short) 2.dp else 10.dp),
    ) {
        if (!short) {
            LogoMark(46.dp)
            Text("Pebble", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
        }
        NAV.forEach { n -> NavButton(n.icon, n.label, tab == n.id, 58.dp, 34.dp, 11f, Modifier.fillMaxWidth()) { onTab(n.id) } }
        Spacer(Modifier.weight(1f))
        NavButton("settings", "Settings", tab == 4, 58.dp, 34.dp, 11f, Modifier.fillMaxWidth()) { onTab(4) }
    }
}

/** Header like the website's `.top`: logo, theme + settings pill, and the search pill. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PebbleTopBar(
    showLogo: Boolean, query: String, onQuery: (String) -> Unit, placeholder: String,
    settingsOn: Boolean, onSettings: () -> Unit, dark: Boolean, onToggleTheme: () -> Unit,
    profile: Profile, onProfile: () -> Unit,
    canRefresh: Boolean = false, refreshing: Boolean = false, onRefresh: () -> Unit = {},
) {
    val focus = LocalFocusManager.current
    val imeOn = WindowInsets.isImeVisible
    var wasIme by remember { mutableStateOf(false) }
    // leaving the keyboard (back / swipe down) also leaves the search field, so its highlight turns off
    LaunchedEffect(imeOn) { if (wasIme && !imeOn) focus.clearFocus(); wasIme = imeOn }
    val cs = MaterialTheme.colorScheme
    val short = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 500
    @Composable
    fun roundBtn(icon: String, on: Boolean, label: String, onClick: () -> Unit) {
        val tint by animateColorAsState(if (on) cs.primary else cs.onSurfaceVariant, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "tb")
        val bg by animateColorAsState(if (on) cs.primaryContainer else Color.Transparent, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "tbbg")
        Box(Modifier.size(42.dp).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(22.dp), tint = tint) }
    }
    var focused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val border by animateColorAsState(if (focused) cs.primary else cs.outlineVariant, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "sb")
    @Composable
    fun searchField(modifier: Modifier) {
        Row(
            modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer).border(1.5.dp, border, CircleShape).padding(start = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) { Icon("search", null, Modifier.size(18.dp), tint = cs.primary) }
            Box(Modifier.weight(1f).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text(placeholder, color = cs.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                androidx.compose.foundation.text.BasicTextField(
                    query, onQuery, Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface, fontWeight = FontWeight.SemiBold),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(cs.primary),
                )
            }
            if (query.isNotEmpty()) Box(Modifier.size(36.dp).clip(CircleShape).clickable { onQuery("") }, contentAlignment = Alignment.Center) { Icon("close", "Clear", Modifier.size(20.dp), tint = cs.onSurfaceVariant) }
        }
    }
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = if (short) 6.dp else 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (short) {
            // landscape phones: one slim row (search + buttons) so the page keeps its height
            Row(verticalAlignment = Alignment.CenterVertically) {
                searchField(Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Row(Modifier.clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (canRefresh) roundBtn("refresh", refreshing, "Refresh", onRefresh)
                    roundBtn(if (dark) "light_mode" else "dark_mode", false, "Theme", onToggleTheme)
                    roundBtn("settings", settingsOn, "Settings", onSettings)
                }
                Spacer(Modifier.width(10.dp))
                Avatar(profile, 42.dp, Modifier.clickable(onClick = onProfile))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showLogo) {
                    LogoMark(38.dp); Spacer(Modifier.width(10.dp))
                    Text("Pebble", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (canRefresh) roundBtn("refresh", refreshing, "Refresh", onRefresh)
                    roundBtn(if (dark) "light_mode" else "dark_mode", false, "Theme", onToggleTheme)
                    roundBtn("settings", settingsOn, "Settings", onSettings)
                }
                Spacer(Modifier.width(10.dp))
                Avatar(profile, 42.dp, Modifier.clickable(onClick = onProfile))
            }
            searchField(Modifier.fillMaxWidth())
        }
    }
}
