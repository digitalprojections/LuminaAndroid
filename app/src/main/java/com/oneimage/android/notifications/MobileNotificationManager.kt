package com.oneimage.android.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.oneimage.android.BuildConfig
import com.oneimage.android.MainActivity
import com.oneimage.android.R
import com.oneimage.android.api.OneImageApi
import com.oneimage.android.api.awaitResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

object MobileNotificationManager {
    const val WORKFLOW_CHANNEL_ID = "workflow_updates"

    fun areNotificationsEnabled(context: Context): Boolean {
        val appContext = context.applicationContext
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return true
    }

    fun openNotificationSettings(context: Context) {
        val appContext = context.applicationContext
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${appContext.packageName}")
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        appContext.startActivity(intent)
    }

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            WORKFLOW_CHANNEL_ID,
            "Workflow updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Generation completion, failure, and cancellation updates."
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    suspend fun registerCurrentToken(context: Context) {
        val appContext = context.applicationContext
        val user = FirebaseAuth.getInstance().currentUser ?: return
        if (!areNotificationsEnabled(appContext)) return
        val token = FirebaseMessaging.getInstance().token.awaitResult()
        if (token.isBlank()) return

        OneImageApi.registerNotificationToken(
            baseUrl = BuildConfig.ONEIMAGE_API_BASE_URL.ifBlank { "https://genstudio.web.app/" },
            token = token,
            appVersion = BuildConfig.VERSION_NAME,
            uidHint = user.uid
        )
        ensureNotificationChannel(appContext)
    }

    fun showSystemNotification(
        context: Context,
        title: String,
        body: String,
        notificationId: String
    ) {
        ensureNotificationChannel(context)
        if (!areNotificationsEnabled(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notificationId", notificationId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId.hashCode().absoluteValue,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, WORKFLOW_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title.ifBlank { "GenStudio" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId.hashCode().absoluteValue, notification)
    }

    suspend fun markSeen(notificationIds: List<String>) {
        val distinctIds = notificationIds.distinct().filter { it.isNotBlank() }
        if (distinctIds.isEmpty()) return

        withContext(Dispatchers.IO) {
            OneImageApi.markNotificationsSeen(
                baseUrl = BuildConfig.ONEIMAGE_API_BASE_URL.ifBlank { "https://genstudio.web.app/" },
                notificationIds = distinctIds
            )
        }
    }
}
