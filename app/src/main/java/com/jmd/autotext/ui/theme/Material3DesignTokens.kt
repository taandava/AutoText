package com.jmd.autotext.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Material 3 Design Tokens for AutoText
 * Pure solid, high-contrast Material 3 tokens for dark OLED displays.
 * Google Material 3 Design System compliant.
 */
object Material3DesignTokens {

    // ==========================================
    // 1. BASE BACKGROUND & SURFACE HUES (Clean Material 3 Dark)
    // ==========================================
    val VoidBackground = Color(0xFF111318)          // M3 Dark Surface (Clean Minimal Neutral)
    val DeepBackground = Color(0xFF0C0E12)          // M3 Container Lowest
    val SurfaceDark = Color(0xFF191C20)             // M3 Base Surface
    val SurfaceElevated = Color(0xFF1F2329)         // M3 Container Default
    val GlassBackground = VoidBackground

    // Clean Subtle Background Gradient
    val BackgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF111318),
            Color(0xFF14171D),
            Color(0xFF191C22)
        )
    )

    // ==========================================
    // 2. SOLID MATERIAL 3 SURFACES & TONAL LEVELS
    // ==========================================
    val GlassSurfaceSubtle = Color(0xFF191C22)      // M3 SurfaceContainerLow
    val GlassSurfaceDefault = Color(0xFF1F2329)     // M3 SurfaceContainer
    val GlassSurfaceElevated = Color(0xFF252A30)    // M3 SurfaceContainerHigh
    val GlassSurfaceHover = Color(0xFF2C3138)       // M3 SurfaceContainerHighest
    val GlassSurfaceActive = Color(0xFF343A43)      // M3 Active Tonal Level

    // Compact Material 3 Elevation Levels (dp)
    val ElevationLevel0 = 0.dp
    val ElevationLevel1 = 0.dp
    val ElevationLevel2 = 1.dp
    val ElevationLevel3 = 2.dp
    val ElevationLevel4 = 3.dp
    val ElevationLevel5 = 4.dp

    // Solid Card Overlays (Clean flat fills, no translucent glare)
    val GlassCardBrush = Brush.verticalGradient(
        colors = listOf(Color.Transparent, Color.Transparent)
    )

    val GlassCardHighlightBrush = Brush.linearGradient(
        colors = listOf(Color.Transparent, Color.Transparent)
    )

    // ==========================================
    // 3. MATERIAL 3 OUTLINES & DIVIDERS (Subtle & Minimal)
    // ==========================================
    val GlassBorderHairline = Color(0xFF282D36)     // M3 Outline Variant (Subtle)
    val GlassBorderSubtle = Color(0xFF363C47)       // M3 Default Card Outline (Clean minimal border)
    val GlassBorderMedium = Color(0xFF454D59)       // M3 Emphasized Outline
    val GlassBorderHighlight = Color(0xFF5A6372)    // M3 Focus Outline
    val GlassBorderActive = Color(0xFF67B7F7)       // M3 Active Primary Outline

    val GlassBorderGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF363C47),
            Color(0xFF282D36)
        )
    )

    // ==========================================
    // 4. MATERIAL 3 CLEAN & MINIMAL TONAL PALETTE
    // ==========================================
    val NeonCyan = Color(0xFF78C8FF)                // M3 Sky Blue (Clean, calm Primary)
    val NeonCyanContainer = Color(0xFF004C6D)
    val NeonCyanGlow = Color(0x2278C8FF)

    val ElectricIndigo = Color(0xFFA5B8FF)          // M3 Soft Periwinkle (Tertiary)
    val ElectricIndigoContainer = Color(0xFF233E82)
    val ElectricIndigoGlow = Color(0x22A5B8FF)

    val VividEmerald = Color(0xFF67DC9E)            // M3 Mint Green (Clean WhatsApp / Success)
    val VividEmeraldContainer = Color(0xFF005230)
    val VividEmeraldGlow = Color(0x2267DC9E)

    val RadiantAmber = Color(0xFFFFB74D)            // M3 Soft Amber (Warning / Notice)
    val RadiantAmberContainer = Color(0xFF5A3B00)
    val RadiantAmberGlow = Color(0x22FFB74D)

    val CyberRose = Color(0xFFFF7170)               // M3 Soft Coral (Error / Alert)
    val CyberRoseContainer = Color(0xFF601416)
    val CyberRoseGlow = Color(0x22FF7170)

    val VividViolet = Color(0xFFD4BBFF)
    val VividVioletContainer = Color(0xFF482B7D)

    // Clean subtle accent gradients
    val CyanGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF78C8FF), Color(0xFF4EA8DE))
    )

    val EmeraldGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF67DC9E), Color(0xFF3CB371))
    )

    val IndigoGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFFA5B8FF), Color(0xFF7F95D1))
    )

    val RoseGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFFFF7170), Color(0xFFE55353))
    )

    val AmberGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFFFFB74D), Color(0xFFF39C12))
    )

    val VioletGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFFD4BBFF), Color(0xFF9F7AEA))
    )

    // ==========================================
    // 5. TEXT HIERARCHY & CONTRAST
    // ==========================================
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFFCBD5E1)
    val TextMuted = Color(0xFF94A3B8)
    val TextDisabled = Color(0xFF64748B)

    // ==========================================
    // 6. MATERIAL 3 SHAPES & RADII (Compact M3 Scale)
    // ==========================================
    val RadiusXSmall = 6.dp
    val RadiusSmall = 8.dp
    val RadiusMedium = 12.dp
    val RadiusLarge = 16.dp
    val RadiusXLarge = 20.dp
    val RadiusPill = 999.dp

    val ShapeXSmall = RoundedCornerShape(RadiusXSmall)
    val ShapeSmall = RoundedCornerShape(RadiusSmall)
    val ShapeMedium = RoundedCornerShape(RadiusMedium)
    val ShapeLarge = RoundedCornerShape(RadiusLarge)
    val ShapeXLarge = RoundedCornerShape(RadiusXLarge)
    val ShapePill = RoundedCornerShape(RadiusPill)

    // ==========================================
    // 6B. SPACING TOKENS (High-Density / Compact)
    // ==========================================
    val SpacingTiny = 3.dp
    val SpacingSmall = 5.dp
    val SpacingMedium = 8.dp
    val SpacingCard = 8.dp
    val SpacingLarge = 10.dp

    // ==========================================
    // 7. COMPACT ELEVATION LEVELS (dp)
    // ==========================================

    // ==========================================
    // 7B. COMPACT COMPONENT SIZES
    // ==========================================
    val IconSizeSm = 16.dp
    val IconSizeMd = 18.dp
    val HitTargetMin = 32.dp

    // ==========================================
    // 7C. CARD PADDING (Horizontal / Vertical)
    // ==========================================
    val CardPaddingH = 8.dp
    val CardPaddingV = 6.dp

    // ==========================================
    // 7D. SPRING ANIMATIONS
    // ==========================================
    val SpringBouncy = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    val SpringFast = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
}
