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
import com.pebble.player.data.K
import com.pebble.player.data.Prefs
import com.pebble.player.data.Prog
import com.pebble.player.data.Store
import com.pebble.player.data.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun fmtLong(ms: Long): String { val m = ms / 60000; val h = m / 60; return if (h > 0) "${h}h ${m % 60}m" else "${m}m" }
private fun fmtSize(b: Long): String = if (b >= 1_073_741_824L) "%.1f GB".format(b / 1073741824.0) else "${b / 1048576} MB"

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
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(greeting(), color = cs.onSurfaceVariant)
                Text(prefs.profile.name.ifBlank { "Welcome back" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onRefresh) { Icon("sync", "Refresh") }
            Avatar(prefs.profile, 48.dp, Modifier.clickable(onClick = onProfile))
        }
        if (videos.isEmpty() || hero == null) {
            Column(Modifier.fillMaxWidth().padding(vertical = 80.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon("movie", null, Modifier.size(56.dp), tint = cs.onSurfaceVariant)
                Text("No videos found", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Add videos to your phone, then tap refresh.", color = cs.onSurfaceVariant)
            }
            return@Column
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatPill("video_library", "${videos.size} videos")
            StatPill("schedule", fmtLong(videos.sumOf { it.dur }))
            StatPill("favorite", "${favs.size} favorites")
            StatPill("folder", "${folders.size} folders")
        }
        Spacer(Modifier.height(16.dp))
        HeroCard(hero, prefs.progress[hero.uri.toString()], if (cont.isNotEmpty()) "Continue watching" else "Latest video") { play(hero, videos) }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button({ val l = videos.shuffled(); play(l.first(), l) }, Modifier.weight(1f).height(52.dp), shapes = ButtonDefaults.shapes()) {
                Icon("shuffle", null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Shuffle all")
            }
            FilledTonalButton({ openTab(1) }, Modifier.weight(1f).height(52.dp), shapes = ButtonDefaults.shapes()) {
                Icon("video_library", null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Library")
            }
        }
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
fun LibraryTab(prefs: Prefs, store: Store, videos: List<Video>, folder: String?, onFolder: (String?) -> Unit, play: (Video, List<Video>) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val folders = remember(videos) { videos.groupingBy { it.folder }.eachCount().entries.sortedBy { it.key.lowercase() } }
    val list = videos.filter { (folder == null || it.folder == folder) && (query.isBlank() || it.name.contains(query, true)) }
    VideoCollection(
        list, prefs, if (query.isBlank()) "No videos here" else "No results", if (query.isBlank()) "Check Settings → Library for hidden folders." else "Try another word.",
        { v -> play(v, list) },
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("${list.size} videos · ${fmtLong(list.sumOf { it.dur })} · ${fmtSize(list.sumOf { it.size })}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    IconButton({ scope.launch { store.put(K.layout, 1 - prefs.layout) } }) { Icon(if (prefs.layout == 1) "grid_view" else "view_list", "Layout") }
                    FilledTonalButton({ if (list.isNotEmpty()) { val l = list.shuffled(); play(l.first(), l) } }, enabled = list.isNotEmpty(), shapes = ButtonDefaults.shapes()) {
                        Icon("shuffle", null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Shuffle")
                    }
                }
                OutlinedTextField(
                    query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(28.dp),
                    placeholder = { Text("Search videos") },
                    leadingIcon = { Icon("search", null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon("close", "Clear") } },
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon("sort", null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
                    listOf("Newest", "Name", "Longest", "Largest").forEachIndexed { i, s -> FilterChip(prefs.sort == i, { scope.launch { store.put(K.sort, i) } }, { Text(s) }) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon("folder", null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
                    FilterChip(folder == null, { onFolder(null) }, { Text("All (${videos.size})") })
                    folders.forEach { e -> FilterChip(folder == e.key, { onFolder(e.key) }, { Text("${e.key} (${e.value})") }) }
                }
            }
        },
    )
}

// ---------------------------------------------------------------- Favorites

@Composable
fun FavoritesTab(prefs: Prefs, store: Store, favs: List<Video>, play: (Video, List<Video>) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    VideoCollection(
        favs, prefs, "No favorites yet", "Tap the heart on any video to keep it here.", { v -> play(v, favs) },
        header = {
            Column(
                Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(32.dp))
                    .background(Brush.linearGradient(listOf(cs.primary, cs.tertiary))).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) { Icon("favorite", null, Modifier.size(28.dp), tint = Color.White) }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text("Favorites", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("${favs.size} videos · ${fmtLong(favs.sumOf { it.dur })}", color = Color.White.copy(alpha = 0.85f))
                    }
                    IconButton({ scope.launch { store.put(K.layout, 1 - prefs.layout) } }) { Icon(if (prefs.layout == 1) "grid_view" else "view_list", "Layout", tint = Color.White) }
                    if (favs.isNotEmpty()) IconButton({ confirm = true }) { Icon("delete_sweep", "Clear all", tint = Color.White) }
                }
                if (favs.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button({ play(favs.first(), favs) }, Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = cs.primary), shapes = ButtonDefaults.shapes()) {
                        Icon("play_arrow", null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("Play all")
                    }
                    FilledTonalButton({ val l = favs.shuffled(); play(l.first(), l) }, Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.22f), contentColor = Color.White), shapes = ButtonDefaults.shapes()) {
                        Icon("shuffle", null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("Shuffle")
                    }
                }
            }
        },
    )
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, title = { Text("Clear all favorites?") }, text = { Text("Your videos stay on the phone. Only the hearts are removed.") },
        confirmButton = { TextButton({ confirm = false; scope.launch { store.clearFavs() } }) { Text("Clear") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
    )
}
