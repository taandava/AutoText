package com.jmd.helper.core

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Modern, timezone-safe date and time utilities using Kotlinx DateTime.
 * Prevents java.util.Calendar race conditions and SimpleDateFormat concurrency bugs.
 */
object AppDateTimeUtils {

    fun nowInstant(): Instant = Clock.System.now()

    fun nowEpochMillis(): Long = nowInstant().toEpochMilliseconds()

    fun nowLocalDateTime(timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime {
        return nowInstant().toLocalDateTime(timeZone)
    }

    fun epochMillisToLocalDateTime(epochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime {
        return Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone)
    }

    fun formatIsoDateTime(epochMillis: Long): String {
        val dt = epochMillisToLocalDateTime(epochMillis)
        return "${dt.year}-${dt.monthNumber.toString().padStart(2, '0')}-${dt.dayOfMonth.toString().padStart(2, '0')} " +
                "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}:${dt.second.toString().padStart(2, '0')}"
    }

    fun formatDisplayTime(epochMillis: Long): String {
        val dt = epochMillisToLocalDateTime(epochMillis)
        val hour12 = if (dt.hour % 12 == 0) 12 else dt.hour % 12
        val amPm = if (dt.hour >= 12) "PM" else "AM"
        return "${hour12.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')} $amPm"
    }
}

