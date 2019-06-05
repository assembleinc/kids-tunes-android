package com.assembleinc.kidstunes.ui.main

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.apple.android.sdk.authentication.AuthenticationFactory
import com.assembleinc.kidstunes.MainApplication
import com.assembleinc.kidstunes.R
import com.assembleinc.kidstunes.services.KeychainService
import kotlinx.android.synthetic.main.activity_sign_in.*
import javax.inject.Inject

/**
 * Created by Assemble, Inc. on 2019-05-26.
 */
class SignInActivity: AppCompatActivity() {

    @Inject lateinit var keychainService: KeychainService

    private var authenticationManager = AuthenticationFactory.createAuthenticationManager(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        (application as MainApplication).component.inject(this)

        setContentView(R.layout.activity_sign_in)

        signInButton.setOnClickListener { signIn() }

        closeButton.setOnClickListener { onBackPressed() }
    }

    private fun signIn() {
        val intent = authenticationManager.createIntentBuilder(getString(R.string.developer_token))
            .setHideStartScreen(true)
            .setStartScreenMessage(getString(R.string.connect_with_apple_music))
            .build()
        startActivityForResult(intent, REQUEST_CODE_APPLE_MUSIC_AUTH)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_APPLE_MUSIC_AUTH) {
            val result = authenticationManager.handleTokenResult(data)

            if (result.isError) {
                val error = result.error
                Log.e(TAG, "error: $error")
            }
            else {
                saveToken(result.musicUserToken)
                startMainActivity()
            }
        }
        else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun saveToken(musicUserToken: String) {
        keychainService.save(KeychainService.KEY_MUSIC_USER_TOKEN, musicUserToken)
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    companion object {
        private val TAG = SignInActivity::class.java.simpleName
        private const val REQUEST_CODE_APPLE_MUSIC_AUTH = 1
    }
}