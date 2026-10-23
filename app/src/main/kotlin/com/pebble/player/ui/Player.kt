package com.pebble.player.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.pebble.player.data.K
import com.pebble.player.data.Prefs
import com.pebble.player.data.Store
import com.pebble.player.data.Video
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val GLASS = Color(0xE0181330)
private val SOFT = Color(0x1FFFFFFF)
private val RATIOS = listOf(0f to "Auto", (16f / 9f) to "16:9", (4f / 3f) to "4:3", (21f / 9f) to "21:9", 1f to "1:1", (9f / 16f) to "9:16")
private val PRESETS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f)
private val FITS = listOf("Fit", "Crop", "Stretch", "Width")
private val ORIENTS = listOf("Auto", "Portrait", "Landscape")

private fun fmtSpeed(v: Float): String = String.format(java.util.Locale.US, "%.2f", v).trimEnd('0').trimEnd('.') + "×"

private fun trackLabel(f: Format, i: Int, kind: String): String =
    f.label ?: f.language?.takeIf { it != "und" }?.let { java.util.Locale.forLanguageTag(it).displayLanguage.ifBlank { it } } ?: "$kind ${i + 1}"

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

private fun deviceLabel(d: AudioDeviceInfo): String = when (d.type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone speaker"
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Phone earpiece"
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headphones"
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> d.productName.toString().ifBlank { "USB audio" }
    AudioDeviceInfo.TYPE_HDMI -> "HDMI"
    else -> d.productName.toString().ifBlank { "Bluetooth audio" }
}

private fun listOutputs(am: AudioManager): List<AudioDeviceInfo> = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter {
    isBt(it) || it.type in listOf(
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_HDMI,
    )
}

