/**
 * Package: com.jmd.superapp.core
 * Module: Core Task Subsystem
 *
 * What it is:
 * Pure deterministic rule matching engine for auto-replies.
 *
 * What it does:
 * Evaluates whether an incoming message or call event qualifies for auto-reply based on
 * trigger keywords, exact match requirements, and active operating hours time-windows.
 *
 * Linked With:
 * - Models & Entities: [com.jmd.superapp.data.model.AutoReplyRule]
 */

package com.jmd.superapp.core

import com.jmd.superapp.data.model.AutoReplyRule

/**
 * Pure deterministic matcher for evaluating auto-reply eligibility.
 */
object AutoReplyMatcher {

    /**
     * Checks if [currentMinutes] (0..1439) falls within the active time window defined by start and end hours/minutes.
     * Handles overnight windows (e.g. 22:00 to 06:00) seamlessly.
     */
    fun isTimeInWindow(
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        currentMinutes: Int
    ): Boolean {
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute

        return when {
            startMinutes == endMinutes -> true
            startMinutes < endMinutes -> currentMinutes in startMinutes..endMinutes
            else -> currentMinutes in startMinutes..1439 || currentMinutes in 0..endMinutes
        }
    }

    /**
     * Evaluates whether [messageText] matches the configured [triggerPattern] under [isExactMatch].
     * Supports comma-separated keywords and wildcard '*' matching.
     */
    fun matchesPattern(
        triggerPattern: String,
        isExactMatch: Boolean,
        messageText: String
    ): Boolean {
        val pattern = triggerPattern.trim()
        if (pattern == "*" || pattern.isEmpty()) return true

        val keywords = pattern.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (keywords.isEmpty()) return true

        val msgLower = messageText.lowercase()

        return if (isExactMatch) {
            keywords.any { it == msgLower }
        } else {
            keywords.any { msgLower.contains(it) }
        }
    }

    /**
     * Comprehensive evaluation of an [AutoReplyRule] against incoming message parameters.
     */
    fun matches(
        rule: AutoReplyRule,
        messageText: String,
        currentMinutes: Int,
        isCallEvent: Boolean = false
    ): Boolean {
        if (!rule.isEnabled) return false

        val inWindow = isTimeInWindow(
            rule.startTimeHour,
            rule.startTimeMinute,
            rule.endTimeHour,
            rule.endTimeMinute,
            currentMinutes
        )
        if (!inWindow) return false

        if (isCallEvent) {
            return true
        }

        return matchesPattern(rule.triggerPattern, rule.isExactMatch, messageText)
    }
}
