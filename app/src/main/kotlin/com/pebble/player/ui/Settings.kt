package com.pebble.player.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pebble.player.data.K
import com.pebble.player.data.Prefs
import com.pebble.player.data.Store
import com.pebble.player.data.Video
import kotlinx.coroutines.launch

private val ICONS = mapOf(
    "Theme" to "contrast", "Material You" to "palette", "Accent color" to "palette", "Layout" to "grid_view", "Sort by" to "sort",
    "Autoplay" to "play_circle", "Resume where you stopped" to "history", "Keep screen on" to "smartphone", "Skip step" to "forward_10",
    "Default speed" to "speed", "Video fit" to "aspect_ratio", "Rotate with the video" to "screen_rotation", "Screen rotation" to "screen_rotation",
    "If Bluetooth or headphones disconnect" to "headphones",
)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
}

@Composable
private fun SwitchRow(title: String, sub: String, v: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!v) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(ICONS[title] ?: "tune", 38.dp)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PebbleSwitch(v, onChange)
    }
}

@Composable
private fun Choice(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(ICONS[title] ?: "tune", 38.dp)
            Text(title, fontWeight = FontWeight.Bold)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, o -> FilterChip(selected == i, { onSelect(i) }, { Text(o) }) }
        }
    }
}

data class Cat(val id: String, val icon: String, val title: String, val sub: String)

