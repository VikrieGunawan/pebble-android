package com.pebble.player.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pebble.player.data.Prefs
import com.pebble.player.data.Store
import com.pebble.player.data.Video
import kotlinx.coroutines.launch

@Composable
private fun SheetAction(icon: String, label: String, primary: Boolean, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg = if (primary) cs.primary else cs.surfaceContainerHigh
    val fg = if (primary) cs.onPrimary else cs.onSurface
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(bg).clickable(onClick = onClick).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(26.dp), tint = if (active) cs.primary else if (primary) fg else cs.primary)
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = fg)
    }
}

@Composable
private fun SheetRow(icon: String, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val c = if (danger) cs.error else cs.primary
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(21.dp), tint = c) }
        Text(label, fontWeight = FontWeight.Bold, color = if (danger) cs.error else cs.onSurface)
    }
}

@Composable
fun VideoMenuSheet(v: Video, prefs: Prefs, store: Store, onPlay: () -> Unit, onRescan: () -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var details by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }
    val bmp = rememberThumb(v)
    val fav = v.uri.toString() in prefs.favs
    val inHistory = v.uri.toString() in prefs.progress
    val delLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) { onRescan(); onDismiss() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = cs.surfaceContainer) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(84.dp).height(48.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHigh)) {
                    bmp?.let { Image(it, null, Modifier.fillMaxWidth().height(48.dp), contentScale = ContentScale.Crop) }
                }
                Column(Modifier.weight(1f)) {
                    Text(v.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${v.ext.ifEmpty { "VIDEO" }} · ${fmtSize(v.size)}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetAction("play_arrow", "Play", true, false, Modifier.weight(1f)) { onDismiss(); onPlay() }
                SheetAction("favorite", if (fav) "Unfavorite" else "Favorite", false, fav, Modifier.weight(1f)) { scope.launch { store.toggleFav(v.uri.toString()) } }
                SheetAction("share", "Share", false, false, Modifier.weight(1f)) {
                    val i = Intent(Intent.ACTION_SEND).apply { type = "video/*"; putExtra(Intent.EXTRA_STREAM, v.uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    ctx.startActivity(Intent.createChooser(i, "Share video"))
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh)) {
                SheetRow("info", "Details") { details = true }
                if (v.path.isNotEmpty()) SheetRow("visibility_off", "Hide this folder") { scope.launch { store.toggleBlacklist(v.path) }; onDismiss() }
                if (inHistory) SheetRow("history", "Remove from history") { scope.launch { store.removeProgress(v.uri.toString()) }; onDismiss() }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.errorContainer.copy(alpha = 0.35f))) {
                SheetRow("delete", "Delete from device", danger = true) { confirmDel = true }
            }
        }
    }

    if (details) AlertDialog(
        onDismissRequest = { details = false }, title = { Text(v.name) },
        text = { Text("Format: ${v.ext.ifEmpty { "-" }}\nSize: ${fmtSize(v.size)}\nDuration: ${fmt(v.dur)}\nFolder: ${v.path.ifEmpty { v.folder }}") },
        confirmButton = { TextButton({ details = false }) { Text("Close") } },
    )
    if (confirmDel) AlertDialog(
        onDismissRequest = { confirmDel = false }, title = { Text("Delete this video?") }, text = { Text("The file is removed from your phone. Android will ask you to confirm.") },
        confirmButton = {
            TextButton({
                confirmDel = false
                if (Build.VERSION.SDK_INT >= 30) {
                    try { delLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(MediaStore.createDeleteRequest(ctx.contentResolver, listOf(v.uri)).intentSender).build()) }
                    catch (_: Exception) { Toast.makeText(ctx, "Couldn't delete", Toast.LENGTH_SHORT).show() }
                } else Toast.makeText(ctx, "Delete needs Android 11 or newer", Toast.LENGTH_SHORT).show()
            }) { Text("Delete", color = cs.error) }
        },
        dismissButton = { TextButton({ confirmDel = false }) { Text("Cancel") } },
    )
}
