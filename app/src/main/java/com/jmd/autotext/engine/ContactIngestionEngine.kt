

/**
 * Package: com.jmd.autotext.engine
 * Module: Automation Engine Layer
 *
 * Coordinates contact and recipient ingestion across device ContactsContract, CallLog,
 * cellular SMS inbox/outbox history, and WhatsApp chat history scans.
 *
 * Linked With:
 * - Normalizer: [com.jmd.autotext.core.PhoneNumberNormalizer]
 * - Database: [com.jmd.autotext.data.db.DatabaseHelper]
 * - Repositories: [com.jmd.autotext.data.repo.AppRepository], [com.jmd.autotext.data.repo.WhatsAppRepository]
 * - Schedulers: [com.jmd.autotext.scheduler.DailyCampaignWorker]
 */
package com.jmd.autotext.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.jmd.autotext.core.PhoneNumberNormalizer
import com.jmd.autotext.data.db.DatabaseHelper
import com.jmd.autotext.data.model.DynamicContactItem
import com.jmd.autotext.data.model.IngestionSummary
import com.jmd.autotext.data.repo.AppRepository
import com.jmd.autotext.data.repo.WhatsAppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ingestion engine for extracting contacts from system providers and local chat caches.
 *
 * @param context Application context.
 * @param repo Application central repository.
 */
