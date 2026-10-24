package com.pebble.player.ui

import android.content.ClipboardManager
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

private class YtBridge(private val cb: (Int) -> Unit, private val q: (List<String>, String) -> Unit) {
    private val h = Handler(Looper.getMainLooper())
    @JavascriptInterface fun error(code: Int) { h.post { cb(code) } }
    @JavascriptInterface fun quality(levels: String, cur: String) { h.post { q(levels.split(',').filter { it.isNotBlank() }, cur) } }
}

/** YouTube's quality ids -> (label, hint), highest first. */
private val YT_Q = linkedMapOf(
    "hd2160" to ("2160p" to "4K Ultra HD"), "hd1440" to ("1440p" to "Quad HD"), "hd1080" to ("1080p" to "Full HD"), "hd720" to ("720p" to "HD"),
    "large" to ("480p" to "Standard"), "medium" to ("360p" to "Data saver"), "small" to ("240p" to "Low"), "tiny" to ("144p" to "Lowest"),
)

private fun ytHtml(id: String, origin: String) = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{width:100%;height:100%}</style></head>
<body><div id="p"></div><script src="https://www.youtube.com/iframe_api"></script><script>
var P,sel='auto';
function rep(){try{Android.quality(P.getAvailableQualityLevels().join(','),P.getPlaybackQuality())}catch(x){}}
function lock(){if(sel=='auto')return;try{P.setPlaybackQualityRange(sel,sel);P.setPlaybackQuality(sel)}catch(x){}}
function setQ(q){sel=q;if(q=='auto'){try{P.setPlaybackQualityRange('tiny','highres')}catch(x){}}else{lock();setTimeout(lock,800)}setTimeout(rep,1200)}
setInterval(function(){lock();rep()},2000);
function onYouTubeIframeAPIReady(){P=new YT.Player('p',{width:'100%',height:'100%',videoId:'$id',
playerVars:{playsinline:1,autoplay:1,rel:0,modestbranding:1,fs:0,iv_load_policy:3,origin:'$origin'},
events:{onError:function(e){Android.error(e.data)},onReady:rep,onPlaybackQualityChange:rep}})}
</script></body></html>"""

@Composable
private fun YtWebView(id: String, server: String, onError: (Int) -> Unit, onQuality: (List<String>, String) -> Unit, onWeb: (WebView) -> Unit) {
    val cb by rememberUpdatedState(onError)
    val qb by rememberUpdatedState(onQuality)
    AndroidView(
        factory = { c ->
            val w = WebView(c)
            w.setBackgroundColor(android.graphics.Color.BLACK)
            w.settings.javaScriptEnabled = true
            w.settings.domStorageEnabled = true
            w.settings.mediaPlaybackRequiresUserGesture = false
            w.webChromeClient = WebChromeClient()
            w.webViewClient = WebViewClient()
            w.addJavascriptInterface(YtBridge({ code -> cb(code) }, { l, c -> qb(l, c) }), "Android")
            onWeb(w)
            // The server address is the page origin YouTube sees, exactly like opening the website from it.
            w.loadDataWithBaseURL("$server/", ytHtml(id, YtStore.origin(server)), "text/html", "utf-8", null)
            w
        },
        onRelease = { it.destroy() },
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
    )
}

/** Plays a YouTube video. Needs the user's server (localhost / tunnel) to be reachable; otherwise shows the error screen. */
@Composable
fun YouTubeScreen(v: Video, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val id = YtStore.idOf(v)
    val server = YtStore.server
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember(attempt) { mutableIntStateOf(0) } // 0 checking, 1 connected, 2 not connected
    var ytErr by remember(attempt) { mutableIntStateOf(0) }
    LaunchedEffect(attempt) { state = if (YtStore.ping(server)) 1 else 2 }
    var web by remember { mutableStateOf<WebView?>(null) }
    var levels by remember(attempt) { mutableStateOf<List<String>>(emptyList()) }
    var playing by remember(attempt) { mutableStateOf("") }
    var qsel by remember(attempt) { mutableStateOf("auto") }
    var qOpen by remember { mutableStateOf(false) }
    val base = listOf(
        "Video" to v.name, "Video ID" to id, "Server" to server.ifBlank { "(not set)" },
        "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", "Device" to "${Build.MANUFACTURER} ${Build.MODEL}", "App" to currentVersion(ctx),
    )
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { } }) {
        if (state == 1 && ytErr == 0) YtWebView(id, server, { ytErr = it }, { l, c -> levels = l; playing = c }, { web = it })
        if (state == 0) LoadingIndicator(Modifier.align(Alignment.Center).size(56.dp))
        if (state == 1 && ytErr == 0) Box(
            Modifier.statusBarsPadding().padding(10.dp).size(44.dp).clip(CircleShape).background(Color(0x66000000)).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Icon("arrow_back", "Back", Modifier.size(24.dp), tint = Color.White) }
        if (state == 1 && ytErr == 0) Row(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(10.dp).height(44.dp).clip(RoundedCornerShape(22.dp)).background(Color(0x66000000))
                .clickable { qOpen = true }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon("high_quality", "Video quality", Modifier.size(22.dp), tint = Color.White)
            Text(YT_Q[playing]?.first ?: "Auto", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
        if (qOpen) QualitySheet(levels, playing, qsel, { q -> qsel = q; web?.evaluateJavascript("setQ('$q')", null); qOpen = false }) { qOpen = false }
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun QualitySheet(levels: List<String>, playing: String, sel: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val known = YT_Q.keys.filter { it in levels }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = cs.surfaceContainer) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile("high_quality", 46.dp, filled = true)
                Column {
                    Text("Video quality", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(if (playing.isNotEmpty()) "Now playing ${YT_Q[playing]?.first ?: playing}" else "Start playing to load the list", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (listOf("auto") + known).forEach { q ->
                    val on = sel == q
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (on) cs.primaryContainer else Color.Transparent).clickable { onPick(q) }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(if (q == "auto") "Auto" else YT_Q[q]?.first ?: q, fontWeight = FontWeight.ExtraBold, color = if (on) cs.onPrimaryContainer else cs.onSurface)
                            Text(if (q == "auto") "Best for your connection" else YT_Q[q]?.second ?: "", style = MaterialTheme.typography.bodySmall, color = if (on) cs.onPrimaryContainer else cs.onSurfaceVariant)
                        }
                        if (q == playing) Text("Playing", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = cs.onPrimary, modifier = Modifier.clip(RoundedCornerShape(50)).background(cs.primary).padding(horizontal = 8.dp, vertical = 3.dp))
                        if (on) Icon("check", null, Modifier.size(20.dp), tint = cs.onPrimaryContainer)
                    }
                }
            }
            Text("Your choice is kept while the video plays. YouTube only offers the sizes this video has.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}
