package com.paychat.koli.data.auth

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What can come back while an OTP is in flight.
 */
sealed interface VerificationEvent {
    /** The SMS was sent and the user should now type the code. */
    data class CodeSent(
        val verificationId: String,
        val resendToken: PhoneAuthProvider.ForceResendingToken,
    ) : VerificationEvent

    /**
     * Google Play services read the SMS itself, or the number was
     * instant-verified. There is nothing for the user to type.
     */
    data class AutoVerified(val credential: PhoneAuthCredential) : VerificationEvent

    data class Failed(val error: AuthError) : VerificationEvent

    /** The window for auto-retrieval closed; the user must type the code. */
    data object AutoRetrievalTimeout : VerificationEvent
}

/**
 * Wraps [PhoneAuthProvider] callbacks as a flow.
 *
 * The Firebase call needs an Activity because it may show a reCAPTCHA when
 * Play Integrity is unavailable, so the caller passes the hosting Activity in.
 */
@Singleton
class PhoneVerifier @Inject constructor(
    private val auth: FirebaseAuth,
) {
    fun verify(
        phoneE164: String,
        activity: Activity,
        resendToken: PhoneAuthProvider.ForceResendingToken? = null,
    ): Flow<VerificationEvent> = callbackFlow {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                trySend(VerificationEvent.AutoVerified(credential))
            }

            override fun onVerificationFailed(e: FirebaseException) {
                trySend(VerificationEvent.Failed(e.toAuthError()))
                close()
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken,
            ) {
                trySend(VerificationEvent.CodeSent(verificationId, token))
            }

            override fun onCodeAutoRetrievalTimeOut(verificationId: String) {
                trySend(VerificationEvent.AutoRetrievalTimeout)
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneE164)
            .setTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .apply { if (resendToken != null) setForceResendingToken(resendToken) }
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)

        // Firebase has no way to cancel a request in flight; dropping the
        // callbacks is all that is available.
        awaitClose { }
    }

    companion object {
        const val TIMEOUT_SECONDS = 60L
    }
}

internal fun FirebaseException.toAuthError(): AuthError = when (this) {
    is FirebaseAuthInvalidCredentialsException -> AuthError.InvalidPhoneNumber
    is FirebaseTooManyRequestsException -> AuthError.TooManyRequests
    else -> AuthError.Unknown(message)
}
