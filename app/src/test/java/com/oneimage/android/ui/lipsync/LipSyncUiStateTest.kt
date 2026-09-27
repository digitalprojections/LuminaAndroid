package com.oneimage.android.ui.lipsync

import org.junit.Assert.assertEquals
import org.junit.Test

class LipSyncUiStateTest {
    @Test
    fun pricingRemainsUnavailableUntilTheBackendResponds() {
        val state = LipSyncUiState(durationSeconds = 10.7f)
        assertEquals(null, state.quotedCredits)
        assertEquals(false, state.hasEnoughCredits)
    }

    @Test
    fun fullAudioDisplaysTheBackendQuoteWithoutRecalculatingIt() {
        val state = LipSyncUiState(
            audioDurationSeconds = 61.2f,
            durationSeconds = 10f,
            useFullAudio = true,
            quotedCredits = 317
        )

        assertEquals(317, state.estimatedCredits)
    }
}
