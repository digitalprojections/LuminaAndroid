package com.oneimage.android

import android.app.Application
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.oneimage.android.api.WorkflowPricingRepository
import com.oneimage.android.notifications.MobileNotificationManager

class OneImageApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        MobileNotificationManager.ensureNotificationChannel(this)
        WorkflowPricingRepository.start()
        Thread {
            MobileAds.initialize(this) {}
        }.start()
    }

    companion object {
        lateinit var appContext: Context
            private set
    }
}
