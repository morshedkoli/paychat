package com.paychat.paychat.data.transactions

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.paychat.paychat.core.ledger.BalanceCalculator
import com.paychat.paychat.core.ledger.CorrectionLink
import com.paychat.paychat.core.ledger.TransactionRules
import com.paychat.paychat.core.model.MessageType
import com.paychat.paychat.core.model.SyncState
import com.paychat.paychat.core.model.TxnDirection
import com.paychat.paychat.core.model.TxnStatus
import com.paychat.paychat.core.money.Money
import com.paychat.paychat.data.auth.AuthRepository
import com.paychat.paychat.data.local.dao.MessageDao
import com.paychat.paychat.data.local.dao.ThreadBalanceDao
import com.paychat.paychat.data.local.dao.ThreadCount
import com.paychat.paychat.data.local.dao.ThreadDao
import com.paychat.paychat.data.local.dao.TransactionDao
import com.paychat.paychat.data.local.entity.MessageEntity
import com.paychat.paychat.data.local.entity.ThreadBalanceEntity
import com.paychat.paychat.data.local.entity.TransactionEntity
import com.paychat.paychat.data.remote.Collections
import com.paychat.paychat.data.remote.ThreadBalanceFields
import com.paychat.paychat.data.remote.ThreadFields
import com.paychat.paychat.data.remote.TransactionFields
import com.paychat.paychat.data.remote.getLongOrTimestamp
import com.paychat.paychat.data.sync.OutboxScheduler
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recording and settling money.
 *
 * Every change is written to Room first and uploaded by the outbox, so the
 * ledger works with no connection. The balance is never stored as the
 * authority: it is recomputed from the transaction rows after every change.
 */
