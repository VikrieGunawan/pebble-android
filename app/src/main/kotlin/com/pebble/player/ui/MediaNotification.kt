package com.pebble.player.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.pebble.player.MainActivity
import com.pebble.player.R

/**
 * Android's own media controls (notification shade and lock screen): cover, title, seek bar and prev / play-pause / next.
 * Built on a platform MediaSession that mirrors the ExoPlayer, so the system draws the controls.
 */
internal class PlayerMediaSession(
    private val ctx: Context,
    private val player: ExoPlayer,
    private val onPrev: () -> Unit,
    private val onNext: () -> Unit,
) {
    private val session = MediaSession(ctx, "PebblePlayer")
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private var title = ""
    private var art: Bitmap? = null
    private var hasPrev = false
    private var hasNext = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                A_TOGGLE -> if (player.playWhenReady) player.pause() else player.play()
                A_PREV -> onPrev()
                A_NEXT -> onNext()
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(p: Player, e: Player.Events) {
            if (e.containsAny(
                    Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_PLAYBACK_PARAMETERS_CHANGED, Player.EVENT_TIMELINE_CHANGED,
                )
            ) refresh()
        }
    }

    init {
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { player.play() }
            override fun onPause() { player.pause() }
            override fun onSeekTo(pos: Long) { player.seekTo(pos) }
            override fun onSkipToNext() { onNext() }
            override fun onSkipToPrevious() { onPrev() }
        })
        session.isActive = true
        ContextCompat.registerReceiver(ctx, receiver, IntentFilter().apply { addAction(A_TOGGLE); addAction(A_PREV); addAction(A_NEXT) }, ContextCompat.RECEIVER_NOT_EXPORTED)
        player.addListener(listener)
    }

    /** Title, cover and whether there is a previous / next video. */
    fun info(title: String, art: Bitmap?, hasPrev: Boolean, hasNext: Boolean) {
        this.title = title; this.art = art; this.hasPrev = hasPrev; this.hasNext = hasNext
        refresh()
    }

    private fun pi(action: String) = PendingIntent.getBroadcast(ctx, action.hashCode(), Intent(action).setPackage(ctx.packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun act(icon: Int, label: String, action: String) = Notification.Action.Builder(Icon.createWithResource(ctx, icon), label, pi(action)).build()

    fun refresh() {
        val st = player.playbackState
        val wants = player.playWhenReady && st != Player.STATE_ENDED && st != Player.STATE_IDLE
        val playing = wants && st == Player.STATE_READY
        val dur = player.duration.takeIf { it > 0 } ?: -1L

        val md = MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title).putString(MediaMetadata.METADATA_KEY_ARTIST, "Pebble Player")
        if (dur > 0) md.putLong(MediaMetadata.METADATA_KEY_DURATION, dur)
        art?.let { md.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) }
        session.setMetadata(md.build())

        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SEEK_TO
        if (hasPrev) actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        if (hasNext) actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        val state = when { wants && st == Player.STATE_BUFFERING -> PlaybackState.STATE_BUFFERING; playing -> PlaybackState.STATE_PLAYING; else -> PlaybackState.STATE_PAUSED }
        session.setPlaybackState(
            PlaybackState.Builder().setActions(actions)
                .setState(state, player.currentPosition.coerceAtLeast(0L), if (playing) player.playbackParameters.speed else 0f).build(),
        )

        val b = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title).setContentText("Pebble Player")
            .setLargeIcon(art)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE))
        val compact = ArrayList<Int>()
        var n = 0
        if (hasPrev) { b.addAction(act(R.drawable.ic_notif_prev, "Previous", A_PREV)); compact += n++ }
        b.addAction(act(if (playing) R.drawable.ic_notif_pause else R.drawable.ic_notif_play, if (playing) "Pause" else "Play", A_TOGGLE)); compact += n++
        if (hasNext) { b.addAction(act(R.drawable.ic_notif_next, "Next", A_NEXT)); compact += n++ }
        b.setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(*compact.toIntArray()))
        nm.notify(ID, b.build())
    }

    fun release() {
        player.removeListener(listener)
        try { ctx.unregisterReceiver(receiver) } catch (_: Exception) { }
        session.isActive = false
        session.release()
        nm.cancel(ID)
    }

    private companion object {
        const val CHANNEL = "playback"
        const val ID = 4201
        const val A_TOGGLE = "com.pebble.player.TOGGLE"
        const val A_PREV = "com.pebble.player.PREV"
        const val A_NEXT = "com.pebble.player.NEXT"
    }
}
