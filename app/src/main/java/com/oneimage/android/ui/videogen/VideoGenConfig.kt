package com.oneimage.android.ui.videogen

import kotlin.math.roundToInt

data class VideoOutputResolution(
    val width: Int,
    val height: Int
) {
    val label: String
        get() = "$width x $height"
}

internal object VideoGenConfig {
    const val MIN_DURATION_SECONDS = 1
    const val DEFAULT_DURATION_SECONDS = 6
    const val MAX_DURATION_SECONDS = 12
    const val MIN_FRAME_RATE = 1
    const val DEFAULT_FRAME_RATE = 25
    const val MAX_FRAME_RATE = 30
    const val MAX_DIMENSION = 1280
    const val MIN_DIMENSION = 160
    private const val DIMENSION_MULTIPLE = 32

    fun clampDuration(value: String?): Int = value
        ?.toFloatOrNull()
        ?.roundToInt()
        ?.coerceIn(MIN_DURATION_SECONDS, MAX_DURATION_SECONDS)
        ?: DEFAULT_DURATION_SECONDS

    fun clampFrameRate(value: String?): Int = value
        ?.toFloatOrNull()
        ?.roundToInt()
        ?.coerceIn(MIN_FRAME_RATE, MAX_FRAME_RATE)
        ?: DEFAULT_FRAME_RATE

    fun numericInputValue(value: String, maxDigits: Int = 2): String =
        value.filter { it.isDigit() }.take(maxDigits.coerceAtLeast(0))

    fun durationValueForInput(input: String, current: Int): Int =
        input.takeIf { it.isNotBlank() }?.let(::clampDuration) ?: current

    fun frameRateValueForInput(input: String, current: Int): Int =
        input.takeIf { it.isNotBlank() }?.let(::clampFrameRate) ?: current

    fun optimalResolution(width: Int, height: Int): VideoOutputResolution {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        var outputWidth = safeWidth
        var outputHeight = safeHeight

        if (outputWidth > MAX_DIMENSION || outputHeight > MAX_DIMENSION) {
            val ratio = outputWidth.toFloat() / outputHeight.toFloat()
            if (ratio > 1f) {
                outputWidth = MAX_DIMENSION
                outputHeight = (MAX_DIMENSION / ratio).roundToInt()
            } else {
                outputHeight = MAX_DIMENSION
                outputWidth = (MAX_DIMENSION * ratio).roundToInt()
            }
        }

        outputWidth = (outputWidth.toFloat() / DIMENSION_MULTIPLE).roundToInt() * DIMENSION_MULTIPLE
        outputHeight = (outputHeight.toFloat() / DIMENSION_MULTIPLE).roundToInt() * DIMENSION_MULTIPLE

        return VideoOutputResolution(
            width = outputWidth.coerceAtLeast(MIN_DIMENSION),
            height = outputHeight.coerceAtLeast(MIN_DIMENSION)
        )
    }
}
