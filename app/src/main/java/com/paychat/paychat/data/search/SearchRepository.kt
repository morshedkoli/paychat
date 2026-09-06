package com.paychat.paychat.data.search

import com.paychat.paychat.core.money.Money
import com.paychat.paychat.data.local.dao.MessageDao
import com.paychat.paychat.data.local.dao.ThreadDao
import com.paychat.paychat.data.local.dao.TransactionDao
import com.paychat.paychat.data.local.entity.ThreadEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a search turned up, grouped the way the screen reads it.
 */
data class SearchResults(
    val query: String = "",
    val people: List<PersonHit> = emptyList(),
    val messages: List<MessageHit> = emptyList(),
    val transactions: List<TransactionHit> = emptyList(),
) {
    val isEmpty: Boolean
        get() = people.isEmpty() && messages.isEmpty() && transactions.isEmpty()
}

data class PersonHit(
    val threadId: String,
    val name: String,
    val phone: String,
    val photoUrl: String?,
)

data class MessageHit(
    val messageId: String,
    val threadId: String,
    val counterparty: String,
    val text: String,
    val at: Long,
)

data class TransactionHit(
    val txnId: String,
    val threadId: String,
    val counterparty: String,
    val note: String?,
    val amount: Money,
    val at: Long,
)

/**
 * Searches conversations, messages and transaction notes.
 *
 * Everything is answered from Room. The device already holds every message
 * and transaction it is allowed to see, so search works offline and needs no
 * server-side index; Firestore could not do this query at all without one.
 *
 * A hit is only useful if the user can tell which conversation it belongs to,
 * so every result carries the counterparty's name.
 */
@Singleton
class SearchRepository @Inject constructor(
    private val threadDao: ThreadDao,
    private val messageDao: MessageDao,
    private val transactionDao: TransactionDao,
) {

    suspend fun search(rawQuery: String): SearchResults = withContext(Dispatchers.IO) {
        val query = rawQuery.trim()
        if (query.length < MIN_QUERY) return@withContext SearchResults(query = query)

        val pattern = LikePattern.containing(query)
        val threads = threadDao.all().associateBy { it.threadId }
        val needle = query.lowercase(Locale.getDefault())

        val people = threads.values
            .filter {
                it.peerName.lowercase(Locale.getDefault()).contains(needle) ||
                    it.peerPhone.contains(query)
            }
            .sortedByDescending { it.lastMessageAt }
            .map {
                PersonHit(
                    threadId = it.threadId,
                    name = it.displayName(),
                    phone = it.peerPhone,
                    photoUrl = it.peerPhotoUrl,
                )
            }

        val messages = messageDao.search(pattern, LIMIT).mapNotNull { message ->
            val thread = threads[message.threadId] ?: return@mapNotNull null
            MessageHit(
                messageId = message.messageId,
                threadId = message.threadId,
                counterparty = thread.displayName(),
                text = message.text.orEmpty(),
                at = message.createdAt,
            )
        }

        val transactions = transactionDao.search(pattern, LIMIT).mapNotNull { txn ->
            val thread = threads[txn.threadId] ?: return@mapNotNull null
            TransactionHit(
                txnId = txn.txnId,
                threadId = txn.threadId,
                counterparty = thread.displayName(),
                note = txn.note,
                amount = Money(txn.amountMinor),
                at = txn.createdAt,
            )
        }

        SearchResults(
            query = query,
            people = people,
            messages = messages,
            transactions = transactions,
        )
    }

    private companion object {
        /**
         * A single character matches most of the database, which is slow and
         * tells the user nothing.
         */
        const val MIN_QUERY = 2

        const val LIMIT = 50
    }
}

private fun ThreadEntity.displayName(): String = peerName.ifBlank { peerPhone }
