package com.jmd.supersms.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.jmd.supersms.core.PhoneNumberNormalizer
import com.jmd.supersms.data.db.DatabaseHelper
import com.jmd.supersms.data.model.DynamicContactItem
import com.jmd.supersms.data.model.IngestionSummary
import com.jmd.supersms.data.repo.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactIngestionEngine(
    private val context: Context,
    private val repo: AppRepository,
) {

    suspend fun runFullIngestion(): IngestionSummary = withContext(Dispatchers.IO) {
        val candidates = mutableMapOf<String, DynamicContactItem>()
        var contactsScanned = 0
        var smsScanned = 0
        var callLogsScanned = 0

        // 1. Device ContactsContract (Address Book)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val resolver = context.contentResolver
                val cursor = resolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                    ),
                    null, null, null
                )
                cursor?.use { c ->
                    val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val photoIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                    while (c.moveToNext()) {
                        contactsScanned++
                        val name = if (nameIdx >= 0) c.getString(nameIdx).orEmpty().trim() else ""
                        val rawNumber = if (numIdx >= 0) c.getString(numIdx).orEmpty().trim() else ""
                        val photoUri = if (photoIdx >= 0) c.getString(photoIdx).orEmpty() else ""

                        if (rawNumber.isNotBlank() && PhoneNumberNormalizer.isMobileNumber(rawNumber)) {
                            val normalized = PhoneNumberNormalizer.normalize(rawNumber)
                            val existing = candidates[normalized]
                            if (existing == null) {
                                candidates[normalized] = DynamicContactItem(
                                    name = name.ifBlank { rawNumber },
                                    phone = rawNumber,
                                    normalizedNumber = normalized,
                                    source = "CONTACTS",
                                    photoUri = photoUri,
                                )
                            } else if (existing.photoUri.isBlank() && photoUri.isNotBlank()) {
                                candidates[normalized] = existing.copy(photoUri = photoUri)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("ContactIngestion", "ContactsContract scan failed: ${e.message}")
            }
        }

        // 2. Call Logs
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            try {
                val resolver = context.contentResolver
                val cursor = resolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(
                        CallLog.Calls.CACHED_NAME,
                        CallLog.Calls.NUMBER,
                    ),
                    null, null,
                    "${CallLog.Calls.DATE} DESC LIMIT 500"
                )
                cursor?.use { c ->
                    val nameIdx = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
                    val numIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
                    while (c.moveToNext()) {
                        callLogsScanned++
                        val name = if (nameIdx >= 0) c.getString(nameIdx).orEmpty().trim() else ""
                        val rawNumber = if (numIdx >= 0) c.getString(numIdx).orEmpty().trim() else ""

                        if (rawNumber.isNotBlank() && PhoneNumberNormalizer.isMobileNumber(rawNumber)) {
                            val normalized = PhoneNumberNormalizer.normalize(rawNumber)
                            if (!candidates.containsKey(normalized)) {
                                candidates[normalized] = DynamicContactItem(
                                    name = name.ifBlank { rawNumber },
                                    phone = rawNumber,
                                    normalizedNumber = normalized,
                                    source = "CALL_LOG",
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("ContactIngestion", "Call log scan failed: ${e.message}")
            }
        }

        // 3. SMS Messages (Inbox & Sent)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val resolver = context.contentResolver
                val cursor = resolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(Telephony.Sms.ADDRESS),
                    null, null,
                    "${Telephony.Sms.DATE} DESC LIMIT 500"
                )
                cursor?.use { c ->
                    val addrIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                    while (c.moveToNext()) {
                        smsScanned++
                        val rawNumber = if (addrIdx >= 0) c.getString(addrIdx).orEmpty().trim() else ""
                        if (rawNumber.isNotBlank() && PhoneNumberNormalizer.isMobileNumber(rawNumber)) {
                            val normalized = PhoneNumberNormalizer.normalize(rawNumber)
                            if (!candidates.containsKey(normalized)) {
                                candidates[normalized] = DynamicContactItem(
                                    name = rawNumber,
                                    phone = rawNumber,
                                    normalizedNumber = normalized,
                                    source = "SMS",
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("ContactIngestion", "SMS scan failed: ${e.message}")
            }
        }

        val addedCount = repo.upsertDynamicPoolBatch(candidates.values.toList())
        val (totalCount, _, _) = repo.getDynamicPoolStats()

        IngestionSummary(
            contactsScanned = contactsScanned,
            smsScanned = smsScanned,
            callLogsScanned = callLogsScanned,
            waChatsScanned = 0,
            waBusinessScanned = 0,
            newAdded = addedCount,
            totalPoolCount = totalCount,
        )
    }
}
