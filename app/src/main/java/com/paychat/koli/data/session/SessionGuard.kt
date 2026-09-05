package com.paychat.koli.data.session

import com.google.firebase.firestore.FirebaseFirestore
import com.paychat.koli.data.remote.Collections
import com.paychat.koli.data.remote.UserFields
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enforces the one-active-device rule.
 *
 * Signing in anywhere writes a fresh session id onto the user document. Every
 * other device is watching that field, sees a value that is not its own, and
 * signs itself out.
 */
@Singleton
class SessionGuard @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val session: SessionStore,
) {
    /**
     * Emits true exactly when this device's session has been replaced.
     *
     * A missing document or a read failure emits nothing: losing connectivity
     * must never look like a revoked session.
     */
    fun sessionRevoked(uid: String): Flow<Boolean> = callbackFlow {
        val local = session.currentSessionId()
        if (local == null) {
            close()
            return@callbackFlow
        }

        val registration = firestore.collection(Collections.USERS).document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val remote = snapshot.getString(UserFields.ACTIVE_SESSION_ID) ?: return@addSnapshotListener
                if (remote != local) trySend(true)
            }

        awaitClose { registration.remove() }
    }
}
