package com.ridervoice.cockpit.domain

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log

data class ContactMatch(
    val name: String,
    val phoneNumber: String
)

class ContactsRepository(
    private val context: Context
) {

    companion object {
        private const val TAG = "ContactsRepository"
    }

    fun searchContactByName(queryName: String): ContactMatch? {
        if (queryName.isBlank()) return null
        val cleanQuery = queryName.trim()

        try {
            val contentResolver = context.contentResolver
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            var cursor: Cursor? = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$cleanQuery%"),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            if (cursor == null || !cursor.moveToFirst()) {
                val firstWord = cleanQuery.split(" ").firstOrNull() ?: cleanQuery
                cursor?.close()
                cursor = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                    arrayOf("%$firstWord%"),
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
                )
            }

            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    if (nameIdx >= 0 && numberIdx >= 0) {
                        val name = it.getString(nameIdx)
                        val rawNumber = it.getString(numberIdx)
                        val cleanNumber = rawNumber.replace(Regex("[^0-9+]"), "")
                        Log.d(TAG, "Found contact match: $name -> $cleanNumber")
                        return ContactMatch(name = name, phoneNumber = cleanNumber)
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "READ_CONTACTS permission not granted", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts", e)
        }

        // Fallback for demo contacts if device permission is not yet granted
        if (cleanQuery.contains("ujjawal", ignoreCase = true) || cleanQuery.contains("ranjan", ignoreCase = true)) {
            return ContactMatch(name = "Ujjawal Ranjan", phoneNumber = "+919876543210")
        }
        if (cleanQuery.contains("rohan", ignoreCase = true)) {
            return ContactMatch(name = "Rohan [Sweep]", phoneNumber = "+919876543211")
        }
        if (cleanQuery.contains("aarav", ignoreCase = true) || cleanQuery.contains("captain", ignoreCase = true)) {
            return ContactMatch(name = "Aarav [Road Captain]", phoneNumber = "+919812345678")
        }

        return null
    }

    fun getContactNameByPhoneNumber(phoneNumber: String): String {
        if (phoneNumber.isBlank()) return "Unknown Caller"

        try {
            val contentResolver = context.contentResolver
            val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                .appendPath(phoneNumber)
                .build()

            val cursor: Cursor? = contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        return it.getString(nameIdx)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up phone number $phoneNumber", e)
        }

        return if (phoneNumber.contains("9876543210")) "Ujjawal Ranjan" else "Caller ($phoneNumber)"
    }
}
