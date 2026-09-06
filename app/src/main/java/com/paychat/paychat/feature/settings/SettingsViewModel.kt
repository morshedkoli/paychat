package com.paychat.paychat.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paychat.paychat.data.export.StatementExporter
import com.paychat.paychat.data.profile.ProfileRepository
import com.paychat.paychat.data.settings.AppPreferences
import com.paychat.paychat.data.settings.ThemeChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val name: String = "",
    val phone: String = "",
    val photoUrl: String? = null,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val appLockEnabled: Boolean = false,
    /** True while a new picture is on its way to the server. */
    val uploadingPhoto: Boolean = false,
    val exporting: Boolean = false,
    val statement: Uri? = null,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profile: ProfileRepository,
    private val preferences: AppPreferences,
    private val exporter: StatementExporter,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            profile.observe().collect { user ->
                _state.update {
                    it.copy(
                        name = user?.name.orEmpty(),
                        phone = user?.phone.orEmpty(),
                        photoUrl = user?.photoUrl,
                    )
                }
            }
        }

        viewModelScope.launch {
            preferences.theme.collect { choice -> _state.update { it.copy(theme = choice) } }
        }

        viewModelScope.launch {
            preferences.appLockEnabled.collect { on ->
                _state.update { it.copy(appLockEnabled = on) }
            }
        }
    }

    fun setName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _state.update { it.copy(message = "A name cannot be empty.") }
            return
        }

        viewModelScope.launch {
            profile.setName(trimmed).onFailure { error ->
                _state.update { it.copy(message = error.message ?: "That name was not saved.") }
            }
        }
    }

    fun setPhoto(source: Uri) {
        _state.update { it.copy(uploadingPhoto = true) }
        viewModelScope.launch {
            profile.setPhoto(source)
                .onFailure { error ->
                    _state.update {
                        it.copy(message = error.message ?: "That picture was not uploaded.")
                    }
                }
            _state.update { it.copy(uploadingPhoto = false) }
        }
    }

    fun setTheme(choice: ThemeChoice) {
        viewModelScope.launch { preferences.setTheme(choice) }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setAppLockEnabled(enabled) }
    }

    fun exportEverything() {
        if (_state.value.exporting) return
        _state.update { it.copy(exporting = true) }

        viewModelScope.launch {
            exporter.exportAll()
                .onSuccess { uri -> _state.update { it.copy(exporting = false, statement = uri) } }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            exporting = false,
                            message = error.message ?: "Could not create that statement.",
                        )
                    }
                }
        }
    }

    fun statementShared() = _state.update { it.copy(statement = null) }

    fun messageShown() = _state.update { it.copy(message = null) }
}
