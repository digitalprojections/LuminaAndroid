package com.oneimage.android.ui.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedHistoryStatusToneTest {
    @Test
    fun cancelledStatusUsesCancelledTone() {
        assertEquals(
            SharedHistoryStatusToneKind.Cancelled,
            sharedHistoryStatusToneKind("cancelled")
        )
    }

    @Test
    fun processingStatusUsesRunningTone() {
        assertEquals(
            SharedHistoryStatusToneKind.Running,
            sharedHistoryStatusToneKind("processing")
        )
    }

    @Test
    fun pendingStatusUsesQueuedTone() {
        assertEquals(
            SharedHistoryStatusToneKind.Queued,
            sharedHistoryStatusToneKind("pending")
        )
    }
}
