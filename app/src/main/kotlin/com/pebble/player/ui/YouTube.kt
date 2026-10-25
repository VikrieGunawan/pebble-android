package com.pebble.player.ui

import android.content.ClipboardManager
import kotlin.math.roundToInt
import com.pebble.player.data.Prefs
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import android.os.SystemClock
import android.media.AudioManager
import android.content.pm.ActivityInfo
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.animateDpAsState
import androidx.activity.compose.BackHandler
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.pebble.player.data.Video
import com.pebble.player.data.YtStore
import kotlinx.coroutines.delay

private val YT_ERRORS = mapOf(
    2 to "The request contains an invalid parameter value.",
    5 to "The requested content cannot be played in an HTML5 player, or another error related to the HTML5 player has occurred.",
    100 to "The video requested was not found. It has been removed or marked as private.",
    101 to "The owner of the requested video does not allow it to be played in embedded players.",
    150 to "The owner of the requested video does not allow it to be played in embedded players.",
    153 to "YouTube player configuration error: it does not accept this page address.",
)

private class YtBridge(
    private val cb: (Int) -> Unit, private val q: (List<String>, String) -> Unit, private val st: (Int, Double, Double) -> Unit,
) {
    private val h = Handler(Looper.getMainLooper())
    @JavascriptInterface fun error(code: Int) { h.post { cb(code) } }
    @JavascriptInterface fun quality(levels: String, cur: String) { h.post { q(levels.split(',').filter { it.isNotBlank() }, cur) } }
    @JavascriptInterface fun state(s: Int, t: Double, d: Double) { h.post { st(s, t, d) } }
}

/** YouTube's quality ids -> (label, hint), highest first. */
private val YT_Q = linkedMapOf(
    "hd2160" to ("2160p" to "4K Ultra HD"), "hd1440" to ("1440p" to "Quad HD"), "hd1080" to ("1080p" to "Full HD"), "hd720" to ("720p" to "HD"),
    "large" to ("480p" to "Standard"), "medium" to ("360p" to "Data saver"), "small" to ("240p" to "Low"), "tiny" to ("144p" to "Lowest"),
)

