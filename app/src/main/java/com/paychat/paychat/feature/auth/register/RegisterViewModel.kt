package com.paychat.paychat.feature.auth.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paychat.paychat.core.phone.PhoneNumbers
import com.paychat.paychat.core.validation.Validators
import com.paychat.paychat.data.auth.PendingRegistration
import com.paychat.paychat.feature.auth.message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegisterUiState(
    val name: String = "",
    val phone: String = "",
    val password: String = "",
    val nameError: String? = null,
    val phoneError: String? = null,
    val passwordError: String? = null,
    val submitting: Boolean = false,
    /** Set when the form is valid and the OTP step should open for this number. */
    val proceedToOtp: String? = null,
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val phoneNumbers: PhoneNumbers,
    private val pending: PendingRegistration,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = null) }
    fun onPhoneChange(value: String) = _state.update { it.copy(phone = value, phoneError = null) }
    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, passwordError = null) }

    /**
     * Validates the form and hands the details to [PendingRegistration]. The
     * account itself is not created until the OTP is verified, so an
     * unverified number never produces an account.
     */
    fun submit() {
        val current = _state.value
        if (current.submitting) return

        val nameError = Validators.validateName(current.name)?.message()
        val e164 = phoneNumbers.toE164(current.phone)
        val phoneError = if (e164 == null) "Enter a valid phone number." else null
        val passwordError = Validators.validatePassword(current.password)?.message()

        if (nameError != null || phoneError != null || passwordError != null) {
            _state.update {
                it.copy(
                    nameError = nameError,
                    phoneError = phoneError,
                    passwordError = passwordError,
                )
            }
            return
        }

        viewModelScope.launch {
            pending.put(
                phoneE164 = e164!!,
                name = current.name.trim(),
                password = current.password,
            )
            _state.update { it.copy(proceedToOtp = e164) }
        }
    }

    fun onNavigated() = _state.update { it.copy(proceedToOtp = null) }
}
