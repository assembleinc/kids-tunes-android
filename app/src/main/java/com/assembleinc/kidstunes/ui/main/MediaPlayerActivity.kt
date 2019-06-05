package com.assembleinc.kidstunes.ui.main

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v7.app.AppCompatActivity
import android.transition.AutoTransition
import android.util.Log
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import com.android.volley.*
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.JsonRequest
import com.assembleinc.kidstunes.MainApplication
import com.assembleinc.kidstunes.R
import com.assembleinc.kidstunes.media.MediaBrowserController
import com.assembleinc.kidstunes.media.MediaProvider
import com.assembleinc.kidstunes.media.MediaSessionManager
import com.assembleinc.kidstunes.model.Song
import com.assembleinc.kidstunes.services.KeychainService
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_media_player.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject


/**
 * Created by Assemble, Inc. on 2019-05-29.
 */
class MediaPlayerActivity: AppCompatActivity() {

    // region Member variables

    @Inject lateinit var requestQueue: RequestQueue
    @Inject lateinit var keychainService: KeychainService
    private lateinit var mediaBrowserConnection: MediaBrowserConnection
    private val mediaBrowserCallback = MediaBrowserCallback()
    private var progressAnimator: ValueAnimator? = null
    private var currentSong: Song? = null
    private var playerPosition: Long = 0
    private var favoritesIndexes: ArrayList<String?> = ArrayList()
    private var favoritesPlaylistId: String? = null

    // endregion

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        (application as MainApplication).component.inject(this)

        setContentView(R.layout.activity_media_player)

        mediaBrowserConnection = MediaBrowserConnection(this)

        initMediaControls()

        configTransition()

        songNameTextView.isSelected = true
        songNameTextView.setSingleLine(true)

        artistNameTextView.isSelected = true
        artistNameTextView.setSingleLine(true)

        val provider = object: ViewOutlineProvider() {
            override fun getOutline(view: View?, outline: Outline?) {
                outline?.setRoundRect(0, 0, view!!.width, view.height, 16f)
            }
        }

        artworkMaskView.apply {
            outlineProvider = provider
            clipToOutline = true
        }

        albumImageView.apply {
            outlineProvider = provider
            clipToOutline = true
        }

        seekBar.setOnTouchListener { _, _ -> true }

