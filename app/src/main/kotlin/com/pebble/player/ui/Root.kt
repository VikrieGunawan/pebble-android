package com.pebble.player.ui

import kotlinx.coroutines.delay
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
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
import com.pebble.player.data.K
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
        PebbleTheme(prefs.theme, prefs.dynamic, Color(prefs.accent), prefs.amoled) {
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
                        0 -> Box(Modifier.fillMaxSize().cutoutSides(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) }
                        1 -> Box(Modifier.fillMaxSize().cutoutSides()) { ClearDataScreen(app) { clearing = false } }
                        2 -> Box(Modifier.fillMaxSize().cutoutSides()) { Onboarding(prefs, app.store) }
                        else -> MainFlow(app, prefs, openUri, onOpenConsumed) { clearing = true }
                    }
                }
            }
        }
    }
}

private fun needPerm() =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE


@Composable
private fun MainFlow(app: PebbleApp, prefs: Prefs, openUri: Uri?, onOpenConsumed: () -> Unit, startClear: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var libFolder by remember { mutableStateOf<String?>(null) }
    var libTrail by remember { mutableStateOf<String?>(null) }
    // the folders you opened stay in the location bar (dimmed when you go up), like the website
    val openFolder: (String?) -> Unit = { p -> libFolder = p; if (p != null && libTrail?.startsWith(p) != true) libTrail = p }
    var query by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<Video?>(null) }
    var playing by remember { mutableStateOf<Video?>(null) }
    var queue by remember { mutableStateOf<List<Video>>(emptyList()) }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, needPerm()) == PackageManager.PERMISSION_GRANTED) }
    var all by remember { mutableStateOf<List<Video>>(emptyList()) }
    var scanned by remember { mutableStateOf(false) }
    var rescan by remember { mutableIntStateOf(0) }
    var folderMenu by remember { mutableStateOf<String?>(null) }
    var profileDialog by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(Unit) { update = checkUpdateAuto(ctx) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    LaunchedEffect(granted, rescan) {
        if (granted) { all = app.media.scan(); scanned = true }
        if (refreshing) { delay(700); refreshing = false }
    }
    LaunchedEffect(openUri) {
        if (openUri != null) { playing = app.media.fromUri(openUri); queue = emptyList(); onOpenConsumed() }
    }

    val visible = remember(all, prefs.blacklist, prefs.sort) {
        val l = all.filter { it.path !in prefs.blacklist }
        when (prefs.sort) { 1 -> l.sortedWith { x, y -> NaturalOrder.compare(x.name, y.name) }; 2 -> l.sortedByDescending { it.dur }; 3 -> l.sortedByDescending { it.size }; else -> l }
    }
    fun play(v: Video, list: List<Video>) { playing = v; queue = list }
    var refreshing by remember { mutableStateOf(false) }
    fun refresh() { refreshing = true; rescan++ }

    Box(Modifier.fillMaxSize()) {
        val cfg = androidx.compose.ui.platform.LocalConfiguration.current
        val wide = (cfg.screenWidthDp >= 744 && cfg.screenHeightDp >= 560) || (cfg.screenWidthDp > cfg.screenHeightDp && cfg.screenWidthDp >= 600)
        val dark = when (prefs.theme) { 1 -> false; 2 -> true; else -> androidx.compose.foundation.isSystemInDarkTheme() }
        CompositionLocalProvider(LocalVideoMenu provides { v: Video -> menuFor = v }, LocalFolderMenu provides { p: String -> folderMenu = p }) {
        run {
        Row(Modifier.fillMaxSize()) {
        if (wide) PebbleRail(tab) { tab = it }
        Scaffold(
            modifier = Modifier.weight(1f).windowInsetsPadding(WindowInsets.displayCutout.only(if (wide) WindowInsetsSides.End else WindowInsetsSides.Horizontal)),
            topBar = {
                PebbleTopBar(
                    showLogo = !wide, query = query, onQuery = { query = it; if (it.isNotEmpty() && tab != 1 && tab != 4) tab = 1 },
                    placeholder = if (tab == 4) "Search settings" else "Search your videos",
                    settingsOn = tab == 4, onSettings = { tab = if (tab == 4) 0 else 4 },
                    dark = dark, onToggleTheme = { scope.launch { app.store.put(K.theme, if (dark) 1 else 2) } },
                    profile = prefs.profile, onProfile = { profileDialog = true },
                    canRefresh = tab != 4 && granted && scanned, refreshing = refreshing, onRefresh = { refresh() },
                )
            },
            bottomBar = { if (!wide) PebbleTabBar(tab) { tab = it } },
        ) { pad ->
            val focus = androidx.compose.ui.platform.LocalFocusManager.current
            // Tabs sit side by side; switching slides the strip and the tabs in between pass by.
            TabPager(
                tab = tab, count = 5, onTab = { tab = it },
                inFolder = tab == 1 && !libFolder.isNullOrBlank(),
                onFolderUp = {
                    val segs = libFolder?.split('/')?.filter { it.isNotBlank() } ?: emptyList()
                    openFolder(if (segs.size <= 1) null else segs.dropLast(1).joinToString("/") + "/")
                },
                modifier = Modifier.padding(pad).fillMaxSize().pointerInput(Unit) { detectTapGestures { focus.clearFocus() } },
            ) { t ->
                if (t == 4) {
                    SettingsScreen(prefs, app.store, all, { rescan++ }, startClear, query)
                } else if (!granted) {
                    PermissionPrompt { ask.launch(needPerm()) }
                } else if (!scanned) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(56.dp)) }
                } else PullPage(refreshing, { refresh() }) { when (t) {
                    0 -> HomeTab(prefs, visible, { rescan++ }, ::play, { tab = it }, openFolder)
                    1 -> LibraryTab(prefs, app.store, visible, libFolder, libTrail, openFolder, query, ::play)
                    2 -> FavoritesTab(prefs, app.store, visible.filter { it.uri.toString() in prefs.favs }, ::play)
                    else -> {
                        val l = visible.filter { it.uri.toString() in prefs.progress }.sortedByDescending { prefs.progress[it.uri.toString()]?.ts ?: 0L }
                        HistoryTab(prefs, app.store, l, ::play)
                    }
                } }
            }
        }
        }
        }
        }
        folderMenu?.let { p ->
            FolderMenuSheet(p, visible, prefs, app.store, onPlay = ::play, onOpen = { openFolder(p); tab = 1 }, onDismiss = { folderMenu = null })
        }
        menuFor?.let { v ->
            VideoMenuSheet(v, prefs, app.store, onPlay = { play(v, visible) }, onRescan = { rescan++ }, onDismiss = { menuFor = null })
        }
        playing?.let { v ->
            PredictiveBack(true, { playing = null }) {
                PlayerScreen(v, queue, prefs, app.store, { playing = null }, { playing = it })
            }
        }
    }

    update?.let { u -> UpdateDialog(u, { update = null }, { skipVersion(ctx, u.version); update = null }) }
    if (profileDialog) ProfileDialog(prefs.profile, { profileDialog = false }) { p -> scope.launch { app.store.saveProfile(p) }; profileDialog = false }
}

/** Pull down to rescan, with the Material 3 Expressive loading indicator (the same one used for buffering). */
@Composable
private fun PullPage(refreshing: Boolean, onRefresh: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing, onRefresh = onRefresh, state = state, modifier = Modifier.fillMaxSize(),
        indicator = { PullToRefreshDefaults.LoadingIndicator(state = state, isRefreshing = refreshing, modifier = Modifier.align(Alignment.TopCenter)) },
    ) { content() }
}

/** Keeps the UI away from a camera notch on the left or right edge (landscape). */
@Composable
private fun Modifier.cutoutSides(): Modifier = windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))

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
