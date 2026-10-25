package com.pebble.player.ui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.OutlinedTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val app = LocalApp.current
    val coverFile = app.media.coverFile(v.id)
    val coverVer = ThumbCache.ver.intValue
    val hasCover = remember(coverVer) { coverFile.exists() }
    var renaming by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(v.name) }
    var busy by remember { mutableStateOf<String?>(null) }
    var copyMode by remember { mutableStateOf(true) }
    var afterWrite by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
    fun realName(): String {
        val n = try {
            ctx.contentResolver.query(v.uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (_: Exception) { null }
        return n ?: (v.name + "." + v.ext.lowercase())
    }
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) afterWrite?.invoke()
        afterWrite = null
    }
    fun renameNow(cv: android.content.ContentValues) {
        try {
            if (ctx.contentResolver.update(v.uri, cv, null, null) > 0) { toast("Renamed"); onRescan(); onDismiss() } else toast("Couldn't rename")
        } catch (e: SecurityException) {
            try {
                val sender = if (Build.VERSION.SDK_INT >= 30) MediaStore.createWriteRequest(ctx.contentResolver, listOf(v.uri)).intentSender
                else (e as? android.app.RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
                if (sender == null) toast("Couldn't rename") else {
                    afterWrite = { renameNow(cv) }
                    writeLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build())
                }
            } catch (_: Exception) { toast("Couldn't rename") }
        } catch (_: Exception) { toast("Couldn't rename") }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { saveCover(ctx, u, coverFile) }
            if (ok) { ThumbCache.invalidate(v.id); toast("Cover image updated") } else toast("Couldn't use that image")
        }
    }
    val destPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) scope.launch {
            val name = realName()
            busy = if (copyMode) "Copying…" else "Moving…"
            val ok = withContext(Dispatchers.IO) { copyToTree(ctx, v.uri, tree, name) }
            busy = null
            if (!ok) toast("Couldn't copy the video")
            else if (copyMode) { toast("Copied"); onRescan(); onDismiss() }
            else if (Build.VERSION.SDK_INT >= 30) {
                toast("Copied — confirm to remove the original")
                try { delLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(MediaStore.createDeleteRequest(ctx.contentResolver, listOf(v.uri)).intentSender).build()) }
                catch (_: Exception) { toast("Copied, but couldn't remove the original") }
            } else {
                try { ctx.contentResolver.delete(v.uri, null, null); toast("Moved") } catch (_: Exception) { toast("Copied, but couldn't remove the original") }
                onRescan(); onDismiss()
            }
        }
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
                SheetRow("edit", "Rename") { newName = v.name; renaming = true }
                SheetRow("image", "Cover image") { coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                if (hasCover) SheetRow("delete", "Remove cover image") { coverFile.delete(); ThumbCache.invalidate(v.id) }
                SheetRow("content_copy", "Copy to folder…") { copyMode = true; destPicker.launch(null) }
                SheetRow("drive_file_move", "Move to folder…") { copyMode = false; destPicker.launch(null) }
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

    if (renaming) AlertDialog(
        onDismissRequest = { renaming = false }, title = { Text("Rename video") },
        text = { OutlinedTextField(newName, { newName = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = {
            TextButton({
                renaming = false
                val clean = newName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "")
                if (clean.isNotEmpty()) {
                    val ext = realName().substringAfterLast('.', v.ext.lowercase())
                    renameNow(android.content.ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, "$clean.$ext") })
                }
            }) { Text("Rename") }
        },
        dismissButton = { TextButton({ renaming = false }) { Text("Cancel") } },
    )
    busy?.let { msg ->
        AlertDialog(
            onDismissRequest = { }, confirmButton = { }, title = { Text(msg) },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(48.dp)) } },
        )
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


private val SORT_OPTS = listOf(
    Triple("schedule", "Recently added", 0),
    Triple("sort_by_alpha", "Name", 1),
    Triple("timer", "Duration", 2),
    Triple("hard_drive", "File size", 3),
)

