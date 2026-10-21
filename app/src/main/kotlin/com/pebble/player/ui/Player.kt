package com.pebble.player.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.pebble.player.data.Prefs
import com.pebble.player.data.Store
import com.pebble.player.data.Video
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    var subUri by remember(video.id) { mutableStateOf<Uri?>(null) }
    var subName by remember(video.id) { mutableStateOf("") }
    var subs by remember(video.id) { mutableStateOf(true) }

    val player = remember(video.id) {
        ExoPlayer.Builder(ctx)
            .setSeekBackIncrementMs(prefs.skip * 1000L)
            .setSeekForwardIncrementMs(prefs.skip * 1000L)
            .setHandleAudioBecomingNoisy(prefs.onLost == 0)
            .setAudioAttributes(androidx.media3.common.AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .build()
    }

    // load media (re-load when subtitles are added, keeping position)
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
    LaunchedEffect(prefs.skip) { /* applied on next video */ }

    var isPlaying by remember { mutableStateOf(false) }
    var dur by remember { mutableLongStateOf(0L) }
    var pos by remember { mutableLongStateOf(0L) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var speed by remember { mutableFloatStateOf(prefs.speed) }
    var fit by remember { mutableIntStateOf(prefs.fit) }
    var orient by remember { mutableIntStateOf(prefs.orient) }
    var vSize by remember { mutableStateOf(VideoSize.UNKNOWN) }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { isPlaying = p }
            override fun onPlaybackStateChanged(s: Int) {
                buffering = s == Player.STATE_BUFFERING
                if (s == Player.STATE_READY) dur = player.duration.coerceAtLeast(0)
                if (s == Player.STATE_ENDED) { curNext?.let { curSelect(it) } }
            }
            override fun onVideoSizeChanged(v: VideoSize) { vSize = v }
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

    // orientation + immersive mode
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
        onDispose {
            c?.show(WindowInsetsCompat.Type.systemBars())
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            w?.attributes = w?.attributes?.also { it.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }

    // progress polling + periodic save
    LaunchedEffect(player) {
        var n = 0
        while (true) {
            pos = player.currentPosition
            if (player.duration > 0) dur = player.duration
            if (++n % 20 == 0 && player.isPlaying && dur > 0) store.saveProgress(uriStr, pos, dur)
            delay(250)
        }
    }

    // controls visibility
    var show by remember { mutableStateOf(true) }
    var dialog by remember { mutableStateOf(0) } // 1 audio, 2 speed
    var scrub by remember { mutableStateOf<Float?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(show, tick, isPlaying, dialog, scrub) {
        if (show && isPlaying && dialog == 0 && scrub == null) { delay(3200); show = false }
    }

    // gestures HUD
    var hud by remember { mutableStateOf<Pair<String, Float>?>(null) }
    var hudTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(hudTick) { if (hudTick > 0) { delay(900); hud = null } }
    var seekFlash by remember { mutableStateOf("") }
    LaunchedEffect(seekFlash) { if (seekFlash.isNotEmpty()) { delay(600); seekFlash = "" } }

    // subtitle picker
    val pickSub = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            subName = try { ctx.contentResolver.query(u, null, null, null, null)?.use { q -> if (q.moveToFirst()) q.getString(q.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else "" } ?: "" } catch (_: Exception) { "" }
            subUri = u; subs = true
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { PlayerView(it).apply { useController = false; setShutterBackgroundColor(android.graphics.Color.BLACK) } },
            update = {
                it.player = player
                it.resizeMode = when (fit) { 1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM; 2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL; else -> AspectRatioFrameLayout.RESIZE_MODE_FIT }
                it.keepScreenOn = prefs.wake
                it.subtitleView?.visibility = if (subs) android.view.View.VISIBLE else android.view.View.GONE
            },
            modifier = Modifier.fillMaxSize(),
        )

        // gesture layer
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { show = !show; tick++ },
                        onDoubleTap = { o ->
                            val w = size.width
                            when {
                                o.x < w / 3f -> { player.seekTo((player.currentPosition - curPrefs.skip * 1000L).coerceAtLeast(0)); seekFlash = "−${curPrefs.skip}s" }
                                o.x > w * 2f / 3f -> { player.seekTo((player.currentPosition + curPrefs.skip * 1000L).coerceAtMost(player.duration.coerceAtLeast(0))); seekFlash = "+${curPrefs.skip}s" }
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
                            hud = "Brightness" to nv
                        } else {
                            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            acc += d * max
                            val steps = acc.toInt()
                            if (steps != 0) {
                                acc -= steps
                                val nv = (am.getStreamVolume(AudioManager.STREAM_MUSIC) + steps).coerceIn(0, max)
                                am.setStreamVolume(AudioManager.STREAM_MUSIC, nv, 0)
                            }
                            hud = "Volume" to am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
                        }
                        hudTick++
                    }
                },
        )

        if (buffering && error == null) androidx.compose.material3.LoadingIndicator(Modifier.align(Alignment.Center).size(56.dp))
        if (seekFlash.isNotEmpty()) Text(seekFlash, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(24.dp)).background(Color(0x99000000)).padding(horizontal = 20.dp, vertical = 10.dp))
        hud?.let { (l, v) ->
            Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 70.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xB0000000)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(l, color = Color.White, style = MaterialTheme.typography.labelLarge)
                LinearProgressIndicator(progress = { v }, modifier = Modifier.padding(top = 8.dp).size(width = 120.dp, height = 6.dp), drawStopIndicator = {})
            }
        }
        error?.let {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(it, color = Color.White, style = MaterialTheme.typography.titleMedium)
                TextButton(onClose) { Text("Go back") }
            }
        }

        PAnim(show, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000))) {
                // top bar
                Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClose) { Icon("arrow_back", "Back", tint = Color.White) }
                    Text(video.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton({ if (subUri == null) pickSub.launch(arrayOf("*/*")) else subs = !subs }) { Icon("closed_caption", "Subtitles", tint = if (subUri != null && subs) MaterialTheme.colorScheme.primary else Color.White) }
                    IconButton({ dialog = 1 }) { Icon("headphones", "Audio output", tint = Color.White) }
                    IconButton({ dialog = 2 }) { Icon("speed", "Speed", tint = Color.White) }
                }
                // center
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ prev?.let(onSelect) }, enabled = prev != null) { Icon("skip_previous", "Previous", Modifier.size(36.dp), tint = Color.White.copy(alpha = if (prev != null) 1f else 0.35f)) }
                    FilledIconButton({ if (player.isPlaying) player.pause() else player.play(); tick++ }, Modifier.size(76.dp)) {
                        Icon(if (isPlaying) "pause" else "play_arrow", "Play/Pause", Modifier.size(42.dp))
                    }
                    IconButton({ next?.let(onSelect) }, enabled = next != null) { Icon("skip_next", "Next", Modifier.size(36.dp), tint = Color.White.copy(alpha = if (next != null) 1f else 0.35f)) }
                }
                // bottom
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fmt(scrub?.let { (it * dur).toLong() } ?: pos), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = scrub ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                            onValueChange = { scrub = it },
                            onValueChangeFinished = { scrub?.let { player.seekTo((it * dur).toLong()) }; scrub = null },
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        Text(fmt(dur), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        IconButton({ fit = (fit + 1) % 3; Toast.makeText(ctx, listOf("Fit", "Zoom", "Stretch")[fit], Toast.LENGTH_SHORT).show() }) { Icon("aspect_ratio", "Fit", tint = Color.White) }
                        IconButton({ orient = (orient + 1) % 3; Toast.makeText(ctx, listOf("Auto rotation", "Portrait", "Landscape")[orient], Toast.LENGTH_SHORT).show() }) { Icon("screen_rotation", "Rotation", tint = Color.White) }
                    }
                }
            }
        }
    }

    if (dialog == 1) AudioOutputDialog(am, player, prefs.onLost) { dialog = 0 }
    if (dialog == 2) Dialog({ dialog = 0 }) {
        Column(Modifier.clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Playback speed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            SPEEDS.forEach { s ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { speed = s; player.setPlaybackSpeed(s); scope.launch { store.put(com.pebble.player.data.K.speed, s) }; dialog = 0 }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${s}x".replace(".0x", "x"), Modifier.weight(1f))
                    if (s == speed) Icon("check", null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
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
