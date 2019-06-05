package com.assembleinc.kidstunes.ui.main

import android.content.Context
import android.graphics.Outline
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.RecyclerView.SCROLL_STATE_IDLE
import android.support.v7.widget.SimpleItemAnimator
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.assembleinc.kidstunes.R
import com.assembleinc.kidstunes.media.MediaBrowserController
import com.assembleinc.kidstunes.media.MediaProvider
import com.assembleinc.kidstunes.media.MediaSessionManager
import com.assembleinc.kidstunes.model.Song
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.fragment_main.*
import kotlinx.android.synthetic.main.recycler_view_item_song.view.*
import kotlinx.android.synthetic.main.recycler_view_playlist_controls_header.view.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread

class FavoritesFragment : Fragment(), SectionsPagerAdapter.OnFragmentPageSelectedListener {

    // region Member variables

    private lateinit var songsAdapter: SongsAdapter
    private var songs: List<MediaBrowserCompat.MediaItem> = ArrayList()
    private var currentSong: Song? = null
    private var isPlaying: Boolean = false
    private var isCurrentPlaylist: Boolean = false
    private var isScrolling: Boolean = false
    private var mediaBrowserConnection: MediaBrowserConnection? = null
    private val mediaBrowserCallback = MediaBrowserCallback()

    // endregion


    // region: Fragment life cycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        songsAdapter = SongsAdapter()

