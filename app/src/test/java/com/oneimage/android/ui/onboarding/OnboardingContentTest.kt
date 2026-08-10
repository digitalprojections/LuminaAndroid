package com.oneimage.android.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingContentTest {
    @Test
    fun firstPageLeadsWithBothWaysToGetCredits() {
        val page = OnboardingContent.pages.first()

        assertEquals("Keep creating", page.eyebrow)
        assertTrue(page.title.contains("credits", ignoreCase = true))
        assertTrue(page.highlights.any { it.contains("Google Play", ignoreCase = true) })
        assertTrue(page.highlights.any { it.contains("rewarded ad", ignoreCase = true) })
    }

    @Test
    fun secondPageLeadsWithSpeed() {
        val page = OnboardingContent.pages[1]

        assertEquals("Built to move", page.eyebrow)
        assertTrue(page.title.contains("fast", ignoreCase = true))
        assertTrue(page.description.contains("queue", ignoreCase = true))
        assertTrue(page.description.contains("processing", ignoreCase = true))
        assertTrue(page.highlights.any { it.contains("capacity", ignoreCase = true) })
    }

    @Test
    fun studioPageSupportsDifferentCreatorsWithoutSellingEveryWorkflowToEveryone() {
        val page = OnboardingContent.pages[2]

        assertEquals("Made for creators", page.eyebrow)
        assertTrue(page.description.contains("different creators", ignoreCase = true))
        assertTrue(page.highlights.any { it.contains("one workflow", ignoreCase = true) })
        assertTrue(page.highlights.any { it.contains("not a checklist", ignoreCase = true) })
    }

    @Test
    fun pagesAreOrderedAsCreditsSpeedThenStudio() {
        assertEquals(
            listOf("credits", "speed", "studio"),
            OnboardingContent.pages.map { it.id }
        )
    }

    @Test
    fun progressCannotAdvancePastTheFinalPage() {
        val progress = OnboardingProgress(pageIndex = OnboardingContent.pages.lastIndex)

        assertTrue(progress.isLast)
        assertEquals(progress, progress.advance())
    }

    @Test
    fun progressStartsAtTheFirstPageAndAdvancesOnePageAtATime() {
        val progress = OnboardingProgress()

        assertTrue(progress.isFirst)
        assertFalse(progress.isLast)
        assertEquals(1, progress.advance().pageIndex)
    }
}
