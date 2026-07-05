package com.oneimage.android.ui.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oneimage.android.api.OneImageTask
import java.text.DateFormat
import java.util.Date

@Composable
fun WorkflowHistoryList(
    title: String,
    emptyText: String,
    tasks: List<OneImageTask>,
    currentTaskId: String?,
    taskTitle: (OneImageTask) -> String,
    onOpen: (OneImageTask) -> Unit,
    onRestore: (OneImageTask) -> Unit,
    onDelete: (OneImageTask) -> Unit,
    onCancel: (OneImageTask) -> Unit
) {
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text(title, fontWeight = FontWeight.Bold)
            }

            if (tasks.isEmpty()) {
                Text(emptyText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                tasks.forEachIndexed { index, task ->
                    if (index > 0) HorizontalDivider()
                    WorkflowHistoryRow(
                        task = task,
                        isCurrent = task.id == currentTaskId,
                        title = taskTitle(task),
                        onOpen = { onOpen(task) },
                        onRestore = { onRestore(task) },
                        onDelete = { onDelete(task) },
                        onCancel = { onCancel(task) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkflowHistoryRow(
    task: OneImageTask,
    isCurrent: Boolean,
    title: String,
    onOpen: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    val isActive = task.status in setOf("pending", "processing", "initializing")
    val canRestore = task.results.any { it.url.startsWith("webrtc://") } || task.useWebRTC

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title.ifBlank { "Generation task" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    listOf(task.status, task.createdAtText()).filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isCurrent) {
                Card(
                    shape = RoundedCornerShape(999.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Text(
                        "Open",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
            }
            Text(
                "${task.results.size}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                Text(if (isCurrent) "Opened" else "Open")
            }
            if (isActive) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            } else if (canRestore) {
                OutlinedButton(
                    onClick = onRestore,
                    enabled = !task.resultRestoreUnavailable,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
            if (!isActive) {
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private fun OneImageTask.createdAtText(): String {
    if (createdAtMs <= 0L) return ""
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(createdAtMs))
}