@Singleton
class TransactionRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val transactionDao: TransactionDao,
    private val messageDao: MessageDao,
    private val threadDao: ThreadDao,
    private val balanceDao: ThreadBalanceDao,
    private val auth: AuthRepository,
    private val outbox: OutboxScheduler,
) {

    fun observeThread(threadId: String): Flow<List<TransactionEntity>> =
        transactionDao.observeThread(threadId)

    fun observe(txnId: String): Flow<TransactionEntity?> = transactionDao.observe(txnId)

    /**
     * Records a transaction and posts it into the conversation.
     *
     * @param photoLocalPath a receipt photo already copied into app storage
     * @param reversesId the entry this one corrects, when it is a correction
     */
    suspend fun create(
        threadId: String,
        direction: TxnDirection,
        amount: Money,
        note: String?,
        photoLocalPath: String?,
        dueDate: Long?,
        reversesId: String? = null,
    ): Result<String> = runCatching {
        require(amount.minor > 0) { "an amount must be more than zero" }
        val createdBy = auth.currentUid ?: error("not signed in")
        val thread = threadDao.byId(threadId) ?: error("that conversation is not on this device")

        val txnId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val transaction = TransactionEntity(
            txnId = txnId,
            threadId = threadId,
            createdBy = createdBy,
            direction = direction,
            amountMinor = amount.minor,
            note = note?.trim()?.takeIf { it.isNotEmpty() },
            localPhotoPath = photoLocalPath,
            dueDate = dueDate,
            status = TransactionRules.initialStatus(direction, thread.isLocal),
            unconfirmed = TransactionRules.initialUnconfirmed(thread.isLocal),
            reversesId = reversesId,
            createdAt = now,
            syncState = SyncState.PENDING,
        )
        transactionDao.upsert(transaction)

        // The transaction also appears in the conversation, so the two people
        // can talk about it in place rather than somewhere else.
        messageDao.insert(
            MessageEntity(
                messageId = txnId,
                threadId = threadId,
                senderId = createdBy,
                type = MessageType.TXN,
                txnId = txnId,
                createdAt = now,
                syncState = SyncState.PENDING,
            )
        )
        threadDao.setLastMessage(threadId, "Transaction", now)

        refreshThread(threadId)
        outbox.scheduleAndFlush()
        txnId
    }

    /** The counterparty agrees that the money moved. */
    suspend fun accept(txnId: String): Result<Unit> =
        resolve(txnId, TxnStatus.ACCEPTED) { transaction, viewerUid ->
            TransactionRules.canAccept(transaction.status, transaction.createdBy, viewerUid)
        }

    suspend fun reject(txnId: String): Result<Unit> =
        resolve(txnId, TxnStatus.REJECTED) { transaction, viewerUid ->
            TransactionRules.canReject(transaction.status, transaction.createdBy, viewerUid)
        }

    /** The author withdraws their own request before it is acted on. */
    suspend fun cancel(txnId: String): Result<Unit> =
        resolve(txnId, TxnStatus.CANCELLED) { transaction, viewerUid ->
            TransactionRules.canCancel(transaction.status, transaction.createdBy, viewerUid)
        }

    /**
     * Corrects an accepted transaction with an opposite entry.
     *
     * The original is never edited or deleted, so the history stays auditable
     * and both people can see what was corrected and when. The correction
     * carries `reversesId`, and the pointer back from the original is derived
     * from that by [refreshThread] rather than written here: a correction the
     * counterparty refuses has to release the entry it was correcting.
     */
    suspend fun reverse(txnId: String, note: String?): Result<String> = runCatching {
        val original = transactionDao.byId(txnId) ?: error("that transaction is not on this device")
        require(
            TransactionRules.canReverse(
                original.status,
                original.reversedBy != null,
                original.unconfirmed,
            )
        ) {
            "only an accepted transaction that has not already been reversed can be corrected"
        }

        create(
            threadId = original.threadId,
            direction = TransactionRules.reversalDirection(original.direction),
            amount = Money(original.amountMinor),
            note = note ?: "Correction",
            photoLocalPath = null,
            dueDate = null,
            reversesId = original.txnId,
        ).getOrThrow()
    }

    /**
     * Entries this user recorded against someone who had not registered, still
     * waiting for that person to confirm them once they joined.
     */
    fun observeAwaitingConfirmation(): Flow<List<ThreadCount>> {
        val viewerUid = auth.currentUid ?: return flowOf(emptyList())
        return transactionDao.observeAwaitingConfirmation(viewerUid)
    }

    fun observeInherited(): Flow<List<TransactionEntity>> {
        val viewerUid = auth.currentUid ?: return flowOf(emptyList())
        return transactionDao.observeUnconfirmed().map { rows ->
            rows.filter { TransactionRules.canReviewInherited(it.unconfirmed, it.createdBy, viewerUid) }
        }
    }

    suspend fun reviewInherited(txnId: String, accepted: Boolean): Result<Unit> = runCatching {
        val viewerUid = auth.currentUid ?: error("not signed in")
        val transaction = transactionDao.byId(txnId)
            ?: error("that transaction is not on this device")

        require(
            TransactionRules.canReviewInherited(
                transaction.unconfirmed,
                transaction.createdBy,
                viewerUid,
            )
        ) { "that entry is not yours to review" }

        transactionDao.upsert(
            transaction.copy(
                status = if (accepted) TxnStatus.ACCEPTED else TxnStatus.REJECTED,
                unconfirmed = false,
                resolvedAt = System.currentTimeMillis(),
                resolvedBy = viewerUid,
                syncState = SyncState.PENDING,
            )
        )
        refreshThread(transaction.threadId)
        outbox.scheduleAndFlush()
    }

    /**
     * Accepts every inherited entry in one conversation.
     *
     * Marked in one local write and one recompute rather than one of each per
     * entry: a review screen holding a year of history would otherwise re-read
     * and re-add the whole conversation once for every row it accepted. The
     * upload still happens a row at a time, so one entry the server refuses
     * does not discard the rest.
     */
    suspend fun acceptAllInherited(threadId: String): Result<Int> = runCatching {
        val viewerUid = auth.currentUid ?: error("not signed in")
        val now = System.currentTimeMillis()

        val reviewed = transactionDao.forThread(threadId)
            .filter {
                TransactionRules.canReviewInherited(it.unconfirmed, it.createdBy, viewerUid)
            }
            .map {
                it.copy(
                    status = TxnStatus.ACCEPTED,
                    unconfirmed = false,
                    resolvedAt = now,
                    resolvedBy = viewerUid,
                    syncState = SyncState.PENDING,
                )
            }
        if (reviewed.isEmpty()) return@runCatching 0

        transactionDao.upsertAll(reviewed)
        refreshThread(threadId)
        outbox.scheduleAndFlush()
        reviewed.size
    }

    private suspend fun resolve(
        txnId: String,
        newStatus: TxnStatus,
        permitted: (TransactionEntity, String) -> Boolean,
    ): Result<Unit> = runCatching {
        val viewerUid = auth.currentUid ?: error("not signed in")
        val transaction = transactionDao.byId(txnId)
            ?: error("that transaction is not on this device")

        require(permitted(transaction, viewerUid)) { "you cannot do that to this transaction" }

        transactionDao.upsert(
            transaction.copy(
                status = newStatus,
                resolvedAt = System.currentTimeMillis(),
                resolvedBy = viewerUid,
                // Inherited history stops being unconfirmed once reviewed.
                unconfirmed = false,
                syncState = SyncState.PENDING,
            )
        )
        refreshThread(transaction.threadId)
        outbox.scheduleAndFlush()
    }

    /**
     * Brings a conversation back into agreement with its transaction rows:
     * which entries stand corrected, and what the conversation comes to.
     *
     * Both are derived rather than remembered, and both are recomputed from
     * the same single read of the thread's rows, because every caller changes
     * one row and then needs the whole conversation re-evaluated.
     */
    private suspend fun refreshThread(threadId: String) {
        val viewerUid = auth.currentUid ?: return
        val rows = transactionDao.forThread(threadId)

        val correctionOf = TransactionRules.correctionsHeld(
            rows.map { CorrectionLink(it.txnId, it.reversesId, it.status) }
        )

        val changed = mutableListOf<TransactionEntity>()
        val current = rows.map { row ->
            val correction = correctionOf[row.txnId]
            if (row.reversedBy == correction) {
                row
            } else {
                row.copy(reversedBy = correction, syncState = SyncState.PENDING)
                    .also { changed += it }
            }
        }
        if (changed.isNotEmpty()) {
            transactionDao.upsertAll(changed)
            outbox.scheduleAndFlush()
        }

        val balance = BalanceCalculator.balanceOf(current, viewerUid)
        val now = System.currentTimeMillis()

        balanceDao.upsert(
            ThreadBalanceEntity(threadId = threadId, amountMinor = balance.minor, updatedAt = now)
        )

        // A freshly installed device has no transactions to add up yet, so the
        // figure is also stored per user on the server. It is a cache: losing
        // it costs nothing, because opening the conversation recomputes it.
        // Fire-and-forget so offline recording never hangs.
        runCatching {
            firestore.collection(Collections.USERS).document(viewerUid)
                .collection(Collections.THREAD_BALANCES).document(threadId)
                .set(
                    mapOf(
                        ThreadBalanceFields.AMOUNT_MINOR to balance.minor,
                        ThreadBalanceFields.UPDATED_AT to now,
                    )
                )
        }
    }

    fun observeBalances(): Flow<List<ThreadBalanceEntity>> = balanceDao.observeAll()

    /** The signed-in user, or null when the session has already gone. */
    fun viewerUid(): String? = auth.currentUid

    fun observeBalance(threadId: String): Flow<ThreadBalanceEntity?> = balanceDao.observe(threadId)

    /**
     * Watches the balance cache written by this user's other devices.
     *
     * This is what lets the home screen of a new install show real totals
     * without opening every conversation first. A local recompute always wins
     * afterwards, because it is derived from the rows rather than remembered.
     */
    fun syncBalances(): Flow<List<ThreadBalanceEntity>> = callbackFlow {
        val viewerUid = auth.currentUid
        if (viewerUid == null) {
            close()
            return@callbackFlow
        }

        val registration = firestore.collection(Collections.USERS).document(viewerUid)
            .collection(Collections.THREAD_BALANCES)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                trySend(
                    snapshot.documents.map { document ->
                        ThreadBalanceEntity(
                            threadId = document.id,
                            amountMinor = document.getLongOrTimestamp(ThreadBalanceFields.AMOUNT_MINOR) ?: 0L,
                            updatedAt = document.getLongOrTimestamp(ThreadBalanceFields.UPDATED_AT) ?: 0L,
                        )
                    }
                )
            }

        awaitClose { registration.remove() }
    }

    suspend fun persistBalances(balances: List<ThreadBalanceEntity>) {
        if (balances.isNotEmpty()) balanceDao.upsertAll(balances)
    }

    /** Uploads one transaction. Called by the outbox syncer. */
    suspend fun upload(transaction: TransactionEntity) {
        val thread = threadDao.byId(transaction.threadId)
        if (thread != null && !thread.isLocal && thread.peerUid != null) {
            val members = listOf(transaction.createdBy, thread.peerUid).sorted()
            firestore.collection(Collections.THREADS).document(transaction.threadId).set(
                mapOf(
                    ThreadFields.MEMBERS to members,
                    ThreadFields.IS_LOCAL to false,
                ),
                SetOptions.merge(),
            ).await()
        }

        firestore.collection(Collections.THREADS)
            .document(transaction.threadId)
            .collection(Collections.TRANSACTIONS)
            .document(transaction.txnId)
            .set(transaction.toRemote(), SetOptions.merge())
            .await()
    }

    /**
     * Watches the transactions of one conversation, emitting rows for the
     * collector to store. A snapshot listener cannot suspend, so it does not
     * write to the database itself.
     */
    fun syncTransactions(threadId: String): Flow<List<TransactionEntity>> = callbackFlow {
        val registration = firestore.collection(Collections.THREADS)
            .document(threadId)
            .collection(Collections.TRANSACTIONS)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                trySend(snapshot.documents.mapNotNull { it.toEntity(threadId) })
            }

        awaitClose { registration.remove() }
    }

    suspend fun persist(threadId: String, transactions: List<TransactionEntity>) {
        if (transactions.isEmpty()) return

        // Anything still waiting to upload is newer here than on the server,
        // so the local row wins until the outbox has caught up.
        val unsynced = transactionDao.forThread(threadId)
            .filter { it.syncState != SyncState.SYNCED }
            .mapTo(mutableSetOf()) { it.txnId }

        val incoming = transactions.filterNot { it.txnId in unsynced }
        if (incoming.isEmpty()) return

        transactionDao.upsertAll(incoming)
        refreshThread(threadId)
    }

    /**
     * Reads one conversation's transactions once, without a listener.
     *
     * The listener lives on the chat screen, so an entry accepted or rejected
     * while its author was anywhere else in the app stayed PENDING on their
     * device until they opened that conversation again. A push carries the
     * thread id, and this is what it acts on.
     */
    suspend fun fetchThread(threadId: String): Result<Unit> = runCatching {
        val snapshot = firestore.collection(Collections.THREADS)
            .document(threadId)
            .collection(Collections.TRANSACTIONS)
            .get()
            .await()

        persist(threadId, snapshot.documents.mapNotNull { it.toEntity(threadId) })
    }

    /**
     * Re-reads every conversation still holding a decision that is not this
     * device's to make, for when the app is opened having missed the push.
     */
    suspend fun refreshPending(): Result<Unit> = runCatching {
        transactionDao.threadsWithPending().forEach { threadId -> fetchThread(threadId) }
    }
}

