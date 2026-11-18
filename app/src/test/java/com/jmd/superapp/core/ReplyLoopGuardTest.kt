package com.jmd.superapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the reply-loop suppression rules that protect against bot-to-bot loops.
 */
class ReplyLoopGuardTest {

    private val cooldown = 120_000L

    @Test
    fun `fingerprint ignores case and extra whitespace`() {
        assertEquals("hello there", ReplyLoopGuard.fingerprint("  Hello   There  "))
    }

    @Test
    fun `no previous reply is never suppressed`() {
        assertFalse(ReplyLoopGuard.shouldSuppress(null, 1_000L, "Hi", cooldown))
    }

    @Test
    fun `identical reply inside cooldown is suppressed`() {
        val previous = ReplyLoopGuard.Entry(1_000L, ReplyLoopGuard.fingerprint("Away right now"))
        assertTrue(ReplyLoopGuard.shouldSuppress(previous, 60_000L, "away   right now", cooldown))
    }

    @Test
    fun `different reply inside cooldown is allowed`() {
        val previous = ReplyLoopGuard.Entry(1_000L, ReplyLoopGuard.fingerprint("Away right now"))
        assertFalse(ReplyLoopGuard.shouldSuppress(previous, 60_000L, "Back online", cooldown))
    }

    @Test
    fun `identical reply after cooldown is allowed`() {
        val previous = ReplyLoopGuard.Entry(1_000L, ReplyLoopGuard.fingerprint("Away right now"))
        assertFalse(ReplyLoopGuard.shouldSuppress(previous, 1_000L + cooldown + 1, "Away right now", cooldown))
    }

    @Test
    fun `zero cooldown disables suppression`() {
        val previous = ReplyLoopGuard.Entry(1_000L, ReplyLoopGuard.fingerprint("Hi"))
        assertFalse(ReplyLoopGuard.shouldSuppress(previous, 1_000L, "Hi", 0L))
    }

    @Test
    fun `storage key is stable for equivalent numbers`() {
        assertEquals(
            ReplyLoopGuard.storageKey("09876543210"),
            ReplyLoopGuard.storageKey("+919876543210")
        )
    }

    @Test
    fun `storage key falls back to label for non numeric recipients`() {
        assertEquals("reply_guard_alice", ReplyLoopGuard.storageKey("Alice"))
    }
}
