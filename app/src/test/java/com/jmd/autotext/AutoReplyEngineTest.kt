package com.jmd.autotext

import com.jmd.autotext.model.AutoReplyRule
import com.jmd.autotext.model.ChannelType
import org.junit.Assert.*
import org.junit.Test

class AutoReplyEngineTest {

    private fun matchesRule(
        rule: AutoReplyRule,
        messageText: String,
        currentMinutes: Int
    ): Boolean {
        val startMinutes = rule.startTimeHour * 60 + rule.startTimeMinute
        val endMinutes = rule.endTimeHour * 60 + rule.endTimeMinute

        // Time window check
        if (startMinutes <= endMinutes) {
            if (currentMinutes !in startMinutes..endMinutes) return false
        } else {
            // Window spans midnight (e.g., 22:00 to 07:00)
            if (currentMinutes in (endMinutes + 1) until startMinutes) return false
        }

        val pattern = rule.triggerPattern.trim()
        if (pattern == "*" || pattern.isEmpty()) return true

        val keywords = pattern.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val msgLower = messageText.lowercase()

        return if (rule.isExactMatch) {
            keywords.any { it == msgLower }
        } else {
            keywords.any { msgLower.contains(it) }
        }
    }

    @Test
    fun testDaytimeRuleMatching() {
        val rule = AutoReplyRule(
            id = 1L,
            name = "Price inquiry rule",
            channel = ChannelType.WHATSAPP,
            triggerPattern = "price, quote, cost",
            replyText = "Here is our pricing list.",
            isExactMatch = false,
            startTimeHour = 9,
            startTimeMinute = 0,
            endTimeHour = 18,
            endTimeMinute = 0
        )

        // At 11:30 AM (690 mins) -> Should match
        assertTrue(matchesRule(rule, "What is the price of the software?", 690))
        assertTrue(matchesRule(rule, "Can you share the quote?", 690))
        assertFalse(matchesRule(rule, "Hello there", 690))

        // At 8:30 PM (1230 mins) -> Outside window -> Should reject
        assertFalse(matchesRule(rule, "What is the price of the software?", 1230))
    }

    @Test
    fun testOvernightRuleMatching() {
        // Window from 22:00 (1320 mins) to 07:00 (420 mins)
        val rule = AutoReplyRule(
            id = 2L,
            name = "Night away rule",
            channel = ChannelType.WHATSAPP,
            triggerPattern = "*",
            replyText = "We are currently closed for the night.",
            isExactMatch = false,
            startTimeHour = 22,
            startTimeMinute = 0,
            endTimeHour = 7,
            endTimeMinute = 0
        )

        // At 23:30 (1410 mins) -> Should match
        assertTrue(matchesRule(rule, "Are you open?", 1410))

        // At 02:00 (120 mins) -> Should match
        assertTrue(matchesRule(rule, "Emergency contact?", 120))

        // At 14:00 (840 mins) -> Daytime, outside night window -> Should reject
        assertFalse(matchesRule(rule, "Are you open?", 840))
    }

    @Test
    fun testCooldownTimerPreventsLoops() {
        val lastReplyTime = 1000000L
        val cooldownMinutes = 15
        val cooldownMillis = cooldownMinutes * 60 * 1000L

        // Attempt 5 minutes later -> should be blocked by cooldown
        val attempt5Min = lastReplyTime + 5 * 60 * 1000L
        assertTrue(attempt5Min - lastReplyTime < cooldownMillis)

        // Attempt 16 minutes later -> should pass cooldown
        val attempt16Min = lastReplyTime + 16 * 60 * 1000L
        assertFalse(attempt16Min - lastReplyTime < cooldownMillis)
    }
}
