package com.oneimage.android.ui.workflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterReplacementConfigTest {
    @Test
    fun sourceVideoIsLimitedToThreeSeconds() {
        assertTrue(CharacterReplacementConfig.inputDurationAllowed(3f))
        assertFalse(CharacterReplacementConfig.inputDurationAllowed(3.1f))
    }

    @Test
    fun productionDurationIsCappedAtThreeSeconds() {
        assertEquals(3f, CharacterReplacementConfig.maxProductionDurationForSource(7.7f), 0.001f)
        assertEquals(3f, CharacterReplacementConfig.clampDuration("7.7", 3f), 0.001f)
    }

    @Test
    fun productionDurationRespectsShorterSources() {
        assertEquals(2.2f, CharacterReplacementConfig.maxProductionDurationForSource(2.2f), 0.001f)
        assertEquals(2.2f, CharacterReplacementConfig.clampDuration("3", 2.2f), 0.001f)
    }
}
