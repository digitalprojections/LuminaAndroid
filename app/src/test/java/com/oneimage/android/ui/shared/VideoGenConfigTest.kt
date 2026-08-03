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
    fun numericInputKeepsBlankAndPartialTypingEditable() {
        assertEquals("", VideoGenConfig.numericInputValue(""))
        assertEquals("1", VideoGenConfig.numericInputValue("1"))
        assertEquals("10", VideoGenConfig.numericInputValue("10"))
    }

    @Test
    fun numericInputFiltersNonDigitsWithoutApplyingDefaults() {
        assertEquals("", VideoGenConfig.numericInputValue("abc"))
        assertEquals("12", VideoGenConfig.numericInputValue("1a2b3"))
        assertEquals("123", VideoGenConfig.numericInputValue("1234", maxDigits = 3))
    }

    @Test
    fun blankNumericInputKeepsCurrentCommittedValues() {
        assertEquals(9, VideoGenConfig.durationValueForInput("", current = 9))
        assertEquals(24, VideoGenConfig.frameRateValueForInput("", current = 24))
    }

    @Test
    fun numericInputCommitsClampedValuesWhenPresent() {
        assertEquals(1, VideoGenConfig.durationValueForInput("0", current = 9))
        assertEquals(12, VideoGenConfig.durationValueForInput("99", current = 9))
        assertEquals(1, VideoGenConfig.frameRateValueForInput("0", current = 24))
        assertEquals(30, VideoGenConfig.frameRateValueForInput("99", current = 24))
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
