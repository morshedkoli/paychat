package com.paychat.paychat.data.account

import com.google.firebase.functions.FirebaseFunctions
import com.paychat.paychat.data.auth.AuthRepository
import com.paychat.paychat.data.local.PayChatDatabase
import com.paychat.paychat.data.notifications.PushTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Closing the account for good.
 *
 * The server side is a Cloud Function: deleting the Firebase Auth user, the
 * phone number's claim on it, and the private collections all need
 * credentials the app does not have, and a half-finished deletion done from
 * the client would leave an account nobody could sign into but which still
 * held a phone number.
 *
 * Transactions in shared conversations are not deleted. Each one is a record
 * of money between two people, and the other party's ledger has to keep
 * adding up; the function marks this user as departed instead. The dialog
 * that leads here says so plainly.
 */
@Singleton
class AccountRepository @Inject constructor(
    private val functions: FirebaseFunctions,
    private val database: PayChatDatabase,
    private val pushTokens: PushTokens,
    private val auth: AuthRepository,
) {

    suspend fun deleteAccount(): Result<Unit> = runCatching {
        val uid = auth.currentUid ?: error("You are not signed in.")

        // Before the account goes: afterwards there is no permission to write
        // the token away, and a device that kept it would go on being sent
        // pushes for a number somebody else may later register.
        pushTokens.clear(uid)

        functions.getHttpsCallable(DELETE_FUNCTION).call().await()

        withContext(Dispatchers.IO) { database.clearAllTables() }
        auth.signOut()
    }

    private companion object {
        const val DELETE_FUNCTION = "deleteAccount"
    }
}
