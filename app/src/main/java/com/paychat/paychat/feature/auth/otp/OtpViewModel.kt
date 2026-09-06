package com.paychat.paychat.feature.auth.otp

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.paychat.paychat.core.phone.PhoneNumbers
import com.paychat.paychat.core.validation.Validators
import com.paychat.paychat.data.auth.AuthError
import com.paychat.paychat.data.auth.AuthException
import com.paychat.paychat.data.auth.AuthRepository
import com.paychat.paychat.data.auth.PendingRegistration
import com.paychat.paychat.data.auth.PhoneVerifier
import com.paychat.paychat.data.auth.VerificationEvent
import com.paychat.paychat.feature.auth.message
import com.paychat.paychat.ui.nav.NavArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OtpPurpose { REGISTER, RESET_PASSWORD }

data class OtpUiState(
    val phoneE164: String = "",
    val phoneDisplay: String = "",
    val purpose: OtpPurpose = OtpPurpose.REGISTER,
    val code: String = "",
    val sending: Boolean = true,
    val verifying: Boolean = false,
    val codeSent: Boolean = false,
    val secondsUntilResend: Int = 0,
    val error: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class OtpViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    phoneNumbers: PhoneNumbers,
    private val verifier: PhoneVerifier,
    private val authRepository: AuthRepository,
    private val pending: PendingRegistration,
) : ViewModel() {

    // The route carries the number without its leading plus. See Routes.otp.
    private val phoneE164 = "+" + savedStateHandle.get<String>(NavArgs.PHONE).orEmpty()

    private val _state = MutableStateFlow(
        OtpUiState(
            phoneE164 = phoneE164,
            phoneDisplay = phoneNumbers.formatForDisplay(phoneE164),
            purpose = savedStateHandle.get<String>(NavArgs.PURPOSE)
                ?.let(OtpPurpose::valueOf)
                ?: OtpPurpose.REGISTER,
        )
    )
    val state: StateFlow<OtpUiState> = _state.asStateFlow()

    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var verificationJob: Job? = null

    fun onCodeChange(value: String) = _state.update { it.copy(code = value, error = null) }

    /**
     * Starts, or restarts, the SMS request. The Activity is needed because
     * Firebase may put up a reCAPTCHA when Play Integrity is not available.
     */
    fun sendCode(activity: Activity, resend: Boolean = false) {
        verificationJob?.cancel()
        _state.update { it.copy(sending = true, error = null) }

        verificationJob = viewModelScope.launch {
            verifier.verify(
                phoneE164 = _state.value.phoneE164,
                activity = activity,
                resendToken = if (resend) resendToken else null,
            ).collect { event ->
                when (event) {
                    is VerificationEvent.CodeSent -> {
                        verificationId = event.verificationId
                        resendToken = event.resendToken
                        _state.update {
                            it.copy(
                                sending = false,
                                codeSent = true,
                                secondsUntilResend = PhoneVerifier.TIMEOUT_SECONDS.toInt(),
                            )
                        }
                        startResendCountdown()
                    }

                    is VerificationEvent.AutoVerified -> {
                        // Play services read the SMS. Finish without asking the
                        // user to type anything.
                        _state.update { it.copy(sending = false, codeSent = true) }
                        finish(event.credential)
                    }

                    is VerificationEvent.Failed ->
                        _state.update { it.copy(sending = false, error = event.error.message()) }

                    VerificationEvent.AutoRetrievalTimeout ->
                        _state.update { it.copy(sending = false, secondsUntilResend = 0) }
                }
            }
        }
    }

    fun submitCode() {
        val current = _state.value
        if (current.verifying) return
        if (!Validators.isValidOtp(current.code)) {
            _state.update { it.copy(error = "Enter the ${Validators.OTP_LENGTH} digit code.") }
            return
        }
        val id = verificationId
        if (id == null) {
            _state.update { it.copy(error = AuthError.OtpExpired.message()) }
            return
        }
        finish(PhoneAuthProvider.getCredential(id, current.code))
    }

    private fun finish(credential: PhoneAuthCredential) {
        viewModelScope.launch {
            _state.update { it.copy(verifying = true, error = null) }

            val details = pending.peek()
            if (details == null) {
                _state.update {
                    it.copy(
                        verifying = false,
                        error = "Your details were lost. Start again.",
                    )
                }
                return@launch
            }

            val result = when (_state.value.purpose) {
                OtpPurpose.REGISTER -> authRepository.completeRegistration(
                    credential = credential,
                    phoneE164 = details.phoneE164,
                    name = details.name,
                    password = details.password,
                ).map { }

                OtpPurpose.RESET_PASSWORD -> authRepository.resetPassword(
                    credential = credential,
                    phoneE164 = details.phoneE164,
                    newPassword = details.password,
                )
            }

            result.fold(
                onSuccess = {
                    pending.clear()
                    _state.update { it.copy(verifying = false, done = true) }
                },
                onFailure = { throwable ->
                    val error = (throwable as? AuthException)?.error
                        ?: AuthError.Unknown(throwable.message)
                    _state.update { it.copy(verifying = false, error = error.message()) }
                },
            )
        }
    }

    private fun startResendCountdown() {
        viewModelScope.launch {
            while (_state.value.secondsUntilResend > 0) {
                kotlinx.coroutines.delay(1_000)
                _state.update { it.copy(secondsUntilResend = it.secondsUntilResend - 1) }
            }
        }
    }
}
