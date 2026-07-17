package com.oneimage.android.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

enum class WebRtcTransferStage {
    Receiving,
    Verifying,
    Complete,
    Failed
}

data class WebRtcTransferProgress(
    val ownerId: String,
    val fileId: String,
    val filename: String,
    val receivedBytes: Long,
    val totalBytes: Long,
    val startedAtMs: Long,
    val updatedAtMs: Long,
    val stage: WebRtcTransferStage,
    val taskId: String? = null,
    val error: String? = null
) {
    val progressFraction: Float
        get() = if (totalBytes <= 0L) 0f else (receivedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)

    val progressPercent: Int
        get() = (progressFraction * 100f).roundToInt().coerceIn(0, 100)
}

object WebRtcTransferProgressStore {
    private const val COMPLETED_VISIBILITY_MS = 2_500L
    private const val FAILED_VISIBILITY_MS = 5_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cleanupJobs = ConcurrentHashMap<String, Job>()
    private val _transfers = MutableStateFlow<List<WebRtcTransferProgress>>(emptyList())
    val transfers: StateFlow<List<WebRtcTransferProgress>> = _transfers.asStateFlow()

    fun start(
        ownerId: String,
        fileId: String,
        filename: String,
        totalBytes: Long,
        taskId: String?,
        nowMs: Long
    ) {
        val key = key(ownerId, fileId)
        cleanupJobs.remove(key)?.cancel()
        val transfer = WebRtcTransferProgress(
            ownerId = ownerId,
            fileId = fileId,
            filename = filename,
            receivedBytes = 0L,
            totalBytes = totalBytes.coerceAtLeast(0L),
            startedAtMs = nowMs,
            updatedAtMs = nowMs,
            stage = WebRtcTransferStage.Receiving,
            taskId = taskId
        )
        _transfers.update { current -> current.filterNot { it.ownerId == ownerId && it.fileId == fileId } + transfer }
    }

    fun receiving(ownerId: String, fileId: String, receivedBytes: Long, totalBytes: Long, nowMs: Long) {
        update(ownerId, fileId) { transfer ->
            transfer.copy(
                receivedBytes = receivedBytes.coerceAtLeast(0L),
                totalBytes = totalBytes.coerceAtLeast(0L),
                updatedAtMs = nowMs,
                stage = WebRtcTransferStage.Receiving,
                error = null
            )
        }
    }

    fun verifying(ownerId: String, fileId: String, totalBytes: Long, nowMs: Long) {
        update(ownerId, fileId) { transfer ->
            transfer.copy(
                receivedBytes = totalBytes.coerceAtLeast(0L),
                totalBytes = totalBytes.coerceAtLeast(0L),
                updatedAtMs = nowMs,
                stage = WebRtcTransferStage.Verifying,
                error = null
            )
        }
    }

    fun complete(ownerId: String, fileId: String, nowMs: Long) {
        update(ownerId, fileId) { transfer ->
            transfer.copy(
                receivedBytes = transfer.totalBytes,
                updatedAtMs = nowMs,
                stage = WebRtcTransferStage.Complete,
                error = null
            )
        }
        scheduleRemoval(ownerId, fileId, COMPLETED_VISIBILITY_MS)
    }

    fun fail(ownerId: String, fileId: String, message: String, nowMs: Long) {
        update(ownerId, fileId) { transfer ->
            transfer.copy(updatedAtMs = nowMs, stage = WebRtcTransferStage.Failed, error = message)
        }
        scheduleRemoval(ownerId, fileId, FAILED_VISIBILITY_MS)
    }

    fun clearOwner(ownerId: String) {
        cleanupJobs.keys.filter { it.startsWith("$ownerId:") }.forEach { key -> cleanupJobs.remove(key)?.cancel() }
        _transfers.update { current -> current.filterNot { it.ownerId == ownerId } }
    }

    private fun update(ownerId: String, fileId: String, transform: (WebRtcTransferProgress) -> WebRtcTransferProgress) {
        _transfers.update { current ->
            current.map { transfer ->
                if (transfer.ownerId == ownerId && transfer.fileId == fileId) transform(transfer) else transfer
            }
        }
    }

    private fun scheduleRemoval(ownerId: String, fileId: String, delayMs: Long) {
        val key = key(ownerId, fileId)
        cleanupJobs.remove(key)?.cancel()
        cleanupJobs[key] = scope.launch {
            delay(delayMs)
            _transfers.update { current -> current.filterNot { it.ownerId == ownerId && it.fileId == fileId } }
            cleanupJobs.remove(key)
        }
    }

    private fun key(ownerId: String, fileId: String) = "$ownerId:$fileId"
}
