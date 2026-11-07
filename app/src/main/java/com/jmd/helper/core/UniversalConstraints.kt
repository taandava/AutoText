package com.jmd.helper.core

/**
 * Universal contracts and validation constraints for the entire Helper Automation platform.
 * Enforces standardized validation, result modeling, and operational boundaries across
 * all UI screens, background engines, repositories, and API endpoints.
 */
sealed interface OperationResult<out T> {
    data class Success<out T>(val data: T, val message: String = "") : OperationResult<T>
    data class Failure(val error: String, val cause: Throwable? = null) : OperationResult<Nothing>
    data object Loading : OperationResult<Nothing>

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = if (this is Success) data else null
}

object UniversalConstraintValidator {
    /**
     * Standard constraint check for recipient identification (phone numbers, group names, contact IDs).
     */
    fun validateRecipient(recipient: String): OperationResult<String> {
        val trimmed = recipient.trim()
        return when {
            trimmed.isBlank() -> OperationResult.Failure("Recipient cannot be blank.")
            trimmed.length < 2 -> OperationResult.Failure("Recipient identifier is too short.")
            trimmed.length > 256 -> OperationResult.Failure("Recipient identifier exceeds 256 characters.")
            else -> OperationResult.Success(trimmed)
        }
    }

    /**
     * Standard constraint check for message bodies.
     */
    fun validateMessageContent(message: String, allowEmpty: Boolean = false): OperationResult<String> {
        val trimmed = message.trim()
        return when {
            !allowEmpty && trimmed.isBlank() -> OperationResult.Failure("Message cannot be empty.")
            trimmed.length > 10_000 -> OperationResult.Failure("Message length exceeds maximum allowable 10,000 characters.")
            else -> OperationResult.Success(trimmed)
        }
    }

    /**
     * Standard constraint check for execution delay / duration intervals.
     */
    fun validateInterval(seconds: Int, minSec: Int = 0, maxSec: Int = 86_400): OperationResult<Int> {
        return if (seconds in minSec..maxSec) {
            OperationResult.Success(seconds)
        } else {
            OperationResult.Failure("Interval $seconds is outside allowable range [$minSec, $maxSec] seconds.")
        }
    }

    /**
     * Standard constraint check for SIM subscription IDs.
     */
    fun validateSimSlot(simSlot: Int): OperationResult<Int> {
        return if (simSlot in -1..4) {
            OperationResult.Success(simSlot)
        } else {
            OperationResult.Failure("Invalid SIM index: $simSlot")
        }
    }
}

