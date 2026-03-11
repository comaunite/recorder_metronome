package com.recorder.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.recorder.MainActivity
import com.recorder.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Foreground service that owns the playback notification.
 *
 * The ViewModel drives it via [startForegroundService] / [stopService] calls with Intent
 * extras. Notification button taps are emitted back on the companion [playPauseRequested]
 * SharedFlow so the ViewModel can toggle the engine without any direct coupling.
 */
class PlaybackService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID      = "recorder_playback"
        const val ACTION_PLAY_PAUSE = "com.recorder.action.PLAY_PAUSE"

        // Observed by PlaybackViewModel to react to notification play/pause taps
        private val _playPauseRequested = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val playPauseRequested: SharedFlow<Unit> = _playPauseRequested.asSharedFlow()

        // Intent extra keys
        const val EXTRA_TITLE       = "title"
        const val EXTRA_IS_PLAYING  = "isPlaying"
        const val EXTRA_POSITION_MS = "positionMs"
        const val EXTRA_DURATION_MS = "durationMs"
    }

    private lateinit var notificationManager: NotificationManager
    private lateinit var mediaSession: MediaSessionCompat
    private var foregroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createChannel()

        mediaSession = MediaSessionCompat(this, "RecorderPlayback").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay()  { _playPauseRequested.tryEmit(Unit) }
                override fun onPause() { _playPauseRequested.tryEmit(Unit) }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> _playPauseRequested.tryEmit(Unit)
            else -> {
                val title      = intent?.getStringExtra(EXTRA_TITLE)      ?: ""
                val isPlaying  = intent?.getBooleanExtra(EXTRA_IS_PLAYING, false) ?: false
                val positionMs = intent?.getLongExtra(EXTRA_POSITION_MS, 0L) ?: 0L
                val durationMs = intent?.getLongExtra(EXTRA_DURATION_MS, 0L) ?: 0L

                updatePlaybackState(isPlaying, positionMs)
                val notification = buildNotification(title, isPlaying, positionMs, durationMs)

                if (!foregroundStarted) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                    foregroundStarted = true
                } else {
                    notificationManager.notify(NOTIFICATION_ID, notification)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mediaSession.release()
        super.onDestroy()
    }

    // -------------------------------------------------------------------------

    private fun buildNotification(
        title: String,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long
    ): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPausePending = PendingIntent.getService(
            this, 1,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon  = if (isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play
        val playPauseLabel = if (isPlaying) "Pause" else "Play"

        // Scale ms → 0..1000 for setProgress (avoids Int overflow on long files)
        val progressMax  = 1000
        val progressCur  = if (durationMs > 0)
            ((positionMs.toDouble() / durationMs) * progressMax).toInt().coerceIn(0, progressMax)
        else 0

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(if (isPlaying) "Playing" else "Paused")
            .setContentIntent(openAppIntent)
            .setProgress(progressMax, progressCur, false)
            .addAction(playPauseIcon, playPauseLabel, playPausePending)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0)
            )
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setSilent(true)
            .build()
    }

    private fun updatePlaybackState(isPlaying: Boolean, positionMs: Long) {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, positionMs, 1f)
                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE)
                .build()
        )
    }

    private fun createChannel() {
        NotificationChannel(
            CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows current recording playback status"
            setShowBadge(false)
        }.also { notificationManager.createNotificationChannel(it) }
    }
}
