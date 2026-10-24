package com.pebble.player.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates are described by update.json in the repository root:
 * { "version": "1.0.1", "notes": ["..."], "apk": "<raw.githubusercontent.com link to the APK>", "size": 0 }
 */
const val UPDATE_JSON = "https://raw.githubusercontent.com/VikrieGunawan/pebble-android/main/update.json"

data class UpdateInfo(val version: String, val notes: String, val apkUrl: String, val size: Long)

fun currentVersion(ctx: Context): String = try {
    val pm = ctx.packageManager
    (if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(ctx.packageName, PackageManager.PackageInfoFlags.of(0))
    else @Suppress("DEPRECATION") pm.getPackageInfo(ctx.packageName, 0)).versionName ?: "0"
} catch (_: Exception) { "0" }

/** "1.0.1" > "1.0.0" > "1.0.0-beta" */
private fun newer(remote: String, local: String): Boolean {
    fun parts(s: String) = s.trim().removePrefix("v").substringBefore('-').split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
    val a = parts(remote)
    val b = parts(local)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    val sa = remote.substringAfter('-', "")
    val sb = local.substringAfter('-', "")
    return when { sa == sb -> false; sa.isEmpty() -> true; sb.isEmpty() -> false; else -> sa > sb }
}

/** Reads update.json. Success(null) = already up to date; failure = offline or bad file. */
suspend fun fetchUpdate(ctx: Context): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
    runCatching {
        val c = URL("$UPDATE_JSON?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.useCaches = false
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val v = j.getString("version")
            if (!newer(v, currentVersion(ctx))) null else {
                val n = j.opt("notes")
                val notes = if (n is org.json.JSONArray) (0 until n.length()).joinToString("\n") { "• " + n.getString(it) } else j.optString("notes")
                UpdateInfo(v.removePrefix("v"), notes, j.getString("apk"), j.optLong("size"))
            }
        } finally { c.disconnect() }
    }
}

private fun prefs(ctx: Context) = ctx.getSharedPreferences("pebble_update", Context.MODE_PRIVATE)
fun skipVersion(ctx: Context, v: String) { prefs(ctx).edit().putString("skip", v).apply() }

/** Automatic check on launch: at most every 12 hours, never for a version the user skipped. */
suspend fun checkUpdateAuto(ctx: Context): UpdateInfo? {
    val p = prefs(ctx)
    val now = System.currentTimeMillis()
    if (now - p.getLong("last", 0L) < 12 * 3600_000L) return null
    p.edit().putLong("last", now).apply()
    val u = fetchUpdate(ctx).getOrNull() ?: return null
    return if (u.version == p.getString("skip", null)) null else u
}

private fun apkFile(ctx: Context) = File(File(ctx.cacheDir, "updates").apply { mkdirs() }, "PebblePlayer.apk")

/** Downloads the APK inside the app. onProgress(bytesDone, bytesTotal, bytesPerSecond). */
private suspend fun download(ctx: Context, info: UpdateInfo, onProgress: (Long, Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
    val out = apkFile(ctx)
    val part = File(out.path + ".part")
    val c = URL(info.apkUrl).openConnection() as HttpURLConnection
    c.connectTimeout = 10000
    c.readTimeout = 15000
    try {
        if (c.responseCode != 200) error("HTTP ${c.responseCode}")
        val total = c.contentLengthLong.takeIf { it > 0 } ?: info.size
        var done = 0L
        var t0 = System.nanoTime()
        var b0 = 0L
        var bps = 0L
        c.inputStream.use { ins ->
            part.outputStream().use { os ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    ensureActive()
                    val n = ins.read(buf)
                    if (n < 0) break
                    os.write(buf, 0, n)
                    done += n
                    val dt = System.nanoTime() - t0
                    if (dt >= 400_000_000L) { bps = (done - b0) * 1_000_000_000L / dt; t0 = System.nanoTime(); b0 = done }
                    onProgress(done, total, bps)
                }
            }
        }
        if (total > 0 && done != total) error("Incomplete download")
        val pm = ctx.packageManager
        val pkg = if (Build.VERSION.SDK_INT >= 33) pm.getPackageArchiveInfo(part.path, PackageManager.PackageInfoFlags.of(0))
        else @Suppress("DEPRECATION") pm.getPackageArchiveInfo(part.path, 0)
        if (pkg?.packageName != ctx.packageName) error("The file isn't a Pebble Player APK")
        out.delete()
        if (!part.renameTo(out)) error("Couldn't save the file")
        out
    } catch (e: Exception) {
        part.delete()
        throw e
    } finally { c.disconnect() }
}

