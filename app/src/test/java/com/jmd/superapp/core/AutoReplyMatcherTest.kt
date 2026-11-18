package com.jmd.superapp.core

import com.jmd.superapp.data.model.AutoReplyRule
import com.jmd.superapp.data.model.ChannelType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoReplyMatcherTest {

    private fun buildRule(
        pattern: String = "*",
        exact: Boolean = false,
        startH: Int = 0,
        startM: Int = 0,
        endH: Int = 23,
        endM: Int = 59,
        enabled: Boolean = true
    ) = AutoReplyRule(
        name = "TestRule",
        channel = ChannelType.SMS,
        replyText = "Hello from Super",
        triggerPattern = pattern,
        isExactMatch = exact,
        startTimeHour = startH,
        startTimeMinute = startM,
        endTimeHour = endH,
        endTimeMinute = endM,
        isEnabled = enabled
    )

    @Test
    fun testWildcardAlwaysMatches() {
        val rule = buildRule(pattern = "*")
        assertTrue(AutoReplyMatcher.matches(rule, "Any message content", 600))
        assertTrue(AutoReplyMatcher.matches(rule, "", 600))
    }

    @Test
    fun testEmptyPatternTreatedAsWildcard() {
        val rule = buildRule(pattern = "")
        assertTrue(AutoReplyMatcher.matches(rule, "Hello", 600))
    }

    @Test
    fun testSubstringMatchCaseInsensitive() {
        val rule = buildRule(pattern = "price, quote", exact = false)
        assertTrue(AutoReplyMatcher.matches(rule, "What is the Price of this item?", 600))
        assertTrue(AutoReplyMatcher.matches(rule, "Please send a QUOTE", 600))
        assertFalse(AutoReplyMatcher.matches(rule, "Hello how are you?", 600))
    }

    @Test
    fun testExactMatchEnforced() {
        val rule = buildRule(pattern = "menu, help", exact = true)
        assertTrue(AutoReplyMatcher.matches(rule, "menu", 600))
        assertTrue(AutoReplyMatcher.matches(rule, "HELP", 600))
        assertFalse(AutoReplyMatcher.matches(rule, "show me the menu please", 600))
        assertFalse(AutoReplyMatcher.matches(rule, "help me", 600))
    }

    @Test
    fun testTimeWindowNormalDaytime() {
        // 09:00 (540 min) to 17:30 (1050 min)
        val rule = buildRule(pattern = "*", startH = 9, startM = 0, endH = 17, endM = 30)

        // Inside window: 12:00 (720 min)
        assertTrue(AutoReplyMatcher.matches(rule, "Hi", 720))
        // Boundary start: 09:00 (540 min)
        assertTrue(AutoReplyMatcher.matches(rule, "Hi", 540))
        // Boundary end: 17:30 (1050 min)
        assertTrue(AutoReplyMatcher.matches(rule, "Hi", 1050))

        // Before window: 08:59 (539 min)
        assertFalse(AutoReplyMatcher.matches(rule, "Hi", 539))
        // After window: 17:31 (1051 min)
        assertFalse(AutoReplyMatcher.matches(rule, "Hi", 1051))
    }

    @Test
    fun testTimeWindowOvernight() {
        // 22:00 (1320 min) to 06:00 (360 min)
        val rule = buildRule(pattern = "*", startH = 22, startM = 0, endH = 6, endM = 0)

        // Late evening inside: 23:30 (1410 min)
        assertTrue(AutoReplyMatcher.matches(rule, "Hi", 1410))
        // Early morning inside: 04:15 (255 min)
        assertTrue(AutoReplyMatcher.matches(rule, "Hi", 255))
        // Midnight inside: 00:00 (0 min)
        assertTrue(AutoReplyMatcher.matches(rule, "Hi", 0))

        // Daytime outside: 12:00 (720 min)
        assertFalse(AutoReplyMatcher.matches(rule, "Hi", 720))
        // Just before start: 21:59 (1319 min)
        assertFalse(AutoReplyMatcher.matches(rule, "Hi", 1319))
        // Just after end: 06:01 (361 min)
        assertFalse(AutoReplyMatcher.matches(rule, "Hi", 361))
    }

    @Test
    fun testCallEventBypassesPattern() {
        val rule = buildRule(pattern = "specific_keyword")
        assertTrue(AutoReplyMatcher.matches(rule, "Ignored Text", 600, isCallEvent = true))
    }

    @Test
    fun testDisabledRuleReturnsFalse() {
        val rule = buildRule(pattern = "*", enabled = false)
        assertFalse(AutoReplyMatcher.matches(rule, "Hello", 600))
    }
}
