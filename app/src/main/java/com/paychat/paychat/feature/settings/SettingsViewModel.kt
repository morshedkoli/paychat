package com.paychat.paychat.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paychat.paychat.core.errors.userMessage
import com.paychat.paychat.data.account.AccountRepository
import com.paychat.paychat.data.export.DataExporter
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
    /** Set once the JSON copy of everything is written. */
    val dataFile: Uri? = null,
    val deleting: Boolean = false,
    /** Flipped once the account is gone, which sends the app back to sign in. */
    val deleted: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profile: ProfileRepository,
    private val preferences: AppPreferences,
    private val exporter: StatementExporter,
    private val dataExporter: DataExporter,
    private val account: AccountRepository,
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
                _state.update { it.copy(message = error.userMessage("That name was not saved.")) }
            }
        }
    }

    fun setPhoto(source: Uri) {
        _state.update { it.copy(uploadingPhoto = true) }
        viewModelScope.launch {
            profile.setPhoto(source)
                .onFailure { error ->
                    _state.update {
                        it.copy(message = error.userMessage("That picture was not uploaded."))
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
                            message = error.userMessage("Could not create that statement."),
                        )
                    }
                }
        }
    }

    /** The whole of it, as JSON, which is what a data export has to be. */
    fun exportData() {
        if (_state.value.exporting) return
        _state.update { it.copy(exporting = true) }

        viewModelScope.launch {
            dataExporter.export()
                .onSuccess { uri -> _state.update { it.copy(exporting = false, dataFile = uri) } }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            exporting = false,
                            message = error.userMessage("Could not export your data."),
                        )
                    }
                }
        }
    }

    fun deleteAccount() {
        if (_state.value.deleting) return
        _state.update { it.copy(deleting = true) }

        viewModelScope.launch {
            account.deleteAccount()
                .onSuccess { _state.update { it.copy(deleting = false, deleted = true) } }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            deleting = false,
                            message = error.userMessage("Your account was not deleted."),
                        )
                    }
                }
        }
    }

    fun statementShared() = _state.update { it.copy(statement = null) }

    fun dataFileShared() = _state.update { it.copy(dataFile = null) }

    fun messageShown() = _state.update { it.copy(message = null) }
}
