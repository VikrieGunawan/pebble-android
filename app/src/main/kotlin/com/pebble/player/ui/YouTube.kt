package com.pebble.player.ui

import android.content.ClipboardManager
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
            w.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
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

/** Plays a YouTube video with Pebble's own controls. Needs the user's server (localhost / tunnel) to be reachable. */
@Composable
fun YouTubeScreen(v: Video, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val id = YtStore.idOf(v)
    val server = YtStore.server
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
    var drag by remember { mutableStateOf<Float?>(null) }
    var speed by remember { mutableStateOf(1f) }
    var show by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    var panel by remember { mutableStateOf<String?>(null) }
    var lastPanel by remember { mutableStateOf("quality") }
    LaunchedEffect(panel) { panel?.let { lastPanel = it } }
    val playing = ps == 1
    val ok = state == 1 && ytErr == 0
    LaunchedEffect(show, playing, panel, tick) { if (show && playing && panel == null) { delay(3200); show = false } }
    BackHandler(panel != null) { panel = null }
    fun js(s: String) { web?.evaluateJavascript(s, null) }
    fun seekBy(d: Int) { js("P.seekTo(Math.max(0,P.getCurrentTime()+($d)),true)"); tick++ }
    val base = listOf(
        "Video" to v.name, "Video ID" to id, "Server" to server.ifBlank { "(not set)" },
        "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", "Device" to "${Build.MANUFACTURER} ${Build.MODEL}", "App" to currentVersion(ctx),
    )
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { } }) {
        val land = maxWidth > maxHeight
        val side by animateDpAsState(if (land && panel != null) 340.dp else 0.dp, label = "ytSide")
        val endPad = side.coerceAtLeast(0.dp)
        if (ok) Box(Modifier.fillMaxSize().padding(end = endPad).statusBarsPadding().navigationBarsPadding()) {
            YtWebView(id, server, Modifier.fillMaxSize(), { ytErr = it }, { l, c -> levels = l; qNow = c }, { s, t, d -> ps = s; if (drag == null) pos = t; dur = d }, { web = it })
            // taps: show/hide controls, double-tap to skip 10 s
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (panel != null) panel = null else { show = !show; tick++ } },
                        onDoubleTap = { o -> seekBy(if (o.x < size.width / 2) -10 else 10); show = true },
                    )
                },
            )
        }
        if (state == 0 || (ok && ps == 3)) LoadingIndicator(Modifier.align(Alignment.Center).padding(end = endPad).size(56.dp))
        PAnim(ok && show, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(110.dp).background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))))
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(170.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000)))))
                Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 8.dp + endPad, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    YBtn("arrow_back", "Back", 44.dp, onClick = onClose)
                    Text(v.name, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.align(Alignment.Center).padding(end = endPad), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    YBtn("replay_10", "Back 10 seconds", 48.dp) { seekBy(-10) }
                    YBtn(if (playing) "pause" else "play_arrow", if (playing) "Pause" else "Play", 68.dp, bg = cs.primary, tint = cs.onPrimary) {
                        if (ps == 0) js("P.seekTo(0,true);P.playVideo()") else js(if (playing) "P.pauseVideo()" else "P.playVideo()"); tick++
                    }
                    YBtn("forward_10", "Forward 10 seconds", 48.dp) { seekBy(10) }
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp + endPad, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(clock(if (drag != null) drag!! * dur else pos), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        PebbleSlider(
                            drag ?: if (dur > 0) (pos / dur).toFloat().coerceIn(0f, 1f) else 0f,
                            { drag = it; tick++ }, Modifier.weight(1f),
                            onValueChangeFinished = { drag?.let { js("P.seekTo(${it * dur},true)"); pos = it * dur }; drag = null },
                        )
                        Text(clock(dur), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        YPill("speed", if (speed != 1f) "${speed}×".replace(".0×", "×") else "Speed", panel == "speed") { panel = if (panel == "speed") null else "speed"; tick++ }
                        Spacer(Modifier.width(8.dp))
                        YPill("high_quality", YT_Q[qNow]?.first ?: "Quality", panel == "quality") { panel = if (panel == "quality") null else "quality"; tick++ }
                    }
                }
            }
        }
        // menu from the right, like the website's other player menus
        PAnim(
            ok && panel != null, Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            Column(
                Modifier.width(if (land) 340.dp else maxWidth * 0.86f).fillMaxHeight().clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                    .background(Color(0xF00B0814)).pointerInput(Unit) { detectTapGestures { } }.statusBarsPadding().navigationBarsPadding().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconTile(if (lastPanel == "speed") "speed" else "high_quality", 40.dp, filled = true)
                    Column(Modifier.weight(1f)) {
                        Text(if (lastPanel == "speed") "Playback speed" else "Video quality", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        if (lastPanel != "speed") Text(if (qNow.isNotEmpty()) "Now playing ${YT_Q[qNow]?.first ?: qNow}" else "Start playing to load the list", color = Color(0xB3FFFFFF), fontSize = 12.sp)
                    }
                    YBtn("close", "Close", 36.dp) { panel = null }
                }
                Column(Modifier.weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (lastPanel == "speed") listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { r ->
                        YRow(if (r == 1f) "Normal" else "${r}×", if (r == 1f) "1×" else "", speed == r, false) { speed = r; js("P.setPlaybackRate($r)") }
                    } else (listOf("auto") + YT_Q.keys.filter { it in levels }).forEach { q ->
                        YRow(
                            if (q == "auto") "Auto" else YT_Q[q]?.first ?: q, if (q == "auto") "Best for your connection" else YT_Q[q]?.second ?: "",
                            qsel == q, q == qNow,
                        ) { qsel = q; js("setQ('$q')") }
                    }
                    if (lastPanel != "speed") Text("YouTube only offers the sizes this video has.", color = Color(0xB3FFFFFF), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
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
private fun YBtn(icon: String, desc: String, size: androidx.compose.ui.unit.Dp, bg: Color = Color(0x55000000), tint: Color = Color.White, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, Modifier.size(size * 0.5f), tint = tint)
    }
}

@Composable
private fun YPill(icon: String, label: String, on: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).background(if (on) cs.primary else Color(0x55000000)).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (on) cs.onPrimary else Color.White)
        Text(label, color = if (on) cs.onPrimary else Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
    }
}

@Composable
private fun YRow(title: String, sub: String, sel: Boolean, playing: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (sel) cs.primaryContainer else Color(0x14FFFFFF)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.ExtraBold, color = if (sel) cs.onPrimaryContainer else Color.White)
            if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = if (sel) cs.onPrimaryContainer else Color(0xB3FFFFFF))
        }
        if (playing) Text("Playing", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = cs.onPrimary, modifier = Modifier.clip(RoundedCornerShape(50)).background(cs.primary).padding(horizontal = 8.dp, vertical = 3.dp))
        if (sel) Icon("check", null, Modifier.size(20.dp), tint = cs.onPrimaryContainer)
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
