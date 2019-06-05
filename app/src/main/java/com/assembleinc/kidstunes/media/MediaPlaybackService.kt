package com.assembleinc.kidstunes.media

import android.content.Intent
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserServiceCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.android.volley.RequestQueue
import com.apple.android.music.playback.controller.MediaPlayerController
import com.apple.android.music.playback.controller.MediaPlayerControllerFactory
import com.apple.android.music.playback.model.MediaPlayerException
import com.apple.android.music.playback.model.PlaybackState
import com.apple.android.music.playback.model.PlayerQueueItem
import com.assembleinc.kidstunes.MainApplication
import com.assembleinc.kidstunes.model.Song
import com.assembleinc.kidstunes.services.KeychainService
import com.assembleinc.kidstunes.util.AppleMusicTokenProvider
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import javax.inject.Inject


/**
 * Created by Assemble, Inc. on 2019-05-16.
 */
class MediaPlaybackService: MediaBrowserServiceCompat(), MediaPlayerController.Listener {

    // region Member variables

    private lateinit var mediaSessionManager: MediaSessionManager
    private lateinit var player: MediaPlayerController
    private lateinit var mediaProvider: MediaProvider
    private lateinit var mediaNotificationManager: MediaNotificationManager
    private var serviceInStartedState = false
    @Inject lateinit var requestQueue: RequestQueue
    @Inject lateinit var keychainService: KeychainService

    // endregion


    // region MediaPlaybackService

    override fun onCreate() {
        super.onCreate()
        (application as MainApplication).component.inject(this)

        initPlayer()
        initMediaProvider()
        initMediaSessionManager()
        initMediaNotificationManager()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.stop()
        mediaSessionManager.onDestroy()
    }

    private fun initPlayer() {
        player = MediaPlayerControllerFactory.createLocalController(this.applicationContext, AppleMusicTokenProvider(this, keychainService))
        player.addListener(this)
    }

    private fun initMediaSessionManager() {
        mediaSessionManager = MediaSessionManager(this, player, mediaProvider)
        sessionToken = mediaSessionManager.sessionToken
    }

    private fun initMediaProvider() {
        mediaProvider = MediaProvider(applicationContext, requestQueue, AppleMusicTokenProvider(this, keychainService))
    }

    private fun initMediaNotificationManager() {
        mediaNotificationManager = MediaNotificationManager(this)
    }

    private fun updateNotificationForItemChanged(currentItem: PlayerQueueItem) {
        var song: Song? = null
        currentItem.item.subscriptionStoreId?.let { song = mediaProvider.getSong(it) }
        doAsync {
            while (player.currentPosition == PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN) {}

            uiThread {
                song?.let {
                    val notification = mediaNotificationManager.getNotification(it, mediaSessionManager.sessionToken,
                        PlaybackStateCompat.STATE_PLAYING, player.currentPosition)
                    mediaNotificationManager.notificationManager
                        .notify(MediaNotificationManager.NOTIFICATION_ID, notification)
                }
            }
        }
    }

    private fun updateQueue(currentItem: PlayerQueueItem) {
        var song: Song? = null
        currentItem.item.subscriptionStoreId?.let { song = mediaProvider.getSong(it) }
        mediaSessionManager.updateQueue(song)
    }

    private fun moveServiceToStartedState() {
        var song: Song? = null
        mediaSessionManager.currentTrackMediaId?.let { song = mediaProvider.getSong(it) }
        song?.let {
            val notification = mediaNotificationManager.getNotification(it, mediaSessionManager.sessionToken,
                PlaybackStateCompat.STATE_PLAYING, player.currentPosition)

            if (!serviceInStartedState) {
                ContextCompat.startForegroundService(this@MediaPlaybackService,
                    Intent(this@MediaPlaybackService, MediaPlaybackService::class.java))
                serviceInStartedState = true
            }

            startForeground(MediaNotificationManager.NOTIFICATION_ID, notification)
        }
    }

    private fun moveServiceOutOfStartedState() {
        stopForeground(true)
        stopSelf()
        serviceInStartedState = false
    }

    private fun updateNotificationForPause() {
        var song: Song? = null
        mediaSessionManager.currentTrackMediaId?.let { song = mediaProvider.getSong(it) }
        song?.let {
            stopForeground(false)
            val notification = mediaNotificationManager.getNotification(it, mediaSessionManager.sessionToken,
                PlaybackStateCompat.STATE_PAUSED, player.currentPosition)
            mediaNotificationManager.notificationManager
                .notify(MediaNotificationManager.NOTIFICATION_ID, notification)
        }
    }

