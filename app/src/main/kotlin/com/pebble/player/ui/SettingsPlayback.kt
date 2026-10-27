package com.pebble.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import com.pebble.player.data.K
import com.pebble.player.data.Prefs
import com.pebble.player.data.Store
import kotlinx.coroutines.launch

private val SKIPS = listOf(5, 10, 15, 30)
private val SPEED_LABELS = listOf("0.5x", "1x", "1.25x", "1.5x", "2x")

/** Settings → Playback: current choices at a glance, then grouped cards (switches and segmented pickers). */
@Composable
fun PlaybackPage(prefs: Prefs, store: Store) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    fun <T> put(k: Preferences.Key<T>, v: T) { scope.launch { store.put(k, v) } }
    val skipI = SKIPS.indexOf(prefs.skip).let { if (it < 0) 1 else it }
    val speedI = SPEEDS.indexOf(prefs.speed).let { if (it < 0) 1 else it }
    val fits = listOf("Fit", "Zoom", "Stretch")

    Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PlayCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile("play_circle", 46.dp, filled = true)
                Column {
                    Text("How videos play", fontWeight = FontWeight.ExtraBold)
                    Text(
                        (if (prefs.autoplay) "Starts by itself" else "Waits for you to press play") + (if (prefs.resume) " · resumes" else ""),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlayStat("forward_10", "${SKIPS[skipI]}s", "Skip step", Modifier.weight(1f))
            PlayStat("speed", SPEED_LABELS[speedI], "Speed", Modifier.weight(1f))
            PlayStat("aspect_ratio", fits.getOrElse(prefs.fit) { "Fit" }, "Video fit", Modifier.weight(1f))
        }

        SectionLabel("play_arrow", "Starting a video")
        ToggleCard("play_circle", "Autoplay", "Start as soon as a video opens.", prefs.autoplay) { put(K.autoplay, it) }
        ToggleCard("history", "Resume where you stopped", "Continue from your last position.", prefs.resume) { put(K.resume, it) }
        ToggleCard("smartphone", "Keep screen on", "The screen stays awake while a video plays.", prefs.wake) { put(K.wake, it) }

        SectionLabel("tune", "Controls")
        ChoiceCard("forward_10", "Skip step", "How far the skip buttons and double-tap jump.", SKIPS.map { "${it}s" }, skipI) { put(K.skip, SKIPS[it]) }
        ChoiceCard("speed", "Default speed", "The speed every video starts at.", SPEED_LABELS, speedI) { put(K.speed, SPEEDS[it]) }
        ChoiceCard("aspect_ratio", "Video fit", "How the picture fills the screen.", fits, prefs.fit) { put(K.fit, it) }

        SectionLabel("screen_rotation", "Screen")
        ToggleCard("screen_rotation", "Rotate with the video", "Wide videos go landscape automatically.", prefs.autoFs) { put(K.autoFs, it) }
        ChoiceCard("smartphone", "Screen rotation", "Lock the player to one direction, or let it follow the phone.", listOf("Auto", "Portrait", "Landscape"), prefs.orient) { put(K.orient, it) }

        SectionLabel("headphones", "Headphones")
        ChoiceCard("headphones", "If Bluetooth or headphones disconnect", "What the video does when the sound device goes away.", listOf("Pause", "Keep playing"), prefs.onLost) { put(K.onLost, it) }
    }
}

@Composable
internal fun PlayCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), content = content,
    )
}

@Composable
internal fun PlayStat(icon: String, value: String, label: String, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconTile(icon, 36.dp)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant, maxLines = 1)
    }
}

/** Rounded icon badge; filled with the accent when [on], muted when off. */
@Composable
internal fun Badge(icon: String, on: Boolean) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (on) cs.primary else cs.surfaceContainerHighest, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "badgeBg")
    val fg by animateColorAsState(if (on) cs.onPrimary else cs.onSurfaceVariant, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "badgeFg")
    Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(26.dp), tint = fg)
    }
}

@Composable
internal fun ToggleCard(icon: String, title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(cs.surfaceContainer).clickable { onChange(!checked) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Badge(icon, checked)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.ExtraBold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        PebbleSwitch(checked, onChange)
    }
}

@Composable
internal fun ChoiceCard(icon: String, title: String, desc: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    PlayCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Badge(icon, true)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.ExtraBold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        Segments(options, selected, onSelect)
    }
}

/** Pill-shaped segmented picker; [selected] may be -1 when nothing matches. */
@Composable
internal fun Segments(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerHighest).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEachIndexed { i, o ->
                val on = i == selected
                val bg by animateColorAsState(if (on) cs.primary else Color.Transparent, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "segBg")
                val fg by animateColorAsState(if (on) cs.onPrimary else cs.onSurfaceVariant, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "segFg")
                Box(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(14.dp)).background(bg).clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) { Text(o, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1) }
            }
        }
}