        postponeEnterTransition()
    }

    override fun onStart() {
        super.onStart()

        mediaBrowserConnection.onStart()

        mediaBrowserConnection.registerCallback(mediaBrowserCallback)

        mediaBrowserConnection.subscribe(MediaProvider.FAVORITES_ROOT_ID)
    }

    override fun onStop() {
        super.onStop()

        mediaBrowserConnection.unsubscribe(MediaProvider.FAVORITES_ROOT_ID)

        mediaBrowserConnection.unregisterCallback(mediaBrowserCallback)

        mediaBrowserConnection.onStop()
    }

    private fun configTransition() {
        val transition = AutoTransition()
        transition.excludeTarget(android.R.id.statusBarBackground, true)
        transition.excludeTarget(android.R.id.navigationBarBackground, true)
        transition.duration = 250

        window.enterTransition = transition
        window.exitTransition = transition
    }

    private fun initMediaControls() {
        playButton.setOnClickListener {
            it.visibility = View.INVISIBLE
            pauseButton.visibility = View.VISIBLE
            mediaBrowserConnection.getTransportControls()?.play()
        }

        pauseButton.setOnClickListener {
            it.visibility = View.INVISIBLE
            playButton.visibility = View.VISIBLE
            mediaBrowserConnection.getTransportControls()?.pause()
        }

        nextButton.setOnClickListener {
            mediaBrowserConnection.getTransportControls()?.skipToNext()
        }

        previousButton.setOnClickListener {
            mediaBrowserConnection.getTransportControls()?.skipToPrevious()
        }

        closeButton.setOnClickListener {
            onBackPressed()
        }
    }

    private inner class MediaBrowserCallback: MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            seekBar.progress = 0
            seekBar.max = 100
            metadata?.let {
                mediaBrowserConnection.mediaController?.let { mc ->
                    val bundle = Bundle()
                    bundle.putString(MediaSessionManager.EXTRA_TRACK_ID, metadata.description.mediaId)
                    mc.sendCommand(MediaSessionManager.COMMAND_GET_TRACK, bundle, object: ResultReceiver(null) {
                        override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                            if (resultCode == MediaSessionManager.RESULT_TRACK) {
                                resultData?.let { bundle ->
                                    val fetchedSong = bundle.getParcelable(MediaSessionManager.EXTRA_TRACK) as Song
                                    bindSong(fetchedSong)
                                }
                            }
                        }
                    })
                }
            }
            resetProgressAnimator()
        }

        override fun onPlaybackStateChanged(playbackState: PlaybackStateCompat?) {
            playbackState?.let {
                bindPlaybackStateButtons(it.state)
                if (it.state == PlaybackStateCompat.STATE_PLAYING) {
                    startProgressAnimator()
                } else {
                    stopProgressAnimator()
                }
            }
        }
    }

    private fun bindSong(song: Song) {
        if (currentSong?.id != song.id) {
            currentSong = song
            val backgroundColor = Color.parseColor(song.backgroundColorHex)
            val textColor1 = Color.parseColor(song.textColorHex)
            val textColor2 = Color.parseColor(song.textColorAccentHex)

            mainLayout.setBackgroundColor(backgroundColor)
            playButton.imageTintList = ColorStateList.valueOf(textColor2)
            pauseButton.imageTintList = ColorStateList.valueOf(textColor2)
            previousButton.imageTintList = ColorStateList.valueOf(textColor2)
            nextButton.imageTintList = ColorStateList.valueOf(textColor2)
            favoriteButton.imageTintList = ColorStateList.valueOf(textColor2)
            songNameTextView.setTextColor(ColorStateList.valueOf(textColor1))
            artistNameTextView.setTextColor(ColorStateList.valueOf(textColor2))
            seekBar.progressDrawable.colorFilter = PorterDuffColorFilter(textColor2, PorterDuff.Mode.MULTIPLY)
            seekBar.thumb.colorFilter = PorterDuffColorFilter(textColor2, PorterDuff.Mode.SRC_IN)
            closeButton.imageTintList = ColorStateList.valueOf(textColor1)
            trackElapsedTime.setTextColor(textColor1)
            trackTotalTime.setTextColor(textColor1)
            progressBar.indeterminateTintList = ColorStateList.valueOf(textColor2)
            songNameTextView.text = song.name
            artistNameTextView.text = song.artistName
            setTimeElapsed()
            setTimeRemaining()
            bindFavoriteButton()
            Picasso.get().load(song.fullArtworkUrl).into(albumImageView)
        }
    }

    private fun favoriteSong() {
        if (null != favoritesPlaylistId && null != currentSong) {
            val songId = currentSong!!.id
            val playlistId = favoritesPlaylistId!!

            val url = "https://api.music.apple.com/v1/me/library/playlists/$playlistId/tracks"

            val song = JSONObject()
            song.put("id", songId)
            song.put("type", "songs")
            val songs = JSONArray()
            songs.put(song)
            val data = JSONObject()
            data.put("data", songs)

            favoriteButton.visibility = View.INVISIBLE
            progressBar.visibility = View.VISIBLE

            val request = object: JsonRequest<Boolean>(
                Method.POST, url, data.toString(),
                Response.Listener {
                    favoritesIndexes.add(songId)
                    favoriteButton.visibility = View.VISIBLE
                    progressBar.visibility = View.INVISIBLE
                    bindFavoriteButton()
                },
                Response.ErrorListener { error ->
                    Log.e(TAG, "$error")
                    favoriteButton.visibility = View.VISIBLE
                    progressBar.visibility = View.INVISIBLE
                    bindFavoriteButton()
                })
            {
                override fun getHeaders(): MutableMap<String, String> {
                    val headers = HashMap<String, String>()
                    headers["Authorization"] = "Bearer ${getString(R.string.developer_token)}"
                    headers["Music-User-Token"] = keychainService.fetch(KeychainService.KEY_MUSIC_USER_TOKEN) ?: ""
                    return headers
                }

                override fun parseNetworkResponse(response: NetworkResponse?): Response<Boolean> {
                    return if (204 == response?.statusCode) Response.success(true, HttpHeaderParser.parseCacheHeaders(response)) else Response.error(
                        VolleyError()
                    )
                }
            }
            request.retryPolicy = DefaultRetryPolicy(
                DefaultRetryPolicy.DEFAULT_TIMEOUT_MS * 10,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
            request.setShouldCache(false)
            requestQueue.add(request)
        }
    }

    private fun bindFavoriteButton() {
        currentSong?.let {
            val isFavorite = favoritesIndexes.contains(it.id)
            val imageResourceId = if (isFavorite) R.drawable.baseline_favorite_50 else R.drawable.outline_favorite_border_50
            favoriteButton.setImageResource(imageResourceId)
            favoriteButton.isEnabled = !isFavorite
            favoriteButton.setOnClickListener { button ->
                button.isEnabled = false
                favoriteSong()
            }
        }
    }

    private fun bindPlaybackStateButtons(playbackState: Int) {
        if (playbackState == PlaybackStateCompat.STATE_PLAYING) {
            playButton.visibility = View.INVISIBLE
            pauseButton.visibility = View.VISIBLE
        }
        else if (playbackState == PlaybackStateCompat.STATE_PAUSED) {
            playButton.visibility = View.VISIBLE
            pauseButton.visibility = View.INVISIBLE
        }
    }

    private val trackDuration: Long
    get() {
            return currentSong?.durationInMillis ?: 0
        }

    private val timeRemainingInMillis: Long
    get() {
        val percentageRemaining = (seekBar.max - seekBar.progress) / 100f
        return (trackDuration * percentageRemaining).toLong()
    }

    private val timeElapsedInMillis: Long
    get() {
        val progressPercentage = (seekBar.progress) / 100f
        return (trackDuration * progressPercentage).toLong()
    }

    private fun setTimeRemaining() {
        trackTotalTime.text = "- ${millisToMinutesAndSeconds(timeRemainingInMillis)}"
    }

    private fun setTimeElapsed() {
        trackElapsedTime.text = millisToMinutesAndSeconds(timeElapsedInMillis)
    }

    private fun millisToMinutesAndSeconds(millis: Long): String {
        val minutes = millis / (60 * 1000)
        val seconds = millis / 1000 % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    private fun startProgressAnimator() {
        progressAnimator = ValueAnimator.ofInt(seekBar.progress, seekBar.max).setDuration(timeRemainingInMillis)
        progressAnimator?.apply {
            interpolator = LinearInterpolator()
            addUpdateListener { valueAnimator ->
                val animatedValue =  valueAnimator.animatedValue as Int
                seekBar.progress = animatedValue
                setTimeElapsed()
                setTimeRemaining()
            }
            start()
        }
    }

    private fun stopProgressAnimator() {
        progressAnimator?.cancel()
        progressAnimator = null
    }

    private fun resetProgressAnimator() {
        stopProgressAnimator()
        startProgressAnimator()
    }

    // region MediaBrowserConnection

    private inner class MediaBrowserConnection(context: Context) : MediaBrowserController(context) {
        override fun onConnected(mediaController: MediaControllerCompat) {
            mediaBrowserConnection.mediaController?.let { mc ->
                mc.sendCommand(MediaSessionManager.COMMAND_GET_CURRENT_TRACK, null, object: ResultReceiver(null) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        if (resultCode == MediaSessionManager.RESULT_CURRENT_TRACK) {
                            resultData?.let { bundle ->
                                val song = bundle.getParcelable(MediaSessionManager.EXTRA_CURRENT_TRACK) as? Song
                                song?.let {
                                    bindSong(song)
                                    startPostponedEnterTransition()
                                }
                            }
                        }
                    }
                })
                mc.sendCommand(MediaSessionManager.COMMAND_GET_CURRENT_TRACK_ELAPSED_TIME, null, object: ResultReceiver(null) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        if (resultCode == MediaSessionManager.RESULT_CURRENT_TRACK_ELAPSED_TIME) {
                            resultData?.let { bundle ->
                                playerPosition = bundle.getLong(MediaSessionManager.EXTRA_CURRENT_TRACK_ELAPSED_TIME)
                                val progress = ((playerPosition / trackDuration.toFloat()) * 100).toInt()
                                seekBar.progress = progress
                                stopProgressAnimator()
                                if (mc.playbackState.state == PlaybackStateCompat.STATE_PLAYING) {
                                    startProgressAnimator()
                                }
                                bindPlaybackStateButtons(mc.playbackState.state)
                            }
                        }
                    }
                })
                mc.sendCommand(MediaSessionManager.COMMAND_GET_FAVORITES_PLAYLIST_ID, null, object: ResultReceiver(null) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        if (resultCode == MediaSessionManager.RESULT_FAVORITES_PLAYLIST_ID) {
                            resultData?.let { bundle -> favoritesPlaylistId = bundle.getString(MediaSessionManager.EXTRA_FAVORITES_PLAYLIST_ID) }
                        }
                    }
                })
            }
        }

        override fun onChildrenLoaded(parentId: String, children: List<MediaBrowserCompat.MediaItem>) {
            doAsync {
                if (parentId == MediaProvider.FAVORITES_ROOT_ID) {
                    val songsIndexes = children.map { it.mediaId }

                    uiThread {
                        favoritesIndexes = ArrayList(songsIndexes)
                        favoriteButton.visibility = View.VISIBLE
                        progressBar.visibility = View.INVISIBLE
                        bindFavoriteButton()
                    }
                }
            }
        }
    }

    // endregion

    companion object {
        private val TAG = MediaPlayerActivity::class.java.simpleName
    }
}