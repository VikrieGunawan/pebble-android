package com.pebble.player.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private val VOL_PRESETS = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
private val BRI_PRESETS = listOf(0.15f, 0.4f, 0.7f, 1f)

/** Settings → Device: battery, volume, brightness, audio outputs, Bluetooth and a few facts about this phone. */
@Composable
fun DevicePage() {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var canWrite by remember { mutableStateOf(Settings.System.canWrite(ctx)) }
    val wl = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { canWrite = Settings.System.canWrite(ctx) }

    val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var bri by remember { mutableFloatStateOf(readBrightness(ctx)) }
    var auto by remember { mutableStateOf(readAuto(ctx)) }
    fun setVol(v: Float) { vol = v.coerceIn(0f, maxVol.toFloat()); am.setStreamVolume(AudioManager.STREAM_MUSIC, vol.roundToInt(), 0) }
    fun setBri(v: Float) {
        bri = v.coerceIn(1f, 255f); auto = false
        try {
            Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, bri.toInt())
        } catch (_: Exception) { }
    }
    fun setAuto(on: Boolean) {
        auto = on
        try { Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) } catch (_: Exception) { }
    }

    val battery by produceState(Pair(-1, false)) {
        while (true) {
            val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            value = Pair(if (level < 0) -1 else level * 100 / scale.coerceAtLeast(1), st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL)
            delay(5000)
        }
    }
    val (batLevel, charging) = battery
    val hz = remember { ((ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate ?: 60f).roundToInt() }
    val dm = ctx.resources.displayMetrics
    val volPct = (vol / maxVol * 100).roundToInt()
    val briPct = (bri / 255f * 100).roundToInt()

    Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PlayCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile("phone_android", 46.dp, filled = true)
                Column {
                    Text("${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}", fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Text("Android ${Build.VERSION.RELEASE}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(cs.surfaceContainerHighest)) {
                Box(Modifier.fillMaxWidth((batLevel / 100f).coerceIn(0.02f, 1f)).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(cs.primary, cs.tertiary))))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (charging) "bolt" else "battery_full", null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                Text(
                    if (batLevel < 0) "Battery level unavailable" else "Battery $batLevel%" + if (charging) " · Charging" else "",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlayStat("volume_up", "$volPct%", "Volume", Modifier.weight(1f))
            PlayStat("brightness_high", if (canWrite) "$briPct%" else "—", "Brightness", Modifier.weight(1f))
            PlayStat("refresh", "$hz Hz", "Refresh rate", Modifier.weight(1f))
        }

        SectionLabel("volume_up", "Sound")
        PlayCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Badge(if (vol <= 0f) "volume_off" else "volume_up", true)
                Column(Modifier.weight(1f)) {
                    Text("Media volume", fontWeight = FontWeight.ExtraBold)
                    Text("Level ${vol.roundToInt()} of $maxVol", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            PebbleSlider(vol, { setVol(it) }, valueRange = 0f..maxVol.toFloat(), steps = (maxVol - 1).coerceAtLeast(0))
            Segments(listOf("Mute", "25%", "50%", "75%", "Max"), VOL_PRESETS.indexOfFirst { abs(vol / maxVol - it) < 0.07f }) { setVol(maxVol * VOL_PRESETS[it]) }
        }
        val outs = remember(vol) { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { outputName(it) != null } }
        PlayCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Badge("speaker", true)
                Column(Modifier.weight(1f)) {
                    Text("Sound outputs", fontWeight = FontWeight.ExtraBold)
                    Text("Where sound can play right now.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            if (outs.isEmpty()) Text("No outputs found.", color = cs.onSurfaceVariant)
            outs.distinctBy { it.id }.forEach { d ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHighest).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(if (isBt(d)) "bluetooth" else if (d.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) "speaker" else "headphones", null, Modifier.size(22.dp), tint = cs.primary)
                    Column(Modifier.weight(1f)) {
                        Text(d.productName.toString().ifBlank { outputName(d) ?: "" }, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(outputName(d) ?: "", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }
        }

        SectionLabel("brightness_high", "Screen")
        if (canWrite) {
            PlayCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Badge(if (auto) "brightness_auto" else "brightness_high", true)
                    Column(Modifier.weight(1f)) {
                        Text("Brightness", fontWeight = FontWeight.ExtraBold)
                        Text(if (auto) "Adaptive · $briPct%" else "$briPct%", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
                PebbleSlider(bri, { setBri(it) }, valueRange = 1f..255f)
                Segments(listOf("Low", "Medium", "High", "Max"), if (auto) -1 else BRI_PRESETS.indexOfFirst { abs(bri / 255f - it) < 0.12f }) { setBri(255f * BRI_PRESETS[it]) }
            }
            ToggleCard("brightness_auto", "Adaptive brightness", "Let the phone match the light around you.", auto) { setAuto(it) }
        } else {
            PlayCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Badge("brightness_high", false)
                    Column(Modifier.weight(1f)) {
                        Text("Brightness", fontWeight = FontWeight.ExtraBold)
                        Text("Allow Pebble to change system settings to control brightness.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
                ActionBtn("lock", "Allow", true) { wl.launch(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }
            }
        }

        SectionLabel("bluetooth", "Bluetooth")
        var btOk by remember { mutableStateOf(Build.VERSION.SDK_INT < 31 || ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) }
        val askBt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { btOk = it }
        if (!btOk) {
            PlayCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Badge("bluetooth", false)
                    Column(Modifier.weight(1f)) {
                        Text("Nearby devices", fontWeight = FontWeight.ExtraBold)
                        Text("Allow access to show real Bluetooth names.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
                ActionBtn("lock", "Allow", true) { askBt.launch(Manifest.permission.BLUETOOTH_CONNECT) }
            }
        } else {
            val adapter = remember { (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter }
            var name by remember { mutableStateOf(try { adapter?.name ?: "" } catch (_: SecurityException) { "" }) }
            var msg by remember { mutableStateOf("") }
            PlayCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Badge("bluetooth", true)
                    Column(Modifier.weight(1f)) {
                        Text("Phone name", fontWeight = FontWeight.ExtraBold)
                        Text("How other devices see this phone.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Bluetooth name") }, singleLine = true, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth(), enabled = adapter != null)
                ActionBtn("check", "Apply name", adapter != null && name.isNotBlank()) {
                    msg = try { if (adapter?.setName(name.trim()) == true) "Name updated" else "Couldn't change the name" } catch (_: SecurityException) { "Permission denied" }
                }
                if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            val bt = remember(btOk, vol) { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { isBt(it) } }
            PlayCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Badge("devices", bt.isNotEmpty())
                    Column(Modifier.weight(1f)) {
                        Text("Connected audio", fontWeight = FontWeight.ExtraBold)
                        Text(if (bt.isEmpty()) "No Bluetooth audio connected." else bt.joinToString { it.productName.toString() }, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }
        }

        SectionLabel("info", "This device")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoTile("phone_android", "Model", Build.MODEL, Modifier.weight(1f))
            InfoTile("memory", "Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoTile("screenshot_monitor", "Screen", "${dm.widthPixels} × ${dm.heightPixels}", Modifier.weight(1f))
            InfoTile("refresh", "Refresh rate", "$hz Hz", Modifier.weight(1f))
        }
    }
}

@Composable
private fun InfoTile(icon: String, label: String, value: String, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(modifier.clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(icon, 36.dp)
        Column {
            Text(value, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
        }
    }
}

private fun outputName(d: AudioDeviceInfo): String? = when (d.type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone speaker"
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headphones"
    AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio"
    AudioDeviceInfo.TYPE_HDMI -> "HDMI"
    else -> if (isBt(d)) "Bluetooth" else null
}

private fun readAuto(ctx: Context): Boolean = try {
    Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
} catch (_: Exception) { false }
