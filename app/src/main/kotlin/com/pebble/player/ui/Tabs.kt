package com.pebble.player.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pebble.player.data.K
import com.pebble.player.data.Prefs
import com.pebble.player.data.Prog
import com.pebble.player.data.Store
import com.pebble.player.data.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun fmtLong(ms: Long): String { val m = ms / 60000; val h = m / 60; return if (h > 0) "${h}h ${m % 60}m" else "${m}m" }

@Composable
fun rememberThumb(v: Video): ImageBitmap? {
    val app = LocalApp.current
    val bmp by produceState(ThumbCache.get(v.id), v.id) {
        if (value == null) withContext(Dispatchers.IO) { app.media.thumb(v)?.asImageBitmap() }?.let { ThumbCache.put(v.id, it); value = it }
    }
    return bmp
}

@Composable
private fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onAction) { Text(action) }
    }
}

@Composable
private fun StatPill(icon: String, text: String) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.clip(RoundedCornerShape(50)).background(cs.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = cs.primary)
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HeroCard(v: Video, prog: Prog?, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bmp = rememberThumb(v)
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(32.dp)).background(cs.surfaceContainerHigh).clickable(onClick = onClick)) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 18.dp, end = 86.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
            Text(v.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (prog != null && prog.dur > 0) {
                Spacer(Modifier.height(8.dp))
                Text("${fmt(prog.pos)} / ${fmt(prog.dur)}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                LinearProgressIndicator(progress = { (prog.pos.toFloat() / prog.dur).coerceIn(0f, 1f) }, modifier = Modifier.padding(top = 4.dp).fillMaxWidth().height(4.dp), color = Color.White, trackColor = Color(0x55FFFFFF), drawStopIndicator = {})
            }
        }
        FilledIconButton(onClick, Modifier.align(Alignment.BottomEnd).padding(16.dp).size(56.dp)) { Icon("play_arrow", "Play", Modifier.size(32.dp)) }
    }
}

@Composable
private fun FolderCard(name: String, count: Int, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.width(150.dp).clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh).clickable(onClick = onClick).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) { Icon("folder", null, Modifier.size(22.dp), tint = cs.onPrimaryContainer) }
        Column {
            Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count videos", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun VideoRow(list: List<Video>, prefs: Prefs, play: (Video) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(list, key = { it.id }) { v -> VideoCard(v, prefs, false, Modifier.width(210.dp)) { play(v) } }
    }
}

// ---------------------------------------------------------------- Home

@Composable
private fun ActionTile(icon: String, title: String, sub: String, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.clip(RoundedCornerShape(26.dp)).background(cs.surfaceContainer).clickable(onClick = onClick).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        IconTile(icon, 46.dp, filled)
        Column { Text(title, fontWeight = FontWeight.ExtraBold); Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
    }
}

@Composable
fun HomeTab(
    prefs: Prefs, videos: List<Video>, onProfile: () -> Unit, onRefresh: () -> Unit,
    play: (Video, List<Video>) -> Unit, openTab: (Int) -> Unit, openFolder: (String?) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val cont = videos.filter { v -> prefs.progress[v.uri.toString()]?.let { it.dur > 0 && it.pos in 3000 until it.dur - 5000 } == true }
        .sortedByDescending { prefs.progress[it.uri.toString()]?.ts ?: 0L }
    val recent = videos.sortedByDescending { it.added }.take(12)
    val favs = videos.filter { it.uri.toString() in prefs.favs }
    val folders = videos.groupingBy { it.folder }.eachCount().entries.sortedByDescending { it.value }.take(10)
    val hero = cont.firstOrNull() ?: recent.firstOrNull()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text((greeting() + if (prefs.profile.name.isNotBlank()) ", ${prefs.profile.name}" else "").uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = cs.primary, letterSpacing = 1.5.sp)
                Text("What are we watching today?", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Avatar(prefs.profile, 48.dp, Modifier.clickable(onClick = onProfile))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.clip(RoundedCornerShape(50)).background(cs.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon("lock", null, Modifier.size(16.dp), tint = cs.onSurfaceVariant)
            Text("Plays on your device only", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile("shuffle", "Shuffle all", "Random mix", true, Modifier.weight(1f).fillMaxHeight()) { if (videos.isNotEmpty()) { val l = videos.shuffled(); play(l.first(), l) } }
            ActionTile("sync", "Scan again", "Find new videos", false, Modifier.weight(1f).fillMaxHeight(), onRefresh)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile("favorite", "Favorites", "${favs.size} pinned", false, Modifier.weight(1f)) { openTab(2) }
            ActionTile("history", "History", "Pick up again", false, Modifier.weight(1f)) { openTab(3) }
        }
        if (videos.isEmpty() || hero == null) {
            EmptyCard("movie", "No videos found", "Add videos to your phone, then tap Scan again.")
            return@Column
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatPill("video_library", "${videos.size} videos")
            StatPill("schedule", fmtLong(videos.sumOf { it.dur }))
            StatPill("folder", "${folders.size} folders")
        }
        Spacer(Modifier.height(16.dp))
        HeroCard(hero, prefs.progress[hero.uri.toString()], if (cont.isNotEmpty()) "Continue watching" else "Latest video") { play(hero, videos) }
        if (cont.size > 1) { SectionHeader("Continue watching"); VideoRow(cont.drop(1), prefs) { play(it, cont) } }
        SectionHeader("Recently added", "See all") { openFolder(null); openTab(1) }
        VideoRow(recent, prefs) { play(it, recent) }
        if (folders.size > 1) {
            SectionHeader("Folders")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(folders, key = { it.key }) { e -> FolderCard(e.key, e.value) { openFolder(e.key); openTab(1) } }
            }
        }
        if (favs.isNotEmpty()) { SectionHeader("Your favorites", "See all") { openTab(2) }; VideoRow(favs.take(12), prefs) { play(it, favs) } }
        Spacer(Modifier.height(28.dp))
    }
}

