package com.pebble.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pebble.player.data.Prefs
import com.pebble.player.data.Store
import com.pebble.player.data.Video
import kotlinx.coroutines.launch

private class Fol(val path: String, val name: String, val count: Int, val bytes: Long)

/** Settings → Library: what was found, scan again, and which folders are shown. */
@Composable
fun LibraryPage(prefs: Prefs, store: Store, all: List<Video>, onRescan: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    val folders = remember(all, prefs.blacklist) {
        val found = all.groupBy { it.path }.map { (p, l) -> Fol(p, l.first().folder, l.size, l.sumOf { it.size }) }
        val seen = found.map { it.path }.toSet()
        (found + prefs.blacklist.filter { it !in seen }.map { Fol(it, it.trimEnd('/').substringAfterLast('/'), 0, 0L) })
            .sortedWith { x, y -> NaturalOrder.compare(x.name, y.name) }
    }
    val hidden = folders.count { it.path in prefs.blacklist }
    val shownVideos = all.count { it.path !in prefs.blacklist }
    val total = all.sumOf { it.size }
    val list = folders.filter { q.isBlank() || it.name.contains(q, true) || it.path.contains(q, true) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PlayCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile("video_library", 46.dp, filled = true)
                Column {
                    Text(t("{0} videos found", all.size), fontWeight = FontWeight.ExtraBold)
                    Text(t("In {0} folders · {1}", folders.size, fmtSize(total)), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            ActionBtn("refresh", t("Scan again"), true) { onRescan() }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlayStat("movie", "$shownVideos", t("Shown"), Modifier.weight(1f))
            PlayStat("folder", "${folders.size - hidden}", t("Folders"), Modifier.weight(1f))
            PlayStat("visibility_off", "$hidden", t("Hidden"), Modifier.weight(1f))
        }

        SectionLabel("folder", t("Folders"), folders.size)
        if (folders.size > 6) OutlinedTextField(
            q, { q = it }, label = { Text(t("Search folders")) }, singleLine = true,
            shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth(),
        )
        if (folders.isEmpty()) PlayCard { Text(t("No folders yet. Tap Scan again to find your videos."), color = cs.onSurfaceVariant) }
        list.forEach { f ->
            val off = f.path in prefs.blacklist
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(cs.surfaceContainer)
                    .clickable { scope.launch { store.toggleBlacklist(f.path) } }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Badge(if (off) "folder" else "folder_open", !off)
                Column(Modifier.weight(1f)) {
                    Text(f.name.ifBlank { t("Videos") }, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (off) t("Hidden from your library") else "${f.count} videos · ${fmtSize(f.bytes)}",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1,
                    )
                    Text(f.path, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                PebbleSwitch(!off, { scope.launch { store.toggleBlacklist(f.path) } })
            }
        }
        if (hidden > 0) {
            SectionLabel("visibility_off", t("Hidden"))
            PlayCard {
                Text(t("{0} folder(s) are hidden from Home, Library and search.", hidden), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                ActionBtn("folder_open", t("Show all folders"), true) { scope.launch { prefs.blacklist.toList().forEach { store.toggleBlacklist(it) } } }
            }
        }
    }
}
