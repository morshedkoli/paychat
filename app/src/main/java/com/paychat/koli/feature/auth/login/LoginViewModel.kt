package com.paychat.koli.feature.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paychat.koli.core.phone.PhoneNumbers
import com.paychat.koli.data.auth.AuthError
import com.paychat.koli.data.auth.AuthException
import com.paychat.koli.data.auth.AuthRepository
import com.paychat.koli.data.auth.PendingRegistration
import com.paychat.koli.feature.auth.message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val phone: String = "",
    val password: String = "",
    val phoneError: String? = null,
    val error: String? = null,
    val submitting: Boolean = false,
    val signedIn: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val phoneNumbers: PhoneNumbers,
    private val authRepository: AuthRepository,
    private val pending: PendingRegistration,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onPhoneChange(value: String) =
        _state.update { it.copy(phone = value, phoneError = null, error = null) }

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, error = null) }

    fun submit() {
        val current = _state.value
        if (current.submitting) return

        val e164 = phoneNumbers.toE164(current.phone)
        if (e164 == null) {
            _state.update { it.copy(phoneError = "Enter a valid phone number.") }
            return
        }
        if (current.password.isEmpty()) {
            _state.update { it.copy(error = "Enter your password.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            authRepository.signIn(e164, current.password).fold(
                onSuccess = { _state.update { it.copy(submitting = false, signedIn = true) } },
                onFailure = { throwable ->
                    val error = (throwable as? AuthException)?.error
                        ?: AuthError.Unknown(throwable.message)
                    _state.update { it.copy(submitting = false, error = error.message()) }
                },
            )
        }
    }

    /**
     * Password reset reuses the OTP screen, which reads the new password from
     * [PendingRegistration]. The name is irrelevant here and is left blank.
     *
     * @return the E.164 number to verify, or null when the field is not valid
     */
    fun startPasswordReset(newPassword: String): String? {
        val e164 = phoneNumbers.toE164(_state.value.phone) ?: run {
            _state.update { it.copy(phoneError = "Enter your phone number first.") }
            return null
        }
        pending.put(phoneE164 = e164, name = "", password = newPassword)
        return e164
    }
}