    private fun updatePlaybackState(@PlaybackState currentState: Int, buffering: Boolean) {
        val state = convertPlaybackState(currentState, buffering)
        mediaSessionManager.updatePlaybackState(state)
    }

    // endregion


    // region MediaBrowserServiceCompat

    override fun onLoadChildren(parentId: String, result: Result<List<MediaBrowserCompat.MediaItem>>) {
        mediaProvider.loadMediaItems(parentId, result)
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        return BrowserRoot(MediaProvider.ROOT_ID, null)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    // endregion


    // region MediaPlayerController.Listener

    override fun onItemEnded(playerController: MediaPlayerController, queueItem: PlayerQueueItem, endPosition: Long) = Unit

    override fun onCurrentItemChanged(playerController: MediaPlayerController, previousItem: PlayerQueueItem?, currentItem: PlayerQueueItem?) {
        updatePlaybackState(playerController.playbackState, playerController.isBuffering)
        currentItem?.let {
            updateNotificationForItemChanged(it)
            sendItemChangedBroadcast(it)
            updateQueue(it)
        }
    }

    override fun onPlaybackShuffleModeChanged(playerController: MediaPlayerController, currentShuffleMode: Int) = Unit

    override fun onPlaybackStateUpdated(playerController: MediaPlayerController) = Unit

    override fun onPlaybackStateChanged(playerController: MediaPlayerController, previousState: Int, currentState: Int) {
        updatePlaybackState(currentState, playerController.isBuffering)

        when(convertPlaybackState(currentState, playerController.isBuffering)) {
            PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.STATE_BUFFERING -> {
                moveServiceToStartedState()
            }
            PlaybackStateCompat.STATE_PAUSED -> {
                updateNotificationForPause()
            }
            PlaybackStateCompat.STATE_STOPPED -> {
                moveServiceOutOfStartedState()
            }
        }
    }

    override fun onPlaybackError(playerController: MediaPlayerController, error: MediaPlayerException) = Unit

    override fun onPlaybackRepeatModeChanged(playerController: MediaPlayerController, currentRepeatMode: Int) = Unit

    override fun onPlaybackQueueChanged(playerController: MediaPlayerController, playbackQueueItems: MutableList<PlayerQueueItem>) = Unit

    override fun onBufferingStateChanged(playerController: MediaPlayerController, buffering: Boolean) {
        updatePlaybackState(playerController.playbackState, playerController.isBuffering)
    }

    override fun onMetadataUpdated(playerController: MediaPlayerController, currentItem: PlayerQueueItem) = Unit

    override fun onPlayerStateRestored(playerController: MediaPlayerController) = Unit

    override fun onPlaybackQueueItemsAdded(playerController: MediaPlayerController, queueInsertionType: Int, containerType: Int, itemType: Int) = Unit

    // endregion

    private fun sendItemChangedBroadcast(currentItem: PlayerQueueItem) {
        var song: Song? = null
        currentItem.item.subscriptionStoreId?.let { song = mediaProvider.getSong(it) }
        val localBroadcastManager = LocalBroadcastManager.getInstance(this)
        val intent = Intent(ACTION_CURRENT_ITEM_CHANGED)
        intent.putExtra(EXTRA_CURRENT_ITEM, song)
        localBroadcastManager.sendBroadcast(intent)
    }


    companion object {
        private val TAG = MediaPlaybackService::class.java.simpleName
        private val CANONICAL_NAME = MediaPlaybackService::class.java.canonicalName
        val ACTION_CURRENT_ITEM_CHANGED = "$CANONICAL_NAME.action_current_item_changed"
        val EXTRA_CURRENT_ITEM = "$CANONICAL_NAME.extra_current_item"

        init {
            try {
                System.loadLibrary("c++_shared")
                System.loadLibrary("appleMusicSDK")
            } catch (e: Exception) {
                Log.e(TAG, "Could not load library due to: ${Log.getStackTraceString(e)}")
                throw e
            }
        }

        private fun convertPlaybackState(@PlaybackState playbackState: Int, buffering: Boolean): Int {
            return when (playbackState) {
                PlaybackState.STOPPED -> PlaybackStateCompat.STATE_STOPPED
                PlaybackState.PAUSED -> PlaybackStateCompat.STATE_PAUSED
                PlaybackState.PLAYING -> if (buffering) PlaybackStateCompat.STATE_BUFFERING else PlaybackStateCompat.STATE_PLAYING
                else -> PlaybackStateCompat.STATE_NONE
            }
        }
    }
}
