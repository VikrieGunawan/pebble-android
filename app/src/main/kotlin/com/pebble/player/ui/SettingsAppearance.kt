package com.pebble.player.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import com.pebble.player.data.Video
import android.net.Uri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalConfiguration
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import com.pebble.player.data.K
import com.pebble.player.data.Prefs
import com.pebble.player.data.Store
import kotlinx.coroutines.launch

/** Settings → Appearance: live preview, theme, colors and how the library looks. */
@Composable
fun AppearancePage(prefs: Prefs, store: Store, vids: List<Video> = emptyList()) {
    val scope = rememberCoroutineScope()
    fun <T> put(k: Preferences.Key<T>, v: T) { scope.launch { store.put(k, v) } }
    val dynOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useDyn = dynOk && prefs.dynamic
    val dark = when (prefs.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val themes = listOf("System", "Light", "Dark")
    val colorName = if (useDyn) "Material You" else ACCENTS.firstOrNull { it.second == prefs.accent }?.first ?: "Custom"

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LookPreview(prefs, vids)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlayStat("contrast", themes[prefs.theme.coerceIn(0, 2)], "Theme", Modifier.weight(1f))
            PlayStat("palette", colorName, "Colors", Modifier.weight(1f))
            PlayStat(if (prefs.layout == 0) "grid_view" else "view_list", if (prefs.layout == 0) "Grid" else "List", "Layout", Modifier.weight(1f))
        }

        SectionLabel("contrast", "Theme")
        TileChoiceCard(
            "contrast", "Mode", "Follow the phone, or always use light or dark.",
            listOf("contrast", "light_mode", "dark_mode"), themes, prefs.theme,
        ) { put(K.theme, it) }
        ToggleCard("dark_mode", "Pure black dark mode", "A true black background. Saves battery on OLED screens.", prefs.amoled) { put(K.amoled, it) }
        if (!dark && prefs.amoled) Text("Pure black shows up when dark mode is on.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))

        SectionLabel("palette", "Colors")
        if (dynOk) ToggleCard("palette", "Material You", "Use colors from your wallpaper.", prefs.dynamic) { put(K.dyn, it) }
        PAnim(!useDyn) { SwatchGrid(prefs.accent, false) { put(K.accent, it) } }

        SectionLabel("grid_view", "Library look")
        TileChoiceCard(
            "grid_view", "Layout", "Cards in a grid, or one video per row.",
            listOf("grid_view", "view_list"), listOf("Grid", "List"), prefs.layout,
        ) { put(K.layout, it) }
        PAnim(prefs.layout == 0 && isPhoneScreen()) {
            val can3 = maxGridCols() >= 3
            TileChoiceCard(
                "grid_view", "Columns in landscape", if (can3) "Cards per row when the phone is on its side. Portrait shows one." else "Your screen fits 2 per row when on its side. Portrait shows one.",
                listOf("cols_2", "cols_3"), listOf("2", "3"), if (prefs.gridSize == 0 && can3) 1 else 0, if (can3) emptySet() else setOf(1),
            ) { put(K.gridSize, if (it == 1) 0 else 1) }
        }
        PAnim(prefs.layout == 0 && !isPhoneScreen()) {
            TileChoiceCard(
                "zoom_in", "Card size", "How big the video cards are in the grid.",
                listOf("view_compact", "view_comfy", "zoom_in"), listOf("Compact", "Normal", "Large"), prefs.gridSize,
            ) { put(K.gridSize, it) }
        }
        ChoiceCard("sort", "Sort by", "The order of your videos.", listOf("Newest", "Name", "Longest", "Largest"), prefs.sort) { put(K.sort, it) }

        SectionLabel("restart_alt", "Reset")
        PlayCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Badge("restart_alt", true)
                Column(Modifier.weight(1f)) {
                    Text("Reset appearance", fontWeight = FontWeight.ExtraBold)
                    Text("Theme, colors and library look go back to the defaults.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ActionBtn("restart_alt", "Reset", true) {
                put(K.theme, 0); put(K.dyn, true); put(K.accent, 0xFFE8502B.toInt()); put(K.amoled, false)
                put(K.gridSize, 1); put(K.sort, 1)
            }
        }
    }
}

/** A miniature of the real app (real header, navigation and video cards), in the shape and orientation of this screen. */
@Composable
private fun LookPreview(prefs: Prefs, vids: List<Video>) {
    val cs = MaterialTheme.colorScheme
    val cfg = LocalConfiguration.current
    val sw = cfg.screenWidthDp
    val sh = cfg.screenHeightDp.coerceAtLeast(1)
    val ratio = sw.toFloat() / sh
    val wide = (sw >= 744 && sh >= 560) || sw > sh
    val sample = remember(vids) {
        if (vids.size >= 6) vids.take(6)
        else List(6) { i -> Video(-1L - i, Uri.EMPTY, "Sample video ${i + 1}", "Movies", "Movies/", 1_500_000L + i * 400_000L, 300_000_000L + i * 40_000_000L, 0L, "mp4") }
    }
    val shape = RoundedCornerShape(if (wide) 20.dp else 28.dp)
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val w = minOf(maxWidth, 280.dp * ratio)
        val s = w.value / sw
        Box(Modifier.width(w).aspectRatio(ratio).clip(shape).background(cs.background).border(1.5.dp, cs.outlineVariant, shape)) {
            Box(Modifier.requiredSize(sw.dp, sh.dp).graphicsLayer { scaleX = s; scaleY = s; transformOrigin = TransformOrigin(0f, 0f) }) {
                @Composable
                fun top(logo: Boolean) = PebbleTopBar(logo, "", {}, "Search your videos", false, {}, {}, prefs.profile, {}, prefs.theme)
                if (wide) Row(Modifier.fillMaxSize()) {
                    PebbleRail(0) {}
                    Column(Modifier.weight(1f)) { top(false); Box(Modifier.weight(1f)) { VideoCollection(sample, prefs, {}) } }
                } else Column(Modifier.fillMaxSize()) {
                    top(true)
                    Box(Modifier.weight(1f)) { VideoCollection(sample, prefs, {}) }
                    PebbleTabBar(0) {}
                }
            }
            Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { } }) // look only, no touches
        }
    }
}

/** Card with a title and a row of big icon tiles to pick from. */
@Composable
internal fun TileChoiceCard(icon: String, title: String, desc: String, icons: List<String>, labels: List<String>, selected: Int, disabled: Set<Int> = emptySet(), onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    PlayCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Badge(icon, true)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.ExtraBold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, l ->
                val on = i == selected
                val bg by animateColorAsState(if (on) cs.primary else cs.surfaceContainerHighest, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "tcBg")
                val fg by animateColorAsState(if (on) cs.onPrimary else cs.onSurfaceVariant, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "tcFg")
                Column(
                    Modifier.weight(1f).alpha(if (i in disabled) 0.35f else 1f).clip(RoundedCornerShape(20.dp)).background(bg).clickable(enabled = i !in disabled) { onSelect(i) }.padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(icons[i], null, Modifier.size(26.dp), tint = fg)
                    Text(l, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }
}

/** Full-width pill button in the Data-page style. */
@Composable
internal fun ActionBtn(icon: String, text: String, enabled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(46.dp).alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(16.dp)).background(cs.primaryContainer).clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = cs.onPrimaryContainer)
        Text(text, Modifier.padding(start = 8.dp), color = cs.onPrimaryContainer, fontWeight = FontWeight.ExtraBold)
    }
}
