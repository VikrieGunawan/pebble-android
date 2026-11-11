package com.pebble.player.ui

import androidx.compose.ui.unit.sp
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

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
}

data class Cat(val id: String, val icon: String, val title: String, val sub: String)

/** Every individual setting, so the search box finds them all (not just the page names). */
private data class Hit(val page: String, val icon: String, val title: String, val sub: String)
private val HITS get() = listOf(
    Hit("appearance", "contrast", t("Mode"), t("Follow the phone, or always use light or dark theme")),
    Hit("appearance", "dark_mode", t("Pure black dark mode"), t("A true black AMOLED background")),
    Hit("appearance", "palette", "Material You", t("Use colors from your wallpaper")),
    Hit("appearance", "text_fields", t("Language"), t("Choose the app language")),
    Hit("appearance", "grid_view", t("Layout"), t("Cards in a grid, or one video per row (list)")),
    Hit("appearance", "zoom_in", t("Card size"), t("How big the video cards are in the grid")),
    Hit("appearance", "sort", t("Sort by"), t("The order of your videos: newest, name, longest, largest")),
    Hit("playback", "play_circle", t("Autoplay"), t("Start as soon as a video opens")),
    Hit("playback", "history", t("Resume where you stopped"), t("Continue from your last position")),
    Hit("playback", "smartphone", t("Keep screen on"), t("The screen stays awake while a video plays")),
    Hit("playback", "forward_10", t("Skip step"), t("How far the skip buttons and double-tap jump")),
    Hit("playback", "speed", t("Default speed"), t("The speed every video starts at")),
    Hit("playback", "speed", t("Hold to speed up"), t("Press and hold the video to play faster")),
    Hit("playback", "aspect_ratio", t("Video fit"), t("How the picture fills the screen")),
    Hit("playback", "screen_rotation", t("Rotate with the video"), t("Wide videos go landscape automatically")),
    Hit("playback", "smartphone", t("Screen rotation"), t("Lock the player to one direction, or follow the phone")),
    Hit("playback", "headphones", t("If Bluetooth or headphones disconnect"), t("What the video does when the sound device goes away")),
    Hit("device", "volume_up", t("Sound"), t("Volume, speaker and audio output")),
    Hit("device", "brightness_high", t("Screen"), t("Brightness, refresh rate and adaptive brightness")),
    Hit("device", "bluetooth", "Bluetooth", t("Connected devices and device name")),
    Hit("device", "info", t("This device"), t("Model, Android version and hardware")),
    Hit("library", "refresh", t("Scan again"), t("Look for new videos")),
    Hit("library", "folder", t("Folders"), t("Choose which folders are shown")),
    Hit("library", "visibility_off", t("Hidden folders"), t("Show hidden folders again")),
    Hit("data", "delete_sweep", t("Clear watch history"), t("Forget where you stopped in every video")),
    Hit("data", "heart_broken", t("Clear favorites"), t("Remove the heart from every video")),
    Hit("data", "hide_image", t("Remove cover images"), t("Back to the original thumbnails")),
    Hit("data", "restart_alt", t("Reset settings"), t("Return every option to its default")),
    Hit("data", "cleaning_services", t("Clear app data"), t("Delete history, favorites, settings and your profile")),
    Hit("about", "system_update", t("Check for updates"), t("Download the newest version")),
    Hit("about", "info", t("App info"), t("Version, build and package")),
    Hit("about", "share", t("Share Pebble Player"), t("Send the app to a friend")),
    Hit("about", "content_copy", t("Copy app info"), t("Copy version and device details")),
)

