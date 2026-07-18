package com.oneimage.android

import android.app.Application
import android.content.Context
import com.oneimage.android.api.WorkflowPricingRepository
import com.oneimage.android.notifications.MobileNotificationManager

class OneImageApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        MobileNotificationManager.ensureNotificationChannel(this)
        WorkflowPricingRepository.start()
    }

    companion object {
        lateinit var appContext: Context
            private set
    }
}
