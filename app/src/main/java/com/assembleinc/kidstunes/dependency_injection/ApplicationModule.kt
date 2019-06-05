package com.assembleinc.kidstunes.dependency_injection

import android.app.Application
import com.assembleinc.kidstunes.services.KeychainService
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

/**
 * Created by Assemble, Inc. on 2019-05-14.
 */
@Module
class ApplicationModule(private val application: Application) {
    @Provides
    @Singleton
    fun providesApplication() = application

    @Provides
    @Singleton
    fun providesKeychainService(application: Application): KeychainService {
        return KeychainService(application)
    }
}