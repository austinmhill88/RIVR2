package com.reboundrocket.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ReboundRocketApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
