package com.oneimage.android

import android.app.Application
import android.content.Context

class OneImageApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }

    companion object {
        lateinit var appContext: Context
            private set
    }
}
