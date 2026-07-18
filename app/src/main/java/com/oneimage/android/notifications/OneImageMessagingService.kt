package com.oneimage.android.notifications

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.oneimage.android.BuildConfig
import com.oneimage.android.api.OneImageApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OneImageMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (FirebaseAuth.getInstance().currentUser == null || token.isBlank()) return

        serviceScope.launch {
            runCatching {
                OneImageApi.registerNotificationToken(
                    baseUrl = BuildConfig.ONEIMAGE_API_BASE_URL.ifBlank { "https://genstudio.web.app/" },
                    token = token,
                    appVersion = BuildConfig.VERSION_NAME
                )
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "OneStudio"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "Workflow update"
        val notificationId = message.data["notificationId"]
            ?: message.messageId
            ?: System.currentTimeMillis().toString()

        MobileNotificationManager.showSystemNotification(
            context = applicationContext,
            title = title,
            body = body,
            notificationId = notificationId
        )
    }
}
