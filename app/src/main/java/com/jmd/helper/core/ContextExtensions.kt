/**
 * Package: com.jmd.helper.core
 * Module: Core Security, Invariants & Privacy
 *
 * What it is:
 * Architecture definition and implementation for [ContextExtensions].
 *
 * What it does:
 * Provides convenient context extensions for Toast messaging and native dialog interactions.
 *
 * Linked With:
 * - Android Framework & Jetpack: [android.app.TimePickerDialog], [android.os.Handler], [android.os.Looper], [android.widget.Toast]
 */

package com.jmd.helper.core

import android.app.TimePickerDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * What it is:
 * Displays a standard Toast notification with standard or long duration.
 *
 * What it does:
 * Thread-safe: can be called from any background or worker thread.
 *
 * Linked with:
 * - Architecture: [ContextExtensions], [Context]
 * @param message Text content to display.
 * @param isLong If true, uses [Toast.LENGTH_LONG]; otherwise [Toast.LENGTH_SHORT].
 */
fun Context.showAppToast(message: String, isLong: Boolean = false) {
    val duration = if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
    if (Looper.myLooper() == Looper.getMainLooper()) {
        try {
            Toast.makeText(this, message, duration).show()
        } catch (t: Throwable) {
            android.util.Log.d("AppExtensions", "showAppToast suppressed: ${t.message}")
        }
    } else {
        Handler(Looper.getMainLooper()).post {
            try {
                Toast.makeText(this, message, duration).show()
            } catch (t: Throwable) {
                android.util.Log.d("AppExtensions", "showAppToast background suppressed: ${t.message}")
            }
        }
    }
}

/**
 * What it is:
 * Launches the native system [TimePickerDialog].
 *
 * What it does:
 * Executes [showAppTimePicker] logic in accordance with system and security invariants.
 *
 * Linked with:
 * - Architecture: [ContextExtensions], [showAppTimePicker]
 * @param context Active Android [Context].
 * @param initialHour Initial hour of day (0-23).
 * @param initialMinute Initial minute (0-59).
 * @param is24Hour True for 24-hour military clock display, false for AM/PM format.
 * @param onTimeSet Callback invoked with the selected (hour, minute).
 */
fun showAppTimePicker(
    context: Context,
    initialHour: Int,
    initialMinute: Int,
    is24Hour: Boolean = false,
    onTimeSet: (Int, Int) -> Unit,
) {
    TimePickerDialog(
        context,
        { _, h, m -> onTimeSet(h, m) },
        initialHour,
        initialMinute,
        is24Hour
    ).show()
}
