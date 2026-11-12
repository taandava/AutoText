/**
 * Package: com.jmd.helper.core
 * Module: Core Security, Invariants & Privacy
 *
 * What it is:
 * Architecture definition and implementation for [PostureManager].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for PostureManager.
 *
 * Linked With:
 * - Android Framework & Jetpack: [android.app.Activity], [androidx.window.layout.FoldingFeature], [androidx.window.layout.WindowInfoTracker], [androidx.window.layout.WindowLayoutInfo]
 */

package com.jmd.helper.core

import android.app.Activity
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * What it is:
 * Foldable and multi-window posture detector powered by AndroidX Window Manager.
 *
 * What it does:
 * Detects Samsung Galaxy Z Fold / Z Flip postures (Tabletop, Book, Flat) for responsive UI layouts.
 *
 * Linked with:
 * - Architecture: [PostureManager], [AppPostureManager]
 */
class AppPostureManager(private val activity: Activity) {

    private val windowInfoTracker = WindowInfoTracker.getOrCreate(activity)

    val windowLayoutInfo: Flow<WindowLayoutInfo> = windowInfoTracker.windowLayoutInfo(activity)

    val isTableTopMode: Flow<Boolean> = windowLayoutInfo.map { layoutInfo ->
        val foldingFeature = layoutInfo.displayFeatures
            .filterIsInstance<FoldingFeature>()
            .firstOrNull()

        foldingFeature != null &&
                foldingFeature.state == FoldingFeature.State.HALF_OPENED &&
                foldingFeature.orientation == FoldingFeature.Orientation.HORIZONTAL
    }

    val isBookMode: Flow<Boolean> = windowLayoutInfo.map { layoutInfo ->
        val foldingFeature = layoutInfo.displayFeatures
            .filterIsInstance<FoldingFeature>()
            .firstOrNull()

        foldingFeature != null &&
                foldingFeature.state == FoldingFeature.State.HALF_OPENED &&
                foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL
    }

    val isSeparatingHinge: Flow<Boolean> = windowLayoutInfo.map { layoutInfo ->
        val foldingFeature = layoutInfo.displayFeatures
            .filterIsInstance<FoldingFeature>()
            .firstOrNull()

        foldingFeature?.isSeparating ?: false
    }
}
