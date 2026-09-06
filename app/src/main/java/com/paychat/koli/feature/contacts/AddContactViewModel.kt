package com.paychat.koli.feature.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paychat.koli.core.phone.PhoneNumbers
import com.paychat.koli.core.validation.Validators
import com.paychat.koli.data.contacts.ContactsRepository
import com.paychat.koli.feature.auth.message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddContactUiState(
    val name: String = "",
    val phone: String = "",
    val nameError: String? = null,
    val phoneError: String? = null,
    val submitting: Boolean = false,
    val error: String? = null,
    val openThreadId: String? = null,
    /** True once we know the typed number already has an account. */
    val peerIsRegistered: Boolean? = null,
)

@HiltViewModel
class AddContactViewModel @Inject constructor(
    private val contacts: ContactsRepository,
    private val phoneNumbers: PhoneNumbers,
) : ViewModel() {

    private val _state = MutableStateFlow(AddContactUiState())
    val state: StateFlow<AddContactUiState> = _state.asStateFlow()

    fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = null) }

    private var lookupJob: Job? = null

    fun onPhoneChange(value: String) {
        _state.update { it.copy(phone = value, phoneError = null, peerIsRegistered = null) }
        scheduleNumberCheck()
    }

    /**
     * Tells the user whether this number already has an account, so they know
     * before saving whether transactions will need the other side to accept.
     *
     * Debounced, because it is a network read and the field changes on every
     * keystroke.
     */
    private fun scheduleNumberCheck() {
        lookupJob?.cancel()
        val e164 = phoneNumbers.toE164(_state.value.phone) ?: return
        lookupJob = viewModelScope.launch {
            delay(LOOKUP_DEBOUNCE_MS)
            contacts.findAccountByPhone(e164).onSuccess { uid ->
                _state.update { it.copy(peerIsRegistered = uid != null) }
            }
        }
    }

    fun submit() {
        val current = _state.value
        if (current.submitting) return

        val nameError = Validators.validateName(current.name)?.message()
        val e164 = phoneNumbers.toE164(current.phone)
        val phoneError = if (e164 == null) "Enter a valid phone number." else null

        if (nameError != null || phoneError != null) {
            _state.update { it.copy(nameError = nameError, phoneError = phoneError) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            contacts.openOrCreateThread(rawPhone = e164!!, name = current.name.trim()).fold(
                onSuccess = { threadId ->
                    _state.update { it.copy(submitting = false, openThreadId = threadId) }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            submitting = false,
                            error = error.message ?: "Could not add that contact.",
                        )
                    }
                },
            )
        }
    }

    fun onThreadOpened() = _state.update { it.copy(openThreadId = null) }

    private companion object {
        const val LOOKUP_DEBOUNCE_MS = 600L
    }
}
