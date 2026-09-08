package com.paychat.paychat.data.auth

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.paychat.paychat.data.remote.Collections
import com.paychat.paychat.data.remote.PhoneIndexFields
import com.paychat.paychat.data.remote.UserFields
import com.paychat.paychat.data.session.SessionStore
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.util.UUID
import com.paychat.paychat.data.local.PayChatDatabase
import com.paychat.paychat.data.sync.OutboxScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class AuthedUser(
    val uid: String,
    val phone: String,
    val name: String,
)

/**
 * Registration, sign in, and password reset.
 *
 * An account is a Firebase phone credential (the verified number, and the
 * identity other users look up) plus a linked Email/Password credential whose
 * address is derived from that number by [SyntheticEmail]. Phone gives us
 * verification, password gives us sign-in without an SMS every time.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val session: SessionStore,
    private val database: PayChatDatabase,
    private val outbox: OutboxScheduler,
) {

    val currentUid: String? get() = auth.currentUser?.uid

    /**
     * Finishes registration once the phone number has been verified.
     *
     * The phone number is not checked for an existing account beforehand:
     * `phoneIndex` is not readable while signed out, and making it public would
     * leak which numbers use the app. Instead the phone sign-in itself reports
     * whether the account is new.
     */
    suspend fun completeRegistration(
        credential: PhoneAuthCredential,
        phoneE164: String,
        name: String,
        password: String,
    ): Result<AuthedUser> = runCatchingAuth {
        val result = auth.signInWithCredential(credential).await()
        val user = result.user ?: error("phone sign-in returned no user")

        if (result.additionalUserInfo?.isNewUser == false) {
            // Somebody already registered this number. Leave the session as we
            // found it rather than half-signing them in.
            auth.signOut()
            return Result.failure(AuthException(AuthError.PhoneAlreadyRegistered))
        }

        val email = SyntheticEmail.forPhone(phoneE164)
        try {
            user.linkWithCredential(EmailAuthProvider.getCredential(email, password)).await()
        } catch (e: Exception) {
            // Without the password credential the account could never be signed
            // into again without an SMS, so a half-built account is worse than
            // none. Remove it and let the user try again.
            runCatching { user.delete().await() }
            throw e
        }

        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        firestore.runBatch { batch ->
            batch.set(
                firestore.collection(Collections.USERS).document(user.uid),
                mapOf(
                    UserFields.PHONE to phoneE164,
                    UserFields.NAME to name,
                    UserFields.CREATED_AT to now,
                    UserFields.UPDATED_AT to now,
                    UserFields.ACTIVE_SESSION_ID to sessionId,
                )
            )
            batch.set(
                firestore.collection(Collections.PHONE_INDEX).document(phoneE164),
                mapOf(PhoneIndexFields.UID to user.uid)
            )
        }.await()

        session.save(uid = user.uid, phone = phoneE164, sessionId = sessionId)
        AuthedUser(uid = user.uid, phone = phoneE164, name = name)
    }

    /**
     * Signs in with the phone number and password. No SMS is involved.
     */
    suspend fun signIn(phoneE164: String, password: String): Result<AuthedUser> = runCatchingAuth {
        val email = SyntheticEmail.forPhone(phoneE164)
        val result = auth.signInWithEmailAndPassword(email, password).await()
        val user = result.user ?: error("password sign-in returned no user")

        val sessionId = UUID.randomUUID().toString()
        val doc = firestore.collection(Collections.USERS).document(user.uid)

        // Claiming the session here is what signs the previous device out.
        doc.set(
            mapOf(
                UserFields.ACTIVE_SESSION_ID to sessionId,
                UserFields.UPDATED_AT to System.currentTimeMillis(),
            ),
            SetOptions.merge()
        ).await()

        val name = doc.get().await().getString(UserFields.NAME).orEmpty()
        session.save(uid = user.uid, phone = phoneE164, sessionId = sessionId)
        AuthedUser(uid = user.uid, phone = phoneE164, name = name)
    }

    /**
     * Sets a new password after the number has been verified by OTP.
     *
     * Signing in with the phone credential both proves ownership and satisfies
     * Firebase's recent-login requirement for a password change.
     */
    suspend fun resetPassword(
        credential: PhoneAuthCredential,
        phoneE164: String,
        newPassword: String,
    ): Result<Unit> = runCatchingAuth {
        val result = auth.signInWithCredential(credential).await()
        val user = result.user ?: error("phone sign-in returned no user")

        if (result.additionalUserInfo?.isNewUser == true) {
            // The number has no account. Signing in created an empty one, so
            // remove it again rather than leaving a stray account behind.
            runCatching { user.delete().await() }
            return Result.failure(AuthException(AuthError.PhoneNotRegistered))
        }

        user.updatePassword(newPassword).await()

        val sessionId = UUID.randomUUID().toString()
        firestore.collection(Collections.USERS).document(user.uid).set(
            mapOf(
                UserFields.ACTIVE_SESSION_ID to sessionId,
                UserFields.UPDATED_AT to System.currentTimeMillis(),
            ),
            SetOptions.merge()
        ).await()
        session.save(uid = user.uid, phone = phoneE164, sessionId = sessionId)
    }

    /**
     * Whether [phoneE164] already has an account.
     *
     * `phoneIndex` is not readable while signed out — making it public would
     * leak which numbers use the app — so the question goes to a callable that
     * reads it with admin credentials and answers with one bit.
     *
     * This is a hint for the signed out flow, never a decision: whichever
     * branch it sends someone down, [completeRegistration] still refuses a
     * number that is taken and [signIn] still refuses one that is not.
     */
    suspend fun phoneExists(phoneE164: String): Result<Boolean> = runCatchingAuth {
        val response = functions.getHttpsCallable(LOOKUP_FUNCTION)
            .call(mapOf("phone" to phoneE164))
            .await()

        @Suppress("UNCHECKED_CAST")
        val data = response.getData() as? Map<String, Any?>
            ?: error("phoneLookup returned no data")
        data["exists"] as? Boolean ?: error("phoneLookup returned no exists flag")
    }

    suspend fun signOut() {
        auth.signOut()
        session.clear()
        outbox.cancel()
        withContext(Dispatchers.IO) {
            database.clearAllTables()
        }
    }

    private companion object {
        const val LOOKUP_FUNCTION = "phoneLookup"
    }
}

