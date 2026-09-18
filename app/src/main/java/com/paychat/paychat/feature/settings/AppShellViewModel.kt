package com.paychat.paychat.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paychat.paychat.data.settings.AppLock
import com.paychat.paychat.data.settings.AppPreferences
import com.paychat.paychat.data.settings.ThemeChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The settings the whole app is drawn with, rather than any one screen.
 *
 * Held by the activity so that the colours are decided once, above the
 * navigation graph, and a change on the settings screen repaints everything
 * at once. The lock lives here for the same reason: it has to cover every
 * destination, so nothing below the graph can own it.
 */
@HiltViewModel
class AppShellViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val appLock: AppLock,
) : ViewModel() {

    val theme: StateFlow<ThemeChoice> = preferences.theme.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        // Following the system until the stored value is read avoids a flash
        // of the wrong colours on launch.
        initialValue = ThemeChoice.SYSTEM,
    )

    val hideBalances: StateFlow<Boolean> = preferences.hideBalances.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false,
    )

    val locked: StateFlow<Boolean> = appLock.locked

    fun toggleHideBalances() {
        viewModelScope.launch {
            preferences.setHideBalances(!hideBalances.value)
        }
    }

    init {
        // A launch is not a return from a detour: the phone may have been off
        // for a week, so a cold start locks straight away.
        viewModelScope.launch { appLock.onColdStart(preferences.appLockEnabledNow()) }

        viewModelScope.launch {
            preferences.appLockEnabled.collect { appLock.onSettingChanged(it) }
        }

        // Apply default privacy mode on cold start
        viewModelScope.launch {
            if (preferences.alwaysHideBalancesOnLaunchNow()) {
                preferences.setHideBalances(true)
            }
        }
    }

    fun onForeground() {
        viewModelScope.launch {
            val enabled = preferences.appLockEnabledNow()
            val timeout = preferences.lockTimeoutNow()
            appLock.onForeground(enabled = enabled, graceMs = timeout.millis)
        }
    }

    fun onBackground() = appLock.onBackground()

    fun unlocked() = appLock.unlocked()
}
