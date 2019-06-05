package com.assembleinc.kidstunes.util

import android.content.Context
import com.apple.android.sdk.authentication.TokenProvider
import com.assembleinc.kidstunes.R
import com.assembleinc.kidstunes.services.KeychainService

/**
 * Created by Assemble, Inc. on 2019-05-16.
 */
class AppleMusicTokenProvider(private val context: Context, private val keychainService: KeychainService): TokenProvider {
    override fun getDeveloperToken(): String {
        return context.getString(R.string.developer_token)
    }

    override fun getUserToken(): String? {
        return keychainService.fetch(KeychainService.KEY_MUSIC_USER_TOKEN)
    }
}