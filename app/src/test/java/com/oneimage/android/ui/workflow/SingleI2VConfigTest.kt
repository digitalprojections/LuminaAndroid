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
    fun unsupportedAspectRatioFallsBackToSquare() {
        assertEquals("16:9 (Widescreen)", SingleI2VConfig.normalizeAspectRatio("16:9 (Widescreen)"))
        assertEquals("16:9 (Widescreen)", SingleI2VConfig.normalizeAspectRatio("16:9 (Landscape)"))
        assertEquals("1:1 (Square)", SingleI2VConfig.normalizeAspectRatio("unsupported"))
    }
}
