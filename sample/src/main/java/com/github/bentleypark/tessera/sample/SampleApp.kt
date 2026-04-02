package com.github.bentleypark.tessera.sample

import android.app.Application
import timber.log.Timber

class SampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}
