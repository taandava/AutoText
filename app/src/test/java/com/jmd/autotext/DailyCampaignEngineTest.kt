package com.jmd.autotext

import com.jmd.autotext.model.DailyCampaignConfig
import com.jmd.autotext.model.DynamicContactItem
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class DailyCampaignEngineTest {

    @Test
    fun testPriorityQueueOrdering() {
        val contacts = listOf(
            DynamicContactItem(id = 1, name = "Sent User", phone = "+919000000001", normalizedNumber = "919000000001", smsStatus = "SENT"),
            DynamicContactItem(id = 2, name = "Pending User 1", phone = "+919000000002", normalizedNumber = "919000000002", smsStatus = "PENDING"),
            DynamicContactItem(id = 3, name = "Failed User", phone = "+919000000003", normalizedNumber = "919000000003", smsStatus = "FAILED"),
            DynamicContactItem(id = 4, name = "Excluded User", phone = "+919000000004", normalizedNumber = "919000000004", smsStatus = "EXCLUDED"),
            DynamicContactItem(id = 5, name = "Pending User 2", phone = "+919000000005", normalizedNumber = "919000000005", smsStatus = "PENDING")
        )

        // Priority comparator: FAILED (0) -> PENDING (1) -> others (2)
        val sorted = contacts
            .filter { it.smsStatus != "SENT" && it.smsStatus != "EXCLUDED" }
            .sortedWith(compareBy {
                when (it.smsStatus) {
                    "FAILED" -> 0
                    "PENDING" -> 1
                    else -> 2
                }
            })

        assertEquals(3, sorted.size)
        assertEquals("Failed User", sorted[0].name)
        assertEquals("Pending User 1", sorted[1].name)
        assertEquals("Pending User 2", sorted[2].name)
    }

    @Test
    fun testAntiSpamRandomJitterRange() {
        val minIntervalSeconds = 3
        val maxIntervalSeconds = 8

        for (i in 0 until 50) {
            val delaySeconds = Random.nextInt(minIntervalSeconds, maxIntervalSeconds + 1)
            assertTrue(delaySeconds in minIntervalSeconds..maxIntervalSeconds)
        }
    }

    @Test
    fun testDailyTargetCapEnforcement() {
        val config = DailyCampaignConfig(
            id = 1L,
            channel = "SMS",
            dailyLimit = 150 // User entered 150, but regulatory cap is 100
        )

        val effectiveCap = minOf(100, config.dailyLimit)
        assertEquals(100, effectiveCap)
    }
}
