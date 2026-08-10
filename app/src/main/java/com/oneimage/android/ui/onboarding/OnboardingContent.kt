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
            eyebrow = "Built to move",
            title = "Fast where it counts",
            description = "Once your task starts processing, GenStudio is designed to move quickly from request to result. During busy periods, a short queue may come first.",
            highlights = listOf(
                "Focused processing keeps your creative loop moving.",
                "We add capacity as demand grows, so more creators can work without compromising the experience."
            )
        ),
        OnboardingPage(
            id = "studio",
            eyebrow = "Made for creators",
            title = "A studio that fits your craft",
            description = "Different creators need different tools. Choose what fits your work—social content, design, film, products, game art, storytelling, and more.",
            highlights = listOf(
                "Start with one workflow. Explore others only when your next idea calls for them.",
                "Images, video, motion, stories, lip sync, and 3D are options—not a checklist."
            )
        )
    )
}

data class OnboardingProgress(val pageIndex: Int = 0) {
    val isFirst: Boolean get() = pageIndex == 0
    val isLast: Boolean get() = pageIndex == OnboardingContent.pages.lastIndex

    fun advance(): OnboardingProgress = if (isLast) this else copy(pageIndex = pageIndex + 1)
}
