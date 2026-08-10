package com.oneimage.android.ui.onboarding

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class OnboardingPreferencesTest {
    @Test
    fun completionIsStoredPerAccount() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        OnboardingPreferences.clear(context, "account-a")
        OnboardingPreferences.clear(context, "account-b")
        assertFalse(OnboardingPreferences.isComplete(context, "account-a"))

        OnboardingPreferences.markComplete(context, "account-a")

        assertTrue(OnboardingPreferences.isComplete(context, "account-a"))
        assertFalse(OnboardingPreferences.isComplete(context, "account-b"))
    }

    @Test
    fun blankAccountIdsNeverCompleteTheTour() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        OnboardingPreferences.markComplete(context, "")

        assertFalse(OnboardingPreferences.isComplete(context, ""))
    }
}
