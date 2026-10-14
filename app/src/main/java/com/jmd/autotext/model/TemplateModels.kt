package com.jmd.autotext.model

data class MessageTemplate(
    val id: Long = 0,
    val title: String,
    val category: String = "General",
    val content: String
)
