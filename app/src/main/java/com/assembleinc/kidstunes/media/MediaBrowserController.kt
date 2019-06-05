package com.assembleinc.kidstunes.media

import android.content.*
import android.os.RemoteException
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.assembleinc.kidstunes.model.Song

/**
 * Created by Assemble, Inc. on 2019-05-16.
 */
open class MediaBrowserController(val context: Context) {

    // region Interfaces

    private interface CallbackCommand {
        fun perform(callback: MediaControllerCompat.Callback)
    }

    // endregion


    // region Member variables

    private val callbacks = ArrayList<MediaControllerCompat.Callback>()
    private val mediaBrowserConnectionCallback = MediaBrowserConnectionCallback()
    private val mediaControllerCallback: MediaControllerCallback = MediaControllerCallback()
    private var mediaBrowserSubscriptionCallback = MediaBrowserSubscriptionCallback()
    private var playbackServiceBroadcastReceiver = PlaybackServiceBroadcastReceiver()
    private var mediaBrowser: MediaBrowserCompat? = null
    var mediaController: MediaControllerCompat? = null
        private set

    // endregion


    // region MediaBrowserController

    fun onStart() {
        if (null == mediaBrowser) {
            mediaBrowser = MediaBrowserCompat(context,
                ComponentName(context, MediaPlaybackService::class.java),
                mediaBrowserConnectionCallback,
                null)
            mediaBrowser?.connect()
        }

        LocalBroadcastManager.getInstance(context)
            .registerReceiver(playbackServiceBroadcastReceiver, IntentFilter(MediaPlaybackService.ACTION_CURRENT_ITEM_CHANGED))
    }

    fun onStop() {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(playbackServiceBroadcastReceiver)
        mediaController?.unregisterCallback(mediaControllerCallback)
        mediaController = null
        mediaBrowser?.disconnect()
        mediaBrowser = null
    }

    fun subscribe(parentId: String) {
        mediaBrowser?.let {
            it.subscribe(parentId, mediaBrowserSubscriptionCallback)
        }
    }

    fun unsubscribe(parentId: String) {
        mediaBrowser?.let {
            it.unsubscribe(parentId, mediaBrowserSubscriptionCallback)
        }
    }

    fun getTransportControls(): MediaControllerCompat.TransportControls? {
        return mediaController?.transportControls
    }

    fun registerCallback(callback: MediaControllerCompat.Callback?) {
        callback?.let {
            callbacks.add(callback)
            mediaController?.let { controller ->
                controller.metadata?.let { metadata -> callback.onMetadataChanged(metadata) }
                controller.playbackState?.let { playbackState -> callback.onPlaybackStateChanged(playbackState) }
            }
        }
    }

    fun unregisterCallback(callback: MediaControllerCompat.Callback?) {
        callbacks.remove(callback)
    }

    private fun performOnAllCallbacks(command: CallbackCommand) {
        for (callback in callbacks) {
            command.perform(callback)
        }
    }

    protected open fun onConnected(mediaController: MediaControllerCompat) = Unit

    protected open fun onChildrenLoaded(parentId: String, children: List<MediaBrowserCompat.MediaItem>) = Unit

    protected fun onDisconnected() = Unit

    // endregion


    // region MediaBrowserConnectionCallback

    private inner class MediaBrowserConnectionCallback: MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            try {
                mediaController = MediaControllerCompat(context, mediaBrowser!!.sessionToken)
                mediaController?.let { mc ->
                    mc.registerCallback(mediaControllerCallback)
                    this@MediaBrowserController.onConnected(mc)
                }
            } catch (e: RemoteException) {
                throw RuntimeException(e)
            }

            mediaBrowser?.let { it.subscribe(it.root, mediaBrowserSubscriptionCallback) }
        }
    }

    // endregion


    // region MediaControllerCallback

    private inner class MediaControllerCallback: MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            performOnAllCallbacks(object : CallbackCommand {
                override fun perform(callback: MediaControllerCompat.Callback) {
                    callback.onMetadataChanged(metadata)
                }
            })
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            performOnAllCallbacks(object : CallbackCommand {
                override fun perform(callback: MediaControllerCompat.Callback) {
                    callback.onPlaybackStateChanged(state)
                }
            })
        }

        override fun onSessionDestroyed() {
            onPlaybackStateChanged(null)
            this@MediaBrowserController.onDisconnected()
        }
    }

    // endregion


    // region MediaBrowserSubscriptionCallback

    private inner class MediaBrowserSubscriptionCallback: MediaBrowserCompat.SubscriptionCallback() {
        override fun onChildrenLoaded(parentId: String, children: List<MediaBrowserCompat.MediaItem>) {
            this@MediaBrowserController.onChildrenLoaded(parentId, children)
        }
    }

    // endregion


    // region

    private inner class PlaybackServiceBroadcastReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                if (it.action == MediaPlaybackService.ACTION_CURRENT_ITEM_CHANGED) {
                    it.extras?.let { extras ->
                        val song = extras.getParcelable(MediaPlaybackService.EXTRA_CURRENT_ITEM) as Song
                        performOnAllCallbacks(object : CallbackCommand {
                            override fun perform(callback: MediaControllerCompat.Callback) {
                                callback.onMetadataChanged(song.toMediaMetadataCompat())
                            }
                        })
                    }
                }
            }
        }
    }

    // endregion


    companion object {
        private val TAG = MediaBrowserController::class.java.simpleName
    }
}
