/**
 * Package: com.jmd.autotext.ui.theme
 * Module: UI Theme & Design Tokens Layer
 *
 * Defines the Material 3 Expressive Orange Design System:
 * - Brand Orange Palette (#E76F00, #FFB77A, #FFF3E7, #F8E2CF, #563316)
 * - Compact spacing & information density tokens
 * - Full-width layout conventions
 * - M3 Expressive shape scale & typography
 * - System-integrated Light, Dark, and optional Dynamic Color theming
 *
 * Linked With:
 * - Directives: design.md
 * - Activity: [com.jmd.autotext.ui.activity.MainActivity]
 * - Components: [com.jmd.autotext.ui.components.GlassComponents]
 */
package com.jmd.autotext.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// MATERIAL 3 EXPRESSIVE ORANGE DESIGN SYSTEM
// ============================================================================

/**
 * Standardized design constants and theme tokens for AutoText.
 */
val AppDesignTokens = Material3DesignTokens

// ============================================================================
// 1. BRAND PALETTE (Per design.md Section 5.1)
// ============================================================================
object OrangeBrandPalette {
    val PrimaryBrand = Color(0xFFE76F00)       // Primary brand orange
    val PrimaryLightAccent = Color(0xFFFFB77A) // Light orange accent
    val WarmBackground = Color(0xFFFFF3E7)     // Warm neutral background canvas
    val WarmContainer = Color(0xFFF8E2CF)      // Warm container surface
    val SupportingBrown = Color(0xFF563316)    // Supporting brand brown
    val PrimaryDarkText = Color(0xFF1F1B17)    // High contrast reading dark
    val SuccessGreen = Color(0xFF2E7D32)       // Material Success Green
    val ErrorRed = Color(0xFFBA1A1A)           // Material Error Red

    // Tonal Scales
    val Orange90 = Color(0xFFFFDCC1)
    val Orange80 = Color(0xFFFFB77A)
    val Orange70 = Color(0xFFFF9438)
    val Orange60 = Color(0xFFF97800)
    val Orange50 = Color(0xFFE76F00)
    val Orange40 = Color(0xFFB85400)
    val Orange30 = Color(0xFF883C00)
    val Orange20 = Color(0xFF5B2600)
    val Orange10 = Color(0xFF321200)

    // Neutral Surfaces & Slates
    val DarkCanvas = Color(0xFF141210)
    val DarkSurface = Color(0xFF1C1917)
    val DarkSurfaceContainer = Color(0xFF26221E)
    val DarkSurfaceContainerHigh = Color(0xFF332D28)
    val DarkSurfaceContainerHighest = Color(0xFF423B35)
    val DarkOutline = Color(0xFF4D443D)
    val DarkOutlineVariant = Color(0xFF38312B)
    val DarkOnSurface = Color(0xFFEDE0D7)
    val DarkOnSurfaceVariant = Color(0xFFCBBFAF)

    val LightCanvas = Color(0xFFFDF7F2)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceContainerLow = Color(0xFFFBF2EA)
    val LightSurfaceContainer = Color(0xFFF6EAE0)
    val LightSurfaceContainerHigh = Color(0xFFF0E2D6)
    val LightSurfaceContainerHighest = Color(0xFFE8D8CB)
    val LightOutline = Color(0xFFDFCEBF)
    val LightOutlineVariant = Color(0xFFEBE0D5)
    val LightOnSurface = Color(0xFF1F1B17)
    val LightOnSurfaceVariant = Color(0xFF5B5249)
}

/**
 * Material 3 Design Tokens for AutoText
 * Compact, full-width, responsive, with an expressive Orange brand identity.
 */
object Material3DesignTokens {

    // ==========================================
    // DYNAMIC BASE BACKGROUND & SURFACE HUES
    // ==========================================
    val VoidBackground: Color
        @Composable
        get() = MaterialTheme.colorScheme.background

    val GlassBackground: Color
        @Composable
        get() = MaterialTheme.colorScheme.background

    val GlassSurfaceSubtle: Color
        @Composable
        get() = MaterialTheme.colorScheme.surfaceContainerLow

    val GlassSurfaceDefault: Color
        @Composable
        get() = MaterialTheme.colorScheme.surface

    val GlassSurfaceElevated: Color
        @Composable
        get() = MaterialTheme.colorScheme.surfaceContainerHigh

