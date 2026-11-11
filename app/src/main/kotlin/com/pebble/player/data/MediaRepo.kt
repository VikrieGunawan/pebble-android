package com.pebble.player.data

import kotlin.coroutines.resume
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import android.os.Environment
import android.media.MediaScannerConnection
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Video(
    val id: Long,
    val uri: Uri,
    val name: String,
    val folder: String,
    val path: String,        // relative path, used as the blacklist key
    val dur: Long,
    val size: Long,
    val added: Long,
    val ext: String = "",
    val w: Int = 0,
    val h: Int = 0,
) {
    /** Exact resolution such as "1070×680"; falls back to the file extension when unknown. */
    val res: String get() = if (w > 0 && h > 0) "$w×$h" else ext
}

class MediaRepo(private val c: Context) {

    suspend fun scan(): List<Video> = withContext(Dispatchers.IO) {
        val out = ArrayList<Video>()
        val col = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val proj = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.RELATIVE_PATH,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
        )
        try {
            c.contentResolver.query(col, proj, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { q ->
                val iId = q.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val iName = q.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val iBucket = q.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val iPath = q.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)
                val iDur = q.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val iSize = q.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val iAdd = q.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val iW = q.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val iH = q.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                while (q.moveToNext()) {
                    val id = q.getLong(iId)
                    out += Video(
                        id = id,
                        uri = Uri.withAppendedPath(col, id.toString()),
                        name = (q.getString(iName) ?: "Video").substringBeforeLast('.'),
                        folder = q.getString(iBucket) ?: "Videos",
                        path = q.getString(iPath) ?: "",
                        dur = q.getLong(iDur),
                        size = q.getLong(iSize),
                        added = q.getLong(iAdd),
                        ext = (q.getString(iName) ?: "").substringAfterLast('.', "").uppercase().take(5),
                        w = q.getInt(iW),
                        h = q.getInt(iH),
                    )
                }
            }
        } catch (_: SecurityException) { }
        out
    }

    /**
     * MediaStore only lists files the system media scanner has already seen, so new or copied videos can be missing.
     * Walk the storage, find video files MediaStore doesn't know yet, and ask the scanner to index them.
     * Returns how many files were handed to the scanner.
     */
    @Suppress("DEPRECATION")
    suspend fun reindex(): Int = withContext(Dispatchers.IO) {
        try {
            val known = HashSet<String>()
            c.contentResolver.query(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), arrayOf(MediaStore.Video.Media.DATA), null, null, null)?.use { q ->
                while (q.moveToNext()) q.getString(0)?.let { known += it }
            }
            val roots = LinkedHashMap<String, File>()
            roots[Environment.getExternalStorageDirectory().path] = Environment.getExternalStorageDirectory()
            c.getExternalFilesDirs(null).forEach { f -> f?.path?.substringBefore("/Android")?.let { roots.putIfAbsent(it, File(it)) } }
            val exts = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "flv", "wmv", "ts", "mpg", "mpeg", "mts", "m2ts")
            val found = ArrayList<String>()
            for (r in roots.values) {
                r.walkTopDown()
                    .onEnter { dir -> dir == r || (!dir.name.startsWith(".") && dir.name != "Android" && !File(dir, ".nomedia").exists()) }
                    .filter { it.isFile && it.extension.lowercase() in exts && it.path !in known }
                    .take(500 - found.size)
                    .forEach { found += it.path }
            }
            if (found.isEmpty()) return@withContext 0
            withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine<Unit> { cont ->
                    var left = found.size
                    MediaScannerConnection.scanFile(c, found.toTypedArray(), null) { _, _ -> if (--left <= 0 && cont.isActive) cont.resume(Unit) }
                }
            }
            found.size
        } catch (_: Exception) { 0 }
    }

    /** A user-chosen cover image for this video (saved in the app's own folder). */
    fun coverFile(id: Long) = java.io.File(c.filesDir, "covers/$id.jpg")

    fun thumb(v: Video): Bitmap? {
        val f = coverFile(v.id)
        if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.path)?.let { return it }
        return try { c.contentResolver.loadThumbnail(v.uri, Size(480, 270), null) } catch (e: Exception) { null }
    }

    /** A video opened from outside the app (Intent.ACTION_VIEW). */
    fun fromUri(uri: Uri): Video {
        var name = "Video"
        var size = 0L
        try {
            c.contentResolver.query(uri, null, null, null, null)?.use { q ->
                if (q.moveToFirst()) {
                    val n = q.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val s = q.getColumnIndex(OpenableColumns.SIZE)
                    if (n >= 0) name = (q.getString(n) ?: name).substringBeforeLast('.')
                    if (s >= 0) size = q.getLong(s)
                }
            }
        } catch (_: Exception) { }
        return Video(uri.hashCode().toLong(), uri, name, "Opened", "", 0L, size, System.currentTimeMillis() / 1000)
    }
}
