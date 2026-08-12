package com.oneimage.android.ui.workflow

internal object CharacterReplacementConfig {
    const val MIN_DURATION_SECONDS = 0.1f
    const val MAX_INPUT_DURATION_SECONDS = 3f
    const val MAX_PRODUCTION_DURATION_SECONDS = 3f

    fun maxProductionDurationForSource(sourceDuration: Float?): Float {
        val source = sourceDuration?.takeIf { it > 0f } ?: MAX_PRODUCTION_DURATION_SECONDS
        return source.coerceAtMost(MAX_PRODUCTION_DURATION_SECONDS).coerceAtLeast(MIN_DURATION_SECONDS)
    }

    fun clampDuration(value: String?, sourceDuration: Float?): Float {
        val maxDuration = maxProductionDurationForSource(sourceDuration)
        val requested = value?.toFloatOrNull()?.takeIf { it > 0f } ?: maxDuration
        return requested.coerceIn(MIN_DURATION_SECONDS, maxDuration)
    }

    fun inputDurationAllowed(sourceDuration: Float?): Boolean =
        sourceDuration?.let { it > 0f && it <= MAX_INPUT_DURATION_SECONDS } == true
}
