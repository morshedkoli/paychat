package com.paychat.paychat.data.notifications

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.paychat.paychat.data.auth.AuthRepository
import com.paychat.paychat.data.remote.Collections
import com.paychat.paychat.data.remote.UserFields
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the server's copy of this device's push token current.
 *
 * PayChat allows one active device, so the user document holds one token
 * rather than a set. Signing in elsewhere replaces it, which is the same rule
 * that already governs the session, and it means a signed-out device stops
 * receiving notifications for the account.
 */
@Singleton
class PushTokens @Inject constructor(
    private val messaging: FirebaseMessaging,
    private val firestore: FirebaseFirestore,
    private val auth: AuthRepository,
) {

    suspend fun register(): Result<Unit> = runCatching {
        val uid = auth.currentUid ?: return@runCatching
        val token = messaging.token.await()
        store(uid, token)
    }

    /** Called when Firebase issues a new token for this device. */
    suspend fun onTokenRefreshed(token: String): Result<Unit> = runCatching {
        val uid = auth.currentUid ?: return@runCatching
        store(uid, token)
    }

    /**
     * Signing out deletes the token, so notifications for the account stop
     * arriving on a device that is no longer signed in to it.
     */
    suspend fun clear(uid: String): Result<Unit> = runCatching {
        firestore.collection(Collections.USERS).document(uid)
            .set(mapOf(UserFields.FCM_TOKEN to null), SetOptions.merge())
            .await()
        messaging.deleteToken().await()
    }

    private suspend fun store(uid: String, token: String) {
        firestore.collection(Collections.USERS).document(uid).set(
            mapOf(
                UserFields.FCM_TOKEN to token,
                UserFields.UPDATED_AT to System.currentTimeMillis(),
            ),
            SetOptions.merge(),
        ).await()
    }
}
