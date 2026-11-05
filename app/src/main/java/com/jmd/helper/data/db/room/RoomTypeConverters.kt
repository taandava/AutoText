package com.jmd.helper.data.db.room

import androidx.room.TypeConverter
import com.jmd.helper.data.model.ChannelType
import com.jmd.helper.data.model.RecurrenceType
import com.jmd.helper.data.model.TaskStatus

/**
 * Room TypeConverters for strongly typed domain enums.
 */
class RoomTypeConverters {

    @TypeConverter
    fun fromChannelType(channel: ChannelType): String = channel.name

    @TypeConverter
    fun toChannelType(value: String): ChannelType = try {
        ChannelType.valueOf(value)
    } catch (e: Exception) {
        ChannelType.SMS
    }

    @TypeConverter
    fun fromTaskStatus(status: TaskStatus): String = status.name

    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus = try {
        TaskStatus.valueOf(value)
    } catch (e: Exception) {
        TaskStatus.PENDING
    }

    @TypeConverter
    fun fromRecurrenceType(recurrence: RecurrenceType): String = recurrence.name

    @TypeConverter
    fun toRecurrenceType(value: String): RecurrenceType = try {
        RecurrenceType.valueOf(value)
    } catch (e: Exception) {
        RecurrenceType.ONCE
    }
}
