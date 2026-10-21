package com.pebble.player.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pebble.player.PebbleApp
import com.pebble.player.data.Prefs
import com.pebble.player.data.Profile
import com.pebble.player.data.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PebbleRoot(app: PebbleApp, openUri: Uri?, onOpenConsumed: () -> Unit) {
    val prefs by app.store.prefs.collectAsStateWithLifecycle(Prefs())
    var clearing by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalApp provides app) {
        PebbleTheme(prefs.theme, prefs.dynamic, Color(prefs.accent)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Crossfade(
                    targetState = when {
                        !prefs.loaded -> 0
                        clearing -> 1
                        !prefs.onboarded -> 2
                        else -> 3
                    },
                    label = "root",
                ) { s ->
                    when (s) {
                        0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) }
                        1 -> ClearDataScreen(app) { clearing = false }
                        2 -> Onboarding(prefs, app.store)
                        else -> MainFlow(app, prefs, openUri, onOpenConsumed) { clearing = true }
                    }
                }
            }
        }
    }
}

private fun needPerm() =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE

private val TABS = listOf("Home", "Library", "Favorites", "History", "Settings")

@Composable
private fun MainFlow(app: PebbleApp, prefs: Prefs, openUri: Uri?, onOpenConsumed: () -> Unit, startClear: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf<Video?>(null) }
    var queue by remember { mutableStateOf<List<Video>>(emptyList()) }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, needPerm()) == PackageManager.PERMISSION_GRANTED) }
    var all by remember { mutableStateOf<List<Video>>(emptyList()) }
    var scanned by remember { mutableStateOf(false) }
    var rescan by remember { mutableIntStateOf(0) }
    var profileDialog by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    LaunchedEffect(granted, rescan) {
        if (granted) { all = app.media.scan(); scanned = true }
    }
    LaunchedEffect(openUri) {
        if (openUri != null) { playing = app.media.fromUri(openUri); queue = emptyList(); onOpenConsumed() }
    }

    val visible = remember(all, prefs.blacklist, prefs.sort) {
        val l = all.filter { it.path !in prefs.blacklist }
        when (prefs.sort) { 1 -> l.sortedBy { it.name.lowercase() }; 2 -> l.sortedByDescending { it.dur }; else -> l }
    }
    fun play(v: Video, list: List<Video>) { playing = v; queue = list }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    val icons = listOf("home", "video_library", "favorite", "history", "settings")
                    TABS.forEachIndexed { i, t ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icons[i], t) }, label = { Text(t) })
                    }
                }
            },
        ) { pad ->
            val inSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            val outSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(inSpec) togetherWith fadeOut(outSpec) },
                modifier = Modifier.padding(pad).fillMaxSize(),
                label = "tab",
            ) { t ->
                if (t == 4) {
                    SettingsScreen(prefs, app.store, all, { rescan++ }, startClear)
                } else if (!granted) {
                    PermissionPrompt { ask.launch(needPerm()) }
                } else if (!scanned) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(56.dp)) }
                } else when (t) {
                    0 -> HomeTab(prefs, visible, { profileDialog = true }, { rescan++ }, ::play)
                    1 -> LibraryTab(prefs, visible, ::play)
                    2 -> {
                        val l = visible.filter { it.uri.toString() in prefs.favs }
                        VideoCollection(l, prefs, "No favorites yet", "Tap the heart on a video to keep it here.", { v -> play(v, l) })
                    }
                    else -> {
                        val l = visible.filter { it.uri.toString() in prefs.progress }.sortedByDescending { prefs.progress[it.uri.toString()]?.ts ?: 0L }
                        VideoCollection(l, prefs, "Nothing watched yet", "Videos you play will show up here.", { v -> play(v, l) })
                    }
                }
            }
        }
        playing?.let { v ->
            BackHandler { playing = null }
            PlayerScreen(v, queue, prefs, app.store, { playing = null }, { playing = it })
        }
    }

    if (profileDialog) ProfileDialog(prefs.profile, { profileDialog = false }) { p -> scope.launch { app.store.saveProfile(p) }; profileDialog = false }
}

@Composable
private fun PermissionPrompt(onAsk: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        AppMark(96)
        Spacer(Modifier.height(24.dp))
        Text("Let Pebble find your videos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Pebble only reads video files on this device. Nothing is uploaded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAsk, shapes = ButtonDefaults.shapes(), modifier = Modifier.height(52.dp)) { Text("Allow access") }
    }
}

