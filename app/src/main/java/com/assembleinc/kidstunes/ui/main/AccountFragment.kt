package com.assembleinc.kidstunes.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.app.Fragment
import android.support.v4.media.session.MediaControllerCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.assembleinc.kidstunes.MainApplication
import com.assembleinc.kidstunes.R
import com.assembleinc.kidstunes.media.MediaBrowserController
import com.assembleinc.kidstunes.media.MediaSessionManager
import com.assembleinc.kidstunes.services.KeychainService
import kotlinx.android.synthetic.main.fragment_account.*
import javax.inject.Inject


/**
 * Created by Assemble, Inc. on 2019-05-26.
 */
class AccountFragment: Fragment() {

    @Inject lateinit var keychainService: KeychainService
    private lateinit var mediaBrowserConnection: MediaBrowserConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity?.application as MainApplication).component.inject(this)
        mediaBrowserConnection = MediaBrowserConnection(activity!!)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val root = inflater.inflate(R.layout.fragment_account, container, false)
        initLogOutButton(root)
        initVisitWebSiteButton(root)
        return root
    }

    override fun onStart() {
        super.onStart()
        mediaBrowserConnection.onStart()
    }

    override fun onStop() {
        super.onStop()
        mediaBrowserConnection.onStop()
    }

    private fun initLogOutButton(root: View) {
        val logOutButton = root.findViewById<Button>(R.id.signOutButton)
        logOutButton.setOnClickListener {
            keychainService.delete(KeychainService.KEY_MUSIC_USER_TOKEN)
            activity?.let {
                mediaBrowserConnection.mediaController?.let { mc ->
                    mc.sendCommand(MediaSessionManager.COMMAND_STOP, null, object: ResultReceiver(null) {
                        override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                            if (resultCode == MediaSessionManager.RESULT_OK) {
                                finishActivity()
                            }
                        }
                    })
                }
            }
        }
    }

    private fun finishActivity() {
        val intent = Intent(activity, SignInActivity::class.java)
        startActivity(intent)
        activity?.finish()
    }

    private fun initVisitWebSiteButton(root: View) {
        val visitWebSiteButton = root.findViewById<Button>(R.id.visitWebSiteButton)
        visitWebSiteButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://assembleinc.com/"))
            startActivity(intent)
        }
    }

    private inner class MediaBrowserConnection(context: Context): MediaBrowserController(context) {
        override fun onConnected(mediaController: MediaControllerCompat) {
            signOutButton.isEnabled = true
        }
    }

    // region Companion

    companion object {
        @JvmStatic
        fun newInstance(): AccountFragment {
            return AccountFragment()
        }
    }

    // endregion
}