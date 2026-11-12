/**
 * Package: com.jmd.helper.compat
 * Module: Platform Compatibility Layer
 *
 * What it is:
 * Architecture definition and implementation for [PackageManagerCompat].
 *
 * What it does:
 * Provides safe, backward-compatible [PackageManager] query operations across Android versions.
 *
 * Linked With:
 * - Core Security & Utilities: [android.content.pm.PackageManager]
 * - Android Framework & Jetpack: [android.content.pm.ResolveInfo], [android.os.Build]
 */

package com.jmd.helper.compat

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

/**
 * What it is:
 * Backward-compatible helper for [PackageManager] queries across evolving Android SDK releases.
 *
 * What it does:
 * Executes PackageManagerCompat operations conforming to application constraints.
 *
 * Linked with:
 * - Architecture: [PackageManagerCompat], [PackageManagerCompat]
 */
object PackageManagerCompat {

    /**
     * What it is:
     * Retrieves all activities that can be performed for the given intent across legacy and modern Android releases.
     *
     * What it does:
     * Executes queryIntentActivities operations conforming to application constraints.
     *
     * Linked with:
     * - Architecture: [PackageManagerCompat], [queryIntentActivities]
     * @param pm System [PackageManager] instance.
     * @param intent Target query [Intent].
     * @return List of matching [ResolveInfo] instances.
     */
    @JvmStatic
    fun queryIntentActivities(pm: PackageManager, intent: Intent): List<ResolveInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            pm.queryIntentActivities(intent, 0)
        }
    }
}