@Composable
fun SettingsScreen(prefs: Prefs, store: Store, all: List<Video>, onRescan: () -> Unit, onClearAll: () -> Unit, query: String = "") {
    val scope = rememberCoroutineScope()
    fun <T> put(k: androidx.datastore.preferences.core.Preferences.Key<T>, v: T) { scope.launch { store.put(k, v) } }

    var page by remember { mutableStateOf<String?>(null) }
    val cs = MaterialTheme.colorScheme
    val cats = listOf(
        Cat("playback", "play_circle", "Playback", "Autoplay, skipping and controls"),
        Cat("appearance", "palette", "Appearance", "Theme, colors and layout"),
        Cat("device", "smartphone", "Device", "Brightness, volume and Bluetooth"),
        Cat("library", "video_library", "Library", "Scanning and hidden folders"),
        Cat("data", "database", "Data", "History, favorites and reset"),
        Cat("about", "info", "About", "Pebble Player"),
    )
    val cur = cats.firstOrNull { it.id == page }
    val sc = rememberScrollState()
    LaunchedEffect(page) { sc.scrollTo(0) }
    PredictiveBack(page != null, { page = null }) {
    Column(Modifier.fillMaxSize().verticalScroll(sc).padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(32.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PAnim(cur != null, enter = fadeIn() + expandHorizontally() + scaleIn(), exit = fadeOut() + shrinkHorizontally() + scaleOut()) {
                Row {
                    Box(Modifier.size(42.dp).clip(androidx.compose.foundation.shape.CircleShape).background(cs.surfaceContainerHigh).clickable { page = null }, contentAlignment = Alignment.Center) { Icon("arrow_back", "Back", Modifier.size(22.dp)) }
                    Spacer(Modifier.width(14.dp))
                }
            }
            AnimatedContent(cur, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "setHead") { c ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile(c?.icon ?: "tune", 52.dp, filled = true)
                    Column {
                        Text(c?.title ?: "Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(c?.sub ?: "Make Pebble work the way you like", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
        AnimatedContent(page, transitionSpec = { slideTransition(if (targetState != null) 1 else -1) }, label = "setPage") { p ->
        Column {
        if (p == null) {
            val shown = cats.filter { query.isBlank() || it.title.contains(query, true) || it.sub.contains(query, true) }
            Spacer(Modifier.height(14.dp))
            if (shown.isEmpty()) EmptyCard("info", "No results", "Try another word.")
            shown.chunked(2).forEach { r ->
                Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    r.forEach { c ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surfaceContainer).clickable { page = c.id }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            IconTile(c.icon, 46.dp, filled = true)
                            Column { Text(c.title, fontWeight = FontWeight.ExtraBold); Text(c.sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, minLines = 2) }
                        }
                    }
                    if (r.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (p == "appearance") Section("Appearance") {
            Choice("Theme", listOf("System", "Light", "Dark"), prefs.theme) { put(K.theme, it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) SwitchRow("Material You", "Use colors from your wallpaper", prefs.dynamic) { put(K.dyn, it) }
            if (!prefs.dynamic || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Text("Accent color", fontWeight = FontWeight.SemiBold)
                SwatchGrid(prefs.accent, false) { put(K.accent, it) }
            }
            Choice("Layout", listOf("Grid", "List"), prefs.layout) { put(K.layout, it) }
            Choice("Sort by", listOf("Newest", "Name", "Longest", "Largest"), prefs.sort) { put(K.sort, it) }
        }

        if (p == "playback") PlaybackPage(prefs, store)

        if (p == "device") Section("Device") { DeviceControls() }

        if (p == "library") Section("Library") { LibraryControls(prefs, store, all, onRescan) }

        if (p == "data") DataPage(prefs, store, all, onClearAll)

        if (p == "about") Section("About") {
            Text("Pebble Player", fontWeight = FontWeight.SemiBold)
            Text("v${currentVersion(LocalContext.current)} · Material 3 Expressive", color = MaterialTheme.colorScheme.onSurfaceVariant)
            UpdateRow()
        }
        }
        }
        Spacer(Modifier.height(32.dp))
    }
    }
}

val SPEEDS = listOf(0.5f, 1f, 1.25f, 1.5f, 2f)

@Composable
private fun LibraryControls(prefs: Prefs, store: Store, all: List<Video>, onRescan: () -> Unit) {
    val scope = rememberCoroutineScope()
    val folders = remember(all, prefs.blacklist) {
        (all.map { it.path to it.folder } + prefs.blacklist.map { it to it.trimEnd('/').substringAfterLast('/') })
            .distinctBy { it.first }.sortedWith { x, y -> NaturalOrder.compare(x.second, y.second) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${all.size} videos found", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        OutlinedButton(onRescan) { Text("Scan again") }
    }
    Text("Hide folders", fontWeight = FontWeight.SemiBold)
    if (folders.isEmpty()) Text("No folders yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    folders.forEach { (path, name) ->
        val hidden = path in prefs.blacklist
        Row(Modifier.fillMaxWidth().clickable { scope.launch { store.toggleBlacklist(path) } }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name.ifBlank { "Videos" })
                Text(path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PebbleSwitch(hidden, { scope.launch { store.toggleBlacklist(path) } })
        }
    }
}

@Composable
private fun DeviceControls() {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var canWrite by remember { mutableStateOf(Settings.System.canWrite(ctx)) }
    val wl = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { canWrite = Settings.System.canWrite(ctx) }

    var bri by remember { mutableFloatStateOf(readBrightness(ctx)) }
    var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    Text("System volume", fontWeight = FontWeight.SemiBold)
    PebbleSlider(vol, { vol = it; am.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0) }, valueRange = 0f..maxVol.toFloat(), steps = (maxVol - 1).coerceAtLeast(0))

    Text("System brightness", fontWeight = FontWeight.SemiBold)
    if (canWrite) {
        PebbleSlider(bri, {
            bri = it
            try {
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, it.toInt())
            } catch (_: Exception) { }
        }, valueRange = 1f..255f)
    } else {
        Text("Allow Pebble to change system settings to control brightness.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton({ wl.launch(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }) { Text("Allow") }
    }

    // Bluetooth
    var btOk by remember { mutableStateOf(Build.VERSION.SDK_INT < 31 || ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) }
    val askBt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { btOk = it }
    Text("Bluetooth", fontWeight = FontWeight.SemiBold)
    if (!btOk) {
        Text("Allow nearby devices access to show real Bluetooth names.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton({ askBt.launch(Manifest.permission.BLUETOOTH_CONNECT) }) { Text("Allow") }
    } else {
        val adapter = remember { (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter }
        var name by remember { mutableStateOf(try { adapter?.name ?: "" } catch (_: SecurityException) { "" }) }
        var msg by remember { mutableStateOf("") }
        OutlinedTextField(name, { name = it }, label = { Text("This phone's Bluetooth name") }, singleLine = true, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth(), enabled = adapter != null)
        Button({
            msg = try { if (adapter?.setName(name.trim()) == true) "Name updated" else "Couldn't change the name" } catch (_: SecurityException) { "Permission denied" }
        }, enabled = adapter != null && name.isNotBlank()) { Text("Apply name") }
        if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.bodySmall)
        val outs = remember(btOk, vol) { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { isBt(it) } }
        Text(if (outs.isEmpty()) "No Bluetooth audio connected." else "Connected: " + outs.joinToString { it.productName.toString() }, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun isBt(d: AudioDeviceInfo) = d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
    (Build.VERSION.SDK_INT >= 31 && (d.type == AudioDeviceInfo.TYPE_BLE_HEADSET || d.type == AudioDeviceInfo.TYPE_BLE_SPEAKER || d.type == AudioDeviceInfo.TYPE_BLE_BROADCAST))

private fun readBrightness(ctx: Context): Float = try {
    Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS).toFloat().coerceIn(1f, 255f)
} catch (_: Exception) { 128f }