/** Carries an [AuthError] through [Result]. */
class AuthException(val error: AuthError) : Exception(error.toString())

/**
 * Runs [block], mapping Firebase's exception types to [AuthError] so that no
 * Firebase type reaches a ViewModel.
 */
private inline fun <T> runCatchingAuth(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: AuthException) {
    Result.failure(e)
} catch (e: FirebaseAuthWeakPasswordException) {
    Result.failure(AuthException(AuthError.WeakPassword))
} catch (e: FirebaseAuthUserCollisionException) {
    Result.failure(AuthException(AuthError.PhoneAlreadyRegistered))
} catch (e: FirebaseAuthInvalidUserException) {
    Result.failure(AuthException(AuthError.PhoneNotRegistered))
} catch (e: FirebaseAuthInvalidCredentialsException) {
    // The same exception type covers a wrong password and a bad OTP; the
    // error code is the only thing that separates them.
    val error = when (e.errorCode) {
        "ERROR_INVALID_VERIFICATION_CODE" -> AuthError.InvalidOtp
        "ERROR_SESSION_EXPIRED" -> AuthError.OtpExpired
        else -> AuthError.WrongPassword
    }
    Result.failure(AuthException(error))
} catch (e: FirebaseFunctionsException) {
    // A callable reports "too many" as RESOURCE_EXHAUSTED; everything else it
    // can fail with is either a bug or the network.
    val error = when (e.code) {
        FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> AuthError.TooManyRequests
        FirebaseFunctionsException.Code.UNAVAILABLE,
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> AuthError.Network
        else -> AuthError.Unknown(e.message)
    }
    Result.failure(AuthException(error))
} catch (e: IOException) {
    Result.failure(AuthException(AuthError.Network))
} catch (e: Exception) {
    Result.failure(AuthException(AuthError.Unknown(e.message)))
}