/** Bottom sheet like the website's "Sort by" menu. */
@Composable
fun SortSheet(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = cs.surfaceContainer) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile("sort", 46.dp, filled = true)
                Column {
                    Text("Sort by", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Library order", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SORT_OPTS.forEach { (ic, label, i) ->
                    val on = current == i
                    val bg by androidx.compose.animation.animateColorAsState(if (on) cs.primaryContainer else androidx.compose.ui.graphics.Color.Transparent, label = "sortBg")
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(bg).clickable { onPick(i); onDismiss() }.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(ic, null, Modifier.size(24.dp), tint = if (on) cs.primary else cs.onSurfaceVariant)
                        Text(label, Modifier.weight(1f), fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold)
                        PAnim(on, enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut()) {
                            Icon("check", null, Modifier.size(24.dp), tint = cs.primary)
                        }
                    }
                }
            }
        }
    }
}


private fun saveCover(ctx: Context, src: Uri, out: java.io.File): Boolean = try {
    val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, src)) { dec, info, _ ->
        dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        dec.setTargetSampleSize(maxOf(1, maxOf(info.size.width, info.size.height) / 1280))
    }
    val w = bmp.width
    val h = bmp.height
    val tw = if (w * 9 > h * 16) h * 16 / 9 else w
    val th = if (w * 9 > h * 16) h else w * 9 / 16
    val crop = Bitmap.createBitmap(bmp, (w - tw) / 2, (h - th) / 2, tw, th)
    out.parentFile?.mkdirs()
    out.outputStream().use { crop.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    true
} catch (_: Exception) { false }

private fun copyToTree(ctx: Context, src: Uri, tree: Uri, name: String): Boolean = try {
    val cr = ctx.contentResolver
    val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    val dest = DocumentsContract.createDocument(cr, parent, cr.getType(src) ?: "video/*", name)
    if (dest == null) false else {
        cr.openInputStream(src)!!.use { i -> cr.openOutputStream(dest)!!.use { o -> i.copyTo(o) } }
        true
    }
} catch (_: Exception) { false }

/** ⋮ menu of a folder in the Library tree (covers every video inside it, subfolders included). */
@Composable
fun FolderMenuSheet(path: String, videos: List<Video>, prefs: Prefs, store: Store, onPlay: (Video, List<Video>) -> Unit, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val vids = remember(path, videos) { videos.filter { it.path.startsWith(path) }.sortedWith { a, b -> NaturalOrder.compare(a.name, b.name) } }
    val name = path.trimEnd('/').substringAfterLast('/').ifBlank { "Videos" }
    var details by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = cs.surfaceContainer) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile("folder", 48.dp, filled = true)
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${vids.size} video${if (vids.size == 1) "" else "s"} · ${fmtSize(vids.sumOf { it.size })}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetAction("play_arrow", "Play all", true, false, Modifier.weight(1f)) { onDismiss(); vids.firstOrNull()?.let { onPlay(it, vids) } }
                SheetAction("shuffle", "Shuffle", false, false, Modifier.weight(1f)) { val l = vids.shuffled(); onDismiss(); l.firstOrNull()?.let { onPlay(it, l) } }
                SheetAction("folder_open", "Open", false, false, Modifier.weight(1f)) { onDismiss(); onOpen() }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh)) {
                SheetRow("info", "Details") { details = true }
                SheetRow("visibility_off", "Hide this folder") {
                    scope.launch { vids.map { it.path }.distinct().filter { it !in prefs.blacklist }.forEach { store.toggleBlacklist(it) } }
                    onDismiss()
                }
            }
        }
    }
    if (details) AlertDialog(
        onDismissRequest = { details = false }, title = { Text(name) },
        text = { Text("Path: ${path.ifEmpty { "/" }}\nVideos: ${vids.size}\nSize: ${fmtSize(vids.sumOf { it.size })}\nDuration: ${fmt(vids.sumOf { it.dur })}\nSubfolders: ${vids.map { it.path }.distinct().count { it != path }}") },
        confirmButton = { TextButton({ details = false }) { Text("Close") } },
    )
}