class ContactIngestionEngine(
    private val context: Context,
    private val repo: AppRepository,
) {

    /**
     * Executes an end-to-end multi-provider scan to discover and ingest contacts from
     * device address books, call logs, SMS message threads, and WhatsApp account links.
     *
     * Ingested items are deduplicated by phone number, normalized into E.164 format via
     * [PhoneNumberNormalizer], enriched with high-resolution photo URIs where available,
     * and stored in the dynamic campaign recipient pool.
     *
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
                val fullProjection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                )
                val basicProjection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                )

                val phoneCursor = try {
                    resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, fullProjection, null, null, null)
                } catch (_: Exception) {
                    try {
                        resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, basicProjection, null, null, null)
                    } catch (_: Exception) { null }
                }

                phoneCursor?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val photoThumbIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
                    val photoUriIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                    val contactIdIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)

                    while (cursor.moveToNext()) {
                        contactsScanned++
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else ""
                        val phone = if (numberIdx >= 0) cursor.getString(numberIdx).orEmpty().trim() else ""
                        if (phone.isBlank()) continue

                        var photo = if (photoThumbIdx >= 0) cursor.getString(photoThumbIdx).orEmpty().trim() else ""
                        if (photo.isBlank() && (photoUriIdx >= 0)) {
                            photo = cursor.getString(photoUriIdx).orEmpty().trim()
                        }
                        if (photo.isBlank() && (contactIdIdx >= 0)) {
                            val cid = cursor.getLong(contactIdIdx)
                            if (cid > 0) photo = "content://com.android.contacts/contacts/$cid/photo"
                        }

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

                val phoneRegex = Regex("""\+?[0-9]{10,13}""")
                resolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(
                        ContactsContract.Data.DISPLAY_NAME,
                        ContactsContract.Data.DATA1,
                        ContactsContract.Data.DATA3,
                        ContactsContract.Data.CONTACT_ID,
                        ContactsContract.Data.MIMETYPE,
                    ),
                    "${ContactsContract.Data.MIMETYPE} = 'vnd.android.cursor.item/vnd.com.whatsapp.profile' OR " +
                    "${ContactsContract.Data.MIMETYPE} = 'vnd.android.cursor.item/vnd.com.whatsapp.w4b.profile' OR " +
                    "${ContactsContract.Data.DATA3} LIKE 'Message +%'",
                    null,
                    null,
                )?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)
                    val data3Idx = cursor.getColumnIndex(ContactsContract.Data.DATA3)
                    val data1Idx = cursor.getColumnIndex(ContactsContract.Data.DATA1)
                    val cidIdx = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                    val mimeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)

                    while (cursor.moveToNext()) {
                        contactsScanned++
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else ""
                        val data3 = if (data3Idx >= 0) cursor.getString(data3Idx).orEmpty().trim() else ""
                        val data1 = if (data1Idx >= 0) cursor.getString(data1Idx).orEmpty().trim() else ""
                        val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx).orEmpty().trim() else ""
                        val phoneMatch = phoneRegex.find(data3)?.value
                        val candidatePhone = when {
                            phoneMatch != null -> phoneMatch
                            data1.contains("@") -> "+" + data1.substringBefore("@").filter { it.isDigit() }
                            else -> phoneRegex.find(data1)?.value.orEmpty()
                        }

                        if (candidatePhone.isNotBlank() && PhoneNumberNormalizer.isMobileNumber(candidatePhone)) {
                            val normalized = PhoneNumberNormalizer.normalize(candidatePhone)
                            val contactId = if (cidIdx >= 0) cursor.getLong(cidIdx) else 0L
                            val photo = if (contactId > 0) "content://com.android.contacts/contacts/$contactId/photo" else ""
                            val isW4b = mime.contains("w4b", ignoreCase = true)
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
                                candidates[normalized] = existing.copy(name = updatedName, photoUri = updatedPhoto, source = src)
                            }
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
                        ContactsContract.Data.MIMETYPE,
                    ),
                    "${ContactsContract.Data.MIMETYPE} = 'vnd.android.cursor.item/vnd.com.whatsapp.profile' OR " +
                    "${ContactsContract.Data.MIMETYPE} = 'vnd.android.cursor.item/vnd.com.whatsapp.w4b.profile' OR " +
                    "${ContactsContract.Data.DATA3} LIKE 'Message +%'",
                    null,
                    null,
                )
                cursor?.use { c ->
                    val nameIdx = c.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)
                    val d1Idx = c.getColumnIndex(ContactsContract.Data.DATA1)
                    val d3Idx = c.getColumnIndex(ContactsContract.Data.DATA3)
                    val mimeIdx = c.getColumnIndex(ContactsContract.Data.MIMETYPE)
                    val phoneRegex = Regex("""\+?[0-9]{10,13}""")

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
                            val src = if (isW4b) "WA_BUSINESS" else "WA_CHAT"
                            val existing = candidates[normalized]
                            if (existing == null) {
                                candidates[normalized] = DynamicContactItem(
                                    name = name.ifBlank { candidatePhone },
                                    phone = candidatePhone,
                                    normalizedNumber = normalized,
                                    source = src,
                                )
                            } else {
                                val updatedName = if ((existing.name == existing.phone) && name.isNotBlank() && (name != candidatePhone)) name else existing.name
                                candidates[normalized] = existing.copy(name = updatedName, source = src)
                            }
                        }
                    }
                }
            }

            val waRepo = WhatsAppRepository(context)
            val groups = waRepo.getWhatsAppGroupItems()
            for ((_, groupName, _, memberCountStr) in groups) {
                waChatsScanned++
                val groupTitle = groupName.trim()
                if (groupTitle.isBlank()) continue
                val isBizGroup = memberCountStr.contains("Business", ignoreCase = true) || memberCountStr.contains("W4B", ignoreCase = true)
                if (isBizGroup) waBusinessScanned++

                val phoneMatch = Regex("""\+?[0-9]{10,13}""").find(groupTitle)?.value
                if ((phoneMatch != null) && PhoneNumberNormalizer.isMobileNumber(phoneMatch)) {
                    val normalized = PhoneNumberNormalizer.normalize(phoneMatch)
                    val src = if (isBizGroup) "WA_BUSINESS" else "WA_CHAT"
                    if (!candidates.containsKey(normalized)) {
                        candidates[normalized] = DynamicContactItem(
                            name = groupTitle,
                            phone = phoneMatch,
                            normalizedNumber = normalized,
                            source = src,
                        )
                    }
                } else if (waRepo.isValidGroupName(groupTitle)) {
                    val groupKey = "group:$groupTitle"
                    val groupSource = if (isBizGroup) "WA_BUSINESS" else "WA_GROUP"
                    if (!candidates.containsKey(groupKey)) {
                        candidates[groupKey] = DynamicContactItem(
                            name = groupTitle,
                            phone = groupKey,
                            normalizedNumber = groupKey,
                            source = groupSource,
                        )
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

        val addedCount = repo.upsertDynamicPoolBatch(candidates.values.toList())
        val (totalCount, _, _) = repo.getDynamicPoolStats()

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