    // Compact Card Elevation Levels (dp)
    val ElevationLevel1 = 1.dp
    val ElevationLevel2 = 2.dp

    // Outlines & Dividers
    val GlassBorderHairline: Color
        @Composable
        get() = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    val GlassBorderSubtle: Color
        @Composable
        get() = MaterialTheme.colorScheme.outlineVariant

    val GlassBorderGradient: Brush
        @Composable
        get() = Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            ),
        )

    // Semantic Brand & Functional Accent Colors
    val NeonCyan: Color
        @Composable
        get() = MaterialTheme.colorScheme.primary

    val ElectricIndigo: Color
        @Composable
        get() = MaterialTheme.colorScheme.primary

    val ElectricAzure: Color
        @Composable
        get() = MaterialTheme.colorScheme.primary

    val VividEmerald = OrangeBrandPalette.SuccessGreen
    val RadiantAmber = Color(0xFFF57C00)
    val CyberRose = OrangeBrandPalette.ErrorRed

    val EmeraldGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF2E7D32), Color(0xFF4CAF50))
    )

    val IndigoGradient = Brush.horizontalGradient(
        colors = listOf(OrangeBrandPalette.PrimaryBrand, OrangeBrandPalette.Orange70)
    )

    val RoseGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFFC62828), Color(0xFFE53935))
    )

    val VioletGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF7B1FA2), Color(0xFF9C27B0))
    )

    // Dynamic High-Contrast Text Tokens
    val TextPrimary: Color
        @Composable
        get() = MaterialTheme.colorScheme.onSurface

    val TextSecondary: Color
        @Composable
        get() = MaterialTheme.colorScheme.onSurfaceVariant

    val TextMuted: Color
        @Composable
        get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)

    val TextDisabled: Color
        @Composable
        get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    // Material 3 Expressive Shapes & Radii
    val RadiusXSmall = 6.dp
    val RadiusSmall = 10.dp
    val RadiusMedium = 14.dp
    val RadiusLarge = 18.dp
    val RadiusXLarge = 22.dp
    val RadiusPill = 999.dp

    val ShapeXSmall = RoundedCornerShape(RadiusXSmall)
    val ShapeSmall = RoundedCornerShape(RadiusSmall)
    val ShapeMedium = RoundedCornerShape(RadiusMedium)
    val ShapeLarge = RoundedCornerShape(RadiusLarge)
    val ShapeXLarge = RoundedCornerShape(RadiusXLarge)
    val ShapePill = RoundedCornerShape(RadiusPill)

    // Compact Spacing Scale (design.md Section 7.1)
    val SpaceMicro = 2.dp
    val SpaceXS = 4.dp
    val SpaceSmall = 6.dp
    val SpaceMedium = 8.dp
    val SpaceStandard = 12.dp
    val SpaceLarge = 16.dp
    val SpaceXL = 20.dp
    val SpaceSection = 24.dp

    // Content gutters & card padding
    val CardPaddingH = 12.dp
    val CardPaddingV = 10.dp
    val ScreenGutter = 12.dp

    // Animation Specs
    val SpringFast = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium
    )
}

// ============================================================================
// 2. TYPOGRAPHY HIERARCHY (Compact, High Readability)
// ============================================================================
private val AutoTextTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.15).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.5.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.5.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp
    )
)

