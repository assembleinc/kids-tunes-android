package com.assembleinc.kidstunes.ui.main

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Outline
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v7.app.AppCompatActivity
import android.transition.AutoTransition
import android.util.Pair
import android.view.View
import android.view.ViewOutlineProvider
import com.assembleinc.kidstunes.MainApplication
import com.assembleinc.kidstunes.R
import com.assembleinc.kidstunes.media.MediaBrowserController
import com.assembleinc.kidstunes.media.MediaSessionManager
import com.assembleinc.kidstunes.model.Song
import com.assembleinc.kidstunes.services.KeychainService
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_main.*
import javax.inject.Inject


class MainActivity : AppCompatActivity() {

    @Inject lateinit var keychainService: KeychainService
    private lateinit var mediaBrowserConnection: MediaBrowserConnection
    private val mediaBrowserCallback = MediaBrowserCallback()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        (application as MainApplication).component.inject(this)

        setContentView(R.layout.activity_main)

        initViewPager()

        artworkMaskView.setOnClickListener { onAlbumViewClick() }

        mediaBrowserConnection = MediaBrowserConnection(this)

        configTransition()

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
    }

    override fun onStart() {
        super.onStart()

        validateSession()

        mediaBrowserConnection.onStart()

        mediaBrowserConnection.registerCallback(mediaBrowserCallback)
    }

    override fun onStop() {
        super.onStop()

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

    private fun initViewPager() {
        val sectionsPagerAdapter = SectionsPagerAdapter(this, supportFragmentManager)
        viewPager.adapter = sectionsPagerAdapter
        tabs.setupWithViewPager(viewPager)
        tabs.setTabTextColors(getColor(R.color.colorPrimaryDark), getColor(R.color.colorAccent))

        val states = arrayOf(
            intArrayOf(android.R.attr.state_selected),
            intArrayOf(android.R.attr.state_enabled),
            intArrayOf(-android.R.attr.state_enabled)
        )
        val colors = intArrayOf(
            getColor(R.color.colorAccent),
            getColor(R.color.colorPrimaryDark),
            getColor(R.color.colorPrimary)
        )

        tabs.tabIconTint = ColorStateList(states, colors)
        tabs.tabTextColors = ColorStateList(states, colors)

        TAB_ICONS.forEachIndexed { index, icon -> tabs.getTabAt(index)?.icon = getDrawable(icon) }
    }

    private fun validateSession() {
        if (keychainService.fetch(KeychainService.KEY_MUSIC_USER_TOKEN).isNullOrBlank()) {
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun onAlbumViewClick() {
        val intent = Intent(this, MediaPlayerActivity::class.java)
        val cardView = Pair<View, String>(artworkMaskView, "cardView")
        val options = ActivityOptions.makeSceneTransitionAnimation(this, cardView)
        startActivity(intent, options.toBundle())
    }

    private inner class MediaBrowserCallback: MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            metadata?.let {
                if (artworkMaskView.visibility != View.VISIBLE) artworkMaskView.visibility = View.VISIBLE
                Picasso.get().load(metadata.getString(MediaMetadataCompat.METADATA_KEY_ART_URI)).into(albumImageView)
            }
        }
    }

    // region MediaBrowserConnection

    private inner class MediaBrowserConnection(context: Context) : MediaBrowserController(context) {
        override fun onConnected(mediaController: MediaControllerCompat) {
            mediaBrowserConnection.let {
                it.mediaController?.let { mc ->
                    mc.sendCommand(MediaSessionManager.COMMAND_GET_CURRENT_TRACK, null, object: ResultReceiver(null) {
                        override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                            if (resultCode == MediaSessionManager.RESULT_CURRENT_TRACK) {
                                resultData?.let { bundle ->
                                    val song = bundle.getParcelable(MediaSessionManager.EXTRA_CURRENT_TRACK) as? Song
                                    song?.let {
                                        if (artworkMaskView.visibility != View.VISIBLE) artworkMaskView.visibility = View.VISIBLE
                                        Picasso.get().load(song.fullArtworkUrl).into(albumImageView)
                                    }
                                }
                            }
                        }
                    })
                }
            }
        }

        override fun onChildrenLoaded(parentId: String, children: List<MediaBrowserCompat.MediaItem>) = Unit
    }

    // endregion

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}