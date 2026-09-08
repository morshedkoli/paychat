package com.paychat.paychat.feature.auth.phone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paychat.paychat.core.phone.PhoneNumbers
import com.paychat.paychat.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PhoneEntryUiState(
    /** ISO region of the chosen country; the number is parsed against it. */
    val region: String = PhoneNumbers.DEFAULT_REGION,
    val phone: String = "",
    val phoneError: String? = null,
    val checking: Boolean = false,
    /** Set when the number is known and the password step should open. */
    val knownNumber: String? = null,
    /** Set when the number is unknown, to ask before starting registration. */
    val offerRegistration: String? = null,
    /** The same number formatted for the confirmation dialog to show. */
    val offerRegistrationDisplay: String? = null,
)

@HiltViewModel
class PhoneEntryViewModel @Inject constructor(
    private val phoneNumbers: PhoneNumbers,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PhoneEntryUiState())
    val state: StateFlow<PhoneEntryUiState> = _state.asStateFlow()

    fun onPhoneChange(value: String) =
        _state.update { it.copy(phone = value, phoneError = null) }

    fun onRegionChange(region: String) =
        _state.update { it.copy(region = region, phoneError = null) }

    fun submit() {
        val current = _state.value
        if (current.checking) return

        val e164 = phoneNumbers.toE164(current.phone, current.region)
        if (e164 == null) {
            _state.update { it.copy(phoneError = "Enter a valid phone number.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(checking = true, phoneError = null) }
            when (val outcome = phoneLookupOutcome(authRepository.phoneExists(e164))) {
                PhoneLookupOutcome.Registered ->
                    _state.update { it.copy(checking = false, knownNumber = e164) }
                PhoneLookupOutcome.Unregistered ->
                    _state.update {
                        it.copy(
                            checking = false,
                            offerRegistration = e164,
                            offerRegistrationDisplay = phoneNumbers.formatForDisplay(e164),
                        )
                    }
                is PhoneLookupOutcome.Failed ->
                    _state.update { it.copy(checking = false, phoneError = outcome.message) }
            }
        }
    }

    /** The confirmation was dismissed: keep the number on screen for a second look. */
    fun onRegistrationDeclined() = _state.update {
        it.copy(offerRegistration = null, offerRegistrationDisplay = null)
    }

    fun onNavigated() = _state.update {
        it.copy(knownNumber = null, offerRegistration = null, offerRegistrationDisplay = null)
    }
}
