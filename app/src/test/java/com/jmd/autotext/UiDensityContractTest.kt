package com.jmd.autotext

import androidx.compose.ui.unit.dp
import com.jmd.autotext.ui.theme.Material3DesignTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Density contract for the compact UI pass.
 *
 * These assertions lock in the compaction so it cannot silently regress.
 * All values are pure-JVM Dp comparisons - no Android device required.
 */
class UiDensityContractTest {

    @Test
    fun radiiFormADescendingLadder() {
        val ladder = listOf(
            Material3DesignTokens.RadiusSmall,
            Material3DesignTokens.RadiusMedium,
            Material3DesignTokens.RadiusLarge,
            Material3DesignTokens.RadiusXLarge
        )
        ladder.zipWithNext { smaller, larger ->
            assertTrue(
                "Radius ladder must be strictly ascending but got $ladder",
                smaller < larger
            )
        }
    }

    @Test
    fun radiiStayCompact() {
        // Pre-compaction these were 10 / 14 / 18 / 22 dp.
        assertEquals(8.dp, Material3DesignTokens.RadiusSmall)
        assertEquals(12.dp, Material3DesignTokens.RadiusMedium)
        assertEquals(16.dp, Material3DesignTokens.RadiusLarge)
        assertEquals(20.dp, Material3DesignTokens.RadiusXLarge)
    }

    @Test
    fun spacingTokensNeverExceedCompactBudget() {
        val all = listOf(
            Material3DesignTokens.SpacingTiny,
            Material3DesignTokens.SpacingSmall,
            Material3DesignTokens.SpacingMedium,
            Material3DesignTokens.SpacingCard,
            Material3DesignTokens.SpacingLarge
        )
        all.forEach { value ->
            assertTrue("Spacing token $value exceeds the 12.dp compact budget", value <= 12.dp)
        }
        assertTrue("Spacing ladder must be ascending", all.sorted() == all)
    }

    @Test
    fun cardPaddingIsTighterThanSpacingLarge() {
        // A card must not introduce more gutter than a generic block.
        assertTrue(
            Material3DesignTokens.CardPaddingH <= Material3DesignTokens.SpacingLarge
        )
        assertTrue(
            Material3DesignTokens.CardPaddingV < Material3DesignTokens.CardPaddingH
        )
    }

    @Test
    fun hitTargetRemainsAccessibleWhileIconsShrink() {
        // Icons got smaller (16 / 18 dp) but the touch target must stay usable.
        assertTrue(Material3DesignTokens.HitTargetMin >= 32.dp)
        assertTrue(Material3DesignTokens.HitTargetMin > Material3DesignTokens.IconSizeMd)
    }

    @Test
    fun iconSizesAreOrderedAndSmall() {
        assertTrue(
            Material3DesignTokens.IconSizeSm < Material3DesignTokens.IconSizeMd
        )
        assertTrue(Material3DesignTokens.IconSizeMd <= 18.dp)
    }

    @Test
    fun elevationLadderIsFlatForADenseSurface() {
        // Compact surfaces should rely on border/tint, not heavy shadows.
        assertTrue(Material3DesignTokens.ElevationLevel1 <= 1.dp)
        assertTrue(Material3DesignTokens.ElevationLevel2 <= 1.dp)
        assertTrue(Material3DesignTokens.ElevationLevel3 <= 2.dp)
        assertTrue(
            Material3DesignTokens.ElevationLevel1 < Material3DesignTokens.ElevationLevel3
        )
    }

    @Test
    fun pillRadiusEffectivelyClipsToFullyRound() {
        // Used by chips/buttons; must dominate any realistic component width.
        assertTrue(Material3DesignTokens.RadiusPill >= 999.dp)
    }
}
