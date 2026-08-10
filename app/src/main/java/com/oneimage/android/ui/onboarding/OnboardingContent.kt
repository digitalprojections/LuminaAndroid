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
            title = "Fast once your task is running",
            description = "There can be a queue when the studio is busy. Once your task reaches the server, the workflow itself usually finishes much faster.",
            highlights = listOf(
                "We keep server processing focused so your task can move from queue to result quickly.",
                "Server time is expensive for our one-person operation. As more creators return, we will add concurrent capacity. Thank you for understanding."
            )
        ),
        OnboardingPage(
            id = "studio",
            eyebrow = "Find your lane",
            title = "Made for your kind of creating",
            description = "OneStudio supports many kinds of content creators. Pick the workflows that fit your craft—social content, films, design, product assets, game art, stories, or something entirely your own.",
            highlights = listOf(
                "Use only what you need today, then discover more when your next idea calls for it.",
                "Images, video, motion, stories, lip sync, 3D, and variations are building blocks—not a checklist."
            )
        )
    )
}

data class OnboardingProgress(val pageIndex: Int = 0) {
    val isFirst: Boolean get() = pageIndex == 0
    val isLast: Boolean get() = pageIndex == OnboardingContent.pages.lastIndex

    fun advance(): OnboardingProgress = if (isLast) this else copy(pageIndex = pageIndex + 1)
}
