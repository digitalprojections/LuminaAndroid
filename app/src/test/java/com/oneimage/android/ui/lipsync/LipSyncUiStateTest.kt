package com.oneimage.android.ui.lipsync

import org.junit.Assert.assertEquals
import org.junit.Test

class LipSyncUiStateTest {
    @Test
    fun estimatedCreditsRoundsUpAndUsesMinimum() {
        val short = LipSyncUiState(durationSeconds = 0.2f)
        val medium = LipSyncUiState(durationSeconds = 3.2f)
        val long = LipSyncUiState(durationSeconds = 10.7f)

        assertEquals(4, short.estimatedCredits)
        assertEquals(16, medium.estimatedCredits)
        assertEquals(44, long.estimatedCredits)
    }

    @Test
    fun fullAudioCreditsUseAudioDuration() {
        val state = LipSyncUiState(
            audioDurationSeconds = 61.2f,
            durationSeconds = 10f,
            useFullAudio = true
        )

        assertEquals(248, state.estimatedCredits)
    }
}
