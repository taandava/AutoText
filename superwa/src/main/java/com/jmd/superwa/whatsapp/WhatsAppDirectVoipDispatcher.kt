/**
 * Package: com.jmd.superwa.whatsapp
 * Module: WhatsApp Automation Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [WhatsAppDirectVoipDispatcher].
 *
 * What it does:
 * Queries the device ContactsProvider for synced WhatsApp and WhatsApp Business VoIP contact data URIs (`vnd.android.cursor.item/vnd.com.whatsapp.voip.call` and `vnd.android.cursor.item/vnd.com.whatsapp.w4b.voip.call`) and initiates direct VoIP calls via native Android intents without requiring UI button scraping.
 *
 * Linked With:
 * - Framework & Contacts: [android.content.Context], [android.content.Intent], [android.net.Uri], [android.provider.ContactsContract]
 * - Core Security & Utilities: [android.util.Log], [com.jmd.superwa.core.AppFileLogger]
 */

package com.jmd.superwa.whatsapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import com.jmd.superwa.core.AppFileLogger

/**
 * What it is:
 * Direct native VoIP call launcher leveraging Android's [ContactsContract.Data] synchronization provider.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [WhatsAppDirectVoipDispatcher].
 *
 * Linked with:
 * - Architecture: [WhatsAppDirectVoipDispatcher], [WhatsAppDirectVoipDispatcher]
 */
object WhatsAppDirectVoipDispatcher {

    private const val TAG = "WADirectVoip"

    const val MIME_WHATSAPP_VOIP = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
    const val MIME_WHATSAPP_BUSINESS_VOIP = "vnd.android.cursor.item/vnd.com.whatsapp.w4b.voip.call"
    const val MIME_WHATSAPP_VIDEO = "vnd.android.cursor.item/vnd.com.whatsapp.video.call"
    const val MIME_WHATSAPP_BUSINESS_VIDEO = "vnd.android.cursor.item/vnd.com.whatsapp.w4b.video.call"

    /**
     * What it is:
     * Queries [ContactsContract.Data] to locate a synced WhatsApp or WhatsApp Business VoIP call record matching the specified phone number.
     *
     * What it does:
     * Executes [findContactVoipUri] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [WhatsAppDirectVoipDispatcher], [findContactVoipUri]
     * @param context Application or activity context with READ_CONTACTS permission.
     * @param rawNumber Recipient phone number in standard or international format.
     * @param isBusiness True to query WhatsApp Business VoIP mimetype; false for regular WhatsApp.
     * @return Content [Uri] pointing to the contact's data row (e.g., `content://com.android.contacts/data/12345`), or null if unsaved or not synced.
     */
    fun findContactVoipUri(context: Context, rawNumber: String, isBusiness: Boolean): Uri? {
        val targetMime = if (isBusiness) MIME_WHATSAPP_BUSINESS_VOIP else MIME_WHATSAPP_VOIP
        val digits = rawNumber.filter { it.isDigit() }
        if (digits.length < 7) {
            Log.d(TAG, "findContactVoipUri: Phone number '$rawNumber' has fewer than 7 digits, skipping contacts query")
            return null
        }

        val last10 = if (digits.length >= 10) digits.takeLast(10) else digits

        val projection = arrayOf(
            ContactsContract.Data._ID,
            ContactsContract.Data.DATA1
        )
        val selection = "${ContactsContract.Data.MIMETYPE} = ? AND (${ContactsContract.Data.DATA1} LIKE ? OR ${ContactsContract.Data.DATA1} LIKE ?)"
        val selectionArgs = arrayOf(targetMime, "%$last10%", "%$digits%")

        return try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIndex = cursor.getColumnIndex(ContactsContract.Data._ID)
                    if (idIndex != -1) {
                        val rowId = cursor.getLong(idIndex)
                        val uri = Uri.withAppendedPath(ContactsContract.Data.CONTENT_URI, rowId.toString())
                        Log.i(TAG, "findContactVoipUri: Found direct VoIP contact row ID #$rowId for $rawNumber (targetMime=$targetMime)")
                        uri
                    } else {
                        null
                    }
                } else {
                    Log.d(TAG, "findContactVoipUri: No direct VoIP contact entry for $rawNumber (targetMime=$targetMime)")
                    null
                }
            }
        } catch (ex: Exception) {
            Log.w(TAG, "findContactVoipUri failed for $rawNumber: ${ex.message}")
            null
        }
    }

    /**
     * What it is:
     * Fires an explicit native Intent to launch a direct WhatsApp or WhatsApp Business VoIP call via the contact's data URI.
     *
     * What it does:
     * Executes [launchDirectVoipCall] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [WhatsAppDirectVoipDispatcher], [launchDirectVoipCall]
     * @param context Application or activity context used to start the activity.
     * @param dataUri Direct contact data URI returned by [findContactVoipUri].
     * @param targetPkg Target package identifier (`com.whatsapp` or `com.whatsapp.w4b`).
     * @param isBusiness True if targeting WhatsApp Business; false for personal WhatsApp.
     * @return True if the direct VoIP call intent dispatched successfully; false otherwise.
     */
    fun launchDirectVoipCall(
        context: Context,
        dataUri: Uri,
        targetPkg: String,
        isBusiness: Boolean
    ): Boolean {
        val targetMime = if (isBusiness) MIME_WHATSAPP_BUSINESS_VOIP else MIME_WHATSAPP_VOIP
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(dataUri, targetMime)
                setPackage(targetPkg)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
            Log.i(TAG, "launchDirectVoipCall: Successfully dispatched native VoIP intent to $targetPkg for $dataUri")
            AppFileLogger.action(context.applicationContext, TAG, "Dispatched direct native VoIP call intent to $targetPkg via $dataUri")
            true
        } catch (ex: Exception) {
            Log.e(TAG, "launchDirectVoipCall failed for $dataUri: ${ex.message}", ex)
            false
        }
    }
}
