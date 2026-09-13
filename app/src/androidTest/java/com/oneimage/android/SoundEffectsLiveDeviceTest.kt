package com.oneimage.android

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.oneimage.android.api.LocalTaskResultStore
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Explicit opt-in: uses the signed-in account and creates one three-effect test batch. */
class SoundEffectsLiveDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun generatePlayAndDownloadOverUsb() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("soundEffectsLive") == "true")
        check(FirebaseAuth.getInstance().currentUser != null) { "Sign in on the connected device before running live verification." }
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Creator Tools").fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Sound Effects"))
        rule.onNodeWithText("Sound Effects").performClick()
        rule.onNodeWithTag("sound-prompt").performScrollTo().performTextReplacement("Gentle rain tapping on a window, no music. Android USB verification.")
        rule.onNodeWithTag("sound-duration").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        rule.onNodeWithTag("sound-batch").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }
        rule.onNodeWithTag("sound-cfg").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(5f) }
        screenshot("sound-effects-portrait")
        rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        rule.waitForIdle()
        rule.onNodeWithTag("sound-batch").performScrollTo()
        rule.onNodeWithText("3 effects").assertExists()
        screenshot("sound-effects-landscape")
        rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        rule.waitForIdle()
        val generate = rule.onNodeWithText("Generate Sound Effects · 30 credits")
        generate.performScrollTo()
        rule.waitUntil(30_000) { generate.isEnabled() }
        generate.performClick()
        rule.waitUntil(240_000) { rule.onAllNodesWithContentDescription("Play sound effect").fetchSemanticsNodes().size == 3 }
        val first = rule.onAllNodesWithContentDescription("Play sound effect")[0]
        first.performScrollTo()
        rule.waitUntil(30_000) { first.isEnabled() }
        first.performClick()
        rule.onNodeWithContentDescription("Pause sound effect").assertExists().performClick()
        screenshot("sound-effects-results")
        rule.onAllNodesWithText("Download").fetchSemanticsNodes().indices.forEach { index ->
            rule.onAllNodesWithText("Download")[index].performScrollTo().performClick()
            rule.waitForIdle()
        }
    }

    private fun SemanticsNodeInteraction.isEnabled(): Boolean = runCatching { assertIsEnabled(); true }.getOrDefault(false)

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "sound-effects-qa/$name.png")
        output.parentFile?.mkdirs()
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