@Composable
fun PlayerScreen(video: Video, queue: List<Video>, prefs: Prefs, store: Store, onClose: () -> Unit, onSelect: (Video) -> Unit) {
    val ctx = LocalContext.current
    val act = remember { ctx.activity() }
    val scope = rememberCoroutineScope()
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val uriStr = video.uri.toString()
    val idx = queue.indexOfFirst { it.id == video.id }
    val prev = queue.getOrNull(idx - 1).takeIf { idx > 0 }
    val next = queue.getOrNull(idx + 1).takeIf { idx >= 0 }
    val curPrefs by rememberUpdatedState(prefs)
    val curNext by rememberUpdatedState(next)
    val curSelect by rememberUpdatedState(onSelect)
    val curQueue by rememberUpdatedState(queue)
    val curId = video.id

    var subUri by remember(video.id) { mutableStateOf<Uri?>(null) }
    var subName by remember(video.id) { mutableStateOf("") }
    var subs by remember(video.id) { mutableStateOf(true) }

    var loop by remember { mutableStateOf(false) }
    var autoNext by remember { mutableStateOf(true) }
    var shuffle by remember { mutableStateOf(false) }
    var ratio by remember { mutableFloatStateOf(0f) }
    var subSize by remember { mutableIntStateOf(1) }
    var subBg by remember { mutableIntStateOf(1) }
    var boost by remember { mutableIntStateOf(0) }
    var muted by remember { mutableStateOf(false) }
    var sleepEnd by remember { mutableLongStateOf(0L) }
    var nowMs by remember { mutableLongStateOf(0L) }
    var panel by remember { mutableStateOf<String?>(null) }
    var lastPanel by remember { mutableStateOf("speed") }
    var audioDlg by remember { mutableStateOf(false) }
    val curAuto by rememberUpdatedState(autoNext)
    val curShuffle by rememberUpdatedState(shuffle)
    val curPanel by rememberUpdatedState(panel)

    val player = remember(video.id) {
        ExoPlayer.Builder(ctx)
            .setSeekBackIncrementMs(prefs.skip * 1000L)
            .setSeekForwardIncrementMs(prefs.skip * 1000L)
            .setHandleAudioBecomingNoisy(prefs.onLost == 0)
            .setAudioAttributes(androidx.media3.common.AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .build()
    }

    var firstLoad by remember(video.id) { mutableStateOf(true) }
    LaunchedEffect(player, subUri) {
        val keep = if (firstLoad) 0L else player.currentPosition
        val wasPlaying = if (firstLoad) curPrefs.autoplay else player.playWhenReady
        val b = MediaItem.Builder().setUri(video.uri)
        subUri?.let { u ->
            val mime = if (subName.endsWith(".vtt", true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
            b.setSubtitleConfigurations(listOf(MediaItem.SubtitleConfiguration.Builder(u).setMimeType(mime).setLanguage("und").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()))
        }
        var start = keep
        if (firstLoad && curPrefs.resume) curPrefs.progress[uriStr]?.let { if (it.dur > 0 && it.pos < it.dur - 5000) start = it.pos }
        player.setMediaItem(b.build(), start)
        player.setPlaybackSpeed(curPrefs.speed)
        player.prepare()
        player.playWhenReady = wasPlaying
        firstLoad = false
    }
    LaunchedEffect(prefs.onLost) { player.setHandleAudioBecomingNoisy(prefs.onLost == 0) }
    LaunchedEffect(player, loop) { player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }
    LaunchedEffect(player, muted) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(panel) { panel?.let { lastPanel = it } }
    DisposableEffect(player, boost) {
        var le: android.media.audiofx.LoudnessEnhancer? = null
        try {
            if (boost > 0) le = android.media.audiofx.LoudnessEnhancer(player.audioSessionId).apply { setTargetGain(boost * 300); enabled = true }
        } catch (_: Exception) { }
        onDispose { try { le?.release() } catch (_: Exception) { } }
    }
    LaunchedEffect(sleepEnd) {
        if (sleepEnd > 0) {
            val w = sleepEnd - SystemClock.elapsedRealtime()
            if (w > 0) delay(w)
            player.pause(); sleepEnd = 0L
            Toast.makeText(ctx, "Sleep timer ended", Toast.LENGTH_SHORT).show()
        }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var dur by remember { mutableLongStateOf(0L) }
    var pos by remember { mutableLongStateOf(0L) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var speed by remember { mutableFloatStateOf(prefs.speed) }
    var fit by remember { mutableIntStateOf(prefs.fit) }
    var orient by remember { mutableIntStateOf(prefs.orient) }
    var vSize by remember { mutableStateOf(VideoSize.UNKNOWN) }
    var tracks by remember(video.id) { mutableStateOf(Tracks.EMPTY) }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { isPlaying = p }
            override fun onPlaybackStateChanged(s: Int) {
                buffering = s == Player.STATE_BUFFERING
                if (s == Player.STATE_READY) dur = player.duration.coerceAtLeast(0)
                if (s == Player.STATE_ENDED && curAuto) {
                    (if (curShuffle) curQueue.filter { it.id != curId }.randomOrNull() else curNext)?.let { curSelect(it) }
                }
            }
            override fun onVideoSizeChanged(v: VideoSize) { vSize = v }
            override fun onTracksChanged(t: Tracks) { tracks = t }
            override fun onPlayerError(e: PlaybackException) { error = "Can't play this video" }
        }
        player.addListener(l)
        onDispose {
            if (player.duration > 0) scope.launch(kotlinx.coroutines.NonCancellable) {
                val p = player.currentPosition; val d = player.duration
                store.saveProgress(uriStr, if (d > 0 && p > d - 3000) 0L else p, d)
            }
            player.removeListener(l)
            player.release()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }

    LaunchedEffect(orient, vSize, prefs.autoFs) {
        act?.requestedOrientation = when {
            orient == 1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            orient == 2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            prefs.autoFs && vSize.width > vSize.height -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    DisposableEffect(act) {
        val w = act?.window
        val c = w?.let { WindowInsetsControllerCompat(it, it.decorView) }
        c?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c?.hide(WindowInsetsCompat.Type.systemBars())
        // always draw into the cutout area, then keep the player out of it (black border around the notch)
        w?.let { win -> win.attributes = win.attributes.also { a -> a.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES } }
        onDispose {
            w?.let { win -> win.attributes = win.attributes.also { a -> a.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT } }
            c?.show(WindowInsetsCompat.Type.systemBars())
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            w?.let { win -> win.attributes = win.attributes.also { a -> a.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
        }
    }

    LaunchedEffect(player) {
        var n = 0
        while (true) {
            pos = player.currentPosition
            nowMs = SystemClock.elapsedRealtime()
            if (player.duration > 0) dur = player.duration
            if (++n % 20 == 0 && player.isPlaying && dur > 0) store.saveProgress(uriStr, pos, dur)
            delay(250)
        }
    }

    var show by remember { mutableStateOf(true) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(show, tick, isPlaying, panel, scrub) {
        if (show && isPlaying && panel == null && scrub == null) { delay(3200); show = false }
    }
    BackHandler(panel != null) { panel = null }

    var hud by remember { mutableStateOf<Pair<String, Float>?>(null) }
    var hudTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(hudTick) { if (hudTick > 0) { delay(900); hud = null } }
    var seekFlash by remember { mutableStateOf("") }
    LaunchedEffect(seekFlash) { if (seekFlash.isNotEmpty()) { delay(600); seekFlash = "" } }

    val pickSub = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            subName = try { ctx.contentResolver.query(u, null, null, null, null)?.use { q -> if (q.moveToFirst()) q.getString(q.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else "" } ?: "" } catch (_: Exception) { "" }
            subUri = u; subs = true
        }
    }

    fun applySpeed(v: Float, save: Boolean) {
        val s = v.coerceIn(0.25f, 3f)
        speed = s; player.setPlaybackSpeed(s)
        if (save) scope.launch { store.put(K.speed, s) }
    }
    fun applyFit(v: Int) { fit = v; scope.launch { store.put(K.fit, v) } }
    fun enterPip() {
        try {
            val b = android.app.PictureInPictureParams.Builder()
            if (vSize.width > 0 && vSize.height > 0) {
                val r = vSize.width.toFloat() / vSize.height
                if (r in 0.45f..2.3f) b.setAspectRatio(android.util.Rational(vSize.width, vSize.height))
            }
            show = false; panel = null
            act?.enterPictureInPictureMode(b.build())
        } catch (_: Exception) { Toast.makeText(ctx, "Picture-in-picture isn't available", Toast.LENGTH_SHORT).show() }
    }
    fun go(v: Video?) { if (v != null) onSelect(v) }
    fun pickNext(): Video? = if (shuffle) queue.filter { it.id != video.id }.randomOrNull() else next
    fun togglePanel(id: String) { panel = if (panel == id) null else id; tick++ }
    fun pickTrack(g: Tracks.Group) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0)).build()
    }

    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.displayCutout)) {
        val land = maxWidth > maxHeight
        val endPad by animateDpAsState(if (land && panel != null) 384.dp else 0.dp, label = "endPad")
        val effFit = if (ratio > 0f && fit == 0) 1 else fit

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { PlayerView(it).apply { useController = false; setShutterBackgroundColor(android.graphics.Color.BLACK) } },
                update = {
                    it.player = player
                    it.resizeMode = when (effFit) {
                        1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    it.keepScreenOn = prefs.wake
                    it.subtitleView?.let { sv ->
                        sv.visibility = if (subs) android.view.View.VISIBLE else android.view.View.GONE
                        sv.setFractionalTextSize(when (subSize) { 0 -> 0.04f; 1 -> 0.0533f; 2 -> 0.07f; else -> 0.09f })
                        val bg = when (subBg) { 0 -> android.graphics.Color.TRANSPARENT; 1 -> 0xB80A0814.toInt(); else -> 0xF0000000.toInt() }
                        sv.setStyle(CaptionStyleCompat(android.graphics.Color.WHITE, bg, android.graphics.Color.TRANSPARENT,
                            if (subBg == 0) CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW else CaptionStyleCompat.EDGE_TYPE_NONE, android.graphics.Color.BLACK, null))
                    }
                },
                modifier = if (ratio > 0f) Modifier.aspectRatio(ratio) else Modifier.fillMaxSize(),
            )
        }

        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (curPanel != null) panel = null else { show = !show; tick++ } },
                        onDoubleTap = { o ->
                            val w = size.width
                            when {
                                o.x < w / 3f -> { player.seekTo((player.currentPosition - curPrefs.skip * 1000L).coerceAtLeast(0)); seekFlash = "r−${curPrefs.skip}s" }
                                o.x > w * 2f / 3f -> { player.seekTo((player.currentPosition + curPrefs.skip * 1000L).coerceAtMost(player.duration.coerceAtLeast(0))); seekFlash = "f+${curPrefs.skip}s" }
                                else -> if (player.isPlaying) player.pause() else player.play()
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    var left = true
                    var acc = 0f
                    detectVerticalDragGestures(
                        onDragStart = { o -> left = o.x < size.width / 2f; acc = 0f },
                        onDragEnd = { hudTick++ }, onDragCancel = { hudTick++ },
                    ) { change, dy ->
                        change.consume()
                        val d = -dy / size.height * 1.4f
                        if (left) {
                            val win = act?.window ?: return@detectVerticalDragGestures
                            val cur = win.attributes.screenBrightness.let { if (it < 0f) 0.5f else it }
                            val nv = (cur + d).coerceIn(0.02f, 1f)
                            win.attributes = win.attributes.also { it.screenBrightness = nv }
                            hud = "brightness_6" to nv
                        } else {
                            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            acc += d * max
                            val steps = acc.toInt()
                            if (steps != 0) {
                                acc -= steps
                                val nv = (am.getStreamVolume(AudioManager.STREAM_MUSIC) + steps).coerceIn(0, max)
                                am.setStreamVolume(AudioManager.STREAM_MUSIC, nv, 0)
                            }
                            hud = "volume_up" to am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
                        }
                        hudTick++
                    }
                },
        )

        if (buffering && error == null) androidx.compose.material3.LoadingIndicator(Modifier.align(Alignment.Center).size(56.dp))
        if (seekFlash.isNotEmpty()) Row(
            Modifier.align(Alignment.Center).clip(RoundedCornerShape(28.dp)).background(Color(0x99000000)).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(if (seekFlash[0] == 'r') "replay_10" else "forward_10", null, Modifier.size(30.dp), tint = Color.White)
            Text(seekFlash.drop(1), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        hud?.let { (ic, v) ->
            Row(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 76.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xB80A0814)).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(if (ic == "volume_up" && v <= 0f) "volume_off" else ic, null, Modifier.size(24.dp), tint = cs.primary)
                LinearProgressIndicator(progress = { v }, modifier = Modifier.size(width = 110.dp, height = 6.dp), drawStopIndicator = {})
                Text("${(v * 100).roundToInt()}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        error?.let {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon("info", null, Modifier.size(40.dp), tint = Color.White)
                Text(it, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                TextButton(onClose) { Text("Go back") }
            }
        }

        PAnim(show, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                        .statusBarsPadding().padding(end = endPad.coerceAtLeast(0.dp)).padding(bottom = 12.dp),
                ) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PBtn("arrow_back", "Back", 44.dp, onClick = onClose)
                        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            Text(video.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (queue.size > 1 && idx >= 0) Text("${idx + 1} of ${queue.size}", color = Color(0xB3FFFFFF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (land) Pills(Modifier.weight(1.7f), speed, fit, loop, orient, panel, sleepEnd > 0, { togglePanel(it) }, { loop = !loop }, ::enterPip, { orient = (orient + 1) % 3; Toast.makeText(ctx, ORIENTS[orient], Toast.LENGTH_SHORT).show() })
                    }
                    if (!land) Pills(Modifier.fillMaxWidth().padding(horizontal = 12.dp), speed, fit, loop, orient, panel, sleepEnd > 0, { togglePanel(it) }, { loop = !loop }, ::enterPip, { orient = (orient + 1) % 3; Toast.makeText(ctx, ORIENTS[orient], Toast.LENGTH_SHORT).show() })
                }

                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000))))
                        .navigationBarsPadding().padding(start = 16.dp, end = 16.dp + endPad.coerceAtLeast(0.dp), top = 28.dp, bottom = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fmt(scrub?.let { (it * dur).toLong() } ?: pos), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        PebbleSlider(
                            value = scrub ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                            onValueChange = { scrub = it },
                            onValueChangeFinished = { scrub?.let { player.seekTo((it * dur).toLong()) }; scrub = null },
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        Text(fmt(dur), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        PBtn("skip_previous", "Previous", 48.dp, enabled = prev != null, onClick = { go(prev) })
                        PBtn("replay_10", "Back", 52.dp, onClick = { player.seekTo((player.currentPosition - curPrefs.skip * 1000L).coerceAtLeast(0)); tick++ })
                        Box(
                            Modifier.padding(horizontal = 8.dp).size(68.dp).clip(RoundedCornerShape(if (isPlaying) 24.dp else 34.dp)).background(cs.primary)
                                .clickable { if (player.isPlaying) player.pause() else player.play(); tick++ },
                            contentAlignment = Alignment.Center,
                        ) { Icon(if (isPlaying) "pause" else "play_arrow", "Play or pause", Modifier.size(38.dp), tint = cs.onPrimary) }
                        PBtn("forward_10", "Forward", 52.dp, onClick = { player.seekTo((player.currentPosition + curPrefs.skip * 1000L).coerceAtMost(player.duration.coerceAtLeast(0))); tick++ })
                        PBtn("skip_next", "Next", 48.dp, enabled = pickNext() != null, onClick = { go(pickNext()) })
                    }
                }
            }
        }

        PAnim(
            panel != null,
            Modifier.align(if (land) Alignment.CenterEnd else Alignment.BottomCenter),
            enter = if (land) slideInHorizontally { it } + fadeIn() else slideInVertically { it } + fadeIn(),
            exit = if (land) slideOutHorizontally { it } + fadeOut() else slideOutVertically { it } + fadeOut(),
        ) {
            val shown = panel ?: lastPanel
            val (pIcon, pTitle, pSub) = when (shown) {
                "speed" -> Triple("speed", "Playback speed", if (speed == 1f) "Normal" else fmtSpeed(speed))
                "subs" -> Triple("closed_caption", "Subtitles", if (subUri != null) subName else "No file loaded")
                "audio" -> Triple("graphic_eq", "Audio", "Volume and sound")
                "ratio" -> Triple("aspect_ratio", "Video ratio", FITS[fit])
                "queue" -> Triple("queue_music", "Up next", "${queue.size} videos")
                else -> Triple("timer", "Sleep timer", if (sleepEnd > 0) "Pausing in ${((sleepEnd - nowMs) / 60000 + 1).coerceAtLeast(1)} min" else "Off")
            }
            Column(
                (if (land) Modifier.statusBarsPadding().navigationBarsPadding().padding(12.dp).width(360.dp).fillMaxHeight()
                else Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp).heightIn(max = maxHeight * 0.66f))
                    .clip(RoundedCornerShape(28.dp)).background(GLASS)
                    .pointerInput(Unit) { detectTapGestures { } }.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(15.dp)).background(cs.primary), contentAlignment = Alignment.Center) { Icon(pIcon, null, Modifier.size(24.dp), tint = cs.onPrimary) }
                    Column(Modifier.weight(1f)) {
                        Text(pTitle, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Text(pSub, color = Color(0xB3FFFFFF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    PBtn("close", "Close", 40.dp, onClick = { panel = null })
                }
                if (shown == "queue") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Toggle("shuffle", "Shuffle", shuffle, Modifier.weight(1f)) { shuffle = !shuffle }
                        Toggle("skip_next", "Autoplay next", autoNext, Modifier.weight(1f)) { autoNext = !autoNext }
                    }
                    val ls = rememberLazyListState(initialFirstVisibleItemIndex = (idx - 1).coerceAtLeast(0))
                    LazyColumn(Modifier.weight(1f, fill = false), state = ls, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(queue, key = { _, q -> q.id }) { i, q ->
                            val cur = q.id == video.id
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (cur) cs.primary.copy(alpha = 0.28f) else SOFT)
                                    .clickable { panel = null; onSelect(q) }.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                val th = rememberThumb(q)
                                Box(Modifier.size(width = 76.dp, height = 46.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
                                    if (th != null) Image(th, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Icon("movie", null, Modifier.size(22.dp), tint = Color.White)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(q.name, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("${i + 1} · ${fmt(q.dur)}", color = Color(0xB3FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                                if (cur) Icon("play_arrow", null, Modifier.size(22.dp), tint = cs.primary)
                            }
                        }
                    }
                } else Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (shown) {
                        "speed" -> {
                            Text(fmtSpeed(speed), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PBtn("remove", "Slower", 44.dp, bg = SOFT, onClick = { applySpeed(speed - 0.05f, true) })
                                PebbleSlider(speed, { applySpeed((it * 20).roundToInt() / 20f, false) }, Modifier.weight(1f), valueRange = 0.25f..3f, onValueChangeFinished = { applySpeed(speed, true) })
                                PBtn("add", "Faster", 44.dp, bg = SOFT, onClick = { applySpeed(speed + 0.05f, true) })
                            }
                            Sl("tune", "Presets")
                            Chips(PRESETS.map { fmtSpeed(it) }, PRESETS.indexOfFirst { kotlin.math.abs(it - speed) < 0.01f }) { applySpeed(PRESETS[it], true) }
                            Wide("restart_alt", "Normal speed") { applySpeed(1f, true) }
                        }
                        "subs" -> {
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SOFT).clickable { subs = !subs }.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon("closed_caption", null, Modifier.size(24.dp), tint = Color.White)
                                Text("Show subtitles", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold)
                                PebbleSwitch(subs, { subs = it })
                            }
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SOFT).clickable { pickSub.launch(arrayOf("*/*")) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon("upload_file", null, Modifier.size(24.dp), tint = cs.primary)
                                Column(Modifier.weight(1f)) {
                                    Text("Load subtitle file", color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("SRT or VTT from your device", color = Color(0xB3FFFFFF), fontSize = 12.sp)
                                }
                                Icon("chevron_right", null, Modifier.size(22.dp), tint = Color(0x99FFFFFF))
                            }
                            val textTr = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                            if (textTr.isNotEmpty()) {
                                Sl("subtitles", "Subtitle tracks")
                                textTr.forEachIndexed { i, g -> Opt("closed_caption", trackLabel(g.getTrackFormat(0), i, "Subtitle"), g.isSelected) { pickTrack(g) } }
                            }
                            Sl("format_size", "Text size")
                            Chips(listOf("Small", "Medium", "Large", "XL"), subSize) { subSize = it }
                            Sl("palette", "Background")
                            Chips(listOf("None", "Soft", "Solid"), subBg, 3) { subBg = it }
                            if (subUri != null) Wide("delete", "Remove subtitles") { subUri = null; subName = "" }
                        }
                        "audio" -> {
                            var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)) }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PBtn(if (muted || vol <= 0f) "volume_off" else "volume_up", "Mute", 52.dp, bg = if (muted) cs.primary else SOFT, tint = if (muted) cs.onPrimary else Color.White, onClick = { muted = !muted })
                                Column {
                                    Text("${(vol * 100).roundToInt()}%", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                                    Text(if (muted) "Muted" else "Volume", color = Color(0xB3FFFFFF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            PebbleSlider(vol, { vol = it; am.setStreamVolume(AudioManager.STREAM_MUSIC, (it * am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).roundToInt(), 0) }, Modifier.fillMaxWidth())
                            Sl("equalizer", "Volume boost")
                            Chips(listOf("Off", "+3 dB", "+6 dB", "+9 dB"), boost) { boost = it }
                            val aTr = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                            if (aTr.size > 1) {
                                Sl("graphic_eq", "Audio tracks")
                                aTr.forEachIndexed { i, g -> Opt("music_note", trackLabel(g.getTrackFormat(0), i, "Track"), g.isSelected) { pickTrack(g) } }
                            }
                            Wide("headphones", "Audio output") { audioDlg = true }
                        }
                        "ratio" -> {
                            Sl("aspect_ratio", "Scaling")
                            Chips(FITS, fit) { applyFit(it) }
                            Sl("crop", "Aspect ratio")
                            Chips(RATIOS.map { it.second }, RATIOS.indexOfFirst { kotlin.math.abs(it.first - ratio) < 0.01f }, 3) { ratio = RATIOS[it].first }
                            Text("A chosen aspect ratio crops the picture to that frame. Pick Auto to use the video's own shape.", color = Color(0xB3FFFFFF), fontSize = 12.sp)
                        }
                        else -> {
                            Sl("timer", "Pause playback after")
                            val mins = listOf(0, 15, 30, 45, 60, 90)
                            Chips(mins.map { if (it == 0) "Off" else "$it min" }, if (sleepEnd == 0L) 0 else -1, 3) {
                                sleepEnd = if (mins[it] == 0) 0L else SystemClock.elapsedRealtime() + mins[it] * 60000L
                            }
                        }
                    }
                }
            }
        }
    }

    if (audioDlg) AudioOutputDialog(am, player, prefs.onLost) { audioDlg = false }
}

