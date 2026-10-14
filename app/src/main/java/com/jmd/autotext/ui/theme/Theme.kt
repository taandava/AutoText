package com.jmd.autotext.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Direct aliases for backward compatibility
val DarkBackground = BentoGlassTokens.VoidBackground
val DarkSurface = BentoGlassTokens.SurfaceDark
val DarkSurfaceVariant = BentoGlassTokens.SurfaceElevated
val PrimaryIndigo = BentoGlassTokens.ElectricIndigo
val SecondaryEmerald = BentoGlassTokens.VividEmerald
val AccentCyan = BentoGlassTokens.NeonCyan
val AccentAmber = BentoGlassTokens.RadiantAmber
val AccentRose = BentoGlassTokens.CyberRose
val DarkBorder = BentoGlassTokens.GlassBorderSubtle
val WarningAmber = BentoGlassTokens.RadiantAmber
val ErrorRed = BentoGlassTokens.CyberRose
val TextPrimary = BentoGlassTokens.TextPrimary
val TextSecondary = BentoGlassTokens.TextSecondary

/**
 * Curated Material 3 Typography Scale for AutoText
 * Calibrated tracking, font weights, and line heights for high contrast legibility.
 */
val AutoTextTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.2).sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.15).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.1).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.05).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.5.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.5.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.3.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 14.5.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.3.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

private val DarkColorScheme = darkColorScheme(
    // Primary: Sky Cyan
    primary = BentoGlassTokens.NeonCyan,
    onPrimary = Color(0xFF003548),
    primaryContainer = BentoGlassTokens.NeonCyanContainer,
    onPrimaryContainer = Color(0xFFBAE6FD),
    inversePrimary = Color(0xFF0284C7),

    // Secondary: Vivid Emerald
    secondary = BentoGlassTokens.VividEmerald,
    onSecondary = Color(0xFF003822),
    secondaryContainer = BentoGlassTokens.VividEmeraldContainer,
    onSecondaryContainer = Color(0xFFA7F3D0),

    // Tertiary: Electric Indigo
    tertiary = BentoGlassTokens.ElectricIndigo,
    onTertiary = Color(0xFF1E1B4B),
    tertiaryContainer = BentoGlassTokens.ElectricIndigoContainer,
    onTertiaryContainer = Color(0xFFE0E7FF),

    // Background & Surfaces
    background = BentoGlassTokens.VoidBackground,
    onBackground = BentoGlassTokens.TextPrimary,

    surface = BentoGlassTokens.SurfaceDark,
    onSurface = BentoGlassTokens.TextPrimary,
    surfaceVariant = BentoGlassTokens.SurfaceElevated,
    onSurfaceVariant = BentoGlassTokens.TextSecondary,

    // Material 3 Surface Containers
    surfaceDim = BentoGlassTokens.VoidBackground,
    surfaceBright = Color(0xFF2E3D59),
    surfaceContainerLowest = BentoGlassTokens.DeepBackground,
    surfaceContainerLow = BentoGlassTokens.GlassSurfaceSubtle,
    surfaceContainer = BentoGlassTokens.GlassSurfaceDefault,
    surfaceContainerHigh = BentoGlassTokens.GlassSurfaceElevated,
    surfaceContainerHighest = BentoGlassTokens.GlassSurfaceHover,

    // Inverses
    inverseSurface = Color(0xFFE2E8F0),
    inverseOnSurface = Color(0xFF0F172A),

    // Borders & Outlines
    outline = BentoGlassTokens.GlassBorderSubtle,
    outlineVariant = BentoGlassTokens.GlassBorderHairline,

    // Error & Scrim
    error = BentoGlassTokens.CyberRose,
    onError = Color(0xFF450A0A),
    errorContainer = BentoGlassTokens.CyberRoseContainer,
    onErrorContainer = Color(0xFFFECDD3),
    scrim = Color(0xFF000000)
)

@Composable
fun AutoTextTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            dynamicDarkColorScheme(context)
        }
        else -> DarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AutoTextTypography,
        shapes = Shapes(
            extraSmall = BentoGlassTokens.ShapeXSmall,
            small = BentoGlassTokens.ShapeSmall,
            medium = BentoGlassTokens.ShapeMedium,
            large = BentoGlassTokens.ShapeLarge,
            extraLarge = BentoGlassTokens.ShapeXLarge
        ),
        content = content
    )
}