@Composable
private fun HomeTab(prefs: Prefs, videos: List<Video>, onProfile: () -> Unit, onRefresh: () -> Unit, play: (Video, List<Video>) -> Unit) {
    val cont = videos.filter { v -> prefs.progress[v.uri.toString()]?.let { it.dur > 0 && it.pos in 3000 until it.dur - 5000 } == true }
        .sortedByDescending { prefs.progress[it.uri.toString()]?.ts ?: 0L }.take(10)
    VideoCollection(
        videos, prefs, "No videos found", "Add videos to your phone, then tap refresh.", { v -> play(v, videos) },
        header = {
            Column {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(greeting(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(prefs.profile.name.ifBlank { "Welcome back" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onRefresh) { Icon("sync", "Refresh") }
                    Avatar(prefs.profile, 48.dp, Modifier.clickable(onClick = onProfile))
                }
                if (cont.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Text("Continue watching", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(cont, key = { it.id }) { v -> VideoCard(v, prefs, false, Modifier.width(220.dp)) { play(v, cont) } }
                    }
                }
                if (videos.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Text("Recently added", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        },
    )
}

@Composable
private fun LibraryTab(prefs: Prefs, videos: List<Video>, play: (Video, List<Video>) -> Unit) {
    var folder by remember { mutableStateOf<String?>(null) }
    val folders = remember(videos) { videos.groupingBy { it.folder }.eachCount().entries.sortedBy { it.key.lowercase() } }
    val list = if (folder == null) videos else videos.filter { it.folder == folder }
    VideoCollection(
        list, prefs, "No videos found", "Check Settings → Library for hidden folders.", { v -> play(v, list) },
        header = {
            Column {
                Text("Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(folder == null, { folder = null }, { Text("All (${videos.size})") }) }
                    items(folders, key = { it.key }) { e -> FilterChip(folder == e.key, { folder = e.key }, { Text("${e.key} (${e.value})") }) }
                }
                Spacer(Modifier.height(6.dp))
            }
        },
    )
}

@Composable
fun VideoCollection(
    videos: List<Video>, prefs: Prefs, emptyTitle: String, emptySub: String, onClick: (Video) -> Unit,
    header: @Composable () -> Unit = {},
) {
    LazyVerticalGrid(
        columns = if (prefs.layout == 1) GridCells.Fixed(1) else GridCells.Adaptive(168.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { header() }
        if (videos.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(emptyTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(emptySub, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(videos, key = { it.id }) { v -> VideoCard(v, prefs, prefs.layout == 1, Modifier) { onClick(v) } }
    }
}

@Composable
fun VideoCard(v: Video, prefs: Prefs, row: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val fav = v.uri.toString() in prefs.favs
    val prog = prefs.progress[v.uri.toString()]
    val bmp by produceState(ThumbCache.get(v.id), v.id) {
        if (value == null) withContext(Dispatchers.IO) { app.media.thumb(v)?.asImageBitmap() }?.let { ThumbCache.put(v.id, it); value = it }
    }
    val thumb: @Composable (Modifier) -> Unit = { m ->
        Box(m.aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh)) {
            bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                ?: Icon("play_arrow", null, Modifier.align(Alignment.Center).size(36.dp), tint = cs.onSurfaceVariant)
            if (v.dur > 0) Text(
                fmt(v.dur), color = Color.White, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
            if (prog != null && prog.dur > 0) LinearProgressIndicator(
                progress = { (prog.pos.toFloat() / prog.dur).coerceIn(0f, 1f) },
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp),
                color = cs.primary, trackColor = Color.Transparent, drawStopIndicator = {},
            )
        }
    }
    val heart: @Composable () -> Unit = {
        IconButton(onClick = { scope.launch { app.store.toggleFav(v.uri.toString()) } }) {
            Icon("favorite", "Favorite", fill = fav, tint = if (fav) cs.primary else cs.onSurfaceVariant)
        }
    }
    if (row) {
        Row(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            thumb(Modifier.width(150.dp))
            Column(Modifier.weight(1f)) {
                Text(v.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(v.folder, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            heart()
        }
    } else {
        Column(modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)) {
            thumb(Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 4.dp, top = 6.dp)) {
                    Text(v.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(v.folder, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                heart()
            }
        }
    }
}

@Composable
private fun ProfileDialog(p: Profile, onDismiss: () -> Unit, onSave: (Profile) -> Unit) {
    var cur by remember { mutableStateOf(p) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your profile") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().height(440.dp).let { it }) {
                androidx.compose.foundation.layout.Box(Modifier.weight(1f, fill = false)) {
                    Column(Modifier.verticalScrollCompat(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        PhotoAvatar(cur, 110.dp) { cur = cur.copy(photo = it, emoji = "") }
                        OutlinedTextField(cur.name, { cur = cur.copy(name = it.take(24)) }, label = { Text("Name") }, singleLine = true, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth())
                        EmojiColorPicker(cur) { cur = it }
                    }
                }
            }
        },
        confirmButton = { TextButton({ onSave(cur) }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Modifier.verticalScrollCompat(): Modifier =
    this.then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))
