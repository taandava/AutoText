package com.jmd.superapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Unit tests for dispatch journal occurrence key generation and invariants.
 */
class DispatchJournalTest {

    @Test
    fun `occurrence key combines task id and trigger time`() {
        val key = DispatchJournal.occurrenceKey(42L, 1700000000000L)
        assertEquals("task42_1700000000000", key)
    }

    @Test
    fun `different tasks produce distinct occurrence keys`() {
        val key1 = DispatchJournal.occurrenceKey(1L, 1000L)
        val key2 = DispatchJournal.occurrenceKey(2L, 1000L)
        assertNotEquals(key1, key2)
    }

    @Test
    fun `same task with different trigger time produces distinct keys`() {
        val key1 = DispatchJournal.occurrenceKey(1L, 1000L)
        val key2 = DispatchJournal.occurrenceKey(1L, 2000L)
        assertNotEquals(key1, key2)
    }
}
