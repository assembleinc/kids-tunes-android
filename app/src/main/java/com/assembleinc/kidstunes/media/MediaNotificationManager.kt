package com.assembleinc.kidstunes.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.support.v4.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.assembleinc.kidstunes.ui.main.MainActivity
import com.assembleinc.kidstunes.R
import com.assembleinc.kidstunes.model.Song
import com.squareup.picasso.Picasso
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread


/**
 * Created by Assemble, Inc. on 2019-05-20.
 */
class MediaNotificationManager(private val mediaPlaybackService: MediaPlaybackService) {

    // region Member variables

    var notificationManager: NotificationManager
        private set
    private var playAction: NotificationCompat.Action
    private var pauseAction: NotificationCompat.Action
    private var previousAction: NotificationCompat.Action
    private val nextAction: NotificationCompat.Action

    // endregion


    // region MediaNotificationManager

    init {
        notificationManager = mediaPlaybackService.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()

        playAction = NotificationCompat.Action(
            R.drawable.baseline_play_arrow_24,
            mediaPlaybackService.getString(R.string.play),
            MediaButtonReceiver.buildMediaButtonPendingIntent(mediaPlaybackService, PlaybackStateCompat.ACTION_PLAY)
        )

        pauseAction = NotificationCompat.Action(
            R.drawable.baseline_pause_24,
            mediaPlaybackService.getString(R.string.pause),
            MediaButtonReceiver.buildMediaButtonPendingIntent(mediaPlaybackService, PlaybackStateCompat.ACTION_PAUSE)
        )

        previousAction = NotificationCompat.Action(
            R.drawable.baseline_fast_rewind_24,
            mediaPlaybackService.getString(R.string.previous_track),
            MediaButtonReceiver.buildMediaButtonPendingIntent(mediaPlaybackService, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
        )

        nextAction = NotificationCompat.Action(
            R.drawable.baseline_fast_forward_24,
            mediaPlaybackService.getString(R.string.next_track),
            MediaButtonReceiver.buildMediaButtonPendingIntent(mediaPlaybackService, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
        )
    }

    fun getNotification(song: Song, token: MediaSessionCompat.Token, state: Int, currentPosition: Long): Notification {
        val builder = buildNotification(song, token, state, currentPosition)
        return builder.build()
    }

    private fun buildNotification(song: Song, token: MediaSessionCompat.Token, state: Int,
        currentPosition: Long): NotificationCompat.Builder {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) createChannel()

        val isPlaying = (state == PlaybackStateCompat.STATE_PLAYING) or (state == PlaybackStateCompat.STATE_BUFFERING)

        val builder = NotificationCompat.Builder(mediaPlaybackService, CHANNEL_ID)
        builder.setStyle(
            android.support.v4.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(token)
                .setShowActionsInCompactView(0, 1, 2)
                .setShowCancelButton(true)
                .setCancelButtonIntent(
                    MediaButtonReceiver.buildMediaButtonPendingIntent(mediaPlaybackService, PlaybackStateCompat.ACTION_STOP)
                )
        )
            .setColor(ContextCompat.getColor(mediaPlaybackService, R.color.colorAccent))
            .setSmallIcon(R.drawable.baseline_music_note_24)
            .setContentIntent(createContentIntent())
            .setContentTitle(song.name)
            .setContentText(song.albumName)
            .setLargeIcon(null)
            .setDeleteIntent(
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    mediaPlaybackService, PlaybackStateCompat.ACTION_STOP
                )
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (state == PlaybackStateCompat.STATE_PLAYING && currentPosition != PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN) {
            builder.setWhen(System.currentTimeMillis() - currentPosition)
            builder.setShowWhen(true)
            builder.setUsesChronometer(true)
        } else {
            builder.setWhen(0)
            builder.setShowWhen(false)
            builder.setUsesChronometer(false)
        }

        builder.addAction(previousAction)

        builder.addAction(if (isPlaying) pauseAction else playAction)

        builder.addAction(nextAction)

        doAsync {
            val bitmap = Picasso.get().load(song.artworkUrl).get()
            uiThread {
                builder.setLargeIcon(bitmap)
                notificationManager.notify(NOTIFICATION_ID, builder.build())
            }
        }

        return builder
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel() {
        if (null == notificationManager.getNotificationChannel(CHANNEL_ID)) {
            val name = "MediaSession"
            val description = "MediaSession and MediaPlayer"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.description = description
            channel.enableLights(true)
            channel.lightColor = Color.RED
            channel.enableVibration(true)
            channel.vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createContentIntent(): PendingIntent {
        val openUI = Intent(mediaPlaybackService, MainActivity::class.java)
        openUI.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        return PendingIntent.getActivity(mediaPlaybackService, REQUEST_CODE, openUI, PendingIntent.FLAG_CANCEL_CURRENT)
    }

    // endregion


    companion object {
        private val TAG = MediaNotificationManager::class.java.simpleName
        private val CANONICAL_NAME = MediaNotificationManager::class.java.simpleName
        private val CHANNEL_ID = "$CANONICAL_NAME.channel"
        private const val REQUEST_CODE = 501
        const val NOTIFICATION_ID = 412
    }
}
