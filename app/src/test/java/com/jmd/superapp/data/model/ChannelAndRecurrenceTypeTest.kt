package com.jmd.superapp.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelAndRecurrenceTypeTest {

    @Test
    fun testChannelTypeFromString() {
        assertEquals(ChannelType.SMS, ChannelType.fromString("SMS"))
        assertEquals(ChannelType.SMS, ChannelType.fromString("sms"))
        assertEquals(ChannelType.SMS, ChannelType.fromString("  sms  "))
        assertEquals(ChannelType.SMS, ChannelType.fromString(null))
        assertEquals(ChannelType.SMS, ChannelType.fromString(""))
        assertEquals(ChannelType.SMS, ChannelType.fromString("UNKNOWN_CHANNEL"))

        assertEquals(ChannelType.WHATSAPP, ChannelType.fromString("WHATSAPP"))
        assertEquals(ChannelType.WHATSAPP_BUSINESS, ChannelType.fromString("WHATSAPP_BUSINESS"))

        // Legacy aliases
        assertEquals(ChannelType.CALL_SIMULATION, ChannelType.fromString("SIMULATION"))
        assertEquals(ChannelType.CALL_SIMULATION, ChannelType.fromString("SIMULATED_CALL"))
        assertEquals(ChannelType.CALL_SIMULATION, ChannelType.fromString("CALL_SIMULATION"))

        assertEquals(ChannelType.WHATSAPP_GROUP, ChannelType.fromString("GROUP"))
        assertEquals(ChannelType.WHATSAPP_GROUP, ChannelType.fromString("WA_GROUP"))
        assertEquals(ChannelType.WHATSAPP_BUSINESS_GROUP, ChannelType.fromString("W4B_GROUP"))
        assertEquals(ChannelType.WHATSAPP_BUSINESS_GROUP, ChannelType.fromString("WA_BUSINESS_GROUP"))

        assertEquals(ChannelType.REMINDER, ChannelType.fromString("REMINDER"))
        assertEquals(ChannelType.CALL, ChannelType.fromString("CALL"))
    }

    @Test
    fun testRecurrenceTypeFromString() {
        assertEquals(RecurrenceType.ONCE, RecurrenceType.fromString("ONCE"))
        assertEquals(RecurrenceType.ONCE, RecurrenceType.fromString("once"))
        assertEquals(RecurrenceType.ONCE, RecurrenceType.fromString(null))
        assertEquals(RecurrenceType.ONCE, RecurrenceType.fromString(""))
        assertEquals(RecurrenceType.ONCE, RecurrenceType.fromString("UNKNOWN_RECURRENCE"))

        assertEquals(RecurrenceType.DAILY, RecurrenceType.fromString("DAILY"))
        assertEquals(RecurrenceType.WEEKDAYS, RecurrenceType.fromString("WEEKDAYS"))
        assertEquals(RecurrenceType.WEEKENDS, RecurrenceType.fromString("WEEKENDS"))
        assertEquals(RecurrenceType.WEEKLY, RecurrenceType.fromString("WEEKLY"))
        assertEquals(RecurrenceType.MONTHLY, RecurrenceType.fromString("MONTHLY"))
        assertEquals(RecurrenceType.YEARLY, RecurrenceType.fromString("YEARLY"))

        // Repeat units
        assertEquals(RecurrenceType.REPEAT_SECONDS, RecurrenceType.fromString("REPEAT_SECONDS"))
        assertEquals(RecurrenceType.REPEAT_MINUTES, RecurrenceType.fromString("REPEAT_MINUTES"))
        assertEquals(RecurrenceType.REPEAT_HOURS, RecurrenceType.fromString("REPEAT_HOURS"))
        assertEquals(RecurrenceType.REPEAT_DAYS, RecurrenceType.fromString("REPEAT_DAYS"))
        assertEquals(RecurrenceType.REPEAT_WEEKS, RecurrenceType.fromString("REPEAT_WEEKS"))
        assertEquals(RecurrenceType.REPEAT_MONTHS, RecurrenceType.fromString("REPEAT_MONTHS"))

        // Display name matching
        assertEquals(RecurrenceType.ONCE, RecurrenceType.fromString("Once"))
        assertEquals(RecurrenceType.DAILY, RecurrenceType.fromString("Daily"))
        assertEquals(RecurrenceType.CUSTOM_INTERVAL, RecurrenceType.fromString("Custom Interval"))
    }

    @Test
    fun testRepeatUnitConversion() {
        assertEquals(RepeatUnit.SECONDS, RepeatUnit.fromString("SEC"))
        assertEquals(RepeatUnit.MINUTES, RepeatUnit.fromString("MIN"))
        assertEquals(RepeatUnit.HOURS, RepeatUnit.fromString("HOUR"))
        assertEquals(RepeatUnit.DAYS, RepeatUnit.fromString("DAY"))
        assertEquals(RepeatUnit.WEEKS, RepeatUnit.fromString("WEEK"))
        assertEquals(RepeatUnit.MONTHS, RepeatUnit.fromString("MONTH"))
        assertEquals(RepeatUnit.MINUTES, RepeatUnit.fromString(null))

        assertEquals(RecurrenceType.REPEAT_SECONDS, RepeatUnit.SECONDS.toRecurrenceType())
        assertEquals(RecurrenceType.REPEAT_MINUTES, RepeatUnit.MINUTES.toRecurrenceType())
        assertEquals(RecurrenceType.REPEAT_HOURS, RepeatUnit.HOURS.toRecurrenceType())
        assertEquals(RecurrenceType.REPEAT_DAYS, RepeatUnit.DAYS.toRecurrenceType())
        assertEquals(RecurrenceType.REPEAT_WEEKS, RepeatUnit.WEEKS.toRecurrenceType())
        assertEquals(RecurrenceType.REPEAT_MONTHS, RepeatUnit.MONTHS.toRecurrenceType())
    }
}
