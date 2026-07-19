package com.oneimage.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = LuminaDarkPrimary,
    onPrimary = LuminaDarkOnPrimary,
    primaryContainer = LuminaDarkPrimaryVariant,
    onPrimaryContainer = LuminaDarkPrimary,
    secondary = LuminaDarkSecondary,
    onSecondary = LuminaDarkOnSecondary,
    background = LuminaDarkBackground,
    surface = LuminaDarkSurface,
    onBackground = LuminaDarkText,
    onSurface = LuminaDarkText,
    outline = LuminaDarkOutline,
    surfaceVariant = LuminaDarkSurfaceVariant,
    onSurfaceVariant = LuminaDarkMuted
  )

private val LightColorScheme =
  lightColorScheme(
    primary = LuminaLightPrimary,
    onPrimary = LuminaLightOnPrimary,
    primaryContainer = LuminaLightPrimaryVariant,
    onPrimaryContainer = LuminaLightOnPrimaryContainer,
    secondary = LuminaLightSecondary,
    onSecondary = LuminaLightOnSecondary,
    background = LuminaLightBackground,
    surface = LuminaLightSurface,
    onBackground = LuminaLightText,
    onSurface = LuminaLightText,
    outline = LuminaLightOutline,
    surfaceVariant = LuminaLightSurfaceVariant,
    onSurfaceVariant = LuminaLightMuted
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

