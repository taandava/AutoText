package com.jmd.autotext.model

data class RecipientItem(
    val id: Long = 0,
    val name: String,
    val phoneOrEmail: String,
    val groupName: String = "General",
    val photoUri: String = ""
)

data class WhatsAppContactItem(
    val name: String,
    val phone: String,
    val normalizedPhone: String = "",
    val accountType: String = "com.whatsapp",
    val photoUri: String = ""
)

data class RecipientGroup(
    val id: Long = 0,
    val name: String,
    val memberCount: Int = 0
)

data class WhatsAppGroupItem(
    val id: Long = 0,
    val name: String,
    val groupJid: String = "",
    val memberCountStr: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