private fun TransactionEntity.toRemote(): Map<String, Any?> = mapOf(
    TransactionFields.CREATED_BY to createdBy,
    TransactionFields.DIRECTION to direction.name,
    TransactionFields.AMOUNT_MINOR to amountMinor,
    TransactionFields.NOTE to note,
    TransactionFields.PHOTO_URL to photoUrl,
    TransactionFields.PHOTO_PUBLIC_ID to photoPublicId,
    TransactionFields.DUE_DATE to dueDate,
    TransactionFields.STATUS to status.name,
    TransactionFields.UNCONFIRMED to unconfirmed,
    TransactionFields.REVERSES_ID to reversesId,
    TransactionFields.REVERSED_BY to reversedBy,
    TransactionFields.CREATED_AT to createdAt,
    TransactionFields.RESOLVED_AT to resolvedAt,
    TransactionFields.RESOLVED_BY to resolvedBy,
)

private fun DocumentSnapshot.toEntity(threadId: String): TransactionEntity? {
    val direction = getString(TransactionFields.DIRECTION)
        ?.let { runCatching { TxnDirection.valueOf(it) }.getOrNull() } ?: return null
    val status = getString(TransactionFields.STATUS)
        ?.let { runCatching { TxnStatus.valueOf(it) }.getOrNull() } ?: return null

    return TransactionEntity(
        txnId = id,
        threadId = threadId,
        createdBy = getString(TransactionFields.CREATED_BY).orEmpty(),
        direction = direction,
        amountMinor = getLongOrTimestamp(TransactionFields.AMOUNT_MINOR) ?: 0L,
        note = getString(TransactionFields.NOTE),
        photoUrl = getString(TransactionFields.PHOTO_URL),
        photoPublicId = getString(TransactionFields.PHOTO_PUBLIC_ID),
        dueDate = getLongOrTimestamp(TransactionFields.DUE_DATE),
        status = status,
        unconfirmed = getBoolean(TransactionFields.UNCONFIRMED) ?: false,
        reversesId = getString(TransactionFields.REVERSES_ID),
        reversedBy = getString(TransactionFields.REVERSED_BY),
        createdAt = getLongOrTimestamp(TransactionFields.CREATED_AT) ?: 0L,
        resolvedAt = getLongOrTimestamp(TransactionFields.RESOLVED_AT),
        resolvedBy = getString(TransactionFields.RESOLVED_BY),
        syncState = SyncState.SYNCED,
    )
}
