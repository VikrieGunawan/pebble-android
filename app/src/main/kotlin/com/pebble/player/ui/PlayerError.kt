package com.pebble.player.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaLibraryInfo
import androidx.media3.common.PlaybackException
import com.pebble.player.data.Video

/** The website's "An Error has Occurred" screen, filled with the real error Media3 reports. */
@Composable
fun PlayerErrorScreen(e: PlaybackException, v: Video, onRetry: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val appVer = remember { currentVersion(ctx) }
    val cause = e.cause?.let { "${it.javaClass.simpleName}: ${it.message ?: "-"}" } ?: "-"
    val details = listOf(
        "Video" to v.name, "Format" to v.ext.ifEmpty { "-" }, "Size" to fmtSize(v.size), "Folder" to v.path.ifEmpty { v.folder },
        "Error" to e.errorCodeName, "Code" to e.errorCode.toString(), "Cause" to cause,
        "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", "Device" to "${Build.MANUFACTURER} ${Build.MODEL}",
        "Player" to "Media3 ${MediaLibraryInfo.VERSION}", "App" to appVer,
    )
    ErrorPage(e.errorCodeName, e.errorCode.toString(), e.message, details, onRetry, onClose)
}

/** Same screen for any source (local file, YouTube...). */
@Composable
fun ErrorPage(name: String, code: String, message: String?, details: List<Pair<String, String>>, onRetry: () -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val dim = Color(0xB3FFFFFF)

    val head: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0x33FF6B6B)), contentAlignment = Alignment.Center) {
                Icon("error", null, Modifier.size(38.dp), tint = Color(0xFFFF6B6B))
            }
            Spacer(Modifier.height(4.dp))
            Text("An Error has Occurred", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(name, color = cs.primary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text("Error code: $code", color = dim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (!message.isNullOrBlank()) Text(message, color = dim, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.height(46.dp).clip(RoundedCornerShape(16.dp)).background(cs.primary).clickable(onClick = onRetry).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) { Icon("refresh", null, Modifier.size(20.dp), tint = cs.onPrimary); Text("Try again", color = cs.onPrimary, fontWeight = FontWeight.ExtraBold) }
                Row(
                    Modifier.height(46.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x26FFFFFF)).clickable(onClick = onClose).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) { Icon("arrow_back", null, Modifier.size(20.dp), tint = Color.White); Text("Go back", color = Color.White, fontWeight = FontWeight.ExtraBold) }
            }
        }
    }
    val info: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("DETAILS", color = dim, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
            details.forEach { (k, value) ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(k, Modifier.width(72.dp), color = dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Text(value, Modifier.weight(1f), color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF0B0814)).pointerInput(Unit) { detectTapGestures { } }) {
        if (maxWidth > maxHeight) Row(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 32.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) { head() }
            Box(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), contentAlignment = Alignment.CenterStart) { info() }
        } else Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp),
        ) { head(); info() }
    }
}
