/**
 * Package: com.jmd.superapp.engine
 * Module: Task Execution & Dispatch Engines
 *
 * What it is:
 * Architecture definition and implementation for [ContactIngestionEngine].
 *
 * What it does:
 * Coordinates contact and recipient ingestion across device ContactsContract, CallLog, cellular SMS inbox/outbox history, and WhatsApp chat history scans.
 *
 * Linked With:
 * - Repositories: [com.jmd.superapp.data.db.DatabaseHelper], [com.jmd.superapp.data.repo.AppRepository], [com.jmd.superapp.data.repo.WhatsAppRepository]
 * - Models & Entities: [com.jmd.superapp.data.model.DynamicContactItem], [com.jmd.superapp.data.model.IngestionSummary]
 * - Core Security & Utilities: [android.content.pm.PackageManager], [android.util.Log], [androidx.core.content.ContextCompat], [com.jmd.superapp.core.PhoneNumberNormalizer]
 * - Android Framework & Jetpack: [android.Manifest], [android.provider.CallLog], [android.provider.ContactsContract], [android.provider.Telephony]
 */

package com.jmd.superapp.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.jmd.superapp.core.PhoneNumberNormalizer
import com.jmd.superapp.data.db.DatabaseHelper
import com.jmd.superapp.data.model.DynamicContactItem
import com.jmd.superapp.data.model.IngestionSummary
import com.jmd.superapp.data.repo.AppRepository
import com.jmd.superapp.data.repo.WhatsAppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What it is:
 * Ingestion engine for extracting contacts from system providers and local chat caches.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [ContactIngestionEngine].
 *
 * Linked with:
 * - Architecture: [ContactIngestionEngine], [ContactIngestionEngine]
 * @param context Application context.
 * @param repo Application central repository.
 */
