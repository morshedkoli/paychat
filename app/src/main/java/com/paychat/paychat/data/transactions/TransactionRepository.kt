package com.paychat.paychat.data.transactions

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.paychat.paychat.core.ledger.BalanceCalculator
import com.paychat.paychat.core.ledger.TransactionRules
import com.paychat.paychat.core.model.MessageType
import com.paychat.paychat.core.model.SyncState
import com.paychat.paychat.core.model.TxnDirection
import com.paychat.paychat.core.model.TxnStatus
import com.paychat.paychat.core.money.Money
import com.paychat.paychat.data.auth.AuthRepository
import com.paychat.paychat.data.local.dao.MessageDao
import com.paychat.paychat.data.local.dao.ThreadDao
import com.paychat.paychat.data.local.dao.TransactionDao
import com.paychat.paychat.data.local.entity.MessageEntity
import com.paychat.paychat.data.local.entity.TransactionEntity
import com.paychat.paychat.data.remote.Collections
import com.paychat.paychat.data.remote.ThreadBalanceFields
import com.paychat.paychat.data.remote.TransactionFields
import com.paychat.paychat.data.sync.OutboxScheduler
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
     */
    suspend fun create(
        threadId: String,
        direction: TxnDirection,
        amount: Money,
        note: String?,
        photoLocalPath: String?,
        dueDate: Long?,
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

        recomputeBalance(threadId)
        outbox.schedule()
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
     * and both people can see what was corrected and when.
     */
    suspend fun reverse(txnId: String, note: String?): Result<String> = runCatching {
        val original = transactionDao.byId(txnId) ?: error("that transaction is not on this device")
        require(TransactionRules.canReverse(original.status, original.reversedBy != null)) {
            "only an accepted transaction that has not already been reversed can be corrected"
        }

        val reversalId = create(
            threadId = original.threadId,
            direction = TransactionRules.reversalDirection(original.direction),
            amount = Money(original.amountMinor),
            note = note ?: "Correction",
            photoLocalPath = null,
            dueDate = null,
        ).getOrThrow()

        transactionDao.upsert(
            original.copy(reversedBy = reversalId, syncState = SyncState.PENDING)
        )
        recomputeBalance(original.threadId)
        outbox.schedule()
        reversalId
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
        recomputeBalance(transaction.threadId)
        outbox.schedule()
    }

    /**
     * Recomputes the conversation balance from its transactions.
     *
     * The stored figure is a cache for the lists; this is the only thing that
     * ever writes it, and it is always derivable again from the rows.
     */
    suspend fun recomputeBalance(threadId: String) {
        val viewerUid = auth.currentUid ?: return
        val rows = transactionDao.forThread(threadId)
        val balance = BalanceCalculator.balanceOf(rows, viewerUid)
        val now = System.currentTimeMillis()

        threadDao.setBalance(threadId, balance.minor, now)

        // The home screen of a freshly installed device has no transactions to
        // add up yet, so the figure is also stored per user on the server.
        runCatching {
            firestore.collection(Collections.USERS).document(viewerUid)
                .collection(Collections.THREAD_BALANCES).document(threadId)
                .set(
                    mapOf(
                        ThreadBalanceFields.AMOUNT_MINOR to balance.minor,
                        ThreadBalanceFields.UPDATED_AT to now,
                    )
                ).await()
        }
    }

    /** Uploads one transaction. Called by the outbox worker. */
    suspend fun upload(transaction: TransactionEntity) {
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
            .associateBy { it.txnId }

        transactionDao.upsertAll(transactions.filter { it.txnId !in unsynced })
        recomputeBalance(threadId)
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
        amountMinor = getLong(TransactionFields.AMOUNT_MINOR) ?: 0L,
        note = getString(TransactionFields.NOTE),
        photoUrl = getString(TransactionFields.PHOTO_URL),
        photoPublicId = getString(TransactionFields.PHOTO_PUBLIC_ID),
        dueDate = getLong(TransactionFields.DUE_DATE),
        status = status,
        unconfirmed = getBoolean(TransactionFields.UNCONFIRMED) ?: false,
        reversesId = getString(TransactionFields.REVERSES_ID),
        reversedBy = getString(TransactionFields.REVERSED_BY),
        createdAt = getLong(TransactionFields.CREATED_AT) ?: 0L,
        resolvedAt = getLong(TransactionFields.RESOLVED_AT),
        resolvedBy = getString(TransactionFields.RESOLVED_BY),
        syncState = SyncState.SYNCED,
    )
}
