/**
 * Package: com.jmd.helper.core
 * Module: Core Security, Invariants & Privacy
 *
 * What it is:
 * Architecture definition and implementation for [UniversalConstraints].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for UniversalConstraints.
 *
 * Linked With:
 * - Architecture: Standard application component linkages
 */

package com.jmd.helper.core

/**
 * What it is:
 * Universal contracts and validation constraints for the entire Helper Automation platform.
 *
 * What it does:
 * Enforces standardized validation, result modeling, and operational boundaries across all UI screens, background engines, repositories, and API endpoints.
 *
 * Linked with:
 * - Architecture: [UniversalConstraints], [OperationResult]
 */
sealed interface OperationResult<out T> {
    /**
     * What it is:
     * Component definition for [Success].
     *
     * What it does:
     * Provides domain logic and lifecycle management for Success.
     *
     * Linked with:
     * - Component: [UniversalConstraints]
     */
    data class Success<out T>(val data: T, val message: String = "") : OperationResult<T>
    /**
     * What it is:
     * Component definition for [Failure].
     *
     * What it does:
     * Provides domain logic and lifecycle management for Failure.
     *
     * Linked with:
     * - Component: [UniversalConstraints]
     */
    data class Failure(val error: String, val cause: Throwable? = null) : OperationResult<Nothing>
    /**
     * What it is:
     * Component definition for [Loading].
     *
     * What it does:
     * Provides domain logic and lifecycle management for Loading.
     *
     * Linked with:
     * - Component: [UniversalConstraints]
     */
    data object Loading : OperationResult<Nothing>

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure
    /**
     * What it is:
     * Execution routine for [getOrNull].
     *
     * What it does:
     * Coordinates getOrNull operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [UniversalConstraints]
     */

    fun getOrNull(): T? = if (this is Success) data else null
}
/**
 * What it is:
 * Component definition for [UniversalConstraintValidator].
 *
 * What it does:
 * Provides domain logic and lifecycle management for UniversalConstraintValidator.
 *
 * Linked with:
 * - Component: [UniversalConstraints]
 */

object UniversalConstraintValidator {
    /**
     * What it is:
     * Standard constraint check for recipient identification (phone numbers, group names, contact IDs).
     *
     * What it does:
     * Executes [validateRecipient] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [UniversalConstraints], [validateRecipient]
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
     * What it is:
     * Standard constraint check for message bodies.
     *
     * What it does:
     * Executes [validateMessageContent] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [UniversalConstraints], [validateMessageContent]
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
     * What it is:
     * Standard constraint check for execution delay / duration intervals.
     *
     * What it does:
     * Executes [validateInterval] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [UniversalConstraints], [validateInterval]
     */
    fun validateInterval(seconds: Int, minSec: Int = 0, maxSec: Int = 86_400): OperationResult<Int> {
        return if (seconds in minSec..maxSec) {
            OperationResult.Success(seconds)
        } else {
            OperationResult.Failure("Interval $seconds is outside allowable range [$minSec, $maxSec] seconds.")
        }
    }

    /**
     * What it is:
     * Standard constraint check for SIM subscription IDs.
     *
     * What it does:
     * Executes [validateSimSlot] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [UniversalConstraints], [validateSimSlot]
     */
    fun validateSimSlot(simSlot: Int): OperationResult<Int> {
        return if (simSlot in -1..4) {
            OperationResult.Success(simSlot)
        } else {
            OperationResult.Failure("Invalid SIM index: $simSlot")
        }
    }
}
