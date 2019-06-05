package com.assembleinc.kidstunes.dependency_injection

import com.assembleinc.kidstunes.ui.main.MainActivity
import com.assembleinc.kidstunes.ui.main.MediaPlayerActivity
import com.assembleinc.kidstunes.media.MediaPlaybackService
import com.assembleinc.kidstunes.ui.main.AccountFragment
import com.assembleinc.kidstunes.ui.main.SignInActivity
import dagger.Component
import javax.inject.Singleton

/**
 * Created by Assemble, Inc. on 2019-05-14.
 */
@Singleton
@Component(modules = [ApplicationModule::class, NetworkingModule::class])
interface ApplicationComponent {
    fun inject(mainActivity: MainActivity)
    fun inject(signInActivity: SignInActivity)
    fun inject(accountFragment: AccountFragment)
    fun inject(mediaPlayerActivity: MediaPlayerActivity)
    fun inject(mediaPlaybackService: MediaPlaybackService)
}