private fun ytHtml(id: String, origin: String) = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p,iframe{width:100%;height:100%;border:0}</style></head>
<body><div id="p"></div><script src="https://www.youtube.com/iframe_api"></script><script>
var P,sel='auto';
function tick(){try{Android.state(P.getPlayerState(),P.getCurrentTime()||0,P.getDuration()||0)}catch(x){}}
function rep(){try{Android.quality(P.getAvailableQualityLevels().join(','),P.getPlaybackQuality())}catch(x){}}
function lock(){if(sel=='auto')return;try{P.setPlaybackQualityRange(sel,sel);P.setPlaybackQuality(sel)}catch(x){}}
function setQ(q){sel=q;if(q=='auto'){try{P.setPlaybackQualityRange('tiny','highres')}catch(x){}}else{lock();setTimeout(lock,800)}setTimeout(rep,1200)}
var n=0;setInterval(function(){tick();if(++n%5==0){lock();rep()}},400);
function onYouTubeIframeAPIReady(){P=new YT.Player('p',{width:'100%',height:'100%',videoId:'$id',
playerVars:{controls:0,disablekb:1,playsinline:1,autoplay:1,rel:0,modestbranding:1,fs:0,iv_load_policy:3,origin:'$origin'},
events:{onError:function(e){Android.error(e.data)},onReady:rep,onPlaybackQualityChange:rep}})}
</script></body></html>"""

@Composable
private fun YtWebView(
    id: String, server: String, modifier: Modifier,
    onError: (Int) -> Unit, onQuality: (List<String>, String) -> Unit, onState: (Int, Double, Double) -> Unit, onWeb: (WebView) -> Unit,
) {
    val cb by rememberUpdatedState(onError)
    val qb by rememberUpdatedState(onQuality)
    val sb by rememberUpdatedState(onState)
    AndroidView(
        factory = { c ->
            val w = WebView(c)
            w.settings.javaScriptEnabled = true
            w.settings.domStorageEnabled = true
            w.settings.mediaPlaybackRequiresUserGesture = false
            w.webChromeClient = WebChromeClient()
            w.webViewClient = WebViewClient()
            w.addJavascriptInterface(YtBridge({ code -> cb(code) }, { l, c2 -> qb(l, c2) }, { s, t, d -> sb(s, t, d) }), "Android")
            onWeb(w)
            // The server address is the page origin YouTube sees, exactly like opening the website from it.
            w.loadDataWithBaseURL("$server/", ytHtml(id, YtStore.origin(server)), "text/html", "utf-8", null)
            w
        },
        onRelease = { it.destroy() },
        modifier = modifier,
    )
}

private fun clock(s: Double): String {
    val t = s.toInt().coerceAtLeast(0)
    return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, t % 3600 / 60, t % 60) else "%d:%02d".format(t / 60, t % 60)
}

/** Plays a YouTube video with the same controls and menus as the local player. Needs the user's server (localhost / tunnel) to be reachable. */
@Composable
fun YouTubeScreen(v: Video, queue: List<Video>, prefs: Prefs, onSelect: (Video) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val act = remember { ctx.activity() }
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val id = YtStore.idOf(v)
    val server = YtStore.server
    val idx = queue.indexOfFirst { it.id == v.id }
    val prev = queue.getOrNull(idx - 1)
    val next = queue.getOrNull(idx + 1)
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember(attempt) { mutableIntStateOf(0) } // 0 checking, 1 connected, 2 not connected
    var ytErr by remember(attempt) { mutableIntStateOf(0) }
    LaunchedEffect(attempt) { state = if (YtStore.ping(server)) 1 else 2 }
    var web by remember { mutableStateOf<WebView?>(null) }
    var levels by remember(attempt) { mutableStateOf<List<String>>(emptyList()) }
    var qNow by remember(attempt) { mutableStateOf("") }
    var qsel by remember(attempt) { mutableStateOf("auto") }
    var ps by remember(attempt) { mutableIntStateOf(-1) }
    var pos by remember(attempt) { mutableStateOf(0.0) }
    var dur by remember(attempt) { mutableStateOf(0.0) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    var speed by remember { mutableStateOf(1f) }
    var loop by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var orient by remember { mutableIntStateOf(0) }
    var sleepEnd by remember { mutableLongStateOf(0L) }
    var nowMs by remember { mutableLongStateOf(0L) }
    var show by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    var panel by remember { mutableStateOf<String?>(null) }
    var lastPanel by remember { mutableStateOf("quality") }
    LaunchedEffect(panel) { panel?.let { lastPanel = it } }
    val playing = ps == 1
    val ok = state == 1 && ytErr == 0
    fun js(s: String) { web?.evaluateJavascript(s, null) }
    fun seekBy(d: Int) { js("P.seekTo(Math.max(0,P.getCurrentTime()+($d)),true)"); tick++ }
    fun togglePanel(p: String) { panel = if (panel == p) null else p; tick++ }
    fun applySpeed(x: Float) { val s = ((x.coerceIn(0.25f, 2f)) * 4).roundToInt() / 4f; speed = s; js("P.setPlaybackRate($s)") }
    fun enterPip() {
        try {
            show = false; panel = null
            act?.enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(16, 9)).build())
        } catch (_: Exception) { Toast.makeText(ctx, "Picture-in-picture isn't available", Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(show, playing, panel, tick) { if (show && playing && panel == null) { delay(3200); show = false } }
    LaunchedEffect(orient) {
        act?.requestedOrientation = when (orient) { 1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT; 2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE; else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    DisposableEffect(Unit) { onDispose { act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED } }
    LaunchedEffect(ps) {
        if (ps == 0) {
            if (loop) js("P.seekTo(0,true);P.playVideo()") else if (prefs.autoplay) next?.let(onSelect)
        }
    }
    LaunchedEffect(sleepEnd) {
        while (sleepEnd > 0) {
            nowMs = SystemClock.elapsedRealtime()
            if (nowMs >= sleepEnd) { js("P.pauseVideo()"); sleepEnd = 0L; Toast.makeText(ctx, "Sleep timer ended", Toast.LENGTH_SHORT).show(); break }
            delay(1000)
        }
    }
    BackHandler(panel != null) { panel = null }
    val base = listOf(
        "Video" to v.name, "Video ID" to id, "Server" to server.ifBlank { "(not set)" },
        "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", "Device" to "${Build.MANUFACTURER} ${Build.MODEL}", "App" to currentVersion(ctx),
    )
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { } }) {
        val land = maxWidth > maxHeight
        val side by animateDpAsState(if (land && panel != null) 340.dp else 0.dp, label = "ytSide")
        val endPad = side.coerceAtLeast(0.dp)
        if (ok) Box(Modifier.fillMaxSize().padding(end = endPad).statusBarsPadding().navigationBarsPadding()) {
            YtWebView(id, server, Modifier.fillMaxSize(), { ytErr = it }, { l, c -> levels = l; qNow = c }, { s, t, d -> ps = s; if (scrub == null) pos = t; dur = d }, { web = it })
            // taps: show/hide controls, double-tap to skip
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (panel != null) panel = null else { show = !show; tick++ } },
                        onDoubleTap = { o -> seekBy(if (o.x < size.width / 2) -prefs.skip else prefs.skip); show = true },
                    )
                },
            )
        }
        if (state == 0 || (ok && ps == 3)) LoadingIndicator(Modifier.align(Alignment.Center).padding(end = endPad).size(56.dp))
        PAnim(ok && show, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                        .statusBarsPadding().padding(start = 8.dp, end = 8.dp + endPad, top = 6.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PBtn("arrow_back", "Back", 40.dp, onClick = onClose)
                    Text(v.name, Modifier.weight(1f).padding(horizontal = 4.dp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    PBtn("screen_rotation", "Rotate", 40.dp, bg = if (orient != 0) cs.primary else Color.Transparent, tint = if (orient != 0) cs.onPrimary else Color.White,
                        onClick = { orient = (orient + 1) % 3; Toast.makeText(ctx, ORIENTS[orient], Toast.LENGTH_SHORT).show() })
                    PBtn("queue_music", "Queue", 40.dp, bg = if (panel == "queue") cs.primary else Color.Transparent, tint = if (panel == "queue") cs.onPrimary else Color.White, onClick = { togglePanel("queue") })
                }
                Row(Modifier.align(Alignment.Center).padding(end = endPad), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    PBtn("skip_previous", "Previous", 40.dp, enabled = prev != null, onClick = { prev?.let(onSelect) })
                    PBtn("replay_10", "Back", 44.dp, onClick = { seekBy(-prefs.skip) })
                    Box(
                        Modifier.padding(horizontal = 6.dp).size(58.dp).clip(RoundedCornerShape(if (playing) 20.dp else 29.dp)).background(cs.primary)
                            .clickable { if (ps == 0) js("P.seekTo(0,true);P.playVideo()") else js(if (playing) "P.pauseVideo()" else "P.playVideo()"); tick++ },
                        contentAlignment = Alignment.Center,
                    ) { Icon(if (playing) "pause" else "play_arrow", "Play or pause", Modifier.size(32.dp), tint = cs.onPrimary) }
                    PBtn("forward_10", "Forward", 44.dp, onClick = { seekBy(prefs.skip) })
                    PBtn("skip_next", "Next", 40.dp, enabled = next != null, onClick = { next?.let(onSelect) })
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000))))
                        .navigationBarsPadding().padding(start = 16.dp, end = 16.dp + endPad, top = 24.dp, bottom = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(clock(scrub?.let { it * dur } ?: pos), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        PebbleSlider(
                            value = scrub ?: if (dur > 0) (pos / dur).toFloat().coerceIn(0f, 1f) else 0f,
                            onValueChange = { scrub = it; tick++ },
                            onValueChangeFinished = { scrub?.let { js("P.seekTo(${it * dur},true)"); pos = it * dur }; scrub = null },
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        Text(clock(dur), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IBtn("graphic_eq", "Audio", panel == "audio") { togglePanel("audio") }
                        IBtn("speed", "Playback speed", panel == "speed" || speed != 1f, if (speed != 1f) fmtSpeed(speed) else null) { togglePanel("speed") }
                        IBtn("repeat", "Loop", loop) { loop = !loop }
                        IBtn("timer", "Sleep timer", panel == "sleep" || sleepEnd > 0) { togglePanel("sleep") }
                        IBtn("picture_in_picture_alt", "Pop-out", false) { enterPip() }
                        IBtn("high_quality", "Video quality", panel == "quality", YT_Q[qNow]?.first) { togglePanel("quality") }
                    }
                }
            }
        }
        PAnim(
            ok && panel != null,
            Modifier.align(if (land) Alignment.CenterEnd else Alignment.BottomCenter),
            enter = if (land) slideInHorizontally { it } + fadeIn() else slideInVertically { it } + fadeIn(),
            exit = if (land) slideOutHorizontally { it } + fadeOut() else slideOutVertically { it } + fadeOut(),
        ) {
            val shown = panel ?: lastPanel
            val (pIcon, pTitle, pSub) = when (shown) {
                "speed" -> Triple("speed", "Playback speed", if (speed == 1f) "Normal" else fmtSpeed(speed))
                "audio" -> Triple("graphic_eq", "Audio", "Volume and sound")
                "queue" -> Triple("queue_music", "Up next", "${queue.size} videos")
                "quality" -> Triple("high_quality", "Video quality", if (qNow.isNotEmpty()) "Now playing ${YT_Q[qNow]?.first ?: qNow}" else "Start playing to load the list")
                else -> Triple("timer", "Sleep timer", if (sleepEnd > 0) "Pausing in ${((sleepEnd - nowMs) / 60000 + 1).coerceAtLeast(1)} min" else "Off")
            }
            Column(
                (if (land) Modifier.statusBarsPadding().navigationBarsPadding().padding(12.dp).width(320.dp).fillMaxHeight()
                else Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp).heightIn(max = maxHeight * 0.6f))
                    .clip(RoundedCornerShape(28.dp)).background(GLASS)
                    .pointerInput(Unit) { detectTapGestures { } }.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(14.dp)).background(cs.primary), contentAlignment = Alignment.Center) { Icon(pIcon, null, Modifier.size(22.dp), tint = cs.onPrimary) }
                    Column(Modifier.weight(1f)) {
                        Text(pTitle, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        Text(pSub, color = Color(0xB3FFFFFF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    PBtn("close", "Close", 36.dp, onClick = { panel = null })
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(if (shown == "queue") 6.dp else 12.dp)) {
                    when (shown) {
                        "queue" -> queue.forEachIndexed { i, q ->
                            val cur = q.id == v.id
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
                                    Text("${i + 1} · ${q.folder}", color = Color(0xB3FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                                if (cur) Icon("play_arrow", null, Modifier.size(22.dp), tint = cs.primary)
                            }
                        }
                        "speed" -> {
                            Text(fmtSpeed(speed), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PBtn("remove", "Slower", 44.dp, bg = SOFT, onClick = { applySpeed(speed - 0.25f) })
                                PebbleSlider(speed, { applySpeed(it) }, Modifier.weight(1f), valueRange = 0.25f..2f)
                                PBtn("add", "Faster", 44.dp, bg = SOFT, onClick = { applySpeed(speed + 0.25f) })
                            }
                            Sl("tune", "Presets")
                            val pre = PRESETS.filter { it <= 2f }
                            Chips(pre.map { fmtSpeed(it) }, pre.indexOfFirst { kotlin.math.abs(it - speed) < 0.01f }) { applySpeed(pre[it]) }
                            Wide("restart_alt", "Normal speed") { applySpeed(1f) }
                            Text("YouTube tops out at 2×.", color = Color(0xB3FFFFFF), fontSize = 12.sp)
                        }
                        "audio" -> {
                            var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)) }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PBtn(if (muted || vol <= 0f) "volume_off" else "volume_up", "Mute", 52.dp, bg = if (muted) cs.primary else SOFT, tint = if (muted) cs.onPrimary else Color.White,
                                    onClick = { muted = !muted; js(if (muted) "P.mute()" else "P.unMute()") })
                                Column {
                                    Text("${(vol * 100).roundToInt()}%", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                                    Text(if (muted) "Muted" else "Volume", color = Color(0xB3FFFFFF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            PebbleSlider(vol, { vol = it; am.setStreamVolume(AudioManager.STREAM_MUSIC, (it * am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).roundToInt(), 0) }, Modifier.fillMaxWidth())
                            Text("Sound boost and presets can't change YouTube's audio. Use your device volume instead.", color = Color(0xB3FFFFFF), fontSize = 12.sp)
                        }
                        "quality" -> {
                            (listOf("auto") + YT_Q.keys.filter { it in levels }).forEach { q ->
                                YRow(
                                    if (q == "auto") "Auto" else YT_Q[q]?.first ?: q, if (q == "auto") "Best for your connection" else YT_Q[q]?.second ?: "",
                                    qsel == q, q == qNow,
                                ) { qsel = q; js("setQ('$q')") }
                            }
                            Text("YouTube only offers the sizes this video has.", color = Color(0xB3FFFFFF), fontSize = 12.sp)
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
        if (state == 2) ErrorPage(
            "YT_SERVER_OFFLINE", "0",
            if (server.isBlank()) "No server is set. YouTube only plays while the app is connected to your localhost or tunnel (Settings → YouTube)."
            else "Can't reach your server, so YouTube can't play. Start your localhost (for example in Termux) or tunnel, then try again.",
            base, { attempt++ }, onClose,
        )
        if (ytErr != 0) ErrorPage(
            "YT_ERROR_$ytErr", ytErr.toString(),
            if (ytErr in 150..153 && YtStore.isBadIp(server)) "YouTube refuses pages opened from an IP address (${YtStore.origin(server)}). Use a host name instead, for example localhost, a tunnel link, or ${Regex("//([^:/]+)").find(server)?.groupValues?.get(1)?.replace('.', '-')}.sslip.io."
            else YT_ERRORS[ytErr] ?: "YouTube reported error $ytErr.",
            base, { attempt++ }, onClose,
        )
    }
}

@Composable
private fun YRow(title: String, sub: String, sel: Boolean, playing: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (sel) cs.primary.copy(alpha = 0.28f) else SOFT).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.ExtraBold, color = Color.White)
            if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = Color(0xB3FFFFFF))
        }
        if (playing) Text("Playing", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = cs.onPrimary, modifier = Modifier.clip(RoundedCornerShape(50)).background(cs.primary).padding(horizontal = 8.dp, vertical = 3.dp))
        if (sel) Icon("check", null, Modifier.size(20.dp), tint = cs.primary)
    }
}

/** "Add YouTube video" dialog, like the website's. */
@Composable
fun AddYoutubeDialog(onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var link by remember { mutableStateOf("") }
    val id = remember(link) { YtStore.parseId(link) }
    val short = remember(link) { YtStore.isShortLink(link) }
    var meta by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    val have = id != null && YtStore.has(id)
    LaunchedEffect(id) {
        meta = null
        if (id != null) { delay(300); meta = YtStore.oembed(id, short) }
    }
    val thumb by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, id) { value = null; if (id != null) value = YtStore.thumb(id)?.asImageBitmap() }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(cs.surfaceContainerHigh).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile("smart_display", 46.dp, filled = true)
                Column {
                    Text("Add YouTube video", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Paste a video or Short link", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            OutlinedTextField(
                link, { link = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(18.dp),
                placeholder = { Text("https://youtu.be/…") },
                leadingIcon = { Icon("link", null, Modifier.size(22.dp)) },
                trailingIcon = {
                    Row(
                        Modifier.padding(end = 8.dp).clip(RoundedCornerShape(12.dp)).clickable {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            link = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim() ?: link
                        }.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) { Icon("content_paste", null, Modifier.size(18.dp), tint = cs.primary); Text("Paste", color = cs.primary, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            )
            if (link.isNotBlank() && id == null) Text("That doesn't look like a YouTube link.", color = cs.error, style = MaterialTheme.typography.bodySmall)
            if (id != null) Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainer).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.width(110.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHigh)) {
                    thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(meta?.first ?: "Loading…", fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(meta?.second ?: "", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
                    Text(
                        if (have) "Already in your library" else if (meta?.third ?: short) "Goes to the Shorts folder" else "Goes to the YouTube folder",
                        style = MaterialTheme.typography.labelSmall, color = cs.primary, fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Cancel", null, false, Modifier.weight(1f), onDismiss)
                Box(Modifier.weight(1f).background(Color.Transparent)) {
                    Btn("Add", "check", id != null && !have) {
                        val i = id ?: return@Btn
                        if (have) return@Btn
                        val sh = meta?.third ?: short
                        YtStore.add(ctx, YtStore.Item(i, meta?.first ?: "YouTube video", meta?.second ?: "", sh, System.currentTimeMillis()))
                        Toast.makeText(ctx, if (sh) "Added to the Shorts folder" else "Added to the YouTube folder", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                }
            }
        }
    }
}

/** Settings → YouTube: which server YouTube plays through, and whether it's connected. */
@Composable
fun YoutubePage() {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(YtStore.server) }
    var state by remember { mutableIntStateOf(0) } // 0 idle, 1 testing, 2 connected, 3 not connected
    var run by remember { mutableIntStateOf(1) }
    LaunchedEffect(run) {
        if (YtStore.server.isBlank()) { state = 0; return@LaunchedEffect }
        state = 1
        state = if (YtStore.ping(YtStore.server)) 2 else 3
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cs.surfaceContainerHigh).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconTile(if (state == 2) "cloud_done" else if (state == 3) "cloud_off" else "dns", 46.dp, filled = state == 2)
            Column(Modifier.weight(1f)) {
                Text(
                    when (state) { 1 -> "Checking…"; 2 -> "Connected"; 3 -> "Not connected"; else -> "No server set" },
                    fontWeight = FontWeight.ExtraBold, color = if (state == 3) cs.error else cs.onSurface,
                )
                Text(
                    if (YtStore.server.isBlank()) "YouTube plays only while connected" else YtStore.server,
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (state == 1) LoadingIndicator(Modifier.size(32.dp))
        }
        OutlinedTextField(
            text, { text = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(18.dp),
            label = { Text("Server address") }, placeholder = { Text("http://localhost:8080") },
            leadingIcon = { Icon("link", null, Modifier.size(22.dp)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        )
        if (YtStore.isBadIp(YtStore.normalize(text))) Text(
            "YouTube refuses pages opened from an IP address. Use localhost, a tunnel link, or a name like 192-168-1-5.sslip.io.",
            style = MaterialTheme.typography.bodySmall, color = cs.error,
        )
        Btn("Save & test connection", "refresh", true) { YtStore.setServer(ctx, text); text = YtStore.server; run++ }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainer).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("HOW IT WORKS", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = cs.onSurfaceVariant, letterSpacing = 0.8.sp)
            Text("YouTube only accepts players that open from a real web address. Start your server (Termux or anywhere), or a tunnel, and enter its address here.", style = MaterialTheme.typography.bodyMedium)
            Text("Any server works: localhost, Cloudflare, Serveo and others. If the server is off, YouTube videos won't play.", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(4.dp))
    }
}
