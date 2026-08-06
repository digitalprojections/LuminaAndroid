package com.oneimage.android.ui.workflow

import kotlin.math.floor
import kotlin.math.roundToInt

internal object SingleI2VConfig {
    const val MIN_DURATION_SECONDS = 3
    const val DEFAULT_DURATION_SECONDS = 3
    const val MAX_DURATION_SECONDS = 10
    const val MIN_FRAME_RATE = 1
    const val DEFAULT_FRAME_RATE = 16
    const val MAX_FRAME_RATE = 30
    const val MAX_SOURCE_LONG_EDGE = 768
    const val DEFAULT_PROMPT = "gentle cinematic motion, natural camera movement, high quality"
    const val DEFAULT_ASPECT_RATIO = "1:1 (Square)"

    private const val PIXEL_SECONDS_BUDGET = 512 * 512 * MAX_DURATION_SECONDS

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

    fun durationInputValue(value: Float, imageDimensions: Pair<Int, Int>?, frameRate: Int): String =
        value.roundToInt()
            .coerceIn(MIN_DURATION_SECONDS, maxDurationForInput(imageDimensions, frameRate))
            .toString()

    fun clampFrameRate(value: String?): Int = value
        ?.toFloatOrNull()
        ?.roundToInt()
        ?.coerceIn(MIN_FRAME_RATE, MAX_FRAME_RATE)
        ?: DEFAULT_FRAME_RATE

    fun maxDurationForInput(imageDimensions: Pair<Int, Int>?, frameRate: Int = DEFAULT_FRAME_RATE): Int {
        val dimensions = imageDimensions?.takeIf { it.first > 0 && it.second > 0 } ?: return MAX_DURATION_SECONDS
        val fps = frameRate.coerceIn(MIN_FRAME_RATE, MAX_FRAME_RATE)
        val pixels = (dimensions.first * dimensions.second).coerceAtLeast(1)
        val maxByBudget = floor((PIXEL_SECONDS_BUDGET * DEFAULT_FRAME_RATE).toDouble() / (pixels.toDouble() * fps.toDouble()))
            .toInt()
        return maxByBudget.coerceIn(MIN_DURATION_SECONDS, MAX_DURATION_SECONDS)
    }

    fun clampDurationForInput(value: String?, imageDimensions: Pair<Int, Int>?, frameRate: Int): Int =
        clampDuration(value).coerceAtMost(maxDurationForInput(imageDimensions, frameRate))

    fun preparedImageWithinLimit(imageDimensions: Pair<Int, Int>?): Boolean =
        imageDimensions != null &&
            imageDimensions.first in 1..MAX_SOURCE_LONG_EDGE &&
            imageDimensions.second in 1..MAX_SOURCE_LONG_EDGE

    fun normalizeAspectRatio(value: String?): String = value
        ?.trim()
        ?.let { aspectRatioAliases[it] ?: it }
        ?.takeIf(aspectRatios::contains)
        ?: DEFAULT_ASPECT_RATIO
}
