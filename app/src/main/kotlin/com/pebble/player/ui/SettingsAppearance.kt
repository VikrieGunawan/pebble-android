package com.pebble.player.ui

import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
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
fun AppearancePage(prefs: Prefs, store: Store) {
    val scope = rememberCoroutineScope()
    fun <T> put(k: Preferences.Key<T>, v: T) { scope.launch { store.put(k, v) } }
    val dynOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useDyn = dynOk && prefs.dynamic
    val dark = when (prefs.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val themes = listOf("System", "Light", "Dark")
    val colorName = if (useDyn) "Material You" else ACCENTS.firstOrNull { it.second == prefs.accent }?.first ?: "Custom"

    Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LookPreview(prefs)

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
                "view_comfy", "Columns in landscape", if (can3) "Cards per row when the phone is on its side. Portrait shows one." else "Your screen fits 2 per row when on its side. Portrait shows one.",
                listOf("view_comfy", "view_compact"), listOf("2", "3"), if (prefs.gridSize == 0 && can3) 1 else 0, if (can3) emptySet() else setOf(1),
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

/** A tiny copy of the app drawn with the live colors, in the shape and orientation of this phone or tablet. */
@Composable
private fun LookPreview(prefs: Prefs) {
    val cs = MaterialTheme.colorScheme
    val cfg = LocalConfiguration.current
    val ratio = (cfg.screenWidthDp.toFloat() / cfg.screenHeightDp.coerceAtLeast(1)).coerceIn(0.4f, 2.6f)
    val land = ratio > 1f
    val phone = isPhoneScreen()
    val cols = if (phone) phoneCols(prefs) else (((cfg.screenWidthDp - 32) / (listOf(136, 168, 220)[prefs.gridSize.coerceIn(0, 2)] + 12)).coerceIn(1, 6))
    val shape = RoundedCornerShape(if (land) 18.dp else 22.dp)
    val thumb = Brush.linearGradient(listOf(cs.primaryContainer, cs.tertiaryContainer))
    val bar = cs.onSurfaceVariant.copy(alpha = 0.35f)
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val w = minOf(maxWidth, 270.dp * ratio)
        Row(
            Modifier.width(w).aspectRatio(ratio).clip(shape).background(cs.background).border(1.5.dp, cs.outlineVariant, shape).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (land) Column(Modifier.fillMaxHeight().width(20.dp).clip(RoundedCornerShape(10.dp)).background(cs.surfaceContainer).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                repeat(4) { i -> Box(Modifier.size(9.dp).clip(CircleShape).background(if (i == 0) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.5f))) }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(cs.primary))
                    Box(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(cs.surfaceContainer))
                }
                Column(Modifier.weight(1f).clipToBounds(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (prefs.layout == 0) repeat(3) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            repeat(cols) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(6.dp)).background(thumb))
                                    Box(Modifier.fillMaxWidth(0.8f).height(3.dp).clip(CircleShape).background(bar))
                                }
                            }
                        }
                    } else repeat(4) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.width(if (land) 44.dp else 34.dp).aspectRatio(16f / 10f).clip(RoundedCornerShape(5.dp)).background(thumb))
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Box(Modifier.width(if (land) 80.dp else 44.dp).height(4.dp).clip(CircleShape).background(bar))
                                Box(Modifier.width(if (land) 46.dp else 26.dp).height(3.dp).clip(CircleShape).background(bar.copy(alpha = 0.2f)))
                            }
                        }
                    }
                }
                if (!land) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainer).padding(4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    repeat(4) { i -> Box(Modifier.size(7.dp).clip(CircleShape).background(if (i == 0) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.5f))) }
                }
            }
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
