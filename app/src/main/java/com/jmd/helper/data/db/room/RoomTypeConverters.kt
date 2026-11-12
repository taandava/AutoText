/**
 * Package: com.jmd.helper.data.db.room
 * Module: Persistence & Database Layer
 *
 * What it is:
 * Architecture definition and implementation for [RoomTypeConverters].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for RoomTypeConverters.
 *
 * Linked With:
 * - Models & Entities: [com.jmd.helper.data.model.ChannelType], [com.jmd.helper.data.model.RecurrenceType], [com.jmd.helper.data.model.TaskStatus]
 * - Android Framework & Jetpack: [androidx.room.TypeConverter]
 */

package com.jmd.helper.data.db.room

import androidx.room.TypeConverter
import com.jmd.helper.data.model.ChannelType
import com.jmd.helper.data.model.RecurrenceType
import com.jmd.helper.data.model.TaskStatus

/**
 * What it is:
 * Room TypeConverters for strongly typed domain enums.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [RoomTypeConverters].
 *
 * Linked with:
 * - Architecture: [RoomTypeConverters], [RoomTypeConverters]
 */
class RoomTypeConverters {

    /**
     * What it is:
     * Execution routine for [fromChannelType].
     *
     * What it does:
     * Coordinates fromChannelType operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [RoomTypeConverters]
     */
    @TypeConverter
    fun fromChannelType(channel: ChannelType): String = channel.name

    /**
     * What it is:
     * Execution routine for [toChannelType].
     *
     * What it does:
     * Coordinates toChannelType operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [RoomTypeConverters]
     */
    @TypeConverter
    fun toChannelType(value: String): ChannelType = try {
        ChannelType.valueOf(value)
    } catch (e: Exception) {
        ChannelType.SMS
    }

    /**
     * What it is:
     * Execution routine for [fromTaskStatus].
     *
     * What it does:
     * Coordinates fromTaskStatus operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [RoomTypeConverters]
     */
    @TypeConverter
    fun fromTaskStatus(status: TaskStatus): String = status.name

    /**
     * What it is:
     * Execution routine for [toTaskStatus].
     *
     * What it does:
     * Coordinates toTaskStatus operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [RoomTypeConverters]
     */
    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus = try {
        TaskStatus.valueOf(value)
    } catch (e: Exception) {
        TaskStatus.PENDING
    }

    /**
     * What it is:
     * Execution routine for [fromRecurrenceType].
     *
     * What it does:
     * Coordinates fromRecurrenceType operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [RoomTypeConverters]
     */
    @TypeConverter
    fun fromRecurrenceType(recurrence: RecurrenceType): String = recurrence.name

    /**
     * What it is:
     * Execution routine for [toRecurrenceType].
     *
     * What it does:
     * Coordinates toRecurrenceType operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [RoomTypeConverters]
     */
    @TypeConverter
    fun toRecurrenceType(value: String): RecurrenceType = try {
        RecurrenceType.valueOf(value)
    } catch (e: Exception) {
        RecurrenceType.ONCE
    }
}
