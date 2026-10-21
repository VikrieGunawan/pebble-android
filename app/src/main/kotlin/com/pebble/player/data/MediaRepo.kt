package com.pebble.player.data

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
)

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
                    )
                }
            }
        } catch (_: SecurityException) { }
        out
    }

    fun thumb(v: Video): Bitmap? = try {
        c.contentResolver.loadThumbnail(v.uri, Size(480, 270), null)
    } catch (e: Exception) { null }

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
