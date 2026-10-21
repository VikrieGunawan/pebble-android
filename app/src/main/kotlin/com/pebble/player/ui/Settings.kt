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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Text(title, style = MaterialTheme.typography.titleSmall, color = cs.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, top = 22.dp, bottom = 8.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
}

@Composable
private fun SwitchRow(title: String, sub: String, v: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!v) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PebbleSwitch(v, onChange)
    }
}

@Composable
private fun Choice(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, o -> FilterChip(selected == i, { onSelect(i) }, { Text(o) }) }
        }
    }
}

@Composable
fun SettingsScreen(prefs: Prefs, store: Store, all: List<Video>, onRescan: () -> Unit, onClearAll: () -> Unit) {
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<String?>(null) }
    fun <T> put(k: androidx.datastore.preferences.core.Preferences.Key<T>, v: T) { scope.launch { store.put(k, v) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))

        Section("Appearance") {
            Choice("Theme", listOf("System", "Light", "Dark"), prefs.theme) { put(K.theme, it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) SwitchRow("Material You", "Use colors from your wallpaper", prefs.dynamic) { put(K.dyn, it) }
            if (!prefs.dynamic || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Text("Accent color", fontWeight = FontWeight.SemiBold)
                SwatchGrid(prefs.accent, false) { put(K.accent, it) }
            }
            Choice("Layout", listOf("Grid", "List"), prefs.layout) { put(K.layout, it) }
            Choice("Sort by", listOf("Newest", "Name", "Longest"), prefs.sort) { put(K.sort, it) }
        }

        Section("Playback") {
            SwitchRow("Autoplay", "Start as soon as a video opens", prefs.autoplay) { put(K.autoplay, it) }
            SwitchRow("Resume where you stopped", "Continue from your last position", prefs.resume) { put(K.resume, it) }
            SwitchRow("Keep screen on", "While a video is playing", prefs.wake) { put(K.wake, it) }
            Choice("Skip step", listOf("5s", "10s", "15s", "30s"), listOf(5, 10, 15, 30).indexOf(prefs.skip).coerceAtLeast(1)) { put(K.skip, listOf(5, 10, 15, 30)[it]) }
            Choice("Default speed", listOf("0.5x", "1x", "1.25x", "1.5x", "2x"), SPEEDS.indexOf(prefs.speed).coerceAtLeast(1)) { put(K.speed, SPEEDS[it]) }
            Choice("Video fit", listOf("Fit", "Zoom", "Stretch"), prefs.fit) { put(K.fit, it) }
            SwitchRow("Rotate with the video", "Wide videos go landscape automatically", prefs.autoFs) { put(K.autoFs, it) }
            Choice("Screen rotation", listOf("Auto", "Portrait", "Landscape"), prefs.orient) { put(K.orient, it) }
            Choice("If Bluetooth or headphones disconnect", listOf("Pause", "Keep playing"), prefs.onLost) { put(K.onLost, it) }
        }

        Section("Device") { DeviceControls() }

        Section("Library") { LibraryControls(prefs, store, all, onRescan) }

        Section("Data") {
            OutlinedButton({ confirm = "history" }, Modifier.fillMaxWidth()) { Text("Clear watch history") }
            OutlinedButton({ confirm = "favs" }, Modifier.fillMaxWidth()) { Text("Clear favorites") }
            OutlinedButton({ confirm = "reset" }, Modifier.fillMaxWidth()) { Text("Reset settings") }
            Button({ confirm = "all" }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) { Text("Clear app data") }
        }

        Section("About") {
            Text("Pebble Player", fontWeight = FontWeight.SemiBold)
            Text("v1.0.0-beta · Material 3 Expressive", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(32.dp))
    }

    confirm?.let { c ->
        val (t, d) = when (c) {
            "history" -> "Clear watch history?" to "Your resume positions will be removed."
            "favs" -> "Clear favorites?" to "All hearts will be removed."
            "reset" -> "Reset settings?" to "Playback and appearance go back to defaults."
            else -> "Clear app data?" to "Everything is wiped and you go back to Welcome."
        }
        AlertDialog(
            onDismissRequest = { confirm = null }, title = { Text(t) }, text = { Text(d) },
            confirmButton = {
                TextButton({
                    confirm = null
                    when (c) {
                        "history" -> scope.launch { store.clearHistory() }
                        "favs" -> scope.launch { store.clearFavs() }
                        "reset" -> scope.launch { store.resetSettings() }
                        else -> onClearAll()
                    }
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } },
        )
    }
}

val SPEEDS = listOf(0.5f, 1f, 1.25f, 1.5f, 2f)

@Composable
private fun LibraryControls(prefs: Prefs, store: Store, all: List<Video>, onRescan: () -> Unit) {
    val scope = rememberCoroutineScope()
    val folders = remember(all, prefs.blacklist) {
        (all.map { it.path to it.folder } + prefs.blacklist.map { it to it.trimEnd('/').substringAfterLast('/') })
            .distinctBy { it.first }.sortedBy { it.second.lowercase() }
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
    Slider(vol, { vol = it; am.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0) }, valueRange = 0f..maxVol.toFloat(), steps = (maxVol - 1).coerceAtLeast(0))

    Text("System brightness", fontWeight = FontWeight.SemiBold)
    if (canWrite) {
        Slider(bri, {
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
