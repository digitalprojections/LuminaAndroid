package com.oneimage.android.ui.workflow

import org.junit.Assert.*
import org.junit.Test

class SoundEffectsConfigTest {
    @Test fun defaultsMatchTheAudioWorkflow() {
        val config = SoundEffectsConfig(prompt = " Rain on glass ")
        assertEquals(10, config.seconds)
        assertEquals(1, config.batchSize)
        assertEquals(5f, config.cfg)
        assertEquals(10, config.credits)
        assertEquals("Rain on glass", config.payload("user")["prompt"])
        assertEquals(50, config.payload("user")["steps"])
    }

    @Test fun batchAndSliderLimitsAreValidatedBeforeSubmission() {
        assertTrue(SoundEffectsConfig("rain", 47, 3, 10f).isValid)
        assertEquals(30, SoundEffectsConfig("rain", batchSize = 3).credits)
        listOf(
            SoundEffectsConfig(""), SoundEffectsConfig("x".repeat(2001)),
            SoundEffectsConfig("rain", seconds = 48), SoundEffectsConfig("rain", seconds = 0),
            SoundEffectsConfig("rain", batchSize = 4), SoundEffectsConfig("rain", batchSize = 0),
            SoundEffectsConfig("rain", cfg = Float.NaN), SoundEffectsConfig("rain", cfg = 11f)
        ).forEach { assertFalse(it.isValid) }
    }

    @Test fun payloadRetainsAllThreeUserControlsAndAutomaticSampling() {
        val payload = SoundEffectsConfig("thunder", 7, 3, 7.5f).payload("user")
        assertEquals(7, payload["seconds"])
        assertEquals(3, payload["batchSize"])
        assertEquals(7.5f, payload["cfg"])
        assertEquals(0, payload["seed"])
        assertFalse(payload.containsKey("inputImage"))
    }
}
