package com.assembleinc.kidstunes.dependency_injection

import android.app.Application
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

/**
 * Created by Assemble, Inc. on 2019-05-14.
 */
@Module
class NetworkingModule {
    @Provides
    @Singleton
    fun providesRequestQueue(application: Application): RequestQueue {
        return Volley.newRequestQueue(application)
    }
}