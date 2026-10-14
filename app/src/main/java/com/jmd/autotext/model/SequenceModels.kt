package com.jmd.autotext.model

data class ContactSequence(
    val id: Long = 0,
    val contactName: String = "",
    val contactPhone: String = "",
    val title: String = "",
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val steps: List<ContactSequenceStep> = emptyList()
)

data class ContactSequenceStep(
    val id: Long = 0,
    val sequenceId: Long = 0,
    val stepOrder: Int = 1,
    val channel: ChannelType = ChannelType.SMS,
    val messageText: String = "",
    val delayMinutesFromPrevious: Int = 0, // Minutes delay after previous step (Step 1 is initial delay from start)
    val specificTriggerTime: Long = 0, // Optional absolute epoch millis
    val simSlot: Int = 0
)
