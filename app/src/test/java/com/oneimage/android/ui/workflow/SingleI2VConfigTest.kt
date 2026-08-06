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
    fun maxDurationScalesWithPreparedImageSizeAndFrameRate() {
        assertEquals(10, SingleI2VConfig.maxDurationForInput(512 to 512, 16))
        assertEquals(5, SingleI2VConfig.maxDurationForInput(576 to 768, 16))
        assertEquals(4, SingleI2VConfig.maxDurationForInput(768 to 768, 16))
        assertEquals(3, SingleI2VConfig.maxDurationForInput(576 to 768, 30))
    }

    @Test
    fun durationIsClampedToPreparedImageBudget() {
        assertEquals(5, SingleI2VConfig.clampDurationForInput("10", 576 to 768, 16))
        assertEquals(3, SingleI2VConfig.clampDurationForInput("1", 576 to 768, 16))
        assertEquals("5", SingleI2VConfig.durationInputValue(10f, 576 to 768, 16))
    }

    @Test
    fun preparedImageLimitMatchesSingleI2VTransferCap() {
        assertEquals(true, SingleI2VConfig.preparedImageWithinLimit(576 to 768))
        assertEquals(true, SingleI2VConfig.preparedImageWithinLimit(768 to 768))
        assertEquals(false, SingleI2VConfig.preparedImageWithinLimit(768 to 1024))
        assertEquals(false, SingleI2VConfig.preparedImageWithinLimit(null))
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
