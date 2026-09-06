package com.paychat.paychat.data.moderation

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.paychat.paychat.data.auth.AuthRepository
import com.paychat.paychat.data.local.dao.ThreadDao
import com.paychat.paychat.data.remote.Collections
import com.paychat.paychat.data.remote.ThreadFields
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Why a conversation was reported. The list is short on purpose. */
enum class ReportReason {
    SPAM,
    HARASSMENT,
    FRAUD,
    OTHER;

    val label: String
        get() = when (this) {
            SPAM -> "Spam"
            HARASSMENT -> "Harassment or abuse"
            FRAUD -> "Fraud or a false claim of money"
            OTHER -> "Something else"
        }
}

/**
 * Blocking someone, and reporting them.
 *
 * Blocking stops new messages and new transactions in both directions. It
 * does not touch history or balances: what was already recorded is what is
 * owed, and hiding it because two people fell out would be the one thing an
 * app about money must never do.
 *
 * The block lives on the thread rather than in a private list, because the
 * security rules have to see it to refuse the other side's writes, and a
 * thread is the one document both parties can already read.
 */
@Singleton
class ModerationRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val threadDao: ThreadDao,
    private val auth: AuthRepository,
) {

    suspend fun block(threadId: String): Result<Unit> = setBlocked(threadId, blocked = true)

    suspend fun unblock(threadId: String): Result<Unit> = setBlocked(threadId, blocked = false)

    /**
     * Files a report.
     *
     * A Cloud Function does the writing, because a report needs a rate limit
     * and a check that the reporter is in the thread, and a security rule can
     * do neither. The conversation is not copied: a moderator reads it from
     * the thread id.
     */
    suspend fun report(
        threadId: String,
        reason: ReportReason,
        detail: String?,
    ): Result<Unit> = runCatching {
        functions.getHttpsCallable(REPORT_FUNCTION)
            .call(
                mapOf(
                    "threadId" to threadId,
                    "reason" to reason.name,
                    "detail" to detail,
                )
            )
            .await()
        Unit
    }

    private suspend fun setBlocked(threadId: String, blocked: Boolean): Result<Unit> = runCatching {
        val uid = auth.currentUid ?: error("not signed in")

        firestore.collection(Collections.THREADS).document(threadId).update(
            ThreadFields.BLOCKED_BY,
            if (blocked) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid),
        ).await()

        // Written locally as well, so the chat reflects the block at once
        // rather than when the listener next fires.
        threadDao.byId(threadId)?.let { threadDao.upsert(it.copy(blockedByMe = blocked)) }
    }

    private companion object {
        const val REPORT_FUNCTION = "fileReport"
    }
}
