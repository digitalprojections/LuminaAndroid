package com.oneimage.android.ui.shared

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.oneimage.android.notifications.MobileNotificationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AppNotificationTone {
    Success,
    Error,
    Info
}

data class AppNotification(
    val id: Long,
    val title: String,
    val message: String,
    val tone: AppNotificationTone,
    val durationMs: Long
)

class AppNotificationState {
    private val _notifications = mutableStateListOf<AppNotification>()
    private var nextId = 0L

    val notifications: List<AppNotification>
        get() = _notifications

    fun show(
        title: String,
        message: String,
        tone: AppNotificationTone = AppNotificationTone.Info,
        durationMs: Long = 4_800L
    ) {
        nextId += 1
        _notifications.add(
            0,
            AppNotification(
                id = nextId,
                title = title,
                message = message,
                tone = tone,
                durationMs = durationMs
            )
        )
        while (_notifications.size > 3) {
            _notifications.removeAt(_notifications.lastIndex)
        }
    }

    fun dismiss(id: Long) {
        _notifications.removeAll { it.id == id }
    }

    fun clear() {
        _notifications.clear()
    }
}

@Composable
fun rememberAppNotificationState(): AppNotificationState = remember { AppNotificationState() }

@Composable
fun AppNotificationHost(
    state: AppNotificationState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal).asPaddingValues())
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.notifications.forEach { notification ->
            LaunchedEffect(notification.id) {
                delay(notification.durationMs)
                state.dismiss(notification.id)
            }
            AppNotificationCard(
                notification = notification,
                onDismiss = { state.dismiss(notification.id) }
            )
        }
    }
}

@Composable
fun TaskNotificationObserver(
    notificationState: AppNotificationState
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val firestore = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()
    var uid by remember { mutableStateOf(auth.currentUser?.uid) }
    val displayedIds = remember(uid) { mutableSetOf<String>() }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            uid = firebaseAuth.currentUser?.uid
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    DisposableEffect(uid) {
        val userId = uid
        if (userId.isNullOrBlank()) {
            notificationState.clear()
            onDispose { }
        } else {
            val listener = firestore.collection("users")
                .document(userId)
                .collection("notifications")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener { snapshot, _ ->
                    val notifications = snapshot?.documents
                        ?.filter { document -> document.get("readAt") == null }
                        ?.map { document ->
                            StoredNotification(
                                id = document.id,
                                title = document.getString("title").orEmpty(),
                                body = document.getString("body").orEmpty(),
                                tone = document.getString("tone").orEmpty()
                            )
                        }
                        .orEmpty()

                    val newlyDisplayedIds = mutableListOf<String>()
                    notifications.forEach { notification ->
                        if (displayedIds.add(notification.id)) {
                            notificationState.showStoredNotification(notification)
                            newlyDisplayedIds += notification.id
                        }
                    }

                    if (newlyDisplayedIds.isNotEmpty()) {
                        scope.launch {
                            runCatching { MobileNotificationManager.markSeen(newlyDisplayedIds) }
                        }
                    }
                }

            onDispose { listener.remove() }
        }
    }
}

@Composable
private fun AppNotificationCard(
    notification: AppNotification,
    onDismiss: () -> Unit
) {
    val accent = notification.toneColor()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.42f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = notification.toneIcon(),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = notification.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss notification",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun AppNotificationState.showStoredNotification(notification: StoredNotification) {
    val tone = when (notification.tone) {
        "success" -> AppNotificationTone.Success
        "error" -> AppNotificationTone.Error
        else -> AppNotificationTone.Info
    }
    show(
        title = notification.title.ifBlank { "OneStudio" },
        message = notification.body.ifBlank { "Workflow update" },
        tone = tone,
        durationMs = if (tone == AppNotificationTone.Error) 7_000L else 4_800L
    )
}

@Composable
private fun AppNotification.toneColor(): Color = when (tone) {
    AppNotificationTone.Success -> Color(0xFF22C55E)
    AppNotificationTone.Error -> MaterialTheme.colorScheme.error
    AppNotificationTone.Info -> MaterialTheme.colorScheme.primary
}

private fun AppNotification.toneIcon(): ImageVector = when (tone) {
    AppNotificationTone.Success -> Icons.Default.CheckCircle
    AppNotificationTone.Error -> Icons.Default.ErrorOutline
    AppNotificationTone.Info -> Icons.Default.Info
}

private data class StoredNotification(
    val id: String,
    val title: String,
    val body: String,
    val tone: String
)
