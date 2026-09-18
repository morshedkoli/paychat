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

/** Grace period after leaving the app before screen lock is required. */
enum class LockTimeout(val millis: Long, val label: String) {
    IMMEDIATELY(0L, "Immediately"),
    THIRTY_SECONDS(30_000L, "30s"),
    ONE_MINUTE(60_000L, "1 min"),
    FIVE_MINUTES(300_000L, "5 min");

    companion object {
        val DEFAULT = THIRTY_SECONDS
        fun fromName(name: String?): LockTimeout =
            entries.find { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}

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
        val LOCK_TIMEOUT = stringPreferencesKey("lockTimeout")
        val HIDE_BALANCES = booleanPreferencesKey("hideBalances")
        val ALWAYS_HIDE_BALANCES_ON_LAUNCH = booleanPreferencesKey("alwaysHideBalancesOnLaunch")
    }

    val theme: Flow<ThemeChoice> = context.dataStore.data.map { prefs ->
        // An unreadable value means a downgrade or a corrupt write; following
        // the system is the safe answer rather than a crash on launch.
        runCatching { ThemeChoice.valueOf(prefs[Keys.THEME] ?: "") }
            .getOrDefault(ThemeChoice.SYSTEM)
    }

    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.APP_LOCK] ?: false }

    val lockTimeout: Flow<LockTimeout> = context.dataStore.data.map { prefs ->
        LockTimeout.fromName(prefs[Keys.LOCK_TIMEOUT])
    }

    val hideBalances: Flow<Boolean> = context.dataStore.data.map { it[Keys.HIDE_BALANCES] ?: false }

    val alwaysHideBalancesOnLaunch: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.ALWAYS_HIDE_BALANCES_ON_LAUNCH] ?: false
    }

    suspend fun appLockEnabledNow(): Boolean = appLockEnabled.first()

    suspend fun lockTimeoutNow(): LockTimeout = lockTimeout.first()

    suspend fun alwaysHideBalancesOnLaunchNow(): Boolean = alwaysHideBalancesOnLaunch.first()

    suspend fun setTheme(choice: ThemeChoice) {
        context.dataStore.edit { it[Keys.THEME] = choice.name }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.APP_LOCK] = enabled }
    }

    suspend fun setLockTimeout(timeout: LockTimeout) {
        context.dataStore.edit { it[Keys.LOCK_TIMEOUT] = timeout.name }
    }

    suspend fun setHideBalances(hide: Boolean) {
        context.dataStore.edit { it[Keys.HIDE_BALANCES] = hide }
    }

    suspend fun setAlwaysHideBalancesOnLaunch(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ALWAYS_HIDE_BALANCES_ON_LAUNCH] = enabled }
    }
}