// ---------------------------------------------------------------- Library

@Composable
fun LibraryTab(prefs: Prefs, store: Store, videos: List<Video>, folder: String?, onFolder: (String?) -> Unit, query: String, play: (Video, List<Video>) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var sortMenu by remember { mutableStateOf(false) }
    val sorts = listOf("Recently added", "Name", "Longest", "Largest")
    val folders = remember(videos) { videos.groupingBy { it.folder }.eachCount().entries.sortedBy { it.key.lowercase() } }
    val list = videos.filter { (folder == null || it.folder == folder) && (query.isBlank() || it.name.contains(query, true)) }
    VideoCollection(
        list, prefs, { v -> play(v, list) },
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PageHeader("video_library", "Library", "${list.size} videos · ${fmtLong(list.sumOf { it.dur })} · ${fmtSize(list.sumOf { it.size })}")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(cs.surfaceContainer).clickable { sortMenu = true }.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon("sort", null, Modifier.size(22.dp), tint = cs.onSurfaceVariant)
                            Column(Modifier.weight(1f)) {
                                Text("SORT BY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = cs.onSurfaceVariant, letterSpacing = 1.sp)
                                Text(sorts[prefs.sort.coerceIn(0, 3)], fontWeight = FontWeight.Bold)
                            }
                            Icon("expand_more", null, Modifier.size(22.dp), tint = cs.onSurfaceVariant)
                        }
                        androidx.compose.material3.DropdownMenu(sortMenu, { sortMenu = false }) {
                            sorts.forEachIndexed { i, s ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(s, fontWeight = FontWeight.SemiBold) }, onClick = { sortMenu = false; scope.launch { store.put(K.sort, i) } },
                                    trailingIcon = { if (prefs.sort == i) Icon("check", null, Modifier.size(20.dp), tint = cs.primary) },
                                )
                            }
                        }
                    }
                    LayoutToggle(prefs.layout) { scope.launch { store.put(K.layout, it) } }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(folder == null, { onFolder(null) }, { Text("All (${videos.size})") }, leadingIcon = { Icon("folder", null, Modifier.size(18.dp)) })
                    folders.forEach { e -> FilterChip(folder == e.key, { onFolder(e.key) }, { Text("${e.key} (${e.value})") }) }
                }
                SectionLabel("movie", "Videos", list.size)
            }
        },
        empty = { EmptyCard("video_library", if (query.isBlank()) "No videos here" else "No results", if (query.isBlank()) "Check Settings → Library for hidden folders." else "Try another word.") },
    )
}

// ---------------------------------------------------------------- Favorites

@Composable
fun FavoritesTab(prefs: Prefs, store: Store, favs: List<Video>, play: (Video, List<Video>) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    VideoCollection(
        favs, prefs, { v -> play(v, favs) },
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PageHeader("favorite", "Favorites", "${favs.size} pinned videos") {
                    if (favs.isNotEmpty()) Box(Modifier.size(42.dp).clip(CircleShape).clickable { confirm = true }, contentAlignment = Alignment.Center) { Icon("delete_sweep", "Clear all", Modifier.size(24.dp), tint = cs.onSurfaceVariant) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LayoutToggle(prefs.layout) { scope.launch { store.put(K.layout, it) } }
                    Spacer(Modifier.weight(1f))
                    if (favs.isNotEmpty()) {
                        FilledTonalButton({ val l = favs.shuffled(); play(l.first(), l) }, shapes = ButtonDefaults.shapes()) { Icon("shuffle", null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Shuffle") }
                        Button({ play(favs.first(), favs) }, shapes = ButtonDefaults.shapes()) { Icon("play_arrow", null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Play all") }
                    }
                }
            }
        },
        empty = { EmptyCard("favorite", "No favorites yet", "Open a video menu and tap Favorite to pin it here.") },
    )
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, title = { Text("Clear all favorites?") }, text = { Text("Your videos stay on the phone. Only the pins are removed.") },
        confirmButton = { TextButton({ confirm = false; scope.launch { store.clearFavs() } }) { Text("Clear") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------- History

@Composable
fun HistoryTab(prefs: Prefs, store: Store, list: List<Video>, play: (Video, List<Video>) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    VideoCollection(
        list, prefs, { v -> play(v, list) },
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PageHeader("history", "History", "Videos you started watching") {
                    if (list.isNotEmpty()) Box(Modifier.size(42.dp).clip(CircleShape).clickable { confirm = true }, contentAlignment = Alignment.Center) { Icon("delete_sweep", "Clear history", Modifier.size(24.dp), tint = cs.onSurfaceVariant) }
                }
                LayoutToggle(prefs.layout) { scope.launch { store.put(K.layout, it) } }
            }
        },
        empty = { EmptyCard("history", "Nothing here yet", "Start a video and it will appear in your history.") },
    )
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, title = { Text("Clear history?") }, text = { Text("Resume positions will be removed.") },
        confirmButton = { TextButton({ confirm = false; scope.launch { store.clearHistory() } }) { Text("Clear") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
    )
}
