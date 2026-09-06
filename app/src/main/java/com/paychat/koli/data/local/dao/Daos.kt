package com.paychat.koli.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.paychat.koli.core.model.SyncState
import com.paychat.koli.data.local.entity.DeviceContactEntity
import com.paychat.koli.data.local.entity.LocalContactEntity
import com.paychat.koli.data.local.entity.MessageEntity
import com.paychat.koli.data.local.entity.ThreadEntity
import com.paychat.koli.data.local.entity.TransactionEntity
import com.paychat.koli.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Upsert suspend fun upsert(user: UserEntity)

    @Upsert suspend fun upsertAll(users: List<UserEntity>)

    @Query("SELECT * FROM users WHERE uid = :uid")
    fun observe(uid: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE phone = :phone LIMIT 1")
    suspend fun byPhone(phone: String): UserEntity?
}

@Dao
interface ThreadDao {
    @Upsert suspend fun upsert(thread: ThreadEntity)

    @Upsert suspend fun upsertAll(threads: List<ThreadEntity>)

    @Query("SELECT * FROM threads ORDER BY lastMessageAt DESC")
    fun observeAll(): Flow<List<ThreadEntity>>

    @Query("SELECT * FROM threads WHERE threadId = :threadId")
    fun observe(threadId: String): Flow<ThreadEntity?>

    @Query("SELECT * FROM threads WHERE peerPhone = :phone LIMIT 1")
    suspend fun byPhone(phone: String): ThreadEntity?

    @Query("UPDATE threads SET balanceMinor = :minor, updatedAt = :now WHERE threadId = :threadId")
    suspend fun setBalance(threadId: String, minor: Long, now: Long)

    @Query("SELECT balanceMinor FROM threads")
    fun observeAllBalances(): Flow<List<Long>>
}

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Upsert suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY createdAt DESC")
    fun observeThread(threadId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE syncState IN (:states)")
    suspend fun awaitingSync(states: List<SyncState>): List<MessageEntity>

    @Query("UPDATE messages SET syncState = :state WHERE messageId = :messageId")
    suspend fun setSyncState(messageId: String, state: SyncState)
}

@Dao
interface TransactionDao {
    @Upsert suspend fun upsert(txn: TransactionEntity)

    @Upsert suspend fun upsertAll(txns: List<TransactionEntity>)

    @Query("SELECT * FROM transactions WHERE threadId = :threadId ORDER BY createdAt ASC")
    fun observeThread(threadId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE threadId = :threadId ORDER BY createdAt ASC")
    suspend fun forThread(threadId: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE txnId = :txnId")
    fun observe(txnId: String): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE unconfirmed = 1 ORDER BY createdAt ASC")
    fun observeUnconfirmed(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE syncState IN (:states)")
    suspend fun awaitingSync(states: List<SyncState>): List<TransactionEntity>
}

@Dao
interface DeviceContactDao {
    @Upsert suspend fun upsertAll(contacts: List<DeviceContactEntity>)

    @Query("SELECT * FROM device_contacts WHERE linkedUid IS NOT NULL ORDER BY displayName ASC")
    fun observeRegistered(): Flow<List<DeviceContactEntity>>

    @Query("SELECT * FROM device_contacts WHERE linkedUid IS NULL ORDER BY displayName ASC")
    fun observeUnregistered(): Flow<List<DeviceContactEntity>>

    @Query("SELECT * FROM device_contacts WHERE phone = :phone LIMIT 1")
    suspend fun byPhone(phone: String): DeviceContactEntity?

    /**
     * Removes numbers no longer in the address book. Room has no "delete where
     * not in a large list", so the sync passes the timestamp it just wrote and
     * anything older is stale.
     */
    @Query("DELETE FROM device_contacts WHERE resolvedAt < :before")
    suspend fun deleteResolvedBefore(before: Long)
}

@Dao
interface LocalContactDao {
    @Upsert suspend fun upsert(contact: LocalContactEntity)

    @Query("SELECT * FROM local_contacts ORDER BY name ASC")
    fun observeAll(): Flow<List<LocalContactEntity>>

    @Query("SELECT * FROM local_contacts WHERE phone = :phone LIMIT 1")
    suspend fun byPhone(phone: String): LocalContactEntity?

    @Query("UPDATE local_contacts SET linkedUid = :uid WHERE phone = :phone")
    suspend fun link(phone: String, uid: String)
}
