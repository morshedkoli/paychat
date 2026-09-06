package com.paychat.paychat.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Which colours the app draws in. */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/**
 * Settings that belong to this device rather than to the account.
 *
 * They are kept apart from [com.paychat.paychat.data.session.SessionStore]
 * because signing out clears the session but must not change the theme or
 * forget that the user asked for a lock: both describe the phone, not the
 * account on it.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val APP_LOCK = booleanPreferencesKey("appLock")
    }

    val theme: Flow<ThemeChoice> = context.dataStore.data.map { prefs ->
        // An unreadable value means a downgrade or a corrupt write; following
        // the system is the safe answer rather than a crash on launch.
        runCatching { ThemeChoice.valueOf(prefs[Keys.THEME] ?: "") }
            .getOrDefault(ThemeChoice.SYSTEM)
    }

    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.APP_LOCK] ?: false }

    suspend fun appLockEnabledNow(): Boolean = appLockEnabled.first()

    suspend fun setTheme(choice: ThemeChoice) {
        context.dataStore.edit { it[Keys.THEME] = choice.name }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.APP_LOCK] = enabled }
    }
}
