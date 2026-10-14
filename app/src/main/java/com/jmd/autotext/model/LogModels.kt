package com.jmd.autotext.model

data class ExecutionLog(
    val id: Long = 0,
    val taskId: Long = 0,
    val channel: String,
    val recipient: String,
    val messageSnippet: String,
    val status: String, // SUCCESS, FAILED, SKIPPED
    val details: String = "",
    val timestampMillis: Long = System.currentTimeMillis()
)