@Composable
fun SettingsScreen(prefs: Prefs, store: Store, all: List<Video>, onRescan: () -> Unit, onClearAll: () -> Unit, query: String = "") {

    var page by remember { mutableStateOf<String?>(null) }
    val cs = MaterialTheme.colorScheme
    val land = androidx.compose.ui.platform.LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    val cats = listOf(
        Cat("playback", "play_circle", t("Playback"), t("Autoplay, skipping and controls")),
        Cat("appearance", "palette", t("Appearance"), t("Theme, colors and layout")),
        Cat("device", "smartphone", t("Device"), t("Brightness, volume and Bluetooth")),
        Cat("library", "video_library", t("Library"), t("Scanning and hidden folders")),
        Cat("data", "database", t("Data"), t("History, favorites and reset")),
        Cat("about", "info", t("About"), "Pebble Player"),
    )
    val cur = cats.firstOrNull { it.id == page }
    val hubScroll = rememberScrollState() // kept while a sub page is open, so coming back lands where you were
    PredictiveBack(page != null, { page = null }) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (!land) Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(32.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PAnim(cur != null, enter = fadeIn() + expandHorizontally() + scaleIn(), exit = fadeOut() + shrinkHorizontally() + scaleOut()) {
                Row {
                    Box(Modifier.size(42.dp).clip(androidx.compose.foundation.shape.CircleShape).background(cs.surfaceContainerHigh).clickable { page = null }, contentAlignment = Alignment.Center) { Icon("arrow_back", t("Back"), Modifier.size(22.dp)) }
                    Spacer(Modifier.width(14.dp))
                }
            }
            AnimatedContent(cur, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "setHead") { c ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile(c?.icon ?: "tune", 52.dp, filled = true)
                    Column {
                        Text(c?.title ?: t("Settings"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(c?.sub ?: t("Make Pebble work the way you like"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
        AnimatedContent(page, Modifier.weight(1f).padding(top = if (land) 0.dp else 14.dp), transitionSpec = { slideTransition(if (targetState != null) 1 else -1) }, label = "setPage") { p ->
        Column(Modifier.fillMaxSize().verticalScroll(if (p == null) hubScroll else rememberScrollState())) {
        if (land) {
            // landscape: the header scrolls away with the page so more of it fits
            Spacer(Modifier.height(8.dp))
            SetHeadStatic(cats.firstOrNull { it.id == p }, if (p != null) ({ page = null }) else null)
            Spacer(Modifier.height(14.dp))
        }
        if (p == null) {
            val shown = cats.filter { query.isBlank() || it.title.contains(query, true) || it.sub.contains(query, true) }
            val hits = if (query.isBlank()) emptyList() else HITS.filter { it.title.contains(query, true) || it.sub.contains(query, true) }
            if (shown.isEmpty() && hits.isEmpty()) EmptyCard("info", t("No results"), t("Try another word."))
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
            if (hits.isNotEmpty()) {
                Text("SETTINGS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = cs.onSurfaceVariant, letterSpacing = 1.sp, modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 8.dp))
                hits.forEach { h ->
                    val pageName = cats.firstOrNull { it.id == h.page }?.title ?: ""
                    Row(
                        Modifier.padding(bottom = 8.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer).clickable { page = h.page }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        IconTile(h.icon, 42.dp)
                        Column(Modifier.weight(1f)) {
                            Text(h.title, fontWeight = FontWeight.Bold)
                            Text("$pageName · ${h.sub}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2)
                        }
                        Icon("chevron_right", null, Modifier.size(22.dp), tint = cs.onSurfaceVariant)
                    }
                }
            }
        }

        if (p == "appearance") AppearancePage(prefs, store)

        if (p == "playback") PlaybackPage(prefs, store)

        if (p == "device") DevicePage()

        if (p == "library") LibraryPage(prefs, store, all, onRescan)

        if (p == "data") DataPage(prefs, store, all, onClearAll)

        if (p == "about") AboutPage()
        Spacer(Modifier.height(32.dp))
        }
        }
    }
    }
}

val SPEEDS = listOf(0.5f, 1f, 1.25f, 1.5f, 2f)

internal fun isBt(d: AudioDeviceInfo) = d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
    (Build.VERSION.SDK_INT >= 31 && (d.type == AudioDeviceInfo.TYPE_BLE_HEADSET || d.type == AudioDeviceInfo.TYPE_BLE_SPEAKER || d.type == AudioDeviceInfo.TYPE_BLE_BROADCAST))

internal fun readBrightness(ctx: Context): Float = try {
    Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS).toFloat().coerceIn(1f, 255f)
} catch (_: Exception) { 128f }

/** The page header as a plain card (landscape: it lives inside the scrolling page). */
@Composable
private fun SetHeadStatic(c: Cat?, onBack: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(cs.surfaceContainer).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (onBack != null) Box(Modifier.size(42.dp).clip(androidx.compose.foundation.shape.CircleShape).background(cs.surfaceContainerHigh).clickable { onBack() }, contentAlignment = Alignment.Center) { Icon("arrow_back", t("Back"), Modifier.size(22.dp)) }
        IconTile(c?.icon ?: "tune", 52.dp, filled = true)
        Column {
            Text(c?.title ?: t("Settings"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(c?.sub ?: t("Make Pebble work the way you like"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}
