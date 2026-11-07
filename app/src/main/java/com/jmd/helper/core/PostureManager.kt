package com.jmd.helper.core

import android.app.Activity
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Foldable and multi-window posture detector powered by AndroidX Window Manager.
 * Detects Samsung Galaxy Z Fold / Z Flip postures (Tabletop, Book, Flat) for responsive UI layouts.
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

