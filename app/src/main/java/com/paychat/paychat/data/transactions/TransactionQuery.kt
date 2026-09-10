package com.paychat.paychat.data.transactions

import com.paychat.paychat.data.local.dao.TransactionDao
import com.paychat.paychat.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only access to the transaction table for screens that span every
 * conversation. Kept apart from [TransactionRepository], which owns writing and
 * syncing and needs Firestore to exist.
 */
@Singleton
class TransactionQuery @Inject constructor(private val dao: TransactionDao) {

    fun observeRecent(limit: Int): Flow<List<TransactionEntity>> = dao.observeRecent(limit)
}
