package com.oneimage.android.ui.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oneimage.android.api.WebRtcTransferProgress
import com.oneimage.android.api.WebRtcTransferProgressStore
import com.oneimage.android.api.WebRtcTransferStage
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow

@Composable
fun WebRtcTransferProgressOverlay(modifier: Modifier = Modifier) {
    val transfers by WebRtcTransferProgressStore.transfers.collectAsStateWithLifecycle()
    if (transfers.isEmpty()) return

    Column(
        modifier = modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        transfers.takeLast(3).forEach { transfer ->
            TransferProgressCard(transfer)
        }
    }
}

@Composable
private fun TransferProgressCard(transfer: WebRtcTransferProgress) {
    val failed = transfer.stage == WebRtcTransferStage.Failed
    val complete = transfer.stage == WebRtcTransferStage.Complete
    val verifying = transfer.stage == WebRtcTransferStage.Verifying
    val accent = when {
        failed -> MaterialTheme.colorScheme.error
        complete -> Color(0xFF22C55E)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        failed -> Icons.Default.Error
                        complete -> Icons.Default.CheckCircle
                        else -> Icons.Default.CloudDownload
                    },
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            failed -> "Transfer failed"
                            complete -> "Result ready"
                            verifying -> "Verifying result"
                            else -> "Receiving result"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = transfer.filename,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${transfer.progressPercent}%",
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            LinearProgressIndicator(
                progress = { transfer.progressFraction },
                modifier = Modifier.fillMaxWidth(),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        failed -> transfer.error ?: "The result could not be received."
                        complete -> "Transfer complete"
                        verifying -> "Transfer complete · checking file integrity…"
                        else -> "${formatTransferBytes(transfer.receivedBytes)} of ${formatTransferBytes(transfer.totalBytes)}"
                    },
                    modifier = Modifier.weight(1f),
                    color = if (failed) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                transferEstimate(transfer)?.let { estimate ->
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = estimate,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

internal fun transferEstimate(transfer: WebRtcTransferProgress): String? {
    if (transfer.stage != WebRtcTransferStage.Receiving) return null
    val elapsedSeconds = (transfer.updatedAtMs - transfer.startedAtMs).coerceAtLeast(0L) / 1_000.0
    if (elapsedSeconds < 1.0 || transfer.receivedBytes <= 0L || transfer.receivedBytes >= transfer.totalBytes) return null

    val bytesPerSecond = transfer.receivedBytes / elapsedSeconds
    if (!bytesPerSecond.isFinite() || bytesPerSecond <= 0.0) return null
    val secondsRemaining = ceil((transfer.totalBytes - transfer.receivedBytes) / bytesPerSecond).toLong().coerceAtLeast(1L)
    val eta = if (secondsRemaining < 60L) "about ${secondsRemaining}s left" else "about ${ceil(secondsRemaining / 60.0).toLong()}m left"
    return "${formatTransferBytes(bytesPerSecond.toLong())}/s · $eta"
}

internal fun formatTransferBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val unitIndex = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(unitIndex.toDouble())
    val pattern = if (unitIndex == 0 || value >= 10.0) "%.0f %s" else "%.1f %s"
    return String.format(Locale.US, pattern, value, units[unitIndex])
}
