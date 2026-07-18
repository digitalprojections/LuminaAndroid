package com.oneimage.android.ui.workflow

import org.junit.Assert.assertEquals
import org.junit.Test

class SingleI2VConfigTest {
    @Test
    fun durationIsClampedToWebContract() {
        assertEquals(3, SingleI2VConfig.clampDuration("1"))
        assertEquals(6, SingleI2VConfig.clampDuration("6"))
        assertEquals(7, SingleI2VConfig.clampDuration("6.6"))
        assertEquals(10, SingleI2VConfig.clampDuration("15"))
        assertEquals(3, SingleI2VConfig.clampDuration("invalid"))
    }

    @Test
    fun sliderDurationCommitsNearestWholeSecond() {
        assertEquals("3", SingleI2VConfig.durationInputValue(3.1f))
        assertEquals("6", SingleI2VConfig.durationInputValue(5.6f))
        assertEquals("10", SingleI2VConfig.durationInputValue(10.4f))
    }

    @Test
    fun frameRateDefaultsToWorkflowContract() {
        assertEquals(1, SingleI2VConfig.clampFrameRate("0"))
        assertEquals(16, SingleI2VConfig.clampFrameRate(null))
        assertEquals(16, SingleI2VConfig.clampFrameRate("16"))
        assertEquals(30, SingleI2VConfig.clampFrameRate("60"))
    }

    @Test
    fun unsupportedAspectRatioFallsBackToSquare() {
        assertEquals("16:9 (Widescreen)", SingleI2VConfig.normalizeAspectRatio("16:9 (Widescreen)"))
        assertEquals("16:9 (Widescreen)", SingleI2VConfig.normalizeAspectRatio("16:9 (Landscape)"))
        assertEquals("1:1 (Square)", SingleI2VConfig.normalizeAspectRatio("unsupported"))
    }
}