@Composable
private fun PBtn(icon: String, desc: String, size: Dp, modifier: Modifier = Modifier, enabled: Boolean = true, bg: Color = Color.Transparent, tint: Color = Color.White, onClick: () -> Unit) {
    Box(
        modifier.size(size).clip(CircleShape).background(bg).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, Modifier.size(size * 0.52f), tint = tint.copy(alpha = if (enabled) 1f else 0.35f)) }
}

@Composable
private fun Pill(icon: String, label: String, on: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.clip(RoundedCornerShape(22.dp)).background(if (on) cs.primary else Color(0x33FFFFFF)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (on) cs.onPrimary else Color.White)
        Text(label, color = if (on) cs.onPrimary else Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun Pills(
    modifier: Modifier, speed: Float, fit: Int, loop: Boolean, orient: Int, panel: String?, sleepOn: Boolean,
    onPanel: (String) -> Unit, onLoop: () -> Unit, onPip: () -> Unit, onRotate: () -> Unit,
) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Pill("speed", fmtSpeed(speed), panel == "speed" || speed != 1f) { onPanel("speed") }
        Pill("closed_caption", "Subtitles", panel == "subs") { onPanel("subs") }
        Pill("graphic_eq", "Audio", panel == "audio") { onPanel("audio") }
        Pill("aspect_ratio", FITS[fit], panel == "ratio") { onPanel("ratio") }
        Pill("queue_music", "Queue", panel == "queue") { onPanel("queue") }
        Pill("repeat", "Loop", loop) { onLoop() }
        Pill("timer", "Sleep", panel == "sleep" || sleepOn) { onPanel("sleep") }
        Pill("picture_in_picture_alt", "Pop-out", false) { onPip() }
        Pill("screen_rotation", ORIENTS[orient], orient != 0) { onRotate() }
    }
}

