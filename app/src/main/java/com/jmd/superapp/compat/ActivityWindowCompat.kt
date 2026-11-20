/**
 * Package: com.jmd.superapp.compat
 * Module: Platform Compatibility Layer
 *
 * What it is:
 * Backward-compatible activity window and lockscreen helper for Android 8.0 (API 26) through Android 15/16 (API 35+).
 *
 * What it does:
 * Provides safe execution of [Activity.setShowWhenLocked] and [Activity.setTurnScreenOn], which were introduced
 * in Android 8.1 Oreo MR1 (API 27). On Android 8.0 (API 26), safely falls back to window flags.
 *
 * Linked With:
 * - Android Framework: [android.app.Activity], [android.view.WindowManager], [android.os.Build]
 */

package com.jmd.superapp.compat

import android.app.Activity
import android.os.Build
import android.view.WindowManager

/**
 * Platform compatibility helper for window flags and lockscreen presentation.
 */
object ActivityWindowCompat {

    /**
     * Safely sets whether the activity should be shown on top of the lock screen.
     */
    @JvmStatic
    fun setShowWhenLocked(activity: Activity, show: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            activity.setShowWhenLocked(show)
        } else {
            @Suppress("DEPRECATION")
            if (show) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            }
        }
    }

    /**
     * Safely sets whether the screen should be turned on when the activity enters the resumed state.
     */
    @JvmStatic
    fun setTurnScreenOn(activity: Activity, turnOn: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            activity.setTurnScreenOn(turnOn)
        } else {
            @Suppress("DEPRECATION")
            if (turnOn) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            }
        }
    }

    @JvmStatic
    fun setShowWhenLockedCompat(activity: Activity, show: Boolean) = setShowWhenLocked(activity, show)

    @JvmStatic
    fun setTurnScreenOnCompat(activity: Activity, turnOn: Boolean) = setTurnScreenOn(activity, turnOn)
}

/**
 * Extension helper for safe [Activity.setShowWhenLocked].
 */
fun Activity.setShowWhenLockedCompat(show: Boolean) {
    ActivityWindowCompat.setShowWhenLocked(this, show)
}

/**
 * Extension helper for safe [Activity.setTurnScreenOn].
 */
fun Activity.setTurnScreenOnCompat(turnOn: Boolean) {
    ActivityWindowCompat.setTurnScreenOn(this, turnOn)
}
