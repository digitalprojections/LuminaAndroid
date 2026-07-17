package com.oneimage.android.ui.shared

import com.oneimage.android.api.WebRtcTransferProgress
import com.oneimage.android.api.WebRtcTransferStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebRtcTransferProgressTest {
    @Test
    fun formatsTransferredBytesForUi() {
        assertEquals("0 B", formatTransferBytes(0))
        assertEquals("1.5 KB", formatTransferBytes(1_536))
        assertEquals("12 MB", formatTransferBytes(12L * 1024L * 1024L))
    }

    @Test
    fun estimatesRemainingTimeFromAverageThroughput() {
        val transfer = transfer(
            receivedBytes = 2L * 1024L * 1024L,
            totalBytes = 10L * 1024L * 1024L,
            startedAtMs = 1_000L,
            updatedAtMs = 3_000L
        )

        assertEquals("1.0 MB/s · about 8s left", transferEstimate(transfer))
    }

    @Test
    fun hidesEstimateUntilEnoughTimingDataExists() {
        assertNull(transferEstimate(transfer(receivedBytes = 512, totalBytes = 1_024, startedAtMs = 1_000, updatedAtMs = 1_500)))
    }

    private fun transfer(
        receivedBytes: Long,
        totalBytes: Long,
        startedAtMs: Long,
        updatedAtMs: Long
    ) = WebRtcTransferProgress(
        ownerId = "owner",
        fileId = "file",
        filename = "result.mp4",
        receivedBytes = receivedBytes,
        totalBytes = totalBytes,
        startedAtMs = startedAtMs,
        updatedAtMs = updatedAtMs,
        stage = WebRtcTransferStage.Receiving
    )
}
