

/**
 * Package: com.jmd.autotext.data.repo
 * Module: Data Repositories Layer
 *
 * Persists multi-step scheduled sequences of automated messages per contact across channels (SMS, WhatsApp, WhatsApp Business).
 *
 * Linked With:
 * - Database: [com.jmd.autotext.data.db.DatabaseHelper]
 * - Models: [com.jmd.autotext.data.model.ContactSequence], [com.jmd.autotext.data.model.ContactSequenceStep]
 * - UI Screens: `ContactsScreen`
 */
package com.jmd.autotext.data.repo

import android.content.*
import androidx.core.database.sqlite.transaction
import com.jmd.autotext.data.db.DatabaseHelper
import com.jmd.autotext.data.model.ChannelType
import com.jmd.autotext.data.model.ContactSequence
import com.jmd.autotext.data.model.ContactSequenceStep

/**
 * Repository for managing contact outreach sequences and their ordered progression steps.
 *
 * @param context Application context.
 */
class SequenceRepository(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    /**
     * Inserts or replaces a contact sequence along with its constituent sequence steps in an atomic transaction.
     *
     * @param seq The [ContactSequence] to save.
     * @return Effective sequence row ID.
     */
    fun insertSequence(seq: ContactSequence): Long {
        val db = dbHelper.writableDatabase
        return db.transaction {
            val cv = ContentValues().apply {
                if (seq.id > 0) put("id", seq.id)
                put("contact_name", seq.contactName)
                put("contact_phone", seq.contactPhone)
                put("title", seq.title)
                put("is_enabled", if (seq.isEnabled) 1 else 0)
                put("created_at", seq.createdAt)
            }
            val seqId = db.insertWithOnConflict(DatabaseHelper.TABLE_CONTACT_SEQUENCES, null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            val effectiveId = if (seq.id > 0) seq.id else seqId

            db.delete(DatabaseHelper.TABLE_SEQUENCE_STEPS, "sequence_id = ?", arrayOf(effectiveId.toString()))

            seq.steps.forEachIndexed { index, step ->
                val stepCv = ContentValues().apply {
                    put("sequence_id", effectiveId)
                    put("step_order", index + 1)
                    put("delay_minutes", step.delayMinutesFromPrevious)
                    put("trigger_time", step.specificTriggerTime)
                    put("channel", step.channel.name)
                    put("message_text", step.messageText)
                    put("sim_slot", step.simSlot)
                }
                db.insert(DatabaseHelper.TABLE_SEQUENCE_STEPS, null, stepCv)
            }
            effectiveId
        }
    }

    /**
     * Retrieves all saved contact sequences with their respective steps ordered by ID descending.
     *
     * @return List of [ContactSequence] items.
     */
    fun getAllSequences(): List<ContactSequence> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<ContactSequence>()
        db.query(DatabaseHelper.TABLE_CONTACT_SEQUENCES, null, null, null, null, null, "id DESC").use { c ->
            while (c.moveToNext()) {
                val seqId = c.getLong(c.getColumnIndexOrThrow("id"))
                val steps = getStepsForSequence(seqId)
                list.add(
                    ContactSequence(
                        id = seqId,
                        contactName = c.getString(c.getColumnIndexOrThrow("contact_name")),
                        contactPhone = c.getString(c.getColumnIndexOrThrow("contact_phone")),
                        title = c.getString(c.getColumnIndexOrThrow("title")),
                        isEnabled = c.getInt(c.getColumnIndexOrThrow("is_enabled")) == 1,
                        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
                        steps = steps
                    )
                )
            }
        }
        return list
    }

    /**
     * Fetches ordered progression steps for a sequence by parent ID.
     *
     * @param seqId The sequence identifier.
     * @return List of [ContactSequenceStep] instances sorted by step_order ascending.
     */
    fun getStepsForSequence(seqId: Long): List<ContactSequenceStep> {
        val db = dbHelper.readableDatabase
        val steps = mutableListOf<ContactSequenceStep>()
        db.query(
            DatabaseHelper.TABLE_SEQUENCE_STEPS,
            null,
            "sequence_id = ?",
            arrayOf(seqId.toString()),
            null, null,
            "step_order ASC"
        ).use { c ->
            val triggerTimeIdx = c.getColumnIndex("trigger_time")
            while (c.moveToNext()) {
                val chanStr = c.getString(c.getColumnIndexOrThrow("channel"))
                val channelEnum = runCatching { ChannelType.valueOf(chanStr) }.getOrDefault(ChannelType.WHATSAPP)
                val triggerTs = if (triggerTimeIdx >= 0) c.getLong(triggerTimeIdx) else 0L
                steps.add(
                    ContactSequenceStep(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        sequenceId = seqId,
                        stepOrder = c.getInt(c.getColumnIndexOrThrow("step_order")),
                        channel = channelEnum,
                        messageText = c.getString(c.getColumnIndexOrThrow("message_text")),
                        delayMinutesFromPrevious = c.getInt(c.getColumnIndexOrThrow("delay_minutes")),
                        specificTriggerTime = triggerTs,
                        simSlot = c.getInt(c.getColumnIndexOrThrow("sim_slot"))
                    )
                )
            }
        }
        return steps
    }

    /**
     * Deletes a sequence and all associated progression steps.
     *
     * @param id The sequence ID.
     * @return `true` if deletion succeeded.
     */
    fun deleteSequence(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        db.delete(DatabaseHelper.TABLE_SEQUENCE_STEPS, "sequence_id = ?", arrayOf(id.toString()))
        return db.delete(DatabaseHelper.TABLE_CONTACT_SEQUENCES, "id = ?", arrayOf(id.toString())) > 0
    }
}