/** Opens Android's own installer. If installs from this app aren't allowed yet, opens that setting first. */
private fun install(ctx: Context, f: File) {
    if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
        Toast.makeText(ctx, "Allow installs from Pebble Player, then tap Install again", Toast.LENGTH_LONG).show()
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return
    }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    ctx.startActivity(
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private enum class Phase { Idle, Loading, Ready, Failed }

@Composable
fun UpdateDialog(info: UpdateInfo, onLater: () -> Unit, onSkip: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var shown by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(Phase.Idle) }
    var done by remember { mutableLongStateOf(0L) }
    var total by remember { mutableLongStateOf(info.size) }
    var bps by remember { mutableLongStateOf(0L) }
    var err by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(Unit) { shown = true }

    fun start() {
        phase = Phase.Loading; done = 0; bps = 0
        job = scope.launch {
            try {
                val f = download(ctx, info) { d, t, s -> done = d; total = t; bps = s }
                file = f
                phase = Phase.Ready
                install(ctx, f)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                err = e.message ?: "Download failed"
                phase = Phase.Failed
            }
        }
    }

    val frac = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    val animFrac by animateFloatAsState(frac, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "dl")
    val busy = phase == Phase.Loading

    Dialog(onDismissRequest = { if (!busy) onLater() }, properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy)) {
        PAnim(shown, enter = scaleIn(tween(320), initialScale = 0.88f) + fadeIn(tween(220))) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(cs.surfaceContainerHigh)) {
                Box(
                    Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(cs.primary, cs.tertiary))).padding(vertical = 26.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                        if (phase != Phase.Idle) CircularProgressIndicator(
                            progress = { if (phase == Phase.Loading) animFrac else 1f },
                            modifier = Modifier.size(84.dp), color = Color.White, trackColor = Color(0x33FFFFFF), strokeWidth = 5.dp,
                        ) else Box(Modifier.size(76.dp).clip(CircleShape).background(Color(0x33FFFFFF)))
                        AnimatedContent(phase, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }, label = "hi") { p ->
                            when (p) {
                                Phase.Loading -> Text("${(animFrac * 100).toInt()}%", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                                Phase.Ready -> Icon("check", null, Modifier.size(40.dp), tint = Color.White)
                                Phase.Failed -> Icon("error", null, Modifier.size(40.dp), tint = Color.White)
                                else -> Icon("system_update", null, Modifier.size(44.dp), tint = Color.White)
                            }
                        }
                    }
                }
                Column(Modifier.padding(20.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        when (phase) { Phase.Idle -> "Update available"; Phase.Loading -> "Downloading update"; Phase.Ready -> "Ready to install"; Phase.Failed -> "Download failed" },
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Chip(currentVersion(ctx), false)
                        Icon("arrow_forward", null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                        Chip(info.version, true)
                        if (total > 0) Text(fmtSize(total), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    when (phase) {
                        Phase.Idle -> {
                            val notes = info.notes.trim()
                            if (notes.isNotEmpty()) Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainer).padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon("info", null, Modifier.size(18.dp), tint = cs.primary)
                                    Text("WHAT'S NEW", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = cs.onSurfaceVariant, letterSpacing = 0.8.sp)
                                }
                                Text(notes, Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyMedium)
                            }
                            Btn("Download update", "download", true) { start() }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Btn("Later", null, false, Modifier.weight(1f), onLater)
                                Btn("Skip version", null, false, Modifier.weight(1f), onSkip)
                            }
                        }
                        Phase.Loading -> {
                            LinearProgressIndicator(progress = { animFrac }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape), drawStopIndicator = {})
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (total > 0) "${fmtSize(done)} / ${fmtSize(total)}" else fmtSize(done), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                Text(
                                    if (bps > 0) "%.1f MB/s".format(bps / 1048576.0) + (if (total > done) " · ${eta((total - done) / bps)}" else "") else "",
                                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                                )
                            }
                            Btn("Cancel", null, false) { job?.cancel(); phase = Phase.Idle }
                        }
                        Phase.Ready -> {
                            Text("Android will ask you to confirm the install. Your videos and settings stay as they are.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Start)
                            Btn("Install", "system_update", true) { file?.let { install(ctx, it) } }
                            Btn("Close", null, false, Modifier, onLater)
                        }
                        Phase.Failed -> {
                            Text(err, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                            Btn("Try again", "refresh", true) { start() }
                            Btn("Close", null, false, Modifier, onLater)
                        }
                    }
                }
            }
        }
    }
}

private fun eta(s: Long) = if (s >= 60) "${s / 60} min ${s % 60} s left" else "$s s left"

@Composable
internal fun Btn(label: String, icon: String?, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(if (primary) 18.dp else 16.dp)).background(if (primary) cs.primary else cs.surfaceContainer)
            .clickable(onClick = onClick).padding(vertical = if (primary) 15.dp else 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(22.dp), tint = cs.onPrimary)
        Text(label, Modifier.padding(start = if (icon != null) 8.dp else 0.dp), color = if (primary) cs.onPrimary else cs.onSurface, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun Chip(text: String, on: Boolean) {
    val cs = MaterialTheme.colorScheme
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (on) cs.onPrimary else cs.onSurfaceVariant,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) cs.primary else cs.surfaceContainer).padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

/** Settings → About: manual "Check for updates" row. */
@Composable
fun UpdateRow() {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<UpdateInfo?>(null) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerHigh).clickable(enabled = !busy) {
            busy = true
            scope.launch {
                val r = fetchUpdate(ctx)
                busy = false
                r.onSuccess { u -> if (u == null) Toast.makeText(ctx, "You're up to date", Toast.LENGTH_SHORT).show() else found = u }
                    .onFailure { Toast.makeText(ctx, "Couldn't check for updates", Toast.LENGTH_SHORT).show() }
            }
        }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile("system_update", 42.dp, filled = true)
        Column(Modifier.weight(1f)) {
            Text("Check for updates", fontWeight = FontWeight.ExtraBold)
            Text(if (busy) "Checking…" else "Version ${currentVersion(ctx)}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Icon("refresh", null, Modifier.size(22.dp), tint = cs.onSurfaceVariant)
    }
    found?.let { u -> UpdateDialog(u, { found = null }, { skipVersion(ctx, u.version); found = null }) }
}
