package com.oneimage.android.api

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkflowPricingConfigTest {
    @Test
    fun oneVideoCreditsRespectMinimumAndDuration() {
        val pricing = WorkflowPricingConfig(oneVideoPerSecond = 4, oneVideoMinimum = 24)

        assertEquals(24, pricing.oneVideoCredits(1))
        assertEquals(24, pricing.oneVideoCredits(6))
        assertEquals(28, pricing.oneVideoCredits(7))
    }

    @Test
    fun oneMotionCreditsUseTransitionFramesWithMinimum() {
        val pricing = WorkflowPricingConfig(
            oneMotionPerSecond = 6,
            oneMotionMinimum = 60,
            oneMotionExtraKeyframe = 2
        )

        assertEquals(0, pricing.oneMotionCredits(emptyList()))
        assertEquals(60, pricing.oneMotionCredits(listOf(25)))
        assertEquals(122, pricing.oneMotionCredits(listOf(250, 250)))
        assertEquals(76, pricing.oneMotionCredits(listOf(100, 100, 100)))
    }

    @Test
    fun characterReplacementCreditsRoundUpSeconds() {
        val pricing = WorkflowPricingConfig(characterReplacementPerSecond = 4)

        assertEquals(4, pricing.characterReplacementCredits(0.1f))
        assertEquals(16, pricing.characterReplacementCredits(3.2f))
    }
}