class ContactIngestionEngine(
    private val context: Context,
    private val repo: AppRepository,
) {

    /**
     * What it is:
     * Executes an end-to-end multi-provider scan to discover and ingest contacts from device address books, call logs, SMS message threads, and WhatsApp account links.
     *
     * What it does:
     * Ingested items are deduplicated by phone number, normalized into E.164 format via [PhoneNumberNormalizer], enriched with high-resolution photo URIs where available, and stored in the dynamic campaign recipient pool.
     *
     * Linked with:
     * - Architecture: [ContactIngestionEngine], [scanAndIngestAll]
     * @return [IngestionSummary] detailing the counts of records processed from each data source and newly added contacts.
     */
    suspend fun scanAndIngestAll(): IngestionSummary = withContext(Dispatchers.IO) {
        var contactsScanned = 0
        var smsScanned = 0
        var callLogsScanned = 0

        val candidates = LinkedHashMap<String, DynamicContactItem>()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val resolver = context.contentResolver
                // Streamlined projection avoids large CursorWindow allocation limits when reading thousands of contacts
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                )

                resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val contactIdIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)

                    while (cursor.moveToNext()) {
                        contactsScanned++
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else ""
                        val phone = if (numberIdx >= 0) cursor.getString(numberIdx).orEmpty().trim() else ""
                        if (phone.isBlank()) continue

                        val cid = if (contactIdIdx >= 0) cursor.getLong(contactIdIdx) else 0L
                        val photo = if (cid > 0) "content://com.android.contacts/contacts/$cid/photo" else ""

                        if (PhoneNumberNormalizer.isMobileNumber(phone)) {
                            val normalized = PhoneNumberNormalizer.normalize(phone)
                            candidates[normalized] = DynamicContactItem(
                                name = name.ifBlank { phone },
                                phone = phone,
                                normalizedNumber = normalized,
                                source = "CONTACT",
                                photoUri = photo,
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e("ContactIngestion", "Error during contacts ingestion", t)
            }
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            try {
                val resolver = context.contentResolver
                resolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(
                        CallLog.Calls.NUMBER,
                        CallLog.Calls.CACHED_NAME,
                    ),
                    null,
                    null,
                    "${CallLog.Calls.DATE} DESC",
                )?.use { cursor ->
                    val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                    val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                    while (cursor.moveToNext()) {
                        callLogsScanned++
                        val rawNumber = if (numberIdx >= 0) cursor.getString(numberIdx).orEmpty().trim() else ""
                        val cachedName = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else ""
                        if (rawNumber.isBlank()) continue

                        if (!PhoneNumberNormalizer.isMobileNumber(rawNumber)) continue

                        val normalized = PhoneNumberNormalizer.normalize(rawNumber)
                        val resolvedName = cachedName.ifBlank {
                            PhoneNumberNormalizer.getContactName(context, rawNumber).orEmpty()
                        }
                        val existing = candidates[normalized]
                        if (existing == null) {
                            candidates[normalized] = DynamicContactItem(
                                name = resolvedName.ifBlank { rawNumber },
                                phone = rawNumber,
                                normalizedNumber = normalized,
                                source = "CALLLOG",
                            )
                        } else if (existing.name.isBlank() || (existing.name == existing.phone)) {
                            if (resolvedName.isNotBlank()) {
                                candidates[normalized] = existing.copy(name = resolvedName)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("ContactIngestion", "Call log ingestion query failed: ${e.message}")
            }
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val resolver = context.contentResolver
                resolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(Telephony.Sms.ADDRESS),
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC",
                )?.use { cursor ->
                    val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                    if (addressIdx >= 0) {
                        while (cursor.moveToNext()) {
                            smsScanned++
                            val rawAddress = cursor.getString(addressIdx)?.trim().orEmpty()
                            if (rawAddress.isBlank()) continue

                            if (!PhoneNumberNormalizer.isMobileNumber(rawAddress)) continue

                            val normalized = PhoneNumberNormalizer.normalize(rawAddress)
                            if (!candidates.containsKey(normalized)) {
                                val resolvedName = PhoneNumberNormalizer.getContactName(context, rawAddress).orEmpty()
                                candidates[normalized] = DynamicContactItem(
                                    name = resolvedName.ifBlank { rawAddress },
                                    phone = rawAddress,
                                    normalizedNumber = normalized,
                                    source = "SMS",
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("ContactIngestion", "SMS ingestion query failed: ${e.message}")
            }
        }

        // 4. Ingest from WhatsApp & WhatsApp Business Chats, Accounts & Groups
        var waChatsScanned = 0
        var waBusinessScanned = 0
        try {
            // Direct query of WhatsApp & WhatsApp Business sync accounts
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                val resolver = context.contentResolver
                val cursor = resolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(
                        ContactsContract.Data.DISPLAY_NAME,
                        ContactsContract.Data.DATA1,
                        ContactsContract.Data.DATA3,
                        ContactsContract.Data.CONTACT_ID,
                        ContactsContract.Data.MIMETYPE,
                    ),
                    "${ContactsContract.Data.MIMETYPE} LIKE '%whatsapp%' OR " +
                    "${ContactsContract.Data.MIMETYPE} LIKE '%w4b%' OR " +
                    "${ContactsContract.Data.DATA1} LIKE '%@s.whatsapp.net%' OR " +
                    "${ContactsContract.Data.DATA3} LIKE '%+%'",
                    null,
                    null,
                )
                cursor?.use { c ->
                    val nameIdx = c.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)
                    val d1Idx = c.getColumnIndex(ContactsContract.Data.DATA1)
                    val d3Idx = c.getColumnIndex(ContactsContract.Data.DATA3)
                    val cidIdx = c.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                    val mimeIdx = c.getColumnIndex(ContactsContract.Data.MIMETYPE)
                    val phoneRegex = Regex("""\+?[0-9]{7,15}""")

                    while (c.moveToNext()) {
                        val name = if (nameIdx >= 0) c.getString(nameIdx).orEmpty().trim() else ""
                        val d1 = if (d1Idx >= 0) c.getString(d1Idx).orEmpty().trim() else ""
                        val d3 = if (d3Idx >= 0) c.getString(d3Idx).orEmpty().trim() else ""
                        val mime = if (mimeIdx >= 0) c.getString(mimeIdx).orEmpty().trim() else ""

                        val isW4b = mime.contains("w4b", ignoreCase = true)
                        if (isW4b) waBusinessScanned++ else waChatsScanned++

                        val candidatePhone = when {
                            d3.isNotBlank() && (phoneRegex.find(d3) != null) -> phoneRegex.find(d3)?.value.orEmpty()
                            d1.contains("@") -> "+" + d1.substringBefore("@").filter { it.isDigit() }
                            else -> phoneRegex.find(d1)?.value.orEmpty()
                        }

                        if (candidatePhone.isNotBlank() && PhoneNumberNormalizer.isMobileNumber(candidatePhone)) {
                            val normalized = PhoneNumberNormalizer.normalize(candidatePhone)
                            val contactId = if (cidIdx >= 0) c.getLong(cidIdx) else 0L
                            val photo = if (contactId > 0) "content://com.android.contacts/contacts/$contactId/photo" else ""
                            val src = if (isW4b) "WA_BUSINESS" else "WA_CHAT"
                            val existing = candidates[normalized]
                            if (existing == null) {
                                candidates[normalized] = DynamicContactItem(
                                    name = name.ifBlank { candidatePhone },
                                    phone = candidatePhone,
                                    normalizedNumber = normalized,
                                    source = src,
                                    photoUri = photo,
                                )
                            } else {
                                val updatedName = if ((existing.name == existing.phone) && name.isNotBlank() && (name != candidatePhone)) name else existing.name
                                val updatedPhoto = if (existing.photoUri.isBlank() && photo.isNotBlank()) photo else existing.photoUri
                                val mergedSource = when {
                                    existing.source == "WA_BOTH" -> "WA_BOTH"
                                    (existing.source == "WA_CHAT" && src == "WA_BUSINESS") || (existing.source == "WA_BUSINESS" && src == "WA_CHAT") -> "WA_BOTH"
                                    else -> src
                                }
                                candidates[normalized] = existing.copy(name = updatedName, photoUri = updatedPhoto, source = mergedSource)
                            }
                        }
                    }
                }
            }

            // Also ingest any WhatsApp/WhatsApp Business sender numbers from notification auto-reply history
            val db = DatabaseHelper.getInstance(context).readableDatabase
            db.query(
                DatabaseHelper.TABLE_AUTO_REPLY_HISTORY,
                arrayOf("phone_number"),
                null, null, null, null,
                "last_replied_at DESC LIMIT 500",
            ).use { c ->
                val phoneIdx = c.getColumnIndex("phone_number")
                if (phoneIdx >= 0) {
                    while (c.moveToNext()) {
                        waChatsScanned++
                        val phone = c.getString(phoneIdx).orEmpty().trim()
                        if (phone.isNotBlank() && PhoneNumberNormalizer.isMobileNumber(phone)) {
                            val normalized = PhoneNumberNormalizer.normalize(phone)
                            if (!candidates.containsKey(normalized)) {
                                candidates[normalized] = DynamicContactItem(
                                    name = phone,
                                    phone = phone,
                                    normalizedNumber = normalized,
                                    source = "WA_CHAT",
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("ContactIngestion", "WhatsApp chat/group ingestion scan failed: ${e.message}")
        }

        repo.purgeCorruptedGroupsFromPool()
        val addedCount = repo.upsertDynamicPoolBatch(candidates.values.toList())
        val (totalCount, _, _) = repo.getDynamicPoolStats()
        com.jmd.superapp.service.TaskAccessibilityService.notifyScanCompleted()

        IngestionSummary(
            contactsScanned = contactsScanned,
            smsScanned = smsScanned,
            callLogsScanned = callLogsScanned,
            waChatsScanned = waChatsScanned,
            waBusinessScanned = waBusinessScanned,
            newAdded = addedCount,
            totalPoolCount = totalCount,
        )
    }
}
