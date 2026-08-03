package com.oneimage.android.ui.videogen

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoGenConfigTest {
    @Test
    fun durationMatchesServerContract() {
        assertEquals(1, VideoGenConfig.clampDuration("0"))
        assertEquals(6, VideoGenConfig.clampDuration(null))
        assertEquals(9, VideoGenConfig.clampDuration("9"))
        assertEquals(12, VideoGenConfig.clampDuration("30"))
    }

    @Test
    fun frameRateMatchesServerContract() {
        assertEquals(1, VideoGenConfig.clampFrameRate("0"))
        assertEquals(25, VideoGenConfig.clampFrameRate(null))
        assertEquals(24, VideoGenConfig.clampFrameRate("24"))
        assertEquals(30, VideoGenConfig.clampFrameRate("60"))
    }

    @Test
    fun optimalResolutionKeepsMultiplesOfThirtyTwo() {
        val resolution = VideoGenConfig.optimalResolution(1920, 1080)

        assertEquals(1280, resolution.width)
        assertEquals(736, resolution.height)
    }

    @Test
    fun optimalResolutionKeepsMinimumSize() {
        val resolution = VideoGenConfig.optimalResolution(20, 20)

        assertEquals(160, resolution.width)
        assertEquals(160, resolution.height)
    }
}
