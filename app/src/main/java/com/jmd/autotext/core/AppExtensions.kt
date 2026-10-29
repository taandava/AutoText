/**
 * Package: com.jmd.autotext.core
 * Module: Core Automation Utilities
 *
 * Provides convenient context extensions for Toast messaging and native dialog interactions.
 */
package com.jmd.autotext.core

import android.app.TimePickerDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Displays a standard Toast notification with standard or long duration.
 * Thread-safe: can be called from any background or worker thread.
 *
 * @param message Text content to display.
 * @param isLong If true, uses [Toast.LENGTH_LONG]; otherwise [Toast.LENGTH_SHORT].
 */
fun Context.showAppToast(message: String, isLong: Boolean = false) {
    val duration = if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
    if (Looper.myLooper() == Looper.getMainLooper()) {
        try {
            Toast.makeText(this, message, duration).show()
        } catch (_: Throwable) {}
    } else {
        Handler(Looper.getMainLooper()).post {
            try {
                Toast.makeText(this, message, duration).show()
            } catch (_: Throwable) {}
        }
    }
}

/**
 * Launches the native system [TimePickerDialog].
 *
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
