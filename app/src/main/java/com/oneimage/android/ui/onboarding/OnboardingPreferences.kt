package com.oneimage.android.ui.onboarding

import android.content.Context

object OnboardingPreferences {
    private const val preferencesName = "genstudio_onboarding"
    private const val completedPrefix = "completed_"

    fun isComplete(context: Context, accountId: String): Boolean {
        val normalizedAccountId = accountId.trim()
        if (normalizedAccountId.isBlank()) return false
        return preferences(context).getBoolean(keyFor(normalizedAccountId), false)
    }

    fun markComplete(context: Context, accountId: String) {
        val normalizedAccountId = accountId.trim()
        if (normalizedAccountId.isBlank()) return
        preferences(context).edit().putBoolean(keyFor(normalizedAccountId), true).apply()
    }

    fun clear(context: Context, accountId: String) {
        val normalizedAccountId = accountId.trim()
        if (normalizedAccountId.isBlank()) return
        preferences(context).edit().remove(keyFor(normalizedAccountId)).apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    private fun keyFor(accountId: String) = "$completedPrefix$accountId"
}
