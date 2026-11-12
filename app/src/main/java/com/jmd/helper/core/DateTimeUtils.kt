/**
 * Package: com.jmd.helper.core
 * Module: Core Security, Invariants & Privacy
 *
 * What it is:
 * Architecture definition and implementation for [DateTimeUtils].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for DateTimeUtils.
 *
 * Linked With:
 * - Android Framework & Jetpack: [kotlinx.datetime.Clock], [kotlinx.datetime.Instant], [kotlinx.datetime.LocalDateTime], [kotlinx.datetime.TimeZone]
 */

package com.jmd.helper.core

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * What it is:
 * Modern, timezone-safe date and time utilities using Kotlinx DateTime.
 *
 * What it does:
 * Prevents java.util.Calendar race conditions and SimpleDateFormat concurrency bugs.
 *
 * Linked with:
 * - Architecture: [DateTimeUtils], [AppDateTimeUtils]
 */
object AppDateTimeUtils {
    /**
     * What it is:
     * Execution routine for [nowInstant].
     *
     * What it does:
     * Coordinates nowInstant operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [DateTimeUtils]
     */

    fun nowInstant(): Instant = Clock.System.now()
    /**
     * What it is:
     * Execution routine for [nowEpochMillis].
     *
     * What it does:
     * Coordinates nowEpochMillis operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [DateTimeUtils]
     */

    fun nowEpochMillis(): Long = nowInstant().toEpochMilliseconds()
    /**
     * What it is:
     * Execution routine for [nowLocalDateTime].
     *
     * What it does:
     * Coordinates nowLocalDateTime operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [DateTimeUtils]
     */

    fun nowLocalDateTime(timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime {
        return nowInstant().toLocalDateTime(timeZone)
    }
    /**
     * What it is:
     * Execution routine for [epochMillisToLocalDateTime].
     *
     * What it does:
     * Coordinates epochMillisToLocalDateTime operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [DateTimeUtils]
     */

    fun epochMillisToLocalDateTime(epochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime {
        return Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone)
    }
    /**
     * What it is:
     * Execution routine for [formatIsoDateTime].
     *
     * What it does:
     * Coordinates formatIsoDateTime operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [DateTimeUtils]
     */

    fun formatIsoDateTime(epochMillis: Long): String {
        val dt = epochMillisToLocalDateTime(epochMillis)
        return "${dt.year}-${dt.monthNumber.toString().padStart(2, '0')}-${dt.dayOfMonth.toString().padStart(2, '0')} " +
                "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}:${dt.second.toString().padStart(2, '0')}"
    }
    /**
     * What it is:
     * Execution routine for [formatDisplayTime].
     *
     * What it does:
     * Coordinates formatDisplayTime operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [DateTimeUtils]
     */

    fun formatDisplayTime(epochMillis: Long): String {
        val dt = epochMillisToLocalDateTime(epochMillis)
        val hour12 = if (dt.hour % 12 == 0) 12 else dt.hour % 12
        val amPm = if (dt.hour >= 12) "PM" else "AM"
        return "${hour12.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')} $amPm"
    }
}
