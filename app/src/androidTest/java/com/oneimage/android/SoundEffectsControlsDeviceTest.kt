package com.oneimage.android

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.oneimage.android.ui.workflow.SoundEffectsControls
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SoundEffectsControlsDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun slidersUpdateIndependentlyWithoutAdvancedSettings() {
        val values = mutableStateMapOf("prompt" to "", "seconds" to "10", "batchSize" to "1", "cfg" to "5")
        rule.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { SoundEffectsControls(values, true) } } }
        rule.onNodeWithTag("sound-prompt").performTextInput("Rain on a window")
        rule.onNodeWithTag("sound-duration").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(7f) }
        rule.onNodeWithTag("sound-batch").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }
        rule.onNodeWithTag("sound-cfg").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(7.5f) }
        rule.runOnIdle {
            assertEquals("7", values["seconds"])
            assertEquals("3", values["batchSize"])
            assertEquals("7.5", values["cfg"])
        }
        rule.onNodeWithText("Advanced settings", ignoreCase = true).assertDoesNotExist()
    }

    @Test fun controlsRemainReachableInLandscape() {
        rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val values = mutableStateMapOf("prompt" to "Rain", "seconds" to "10", "batchSize" to "1", "cfg" to "5")
        rule.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { SoundEffectsControls(values, true) } } }
        rule.onNodeWithTag("sound-cfg").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("sound-prompt").performScrollTo().assertIsDisplayed()
    }
}
