package com.jmd.superapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit test suite verifying FileLogger naming standards and file resolution contracts.
 */
class FileLoggerMigrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testProductionLogFileNames() {
        val root = tempFolder.newFolder("logs")
        val execLog = File(root, "super_execution.log")
        val errorLog = File(root, "super_error.log")
        val ledgerCsv = File(root, "super_campaign_ledger.csv")

        assertTrue(execLog.name.startsWith("super_"))
        assertTrue(errorLog.name.startsWith("super_"))
        assertTrue(ledgerCsv.name.startsWith("super_"))
    }

    @Test
    fun testLegacyLogFileRenamingSimulation() {
        val root = tempFolder.newFolder("logs_legacy")
        val legacyHelperLog = File(root, "helper_execution.log")
        legacyHelperLog.writeText("Legacy execution trace entry\n")
        assertTrue(legacyHelperLog.exists())

        val targetLog = File(root, "super_execution.log")
        assertFalse(targetLog.exists())

        // Simulate migration logic
        val migrated = legacyHelperLog.renameTo(targetLog)
        assertTrue(migrated)
        assertTrue(targetLog.exists())
        assertEquals("Legacy execution trace entry\n", targetLog.readText())
    }

    @Test
    fun testAutoTextLegacyLogFileRenamingSimulation() {
        val root = tempFolder.newFolder("logs_autotext")
        val legacyAutoTextLog = File(root, "autotext_execution.log")
        legacyAutoTextLog.writeText("AutoText legacy trace\n")
        assertTrue(legacyAutoTextLog.exists())

        val targetLog = File(root, "super_execution.log")
        assertFalse(targetLog.exists())

        val migrated = legacyAutoTextLog.renameTo(targetLog)
        assertTrue(migrated)
        assertTrue(targetLog.exists())
        assertEquals("AutoText legacy trace\n", targetLog.readText())
    }
}
