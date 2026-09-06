package com.paychat.paychat.data.moderation

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.paychat.paychat.data.auth.AuthRepository
import com.paychat.paychat.data.local.dao.ThreadDao
import com.paychat.paychat.data.remote.Collections
import com.paychat.paychat.data.remote.ThreadFields
import kotlinx.coroutines.tasks.await
import java.util.UUID
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
    private val threadDao: ThreadDao,
    private val auth: AuthRepository,
) {

    suspend fun block(threadId: String): Result<Unit> = setBlocked(threadId, blocked = true)

    suspend fun unblock(threadId: String): Result<Unit> = setBlocked(threadId, blocked = false)

    /**
     * Files a report. The thread id and the reason are enough for a moderator
     * to read the conversation; the messages themselves are not copied.
     */
    suspend fun report(
        threadId: String,
        reason: ReportReason,
        detail: String?,
    ): Result<Unit> = runCatching {
        val uid = auth.currentUid ?: error("not signed in")
        val thread = threadDao.byId(threadId)

        firestore.collection(Collections.REPORTS).document(UUID.randomUUID().toString()).set(
            mapOf(
                "reportedBy" to uid,
                "threadId" to threadId,
                "reportedUid" to thread?.peerUid,
                "reportedPhone" to thread?.peerPhone,
                "reason" to reason.name,
                "detail" to detail?.take(DETAIL_LIMIT),
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
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
        /** A report is a pointer for a human, not an essay. */
        const val DETAIL_LIMIT = 500
    }
}
