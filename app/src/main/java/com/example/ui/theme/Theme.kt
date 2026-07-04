package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = LuminaPrimary,
    onPrimary = LuminaOnPrimary,
    primaryContainer = LuminaPrimaryVariant,
    onPrimaryContainer = LuminaPrimary,
    secondary = LuminaSecondary,
    onSecondary = LuminaOnPrimary,
    background = LuminaBackground,
    surface = LuminaSurface,
    onBackground = LuminaText,
    onSurface = LuminaText,
    outline = LuminaOutline,
    surfaceVariant = LuminaSurfaceVariant,
    onSurfaceVariant = LuminaMuted
  )

private val LightColorScheme =
  lightColorScheme(
    primary = LuminaPrimary,
    onPrimary = LuminaOnPrimary,
    secondary = LuminaSecondary,
    background = Color.White,
    surface = Color.White,
    onBackground = LuminaBackground,
    onSurface = LuminaBackground,
    outline = LuminaOutline
  )

@Composable
fun LuminaTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false, // Set to false to prioritize Lumina brand colors
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