@Composable
private fun Sl(icon: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = Color(0xB3FFFFFF))
        Text(text.uppercase(), color = Color(0xB3FFFFFF), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
    }
}

@Composable
private fun Chips(opts: List<String>, sel: Int, cols: Int = 4, onSel: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        opts.withIndex().chunked(cols).forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.forEach { (i, o) ->
                    Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).background(if (i == sel) cs.primary else SOFT).clickable { onSel(i) }, contentAlignment = Alignment.Center) {
                        Text(o, color = if (i == sel) cs.onPrimary else Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1)
                    }
                }
                repeat(cols - r.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Wide(icon: String, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(SOFT).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = Color.White)
        Text(text, color = Color.White, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun Opt(icon: String, text: String, sel: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (sel) cs.primary.copy(alpha = 0.28f) else SOFT).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = Color.White)
        Text(text, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sel) Icon("check", null, Modifier.size(22.dp), tint = cs.primary)
    }
}

@Composable
private fun Toggle(icon: String, text: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(if (on) cs.primary else SOFT).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (on) cs.onPrimary else Color.White)
        Text(text, color = if (on) cs.onPrimary else Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun AudioOutputDialog(am: AudioManager, player: ExoPlayer, onLost: Int, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var devices by remember { mutableStateOf(listOutputs(am)) }
    var chosen by remember { mutableIntStateOf(-1) } // device id, -1 = automatic
    val lostPolicy by rememberUpdatedState(onLost)

    DisposableEffect(am) {
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) { devices = listOutputs(am) }
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
                devices = listOutputs(am)
                removed.firstOrNull { isBt(it) || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }?.let {
                    if (it.id == chosen) { chosen = -1; player.setPreferredAudioDevice(null) }
                    Toast.makeText(ctx, "${deviceLabel(it)} disconnected" + if (lostPolicy == 1) " · still playing" else " · paused", Toast.LENGTH_SHORT).show()
                }
            }
        }
        am.registerAudioDeviceCallback(cb, null)
        onDispose { am.unregisterAudioDeviceCallback(cb) }
    }

    Dialog(onClose) {
        Column(Modifier.clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Audio output", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            @Composable
            fun row(label: String, sub: String, sel: Boolean, onClick: () -> Unit) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(label, fontWeight = FontWeight.SemiBold)
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (sel) Icon("check", null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            row("Automatic", "Let Android choose", chosen == -1) { chosen = -1; player.setPreferredAudioDevice(null) }
            devices.forEach { d ->
                row(deviceLabel(d), if (isBt(d)) "Bluetooth" else "Device", chosen == d.id) { chosen = d.id; player.setPreferredAudioDevice(d) }
            }
            TextButton(onClose, Modifier.align(Alignment.End)) { Text("Done") }
        }
    }
}
