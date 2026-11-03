/**
 * Package: com.jmd.autotext.compat
 * Module: Compat Layer
 *
 * Provides safe, backward-compatible [PackageManager] query operations across Android versions.
 */
package com.jmd.autotext.compat

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

/**
 * Backward-compatible helper for [PackageManager] queries across evolving Android SDK releases.
 */
object PackageManagerCompat {

    /**
     * Retrieves all activities that can be performed for the given intent across legacy and modern Android releases.
     *
     * @param pm System [PackageManager] instance.
     * @param intent Target query [Intent].
     * @return List of matching [ResolveInfo] instances.
     */
    @JvmStatic
    fun queryIntentActivities(pm: PackageManager, intent: Intent): List<ResolveInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
    }
}

