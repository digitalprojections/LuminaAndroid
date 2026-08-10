package com.oneimage.android.ui.onboarding

data class OnboardingPage(
    val id: String,
    val eyebrow: String,
    val title: String,
    val description: String,
    val highlights: List<String>
)

object OnboardingContent {
    val pages = listOf(
        OnboardingPage(
            id = "credits",
            eyebrow = "Keep creating",
            title = "Two ways to get credits",
            description = "Stay in the flow with a credit balance that works for you.",
            highlights = listOf(
                "Buy a Google Play credit pack when you want more runway.",
                "Watch a rewarded ad to earn ${com.oneimage.android.BuildConfig.REWARDED_AD_CREDIT_AMOUNT} credits when you want a free boost."
            )
        ),
        OnboardingPage(
            id = "speed",
            eyebrow = "Move fast",
            title = "From idea to output, fast",
            description = "GenStudio workflows are built for fast creative loops: upload, describe, generate, and keep iterating.",
            highlights = listOf(
                "Short path from a thought to a result.",
                "Keep experimenting without leaving your studio."
            )
        ),
        OnboardingPage(
            id = "studio",
            eyebrow = "Make more",
            title = "One studio for every direction",
            description = "Turn a single idea into images, videos, motion, stories, lip sync, 3D assets, and polished variations.",
            highlights = listOf(
                "Create, refine, and revisit your work in one place.",
                "Your next experiment is always one tap away."
            )
        )
    )
}

data class OnboardingProgress(val pageIndex: Int = 0) {
    val isFirst: Boolean get() = pageIndex == 0
    val isLast: Boolean get() = pageIndex == OnboardingContent.pages.lastIndex

    fun advance(): OnboardingProgress = if (isLast) this else copy(pageIndex = pageIndex + 1)
}
