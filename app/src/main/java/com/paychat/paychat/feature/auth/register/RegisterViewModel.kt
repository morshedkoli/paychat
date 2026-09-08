package com.paychat.paychat.feature.auth.register

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.paychat.paychat.core.phone.PhoneNumbers
import com.paychat.paychat.core.validation.Validators
import com.paychat.paychat.data.auth.PendingRegistration
import com.paychat.paychat.feature.auth.message
import com.paychat.paychat.ui.nav.NavArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class RegisterUiState(
    val phoneE164: String = "",
    val phoneDisplay: String = "",
    val name: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val nameError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val submitting: Boolean = false,
    /** Set when the form is valid and the OTP step should open for this number. */
    val proceedToOtp: String? = null,
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    phoneNumbers: PhoneNumbers,
    private val pending: PendingRegistration,
) : ViewModel() {

    // The route carries the number without its leading plus. See Routes.register.
    private val phoneE164 = "+" + savedStateHandle.get<String>(NavArgs.PHONE).orEmpty()

    private val _state = MutableStateFlow(
        RegisterUiState(
            phoneE164 = phoneE164,
            phoneDisplay = phoneNumbers.formatForDisplay(phoneE164),
        )
    )
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = null) }

    fun onPasswordChange(value: String) = _state.update {
        it.copy(password = value, passwordError = null, confirmPasswordError = null)
    }

    fun onConfirmPasswordChange(value: String) = _state.update {
        it.copy(confirmPassword = value, confirmPasswordError = null)
    }

    /**
     * Validates the form and hands the details to [PendingRegistration]. The
     * account itself is not created until the OTP is verified, so an
     * unverified number never produces an account.
     */
    fun submit() {
        val current = _state.value
        if (current.submitting) return

        val nameError = Validators.validateName(current.name)?.message()
        val passwordError = Validators.validatePassword(current.password)?.message()
        val confirmError =
            if (passwordError == null &&
                !Validators.passwordsMatch(current.password, current.confirmPassword)
            ) "Passwords do not match." else null

        if (nameError != null || passwordError != null || confirmError != null) {
            _state.update {
                it.copy(
                    nameError = nameError,
                    passwordError = passwordError,
                    confirmPasswordError = confirmError,
                )
            }
            return
        }

        pending.put(
            phoneE164 = phoneE164,
            name = current.name.trim(),
            password = current.password,
        )
        _state.update { it.copy(proceedToOtp = phoneE164) }
    }

    fun onNavigated() = _state.update { it.copy(proceedToOtp = null) }
}
