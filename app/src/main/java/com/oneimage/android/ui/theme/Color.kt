package com.oneimage.android.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Lumina Premium Dark Theme Colors
val LuminaDarkBackground = Color(0xFF0F0F13) // Pitch black/deep gray
val LuminaDarkSurface = Color(0xFF1A1A21)
val LuminaDarkPrimary = Color(0xFF00E5FF) // Cyan
val LuminaDarkOnPrimary = Color(0xFF00363D)
val LuminaDarkSecondary = Color(0xFFB388FF) // Purple accent
val LuminaDarkOnSecondary = Color(0xFF2B0071)
val LuminaDarkOutline = Color(0xFF33333D)
val LuminaDarkMuted = Color(0xFF8C8C99)
val LuminaDarkText = Color(0xFFF0F0F5)
val LuminaDarkSurfaceVariant = Color(0xFF22222B)
val LuminaDarkPrimaryVariant = Color(0xFF005661)

// Lumina Premium Light Theme Colors
val LuminaLightBackground = Color(0xFFF8F9FA) // Soft off-white
val LuminaLightSurface = Color(0xFFFFFFFF) // Pure white
val LuminaLightPrimary = Color(0xFF00B8D4) // Slightly darker cyan for better contrast
val LuminaLightOnPrimary = Color(0xFFFFFFFF)
val LuminaLightSecondary = Color(0xFF7E57C2) // Deeper purple for better contrast
val LuminaLightOnSecondary = Color(0xFFFFFFFF)
val LuminaLightOutline = Color(0xFFDEE2E6)
val LuminaLightMuted = Color(0xFF6C757D)
val LuminaLightText = Color(0xFF212529) // Dark gray/black
val LuminaLightSurfaceVariant = Color(0xFFF1F3F5)
val LuminaLightPrimaryVariant = Color(0xFF00E5FF)

val ErrorRed = Color(0xFFFF5252)
val SuccessGreen = Color(0xFF69F0AE)

// Premium Gradient Accent (Used across themes)
val PrimaryGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF00E5FF), Color(0xFFB388FF))
)
