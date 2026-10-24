package com.pebble.player.ui

import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pebble.player.data.Prefs
import com.pebble.player.data.Store
import com.pebble.player.data.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings → Data: storage overview, counters, clean-up actions (tap twice to confirm) and the danger zone. */
@Composable
fun DataPage(prefs: Prefs, store: Store, all: List<Video>, onClearAll: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val coverVer = ThumbCache.ver.intValue
    var appBytes by remember { mutableLongStateOf(0L) }
    var free by remember { mutableLongStateOf(0L) }
    var total by remember { mutableLongStateOf(1L) }
    var covers by remember { mutableIntStateOf(0) }
    LaunchedEffect(coverVer, prefs.progress.size, prefs.favs.size) {
        withContext(Dispatchers.IO) {
            val st = StatFs(Environment.getDataDirectory().path)
            free = st.availableBytes
            total = st.totalBytes.coerceAtLeast(1L)
            appBytes = listOf(ctx.filesDir, ctx.cacheDir).sumOf { d -> d.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
            covers = app.media.coverFile(0L).parentFile?.listFiles()?.size ?: 0
        }
    }
    val used = ((total - free).toFloat() / total).coerceIn(0f, 1f)
    val frac by animateFloatAsState(used, tween(700), label = "storage")

    Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile("database", 46.dp, filled = true)
                Column {
                    Text("Device storage", fontWeight = FontWeight.ExtraBold)
                    Text("${fmtSize(free)} free of ${fmtSize(total)}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(cs.surfaceContainerHighest)) {
                Box(Modifier.fillMaxWidth(frac.coerceIn(0.02f, 1f)).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(cs.primary, cs.tertiary))))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon("info", null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                Text("Pebble keeps ${fmtSize(appBytes)} on this phone", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("history", prefs.progress.size, "In history", Modifier.weight(1f))
            Stat("favorite", prefs.favs.size, "Favorites", Modifier.weight(1f))
            Stat("movie", all.size, "Videos", Modifier.weight(1f))
        }

        SectionLabel("cleaning_services", "Clean up")
        DataRow("delete_sweep", "Clear watch history", "Forget where you stopped in every video.", "Clear", false, prefs.progress.isNotEmpty()) { scope.launch { store.clearHistory() } }
        DataRow("heart_broken", "Clear favorites", "Remove the heart from every video.", "Clear", false, prefs.favs.isNotEmpty()) { scope.launch { store.clearFavs() } }
        DataRow("hide_image", "Remove cover images", "Back to the original thumbnails ($covers set).", "Remove", false, covers > 0) {
            app.media.coverFile(0L).parentFile?.listFiles()?.forEach { it.delete() }
            ThumbCache.clear()
            ThumbCache.ver.intValue++
        }
        DataRow("restart_alt", "Reset settings", "Return every option to its default.", "Reset", false, true) { scope.launch { store.resetSettings() } }

        SectionLabel("warning", "Danger zone")
        DataRow("cleaning_services", "Clear app data", "Delete everything Pebble keeps on this phone: history, favorites, settings and your profile.", "Clear all", true, true) { onClearAll() }
    }
}

@Composable
private fun Stat(icon: String, value: Int, label: String, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconTile(icon, 36.dp)
        Text("$value", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun DataRow(icon: String, title: String, desc: String, action: String, danger: Boolean, enabled: Boolean, onConfirm: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(3000); armed = false } }
    val btnBg by animateColorAsState(
        if (armed) (if (danger) cs.error else cs.primary) else (if (danger) cs.errorContainer else cs.primaryContainer), label = "dataBg",
    )
    val btnFg by animateColorAsState(
        if (armed) (if (danger) cs.onError else cs.onPrimary) else (if (danger) cs.onErrorContainer else cs.onPrimaryContainer), label = "dataFg",
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(if (danger) cs.errorContainer.copy(alpha = 0.3f) else cs.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(if (danger) cs.error else cs.primary), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(26.dp), tint = if (danger) cs.onError else cs.onPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.ExtraBold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        Row(
            Modifier.fillMaxWidth().height(46.dp).alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(16.dp)).background(btnBg)
                .clickable(enabled = enabled) { if (armed) { armed = false; onConfirm() } else armed = true },
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(armed, transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) }, label = "confirm") { a ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(if (a) "check" else icon, null, Modifier.size(20.dp), tint = btnFg)
                    Text(if (a) "Tap again to confirm" else if (!enabled) "Nothing to clear" else action, color = btnFg, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}
