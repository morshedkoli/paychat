package com.paychat.paychat.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.paychat.paychat.data.auth.AuthRepository
import com.paychat.paychat.data.notifications.PushTokens
import com.paychat.paychat.data.session.SessionGuard
import com.paychat.paychat.data.session.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthGate { CHECKING, SIGNED_IN, SIGNED_OUT }

/**
 * Decides where the app opens, and watches for this device's session being
 * replaced by a sign-in elsewhere.
 */
@HiltViewModel
class AuthGateViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val sessionStore: SessionStore,
    private val sessionGuard: SessionGuard,
    private val authRepository: AuthRepository,
    private val pushTokens: PushTokens,
) : ViewModel() {

    private val _gate = MutableStateFlow(AuthGate.CHECKING)
    val gate: StateFlow<AuthGate> = _gate.asStateFlow()

    private val _signedOutElsewhere = MutableStateFlow(false)
    val signedOutElsewhere: StateFlow<Boolean> = _signedOutElsewhere.asStateFlow()

    init {
        viewModelScope.launch {
            val firebaseUser = auth.currentUser
            val storedUid = sessionStore.uid.first()

            // Both sides must agree. A Firebase session with no local session
            // means the app data was cleared; the reverse means the credential
            // was revoked.
            if (firebaseUser == null || storedUid == null || firebaseUser.uid != storedUid) {
                authRepository.signOut()
                _gate.value = AuthGate.SIGNED_OUT
                return@launch
            }

            _gate.value = AuthGate.SIGNED_IN
            watchSession(firebaseUser.uid)
            // Tokens are rotated by Firebase and dropped when app data is
            // cleared, so the server copy is refreshed on every start.
            pushTokens.register()
        }
    }

    private fun watchSession(uid: String) {
        viewModelScope.launch {
            sessionGuard.sessionRevoked(uid).collect { revoked ->
                if (revoked) {
                    authRepository.signOut()
                    _signedOutElsewhere.value = true
                    _gate.value = AuthGate.SIGNED_OUT
                }
            }
        }
    }

    fun onSignedIn() {
        _gate.value = AuthGate.SIGNED_IN
        auth.currentUser?.uid?.let(::watchSession)
        viewModelScope.launch { pushTokens.register() }
    }

    fun acknowledgeSignedOutElsewhere() {
        _signedOutElsewhere.value = false
    }

    fun signOut() {
        viewModelScope.launch {
            // Before the sign out, while the write is still permitted: a
            // signed out device must stop receiving this account's pushes.
            auth.currentUser?.uid?.let { pushTokens.clear(it) }
            authRepository.signOut()
            _gate.value = AuthGate.SIGNED_OUT
        }
    }
}
