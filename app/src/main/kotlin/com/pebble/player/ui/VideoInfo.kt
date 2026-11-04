package com.pebble.player.ui

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Format
import androidx.media3.exoplayer.MetadataRetriever
import com.pebble.player.data.Video
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** What the Details screen shows about a video's file: picture, tracks, subtitles inside the file and where it came from. */
internal class VideoInfo(
    val resolution: String?,
    val video: String?,
    val audio: List<String>,
    val subs: List<String>,
    val container: String?,
    val source: String?,
    val storage: String?,
    val modified: String?,
)

private fun codecName(mime: String?): String = when (mime) {
    null -> "Unknown"
    "video/avc" -> "H.264 (AVC)"
    "video/hevc" -> "H.265 (HEVC)"
    "video/x-vnd.on2.vp9" -> "VP9"
    "video/x-vnd.on2.vp8" -> "VP8"
    "video/av01" -> "AV1"
    "video/mp4v-es" -> "MPEG-4"
    "video/mpeg2" -> "MPEG-2"
    "video/3gpp" -> "H.263"
    "audio/mp4a-latm" -> "AAC"
    "audio/ac3" -> "Dolby Digital"
    "audio/eac3" -> "Dolby Digital Plus"
    "audio/eac3-joc" -> "Dolby Atmos"
    "audio/opus" -> "Opus"
    "audio/vorbis" -> "Vorbis"
    "audio/mpeg" -> "MP3"
    "audio/true-hd" -> "Dolby TrueHD"
    "audio/vnd.dts", "audio/vnd.dts.hd" -> "DTS"
    "audio/flac" -> "FLAC"
    "audio/raw" -> "PCM"
    "text/x-ssa" -> "ASS / SSA"
    "application/x-subrip" -> "SRT"
    "text/vtt" -> "WebVTT"
    "application/pgs" -> "PGS (image)"
    "application/dvbsubs" -> "DVB (image)"
    "application/vobsub" -> "VobSub (image)"
    "application/ttml+xml" -> "TTML"
    else -> mime.substringAfter('/').uppercase()
}

private fun language(code: String?): String? =
    code?.takeIf { it.isNotBlank() && it != "und" }?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.ENGLISH).ifBlank { it } }

private fun channels(n: Int) = when (n) { 1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> "$n ch" }

private fun mbps(bps: Int) = if (bps >= 1_000_000) "%.1f Mbps".format(bps / 1_000_000.0) else "${bps / 1000} kbps"

private fun containerName(mime: String?, ext: String) = when (mime) {
    "video/mp4" -> "MP4"
    "video/x-matroska" -> "Matroska (MKV)"
    "video/webm" -> "WebM"
    "video/avi", "video/x-msvideo" -> "AVI"
    "video/quicktime" -> "QuickTime (MOV)"
    "video/3gpp", "video/3gpp2" -> "3GP"
    "video/mp2ts" -> "MPEG-TS"
    null -> ext.ifBlank { null }
    else -> ext.ifBlank { mime.substringAfter('/').uppercase() }
}

private suspend fun readTracks(ctx: Context, uri: Uri): androidx.media3.exoplayer.source.TrackGroupArray? {
    val r = MetadataRetriever.Builder(ctx, MediaItem.fromUri(uri)).build()
    try {
        val f = r.retrieveTrackGroups()
        val out = withTimeoutOrNull(8000) {
            suspendCancellableCoroutine<androidx.media3.exoplayer.source.TrackGroupArray?> { cont ->
                f.addListener({ cont.resume(try { f.get() } catch (_: Exception) { null }) }, Executor { it.run() })
            }
        }
        if (out == null) f.cancel(true)
        return out
    } finally { r.close() }
}

internal suspend fun loadVideoInfo(ctx: Context, v: Video): VideoInfo = withContext(Dispatchers.IO) {
    var owner: String? = null; var mime: String? = null; var modified = 0L; var w = 0; var h = 0
    try {
        ctx.contentResolver.query(
            v.uri, arrayOf(MediaStore.MediaColumns.OWNER_PACKAGE_NAME, MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT),
            null, null, null,
        )?.use { q -> if (q.moveToFirst()) { owner = q.getString(0); mime = q.getString(1); modified = q.getLong(2); w = q.getInt(3); h = q.getInt(4) } }
    } catch (_: Exception) { }

    var video: Format? = null
    val audio = ArrayList<String>(); val subs = ArrayList<String>()
    val groups = try { readTracks(ctx, v.uri) } catch (_: Exception) { null }
    if (groups != null) for (i in 0 until groups.length) {
        val g = groups[i]
        val f = g.getFormat(0)
        when (g.type) {
            C.TRACK_TYPE_VIDEO -> if (video == null) video = f
            C.TRACK_TYPE_AUDIO -> audio += listOfNotNull(
                codecName(f.sampleMimeType),
                f.channelCount.takeIf { it > 0 }?.let { channels(it) },
                f.sampleRate.takeIf { it > 0 }?.let { "%.1f kHz".format(it / 1000.0) },
                language(f.language), f.label?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            C.TRACK_TYPE_TEXT -> subs += listOfNotNull(
                codecName(if (f.sampleMimeType == "application/x-media3-cues") f.codecs else f.sampleMimeType),
                language(f.language), f.label?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
        }
    }

    val vf = video
    val swap = vf != null && (vf.rotationDegrees == 90 || vf.rotationDegrees == 270)
    val rw = if (vf != null && vf.width > 0) (if (swap) vf.height else vf.width) else w
    val rh = if (vf != null && vf.height > 0) (if (swap) vf.width else vf.height) else h
    val resolution = if (rw > 0 && rh > 0) {
        val long = maxOf(rw, rh); val short = minOf(rw, rh)
        val tag = when { short >= 2160 || long >= 3800 -> "4K"; short >= 1440 || long >= 2500 -> "2K"; short >= 1080 || long >= 1900 -> "Full HD"; short >= 720 || long >= 1280 -> "HD"; else -> "SD" }
        "$rw × $rh · $tag"
    } else null

    val bitrate = vf?.let { if (it.averageBitrate > 0) it.averageBitrate else it.bitrate }?.takeIf { it > 0 }
        ?: if (v.dur > 0 && v.size > 0) (v.size * 8 * 1000 / v.dur).toInt() else null
    val hdr = vf?.colorInfo?.colorTransfer?.let { if (it == C.COLOR_TRANSFER_ST2084) "HDR10" else if (it == C.COLOR_TRANSFER_HLG) "HLG HDR" else null }
    val videoLine = listOfNotNull(
        vf?.let { codecName(it.sampleMimeType) },
        vf?.frameRate?.takeIf { it > 0f }?.let { "%s fps".format(if (it % 1f < 0.05f) it.toInt().toString() else "%.2f".format(it)) },
        bitrate?.let { mbps(it) },
        hdr ?: if (vf != null) "SDR" else null,
    ).joinToString(" · ").ifBlank { null }

    val pm = ctx.packageManager
    val source = owner?.takeIf { it.isNotBlank() }?.let { pkg ->
        try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
    } ?: "Unknown (copied or downloaded without an app record)"
    val storage = try {
        val vol = MediaStore.getVolumeName(v.uri)
        if (vol == MediaStore.VOLUME_EXTERNAL_PRIMARY) "Internal storage" else "SD card or USB drive ($vol)"
    } catch (_: Exception) { null }
    val mod = modified.takeIf { it > 0 }?.let { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(it * 1000)) }

    VideoInfo(resolution, videoLine, audio, subs, containerName(mime, v.ext), source, storage, mod)
}
