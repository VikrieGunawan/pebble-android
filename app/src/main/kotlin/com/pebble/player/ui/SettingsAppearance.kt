package com.pebble.player.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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

/** Settings → Appearance: theme, colors and how the library looks. */
@Composable
fun AppearancePage(prefs: Prefs, store: Store) {
    val scope = rememberCoroutineScope()
    fun <T> put(k: Preferences.Key<T>, v: T) { scope.launch { store.put(k, v) } }
    val dynOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useDyn = dynOk && prefs.dynamic
    val dark = when (prefs.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val themes = listOf(t("System"), t("Light"), t("Dark"))
    val colorName = if (useDyn) "Material You" else ACCENTS.firstOrNull { it.second == prefs.accent }?.first ?: t("Custom")

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlayStat("contrast", themes[prefs.theme.coerceIn(0, 2)], t("Theme"), Modifier.weight(1f))
            PlayStat("palette", colorName, t("Colors"), Modifier.weight(1f))
            PlayStat(if (prefs.layout == 0) "grid_view" else "view_list", if (prefs.layout == 0) t("Grid") else t("List"), t("Layout"), Modifier.weight(1f))
        }

        SectionLabel("contrast", t("Theme"))
        TileChoiceCard(
            "contrast", t("Mode"), t("Follow the phone, or always use light or dark."),
            listOf("contrast", "light_mode", "dark_mode"), themes, prefs.theme,
        ) { put(K.theme, it) }
        ToggleCard("dark_mode", t("Pure black dark mode"), t("A true black background. Saves battery on OLED screens."), prefs.amoled) { put(K.amoled, it) }
        if (!dark && prefs.amoled) Text(t("Pure black shows up when dark mode is on."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))

        SectionLabel("text_fields", t("Language"))
        LanguageCard(prefs.lang) { put(K.lang, it) }

        SectionLabel("palette", t("Colors"))
        if (dynOk) ToggleCard("palette", "Material You", t("Use colors from your wallpaper."), prefs.dynamic) { put(K.dyn, it) }
        PAnim(!useDyn) { SwatchGrid(prefs.accent, false) { put(K.accent, it) } }

        SectionLabel("grid_view", t("Library look"))
        LayoutPreview(prefs)
        TileChoiceCard(
            "grid_view", t("Layout"), t("Cards in a grid, or one video per row."),
            listOf("grid_view", "view_list"), listOf(t("Grid"), t("List")), prefs.layout,
        ) { put(K.layout, it) }
        PAnim(prefs.layout == 0 && isPhoneScreen()) {
            val can3 = maxGridCols() >= 3
            TileChoiceCard(
                "grid_view", t("Columns in landscape"), if (can3) t("Cards per row when the phone is on its side. Portrait shows one.") else t("Your screen fits 2 per row when on its side. Portrait shows one."),
                listOf("cols_2", "cols_3"), listOf("2", "3"), if (prefs.gridSize == 0 && can3) 1 else 0, if (can3) emptySet() else setOf(1),
            ) { put(K.gridSize, if (it == 1) 0 else 1) }
        }
        PAnim(prefs.layout == 0 && !isPhoneScreen()) {
            TileChoiceCard(
                "zoom_in", t("Card size"), t("How big the video cards are in the grid."),
                listOf("view_compact", "view_comfy", "zoom_in"), listOf(t("Compact"), t("Normal"), t("Large")), prefs.gridSize,
            ) { put(K.gridSize, it) }
        }
        ChoiceCard("sort", t("Sort by"), t("The order of your videos."), listOf(t("Newest"), t("Name"), t("Longest"), t("Largest")), prefs.sort) { put(K.sort, it) }

        SectionLabel("restart_alt", t("Reset"))
        PlayCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Badge("restart_alt", true)
                Column(Modifier.weight(1f)) {
                    Text(t("Reset appearance"), fontWeight = FontWeight.ExtraBold)
                    Text(t("Theme, colors and library look go back to the defaults."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ActionBtn("restart_alt", t("Reset"), true) {
                put(K.theme, 0); put(K.dyn, true); put(K.accent, 0xFFE8502B.toInt()); put(K.amoled, false)
                put(K.gridSize, 1); put(K.sort, 1)
            }
        }
    }
}

/** Fixed-size sketch of the chosen layout: list = 2 rows, grid = 2 columns (4 tiles) or 3 columns (6 tiles). Same size in portrait and landscape. */
@Composable
private fun LayoutPreview(prefs: Prefs) {
    val cs = MaterialTheme.colorScheme
    val cols = if (prefs.layout == 1) 1 else if (prefs.gridSize == 0 && (!isPhoneScreen() || maxGridCols() >= 3)) 3 else 2
    val gap = 6.dp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Column(
            Modifier.width(240.dp).height(132.dp).clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainer).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            repeat(2) {
                if (cols == 1) Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.fillMaxHeight().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(cs.primaryContainer))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.fillMaxWidth(0.8f).height(7.dp).clip(CircleShape).background(cs.onSurfaceVariant.copy(alpha = 0.55f)))
                        Box(Modifier.fillMaxWidth(0.5f).height(6.dp).clip(CircleShape).background(cs.onSurfaceVariant.copy(alpha = 0.3f)))
                    }
                } else Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(cols) {
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp)).background(cs.primaryContainer))
                            Box(Modifier.fillMaxWidth(0.7f).height(5.dp).clip(CircleShape).background(cs.onSurfaceVariant.copy(alpha = 0.45f)))
                        }
                    }
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

@Composable
private fun LanguageCard(cur: String, onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val i = Lang.order.indexOf(cur)
    val name = if (i >= 0) Lang.names[i] else t("System default")
    PlayCard(Modifier.clickable { open = true }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Badge("text_fields", true)
            Column(Modifier.weight(1f)) {
                Text(t("Language"), fontWeight = FontWeight.ExtraBold)
                Text(t("Choose the app language."), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Text(name, color = cs.primary, fontWeight = FontWeight.Bold)
        }
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(t("Language")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val rows = listOf("" to t("System default")) + Lang.order.zip(Lang.names)
                rows.forEach { (code, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(code); open = false }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = cur == code, onClick = { onPick(code); open = false })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text(t("Close")) } },
    )
}
