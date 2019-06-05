package com.assembleinc.kidstunes

import android.app.Application
import com.assembleinc.kidstunes.dependency_injection.ApplicationComponent
import com.assembleinc.kidstunes.dependency_injection.ApplicationModule
import com.assembleinc.kidstunes.dependency_injection.DaggerApplicationComponent
import com.facebook.stetho.Stetho

/**
 * Created by Assemble, Inc. on 2019-05-14.
 */
class MainApplication: Application() {
    val component: ApplicationComponent by lazy {
        DaggerApplicationComponent
            .builder()
            .applicationModule(ApplicationModule(this))
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) initStetho()
    }

    private fun initStetho() {
        val initializerBuilder = Stetho.newInitializerBuilder(this)
        initializerBuilder.enableWebKitInspector(
            Stetho.defaultInspectorModulesProvider(this)
        )
        initializerBuilder.enableDumpapp(
            Stetho.defaultDumperPluginsProvider(this)
        )

        val initializer = initializerBuilder.build()

        Stetho.initialize(initializer)
    }
}