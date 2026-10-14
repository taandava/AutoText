package com.jmd.autotext.scanner

import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.model.DynamicContactItem
import com.jmd.autotext.model.IngestionSummary
import com.jmd.autotext.util.PhoneNumberNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactIngestionEngine(
    private val context: Context,
    private val repo: AppRepository
) {

    suspend fun scanAndIngestAll(): IngestionSummary = withContext(Dispatchers.IO) {
        var contactsScanned = 0
        var smsScanned = 0
        var callLogsScanned = 0

        // Map: normalizedNumber -> DynamicContactItem
        val candidates = LinkedHashMap<String, DynamicContactItem>()

        // 1. Scan Contacts Contract
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val resolver = context.contentResolver
                // 1a. Standard Phonebook entries with photo thumbnail resolution
                resolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                        ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
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
                        if (photo.isBlank() && photoUriIdx >= 0) {
                            photo = cursor.getString(photoUriIdx).orEmpty().trim()
                        }
                        if (photo.isBlank() && contactIdIdx >= 0) {
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
                                photoUri = photo
                            )
                        }
                    }
                }

                // 1b. Modern device messaging profiles (WhatsApp, WhatsApp Business, Telegram)
                val phoneRegex = Regex("""\+?[0-9]{10,13}""")
                resolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(
                        ContactsContract.Data.DISPLAY_NAME,
                        ContactsContract.Data.DATA1,
                        ContactsContract.Data.DATA3,
                        ContactsContract.Data.CONTACT_ID,
                        ContactsContract.Data.MIMETYPE
                    ),
                    "${ContactsContract.Data.MIMETYPE} = 'vnd.android.cursor.item/vnd.com.whatsapp.profile' OR " +
                    "${ContactsContract.Data.MIMETYPE} = 'vnd.android.cursor.item/vnd.com.whatsapp.w4b.profile' OR " +
                    "${ContactsContract.Data.DATA3} LIKE 'Message +%'",
                    null,
                    null
                )?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)
                    val data3Idx = cursor.getColumnIndex(ContactsContract.Data.DATA3)
                    val data1Idx = cursor.getColumnIndex(ContactsContract.Data.DATA1)
                    val cidIdx = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)

                    while (cursor.moveToNext()) {
                        contactsScanned++
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else ""
                        val data3 = if (data3Idx >= 0) cursor.getString(data3Idx).orEmpty().trim() else ""
                        val data1 = if (data1Idx >= 0) cursor.getString(data1Idx).orEmpty().trim() else ""
                        val phoneMatch = phoneRegex.find(data3)?.value
                        val candidatePhone = when {
                            phoneMatch != null -> phoneMatch
                            data1.contains("@") -> "+" + data1.substringBefore("@").filter { it.isDigit() }
                            phoneRegex.find(data1) != null -> phoneRegex.find(data1)!!.value
                            else -> ""
                        }

                        if (candidatePhone.isNotBlank() && PhoneNumberNormalizer.isMobileNumber(candidatePhone)) {
                            val normalized = PhoneNumberNormalizer.normalize(candidatePhone)
                            val contactId = if (cidIdx >= 0) cursor.getLong(cidIdx) else 0L
                            val photo = if (contactId > 0) "content://com.android.contacts/contacts/$contactId/photo" else ""

                            val existing = candidates[normalized]
                            if (existing == null) {
                                candidates[normalized] = DynamicContactItem(
                                    name = name.ifBlank { candidatePhone },
                                    phone = candidatePhone,
                                    normalizedNumber = normalized,
                                    source = "CONTACT",
                                    photoUri = photo
                                )
                            } else {
                                val updatedName = if (existing.name == existing.phone && name.isNotBlank() && name != candidatePhone) name else existing.name
                                val updatedPhoto = if (existing.photoUri.isBlank() && photo.isNotBlank()) photo else existing.photoUri
                                candidates[normalized] = existing.copy(name = updatedName, photoUri = updatedPhoto)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore or log permission / query exception
            }
        }

        // 2. Scan Call Log (Incoming, Outgoing, Missed)
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            try {
                val resolver = context.contentResolver
                resolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(
                        CallLog.Calls.NUMBER,
                        CallLog.Calls.CACHED_NAME
                    ),
                    null,
                    null,
                    "${CallLog.Calls.DATE} DESC"
                )?.use { cursor ->
                    val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                    val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                    while (cursor.moveToNext()) {
                        callLogsScanned++
                        val rawNumber = if (numberIdx >= 0) cursor.getString(numberIdx).orEmpty().trim() else ""
                        val cachedName = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else ""
                        if (rawNumber.isBlank()) continue

                        // Filter out non-mobile sender / call service names
                        if (!PhoneNumberNormalizer.isMobileNumber(rawNumber)) continue

                        val normalized = PhoneNumberNormalizer.normalize(rawNumber)
                        val existing = candidates[normalized]
                        if (existing == null) {
                            candidates[normalized] = DynamicContactItem(
                                name = cachedName.ifBlank { rawNumber },
                                phone = rawNumber,
                                normalizedNumber = normalized,
                                source = "CALLLOG"
                            )
                        } else if (existing.name.isBlank() && cachedName.isNotBlank()) {
                            candidates[normalized] = existing.copy(name = cachedName)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore or log
            }
        }

        // 3. Scan SMS Messages (Inbox & Sent)
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val resolver = context.contentResolver
                resolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(Telephony.Sms.ADDRESS),
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC"
                )?.use { cursor ->
                    val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                    if (addressIdx >= 0) {
                        while (cursor.moveToNext()) {
                            smsScanned++
                            val rawAddress = cursor.getString(addressIdx)?.trim().orEmpty()
                            if (rawAddress.isBlank()) continue

                            // Filter out non-mobile headers (e.g. AD-AIRTEL, HDFCBK, AX-AXISBK)
                            if (!PhoneNumberNormalizer.isMobileNumber(rawAddress)) continue

                            val normalized = PhoneNumberNormalizer.normalize(rawAddress)
                            if (!candidates.containsKey(normalized)) {
                                candidates[normalized] = DynamicContactItem(
                                    name = rawAddress,
                                    phone = rawAddress,
                                    normalizedNumber = normalized,
                                    source = "SMS"
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore or log
            }
        }

        // 4. Batch Upsert into Database
        val addedCount = repo.upsertDynamicPoolBatch(candidates.values.toList())
        val stats = repo.getDynamicPoolStats()

        IngestionSummary(
            contactsScanned = contactsScanned,
            smsScanned = smsScanned,
            callLogsScanned = callLogsScanned,
            newAdded = addedCount,
            totalPoolCount = stats.totalCount
        )
    }
}