        mediaBrowserConnection = MediaBrowserConnection(activity!!.applicationContext)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_main, container, false)
        initRecyclerView(root)
        initSwipeToRefreshLayout(root)
        return root
    }

    override fun onStart() {
        super.onStart()
        mediaBrowserConnection?.onStart()
        mediaBrowserConnection?.registerCallback(mediaBrowserCallback)
        if (songs.isEmpty()) {
            swipeRefreshLayout?.isRefreshing = true
            subscribeToProvider()
        }
    }

    override fun onStop() {
        super.onStop()
        swipeRefreshLayout?.isRefreshing = false
        unsubscribeToProvider()
        mediaBrowserConnection?.unregisterCallback(mediaBrowserCallback)
        mediaBrowserConnection?.onStop()
    }

    private fun initRecyclerView(root: View) {
        val recyclerView = root.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.adapter = songsAdapter
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.setHasFixedSize(true)
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                isScrolling = newState != SCROLL_STATE_IDLE
            }
        })
    }

    private fun initSwipeToRefreshLayout(root: View) {
        val swipeRefreshLayout = root.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(root.context, R.color.colorAccent))
        swipeRefreshLayout.isEnabled = true
        swipeRefreshLayout.setOnRefreshListener {
            unsubscribeToProvider()
            subscribeToProvider()
        }
    }

    private fun subscribeToProvider() {
        mediaBrowserConnection?.subscribe(MediaProvider.FAVORITES_ROOT_ID)
    }

    private fun unsubscribeToProvider() {
        mediaBrowserConnection?.unsubscribe(MediaProvider.FAVORITES_ROOT_ID)
    }

    private fun scrollToCurrentSong() {
        if (isScrolling) return

        doAsync {
            val layoutManager = (recyclerView.layoutManager as LinearLayoutManager)
            val indexOfCurrentSong = songs.indexOfFirst { it.mediaId == currentSong?.id }
            val positionOfCurrentSong = if (indexOfCurrentSong != -1) indexOfCurrentSong + 1 else indexOfCurrentSong
            val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
            val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
            val visibleItemsRange = firstVisibleItemPosition..lastVisibleItemPosition
            if (positionOfCurrentSong != -1 && positionOfCurrentSong !in visibleItemsRange) {
                uiThread { layoutManager.scrollToPositionWithOffset(positionOfCurrentSong, 0) }
            }
        }
    }

    private fun updateRecyclerView() {
        songsAdapter.notifyDataSetChanged()
    }

    private fun handleStateChange(bundle: Bundle?, playbackState: Int) {
        bundle?.let {
            val currentPlaylist = bundle.getString(MediaSessionManager.EXTRA_CURRENT_PLAYLIST_ID)
            isCurrentPlaylist = currentPlaylist == MediaProvider.FAVORITES_ROOT_ID
            currentSong = if (isCurrentPlaylist) bundle.getParcelable(MediaSessionManager.EXTRA_CURRENT_TRACK) as? Song else null
        }
        isPlaying = isCurrentPlaylist && (playbackState == PlaybackStateCompat.STATE_PLAYING) or (playbackState == PlaybackState.STATE_BUFFERING)
    }

    // endregion


    // region SectionsPagerAdapter.OnFragmentPageSelectedListener

    override fun onFragmentSelected() {
        if (songs.isEmpty()) {
            swipeRefreshLayout?.isRefreshing = true
            subscribeToProvider()
        }
    }

    override fun onFragmentUnselected() {
        swipeRefreshLayout?.isRefreshing = false
        unsubscribeToProvider()
    }

    // endregion


    // region SongsAdapter

    private inner class SongsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val VIEW_TYPE_HEADER = 0
        private val VIEW_TYPE_LIST_ITEM = 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when(viewType) {
                VIEW_TYPE_HEADER -> {
                    val view = LayoutInflater.from(parent.context).inflate(R.layout.recycler_view_playlist_controls_header, parent, false)
                    PlaylistControlsViewHolder(view, parent)
                }
                else -> {
                    val view = LayoutInflater.from(parent.context).inflate(R.layout.recycler_view_item_song, parent, false)
                    SongViewHolder(view, parent)
                }
            }
        }

        override fun getItemCount(): Int {
            return songs.size + 1
        }

        override fun getItemViewType(position: Int): Int {
            return when(position) {
                0 -> VIEW_TYPE_HEADER
                else -> VIEW_TYPE_LIST_ITEM
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (position == 0) {
                bindPlaylistControlsViewHolder(holder, position)
            }
            else {
                bindSongViewHolder(holder, position - 1)
            }
        }

        private fun bindPlaylistControlsViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val playlistControlsViewHolder = holder as PlaylistControlsViewHolder

            val playButtonResourceId = if (isPlaying) R.drawable.baseline_pause_50 else R.drawable.baseline_play_arrow_50
            val playButtonDrawable = ContextCompat.getDrawable(playlistControlsViewHolder.playButton.context, playButtonResourceId)
            playlistControlsViewHolder.playButton.setCompoundDrawablesWithIntrinsicBounds(null, playButtonDrawable, null, null)

            playlistControlsViewHolder.playButton.setOnClickListener {
                when {
                    isPlaying -> mediaBrowserConnection?.let {
                        it.mediaController?.let { mc ->
                            mc.transportControls.pause()
                        }
                    }
                    null != currentSong -> mediaBrowserConnection?.let {
                        it.mediaController?.let { mc ->
                            mc.transportControls.play()
                        }
                    }
                    else -> doAsync {
                        mediaBrowserConnection?.let {
                            it.mediaController?.let { mc ->
                                val bundle = Bundle().apply {
                                    putString(MediaSessionManager.EXTRA_QUEUE_IDENTIFIER, MediaProvider.FAVORITES_ROOT_ID)
                                }
                                mc.sendCommand(MediaSessionManager.COMMAND_SWAP_QUEUE, bundle, object: ResultReceiver(null) {
                                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                                        if (resultCode == MediaSessionManager.RESULT_ADD_QUEUE_ITEMS) {
                                            for (mediaItem in songs) {
                                                mc.addQueueItem(mediaItem.description)
                                            }

                                            mc.transportControls?.prepare()
                                        }

                                        val firstSong = songs[0]
                                        it.getTransportControls()?.setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_NONE)
                                        it.getTransportControls()?.playFromMediaId(firstSong.mediaId, null)
                                    }
                                })
                            }
                        }
                    }
                }
            }
            playlistControlsViewHolder.shuffleButton.setOnClickListener {
                doAsync {
                    mediaBrowserConnection?.let {
                        it.mediaController?.let { mc ->
                            val bundle = Bundle().apply {
                                putString(MediaSessionManager.EXTRA_QUEUE_IDENTIFIER, MediaProvider.FAVORITES_ROOT_ID)
                            }
                            mc.sendCommand(MediaSessionManager.COMMAND_SWAP_QUEUE, bundle, object: ResultReceiver(null) {
                                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                                    if (resultCode == MediaSessionManager.RESULT_ADD_QUEUE_ITEMS) {
                                        val shuffledSongs = songs.shuffled()

                                        for (mediaItem in shuffledSongs) {
                                            mc.addQueueItem(mediaItem.description)
                                        }

                                        mc.transportControls?.prepare()
                                    }

                                    val randomSongIndex = (0 until songs.size).random()
                                    val firstSong = songs[randomSongIndex]
                                    it.getTransportControls()?.setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_ALL)
                                    it.getTransportControls()?.playFromMediaId(firstSong.mediaId, null)
                                }
                            })
                        }
                    }
                }
            }
        }

        private fun bindSongViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val songMetadata = songs[position].description
            val songViewHolder = holder as SongViewHolder

            songViewHolder.songNameTextView.text = songMetadata.title
            songViewHolder.artistNameTextView.text = songMetadata.subtitle
            songViewHolder.rankPositionTextView.text = (position + 1).toString()
            songViewHolder.favoriteButton.visibility = View.INVISIBLE
            Picasso.get().load(songMetadata.iconUri)
                .into(songViewHolder.albumArtworkImageView)

            val currentPlayingItem = songMetadata.mediaId == currentSong?.id
            if (currentPlayingItem) {
                songViewHolder.songNameTextView.setTextColor(ContextCompat.getColor(songViewHolder.songNameTextView.context, R.color.red))
                songViewHolder.artistNameTextView.setTextColor(ContextCompat.getColor(songViewHolder.artistNameTextView.context, R.color.red))
                songViewHolder.playbackButton.visibility = View.VISIBLE
                songViewHolder.playbackButton.isEnabled = true
                songViewHolder.playbackButton.setOnClickListener {
                    mediaBrowserConnection?.let { mediaBrowserConnection ->
                        mediaBrowserConnection.mediaController?.let { mediaController ->
                            if (isPlaying) mediaController.transportControls.pause() else mediaController.transportControls.play()
                        }
                    }
                }
            }
            else {
                songViewHolder.songNameTextView.setTextColor(ContextCompat.getColor(songViewHolder.songNameTextView.context, R.color.colorAccent))
                songViewHolder.artistNameTextView.setTextColor(ContextCompat.getColor(songViewHolder.artistNameTextView.context, R.color.colorAccent))
                songViewHolder.playbackButton.visibility = View.GONE
                songViewHolder.playbackButton.isEnabled = false
            }

            if (isPlaying) {
                songViewHolder.playbackButton.setImageResource(R.drawable.baseline_pause_circle_filled_32)
            }
            else {
                songViewHolder.playbackButton.setImageResource(R.drawable.baseline_play_circle_filled_32)
            }

            songViewHolder.containerLayout.setOnClickListener {
                songViewHolder.playbackButton.setImageResource(R.drawable.baseline_pause_circle_filled_32)
                doAsync {
                    mediaBrowserConnection?.let {
                        it.mediaController?.let { mc ->
                            val bundle = Bundle().apply {
                                putString(MediaSessionManager.EXTRA_QUEUE_IDENTIFIER, MediaProvider.FAVORITES_ROOT_ID)
                            }
                            mc.sendCommand(MediaSessionManager.COMMAND_SWAP_QUEUE, bundle, object: ResultReceiver(null) {
                                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                                    if (resultCode == MediaSessionManager.RESULT_ADD_QUEUE_ITEMS) {
                                        for (mediaItem in songs) {
                                            mc.addQueueItem(mediaItem.description)
                                        }

                                        mc.transportControls?.prepare()
                                    }

                                    it.getTransportControls()?.setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_NONE)
                                    it.getTransportControls()?.playFromMediaId(songMetadata.mediaId, null)
                                }
                            })
                        }
                    }
                }
            }

            val provider = object: ViewOutlineProvider() {
                override fun getOutline(view: View?, outline: Outline?) {
                    outline?.setRoundRect(0, 0, view!!.width, view.height, 8f)
                }
            }

            songViewHolder.albumArtworkImageView.apply {
                outlineProvider = provider
                clipToOutline = true
            }

            songViewHolder.artworkMaskView.apply {
                outlineProvider = provider
                clipToOutline = true
            }

            songViewHolder.playbackButton.apply {
                outlineProvider = provider
                clipToOutline = true
            }
        }

        inner class SongViewHolder(itemView: View, parent: ViewGroup?) : RecyclerView.ViewHolder(itemView) {
            val containerLayout: ViewGroup = itemView.containerLayout
            val songNameTextView: TextView = itemView.songNameTextView
            val artistNameTextView: TextView = itemView.artistNameTextView
            val albumArtworkImageView: ImageView = itemView.artworkImageView
            val artworkMaskView: View = itemView.artworkMaskView
            val rankPositionTextView: TextView = itemView.rankPositionTextView
            val favoriteButton: ImageButton = itemView.favoriteButton
            val playbackButton: ImageButton = itemView.playbackImageButton
        }

        inner class PlaylistControlsViewHolder(itemView: View, parent: ViewGroup?) : RecyclerView.ViewHolder(itemView) {
            var playButton: Button = itemView.playButton
            var shuffleButton: Button = itemView.shuffleButton
        }
    }

    // endregion


    // region MediaBrowserConnection

    private inner class MediaBrowserConnection(context: Context) : MediaBrowserController(context) {

        override fun onConnected(mediaController: MediaControllerCompat) {
            mediaBrowserConnection?.mediaController?.let { mc ->
                mc.sendCommand(MediaSessionManager.COMMAND_GET_CURRENT_TRACK, null, object: ResultReceiver(null) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        if (resultCode == MediaSessionManager.RESULT_CURRENT_TRACK) {
                            resultData?.let { bundle ->
                                handleStateChange(bundle, mc.playbackState.state)
                                updateRecyclerView()
                                scrollToCurrentSong()
                            }
                        }
                    }
                })
            }
        }

        override fun onChildrenLoaded(parentId: String, children: List<MediaBrowserCompat.MediaItem>) {
            songs = children
            songsAdapter.notifyDataSetChanged()
            swipeRefreshLayout?.isRefreshing = false
        }
    }

    // endregion


    // region MediaControllerCompat.Callback

    private inner class MediaBrowserCallback: MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            metadata?.let {
                mediaBrowserConnection?.mediaController?.let { mc ->
                    mc.sendCommand(MediaSessionManager.COMMAND_GET_CURRENT_TRACK, null, object: ResultReceiver(null) {
                        override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                            if (resultCode == MediaSessionManager.RESULT_CURRENT_TRACK) {
                                resultData?.let { bundle ->
                                    handleStateChange(bundle, mc.playbackState.state)
                                    updateRecyclerView()
                                    scrollToCurrentSong()
                                }
                            }
                        }
                    })
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: PlaybackStateCompat?) {
            playbackState?.let {
                handleStateChange(null, it.state)
                updateRecyclerView()
            }
        }
    }

    // endregion


    // region Companion

    companion object {
        private val TAG = FavoritesFragment::class.java.simpleName

        @JvmStatic
        fun newInstance(): FavoritesFragment {
            return FavoritesFragment()
        }

        init {
            System.loadLibrary("c++_shared")
            System.loadLibrary("appleMusicSDK")
        }
    }

    // endregion
}
