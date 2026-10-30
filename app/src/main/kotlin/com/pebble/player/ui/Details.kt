package com.pebble.player.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pebble.player.data.Prefs
import com.pebble.player.data.Video
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Details of one video: picture, key numbers, where it lives and how far you watched. */
@Composable
fun VideoDetailsDialog(v: Video, prefs: Prefs, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bmp = rememberThumb(v)
    val key = v.uri.toString()
    val prog = prefs.progress[key]
    val fav = key in prefs.favs
    val added = remember(v.added) {
        if (v.added <= 0L) "-" else SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(if (v.added < 100_000_000_000L) v.added * 1000 else v.added))
    }
    val place = v.path.ifEmpty { v.folder }
    DetailsFrame(onClose) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(cs.surfaceContainerHighest)) {
            if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon("movie", null, Modifier.align(Alignment.Center).size(40.dp), tint = cs.onSurfaceVariant)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000)))))
            Row(Modifier.align(Alignment.BottomStart).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip(fmt(v.dur)); GlassChip(fmtSize(v.size)); if (v.ext.isNotEmpty()) GlassChip(v.ext.uppercase())
            }
            Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(36.dp).clip(CircleShape).background(Color(0x66000000)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon("close", "Close", Modifier.size(20.dp), tint = Color.White)
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(v.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlayStat("schedule", fmt(v.dur), "Length", Modifier.weight(1f))
                PlayStat("hard_drive", fmtSize(v.size), "Size", Modifier.weight(1f))
                PlayStat("movie", v.ext.ifEmpty { "-" }.uppercase(), "Format", Modifier.weight(1f))
            }
            InfoRow("folder", "Location", place)
            InfoRow("update", "Added", added)
            InfoRow("favorite", "Favorite", if (fav) "Yes, pinned in Favorites" else "No")
            InfoRow(
                "history", "Watched",
                if (prog == null || prog.dur <= 0) "Not started" else "${fmt(prog.pos)} of ${fmt(prog.dur)} · ${(prog.pos * 100 / prog.dur).coerceIn(0, 100)}%",
            )
            CopyBtn(place)
        }
    }
}

/** Details of a folder: counts, size and where it is. */
@Composable
fun FolderDetailsDialog(name: String, path: String, vids: List<Video>, onClose: () -> Unit) {
    val longest = vids.maxByOrNull { it.dur }
    val subs = vids.map { it.path }.distinct().count { it != path }
    DetailsFrame(onClose) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile("folder", 52.dp, filled = true)
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Folder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon("close", "Close", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlayStat("movie", "${vids.size}", "Videos", Modifier.weight(1f))
                PlayStat("hard_drive", fmtSize(vids.sumOf { it.size }), "Size", Modifier.weight(1f))
                PlayStat("schedule", fmt(vids.sumOf { it.dur }), "Length", Modifier.weight(1f))
            }
            InfoRow("folder", "Location", path.ifEmpty { "/" })
            InfoRow("folder_open", "Subfolders", if (subs == 0) "None" else "$subs")
            if (longest != null) InfoRow("schedule", "Longest video", "${longest.name} · ${fmt(longest.dur)}")
            CopyBtn(path.ifEmpty { "/" })
        }
    }
}

@Composable
private fun DetailsFrame(onClose: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 460.dp).fillMaxWidth().heightIn(max = 640.dp)
                .clip(RoundedCornerShape(32.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).verticalScroll(rememberScrollState()),
        ) { content() }
    }
}

@Composable
private fun GlassChip(text: String) {
    Text(
        text, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x66000000)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun InfoRow(icon: String, label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainer).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(icon, 38.dp)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Text(value, fontWeight = FontWeight.ExtraBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CopyBtn(text: String) {
    val ctx = LocalContext.current
    ActionBtn("content_copy", "Copy location", true) {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Pebble", text))
        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
    }
}
