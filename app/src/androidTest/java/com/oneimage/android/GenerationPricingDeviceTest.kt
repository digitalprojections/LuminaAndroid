package com.oneimage.android
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Native UI verification without Espresso idle detection on MIUI. */
class GenerationPricingDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    @Test fun durationAndFpsRefreshFinalBackendPriceInBothOrientations() {
        val context = instrumentation.targetContext
        // Launch as the shell so MIUI does not suppress a background instrumentation launch.
        instrumentation.uiAutomation.executeShellCommand("am start -n ${context.packageName}/.PricingVerificationActivity").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
        try {
            waitForText("24 credits")
            click("Longer clip"); waitForText("48 credits")
            click("Higher FPS"); waitForText("58 credits")
            screenshot("pricing-portrait")
            instrumentation.uiAutomation.setRotation(1)
            waitForText("58 credits")
            // Allow the device's rotation animation to finish before capturing evidence.
            SystemClock.sleep(2000)
            screenshot("pricing-landscape")
        } finally { instrumentation.uiAutomation.setRotation(-2) }
    }
    private fun find(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == text || node.contentDescription?.toString() == text) return node
        for (i in 0 until node.childCount) find(node.getChild(i), text)?.let { return it }
        return null
    }
    private fun waitForText(text: String) {
        val end = SystemClock.uptimeMillis() + 30000
        while (SystemClock.uptimeMillis() < end) {
            if (find(instrumentation.uiAutomation.rootInActiveWindow, text) != null) return
            SystemClock.sleep(100)
        }
        throw AssertionError("Did not see backend price: $text")
    }
    private fun click(text: String) {
        var node = find(instrumentation.uiAutomation.rootInActiveWindow, text)
        while (node != null && !node.isClickable) node = node.parent
        assertTrue("Control available: $text", node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
    }
    private fun screenshot(name: String) {
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "pricing-test-results/$name.png")
        file.parentFile?.mkdirs()
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
    }
}
