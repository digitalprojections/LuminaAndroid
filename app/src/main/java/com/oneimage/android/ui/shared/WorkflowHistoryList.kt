package com.oneimage.android.ui.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oneimage.android.api.LocalTaskResultAvailability
import com.oneimage.android.api.LocalTaskResultStore
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
    val localAvailability = tasks.associate { task -> task.id to LocalTaskResultStore.availability(task) }
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
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    WorkflowHistoryRow(
                        task = task,
                        isCurrent = task.id == currentTaskId,
                        localAvailability = localAvailability[task.id] ?: LocalTaskResultAvailability(0, task.results.size),
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
    localAvailability: LocalTaskResultAvailability,
    title: String,
    onOpen: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    val isActive = task.status in setOf("pending", "processing", "initializing")
    val canRestore = !localAvailability.hasAllLocal &&
        (task.results.any { it.url.startsWith("webrtc://") } || task.useWebRTC)
    val statusTone = historyStatusTone(task)
    val detailLine = remember(task, localAvailability) {
        when {
            task.status == "failed" -> null
            localAvailability.hasAnyLocal && localAvailability.totalCount > 0 ->
                if (localAvailability.hasAllLocal) {
                    "All ${localAvailability.totalCount} result${if (localAvailability.totalCount == 1) "" else "s"} saved on this device"
                } else {
                    "${localAvailability.localCount} of ${localAvailability.totalCount} results saved on this device"
                }
            !task.statusDetails.isNullOrBlank() -> task.statusDetails
            task.results.isNotEmpty() -> "${task.results.size} result${if (task.results.size == 1) "" else "s"} ready"
            else -> null
        }
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            1.dp,
            if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .size(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(statusTone.accent)
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            title.ifBlank { "Generation task" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        StatusBadge(
                            label = statusTone.label,
                            containerColor = statusTone.container,
                            contentColor = statusTone.content,
                            icon = statusTone.icon
                        )
                        if (isCurrent) {
                            TinyBadge(
                                label = "Open",
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (localAvailability.hasAnyLocal) {
                            TinyBadge(
                                label = if (localAvailability.totalCount > 0) {
                                    "${localAvailability.localCount}/${localAvailability.totalCount} local"
                                } else {
                                    "Local"
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f)
                            )
                        }
                    }

                    Text(
                        listOf(task.createdAtText(), "${task.results.size} result${if (task.results.size == 1) "" else "s"}")
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    detailLine?.let {
                        Text(
                            it,
                            fontSize = 12.sp,
                            color = if (task.status == "failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = if (isCurrent) "Opened task" else "Open task",
                        modifier = Modifier.size(18.dp)
                    )
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
}

@Composable
private fun StatusBadge(
    label: String,
    containerColor: Color,
    contentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = contentColor)
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = contentColor)
        }
    }
}

@Composable
private fun TinyBadge(
    label: String,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color
) {
    Card(
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}

private data class HistoryStatusTone(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val accent: Color,
    val container: Color,
    val content: Color
)

@Composable
private fun historyStatusTone(task: OneImageTask): HistoryStatusTone {
    val scheme = MaterialTheme.colorScheme
    return when (task.status) {
        "completed" -> HistoryStatusTone(
            label = "Completed",
            icon = Icons.Default.CheckCircle,
            accent = scheme.primary,
            container = scheme.primaryContainer,
            content = scheme.onPrimaryContainer
        )
        "failed" -> HistoryStatusTone(
            label = "Failed",
            icon = Icons.Default.ErrorOutline,
            accent = scheme.error,
            container = scheme.errorContainer,
            content = scheme.onErrorContainer
        )
        "cancelled" -> HistoryStatusTone(
            label = "Cancelled",
            icon = Icons.Default.Cancel,
            accent = scheme.outline,
            container = scheme.surfaceVariant,
            content = scheme.onSurfaceVariant
        )
        "processing", "initializing" -> HistoryStatusTone(
            label = "Running",
            icon = Icons.Default.Schedule,
            accent = scheme.tertiary,
            container = scheme.tertiaryContainer,
            content = scheme.onTertiaryContainer
        )
        "pending" -> HistoryStatusTone(
            label = "Queued",
            icon = Icons.Default.RadioButtonUnchecked,
            accent = scheme.secondary,
            container = scheme.secondaryContainer,
            content = scheme.onSecondaryContainer
        )
        else -> HistoryStatusTone(
            label = task.status.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
            icon = Icons.Default.RadioButtonUnchecked,
            accent = scheme.outline,
            container = scheme.surfaceVariant,
            content = scheme.onSurfaceVariant
        )
    }
}

private fun OneImageTask.createdAtText(): String {
    if (createdAtMs <= 0L) return ""
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(createdAtMs))
}