// ============================================================================
// 3. COLOR SCHEMES (Expressive Brand Orange)
// ============================================================================
val DarkColorScheme = darkColorScheme(
    primary = OrangeBrandPalette.PrimaryLightAccent,
    onPrimary = OrangeBrandPalette.Orange10,
    primaryContainer = OrangeBrandPalette.Orange30,
    onPrimaryContainer = OrangeBrandPalette.Orange90,
    inversePrimary = OrangeBrandPalette.PrimaryBrand,

    secondary = OrangeBrandPalette.Orange70,
    onSecondary = OrangeBrandPalette.Orange10,
    secondaryContainer = OrangeBrandPalette.SupportingBrown,
    onSecondaryContainer = OrangeBrandPalette.Orange90,

    tertiary = Color(0xFFFFB273),
    onTertiary = Color(0xFF4C2700),
    tertiaryContainer = Color(0xFF6B3A00),
    onTertiaryContainer = Color(0xFFFFDCBE),

    background = OrangeBrandPalette.DarkCanvas,
    onBackground = OrangeBrandPalette.DarkOnSurface,

    surface = OrangeBrandPalette.DarkSurface,
    onSurface = OrangeBrandPalette.DarkOnSurface,
    surfaceVariant = OrangeBrandPalette.DarkSurfaceContainer,
    onSurfaceVariant = OrangeBrandPalette.DarkOnSurfaceVariant,

    surfaceDim = OrangeBrandPalette.DarkCanvas,
    surfaceBright = OrangeBrandPalette.DarkSurfaceContainerHighest,
    surfaceContainerLowest = Color(0xFF0F0E0D),
    surfaceContainerLow = OrangeBrandPalette.DarkSurface,
    surfaceContainer = OrangeBrandPalette.DarkSurfaceContainer,
    surfaceContainerHigh = OrangeBrandPalette.DarkSurfaceContainerHigh,
    surfaceContainerHighest = OrangeBrandPalette.DarkSurfaceContainerHighest,

    inverseSurface = OrangeBrandPalette.LightSurface,
    inverseOnSurface = OrangeBrandPalette.LightOnSurface,

    outline = OrangeBrandPalette.DarkOutline,
    outlineVariant = OrangeBrandPalette.DarkOutlineVariant,

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000)
)

val LightColorScheme = lightColorScheme(
    primary = OrangeBrandPalette.PrimaryBrand,
    onPrimary = Color.White,
    primaryContainer = OrangeBrandPalette.WarmContainer,
    onPrimaryContainer = OrangeBrandPalette.Orange10,
    inversePrimary = OrangeBrandPalette.PrimaryLightAccent,

    secondary = OrangeBrandPalette.SupportingBrown,
    onSecondary = Color.White,
    secondaryContainer = OrangeBrandPalette.WarmContainer,
    onSecondaryContainer = OrangeBrandPalette.Orange10,

    tertiary = OrangeBrandPalette.Orange60,
    onTertiary = Color.White,
    tertiaryContainer = OrangeBrandPalette.Orange90,
    onTertiaryContainer = OrangeBrandPalette.Orange20,

    background = OrangeBrandPalette.LightCanvas,
    onBackground = OrangeBrandPalette.LightOnSurface,

    surface = OrangeBrandPalette.LightSurface,
    onSurface = OrangeBrandPalette.LightOnSurface,
    surfaceVariant = OrangeBrandPalette.LightSurfaceContainerLow,
    onSurfaceVariant = OrangeBrandPalette.LightOnSurfaceVariant,

    surfaceDim = Color(0xFFEDE0D5),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = OrangeBrandPalette.LightSurfaceContainerLow,
    surfaceContainer = OrangeBrandPalette.LightSurfaceContainer,
    surfaceContainerHigh = OrangeBrandPalette.LightSurfaceContainerHigh,
    surfaceContainerHighest = OrangeBrandPalette.LightSurfaceContainerHighest,

    inverseSurface = OrangeBrandPalette.DarkSurface,
    inverseOnSurface = OrangeBrandPalette.DarkOnSurface,

    outline = OrangeBrandPalette.LightOutline,
    outlineVariant = OrangeBrandPalette.LightOutlineVariant,

    error = OrangeBrandPalette.ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

/**
 * Top-level AutoText Theme Composable.
 * Supports SYSTEM (default), LIGHT, DARK, and Android 12+ Wallpaper Dynamic Color.
 */
@Composable
fun autoTextTheme(
    themeMode: String = "SYSTEM",
    isDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemInDark = isSystemInDarkTheme()

    val isDark = when (themeMode.uppercase()) {
        "DARK" -> true
        "LIGHT" -> false
        else -> systemInDark
    }

    val colorScheme = when {
        isDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            // If theme is NOT dark (i.e. Light theme), status bar icons must be DARK so they are clearly visible
            // If theme IS dark, status bar icons must be LIGHT (white)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AutoTextTypography,
        shapes = Shapes(
            extraSmall = AppDesignTokens.ShapeXSmall,
            small = AppDesignTokens.ShapeSmall,
            medium = AppDesignTokens.ShapeMedium,
            large = AppDesignTokens.ShapeLarge,
            extraLarge = AppDesignTokens.ShapeXLarge
        ),
        content = content
    )
}
