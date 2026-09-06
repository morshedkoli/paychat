package com.paychat.paychat.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/**
 * What this device remembers about the signed in account between launches.
 *
 * [sessionId] is the device's claim on the account. PayChat allows one active
 * device, so signing in elsewhere replaces the value stored on the user
 * document and this device signs itself out when it notices the mismatch.
 */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val UID = stringPreferencesKey("uid")
        val PHONE = stringPreferencesKey("phone")
        val SESSION_ID = stringPreferencesKey("sessionId")
    }

    val uid: Flow<String?> = context.dataStore.data.map { it[Keys.UID] }
    val phone: Flow<String?> = context.dataStore.data.map { it[Keys.PHONE] }
    val sessionId: Flow<String?> = context.dataStore.data.map { it[Keys.SESSION_ID] }

    suspend fun currentSessionId(): String? = sessionId.first()

    suspend fun save(uid: String, phone: String, sessionId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.UID] = uid
            prefs[Keys.PHONE] = phone
            prefs[Keys.SESSION_ID] = sessionId
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
