package com.paychat.paychat.feature.auth.login

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paychat.paychat.core.phone.PhoneNumbers
import com.paychat.paychat.data.auth.AuthError
import com.paychat.paychat.data.auth.AuthException
import com.paychat.paychat.data.auth.AuthRepository
import com.paychat.paychat.data.auth.PendingRegistration
import com.paychat.paychat.feature.auth.message
import com.paychat.paychat.ui.nav.NavArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val phoneE164: String = "",
    val phoneDisplay: String = "",
    val password: String = "",
    val error: String? = null,
    val submitting: Boolean = false,
    val signedIn: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    phoneNumbers: PhoneNumbers,
    private val authRepository: AuthRepository,
    private val pending: PendingRegistration,
) : ViewModel() {

    // The route carries the number without its leading plus. See Routes.login.
    private val phoneE164 = "+" + savedStateHandle.get<String>(NavArgs.PHONE).orEmpty()

    private val _state = MutableStateFlow(
        LoginUiState(
            phoneE164 = phoneE164,
            phoneDisplay = phoneNumbers.formatForDisplay(phoneE164),
        )
    )
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, error = null) }

    fun submit() {
        val current = _state.value
        if (current.submitting) return

        if (current.password.isEmpty()) {
            _state.update { it.copy(error = "Enter your password.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            authRepository.signIn(phoneE164, current.password).fold(
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
     * @return the E.164 number to verify
     */
    fun startPasswordReset(newPassword: String): String {
        pending.put(phoneE164 = phoneE164, name = "", password = newPassword)
        return phoneE164
    }
}
