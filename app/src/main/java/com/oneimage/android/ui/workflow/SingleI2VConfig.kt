package com.oneimage.android.ui.workflow

import kotlin.math.roundToInt

internal object SingleI2VConfig {
    const val MIN_DURATION_SECONDS = 3
    const val DEFAULT_DURATION_SECONDS = 3
    const val MAX_DURATION_SECONDS = 10
    const val MIN_FRAME_RATE = 1
    const val DEFAULT_FRAME_RATE = 16
    const val MAX_FRAME_RATE = 30
    const val DEFAULT_PROMPT = "gentle cinematic motion, natural camera movement, high quality"
    const val DEFAULT_ASPECT_RATIO = "1:1 (Square)"

    val aspectRatios = listOf(
        DEFAULT_ASPECT_RATIO,
        "2:3 (Portrait Photo)",
        "3:2 (Photo)",
        "3:4 (Portrait Standard)",
        "4:3 (Standard)",
        "9:16 (Portrait Widescreen)",
        "16:9 (Widescreen)",
        "21:9 (Ultrawide)"
    )

    private val aspectRatioAliases = mapOf(
        "16:9 (Landscape)" to "16:9 (Widescreen)",
        "9:16 (Portrait)" to "9:16 (Portrait Widescreen)",
        "4:3 (Classic)" to "4:3 (Standard)",
        "3:4 (Vertical)" to "3:4 (Portrait Standard)"
    )

    fun clampDuration(value: String?): Int = value
        ?.toFloatOrNull()
        ?.roundToInt()
        ?.coerceIn(MIN_DURATION_SECONDS, MAX_DURATION_SECONDS)
        ?: DEFAULT_DURATION_SECONDS

    fun durationInputValue(value: Float): String =
        value.roundToInt().coerceIn(MIN_DURATION_SECONDS, MAX_DURATION_SECONDS).toString()

    fun clampFrameRate(value: String?): Int = value
        ?.toFloatOrNull()
        ?.roundToInt()
        ?.coerceIn(MIN_FRAME_RATE, MAX_FRAME_RATE)
        ?: DEFAULT_FRAME_RATE

    fun normalizeAspectRatio(value: String?): String = value
        ?.trim()
        ?.let { aspectRatioAliases[it] ?: it }
        ?.takeIf(aspectRatios::contains)
        ?: DEFAULT_ASPECT_RATIO
}
