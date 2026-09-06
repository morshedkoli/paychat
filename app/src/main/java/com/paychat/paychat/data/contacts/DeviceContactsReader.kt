package com.paychat.paychat.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads names and phone numbers from the device address book.
 *
 * Nothing here leaves the device by itself. The repository looks the numbers
 * up against `phoneIndex`; the address book is never uploaded.
 */
@Singleton
class DeviceContactsReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * @return pairs of (display name, phone number as stored), unnormalised
     */
    suspend fun read(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )

        val rows = mutableListOf<Pair<String, String>>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow(projection[0])
            val numberColumn = cursor.getColumnIndexOrThrow(projection[1])
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn).orEmpty()
                val number = cursor.getString(numberColumn) ?: continue
                rows += name to number
            }
        }
        rows
    }
}
