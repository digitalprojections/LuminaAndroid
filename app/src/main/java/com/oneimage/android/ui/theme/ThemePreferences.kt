package com.oneimage.android.ui.theme

import android.content.Context

object ThemePreferences {
    private const val PREFS_NAME = "lumina_theme_prefs"
    private const val KEY_DARK_MODE = "dark_mode_enabled"

    fun getDarkModeEnabled(context: Context, defaultValue: Boolean): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .run {
                if (contains(KEY_DARK_MODE)) getBoolean(KEY_DARK_MODE, defaultValue) else defaultValue
            }

    fun setDarkModeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK_MODE, enabled)
            .apply()
    }
}
