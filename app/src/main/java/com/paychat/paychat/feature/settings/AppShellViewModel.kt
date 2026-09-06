package com.paychat.paychat.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paychat.paychat.data.settings.AppPreferences
import com.paychat.paychat.data.settings.ThemeChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The settings the whole app is drawn with, rather than any one screen.
 *
 * Held by the activity so that the colours are decided once, above the
 * navigation graph, and a change on the settings screen repaints everything
 * at once.
 */
@HiltViewModel
class AppShellViewModel @Inject constructor(
    preferences: AppPreferences,
) : ViewModel() {

    val theme: StateFlow<ThemeChoice> = preferences.theme.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        // Following the system until the stored value is read avoids a flash
        // of the wrong colours on launch.
        initialValue = ThemeChoice.SYSTEM,
    )
}
