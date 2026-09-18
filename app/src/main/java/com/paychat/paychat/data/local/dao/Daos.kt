package com.paychat.paychat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.paychat.paychat.core.model.SyncState
import com.paychat.paychat.data.local.entity.DeviceContactEntity
import com.paychat.paychat.data.local.entity.LocalContactEntity
import com.paychat.paychat.data.local.entity.MessageEntity
import com.paychat.paychat.data.local.entity.ThreadBalanceEntity
import com.paychat.paychat.data.local.entity.ThreadEntity
import com.paychat.paychat.data.local.entity.TransactionEntity
import com.paychat.paychat.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

/** A per-conversation tally, used by the list screens. */
data class ThreadCount(
    val threadId: String,
    val count: Int,
)

@Dao
interface UserDao {
    @Upsert suspend fun upsert(user: UserEntity)

    @Upsert suspend fun upsertAll(users: List<UserEntity>)

    @Query("SELECT * FROM users WHERE uid = :uid")
    fun observe(uid: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    suspend fun byUid(uid: String): UserEntity?
}

@Dao
interface ThreadDao {
    @Upsert suspend fun upsert(thread: ThreadEntity)

    @Upsert suspend fun upsertAll(threads: List<ThreadEntity>)

    @Query("SELECT * FROM threads ORDER BY lastMessageAt DESC")
    fun observeAll(): Flow<List<ThreadEntity>>

    @Query("SELECT * FROM threads ORDER BY lastMessageAt DESC")
    suspend fun all(): List<ThreadEntity>

    @Query("SELECT * FROM threads WHERE threadId = :threadId")
    fun observe(threadId: String): Flow<ThreadEntity?>

    @Query("SELECT * FROM threads WHERE peerPhone = :phone LIMIT 1")
    suspend fun byPhone(phone: String): ThreadEntity?

    @Query("SELECT * FROM threads WHERE threadId = :threadId LIMIT 1")
    suspend fun byId(threadId: String): ThreadEntity?

    @Query(
        """
        UPDATE threads
        SET lastMessageText = :preview, lastMessageAt = :at, updatedAt = :at
        WHERE threadId = :threadId
        """
    )
    suspend fun setLastMessage(threadId: String, preview: String, at: Long)
}

@Dao
interface ThreadBalanceDao {
    @Upsert suspend fun upsert(balance: ThreadBalanceEntity)

    @Upsert suspend fun upsertAll(balances: List<ThreadBalanceEntity>)

    @Query("SELECT * FROM thread_balances")
    fun observeAll(): Flow<List<ThreadBalanceEntity>>

    @Query("SELECT * FROM thread_balances WHERE threadId = :threadId")
    fun observe(threadId: String): Flow<ThreadBalanceEntity?>
}

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Upsert suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY createdAt DESC")
    fun observeThread(threadId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE syncState IN (:states) ORDER BY createdAt ASC")
    suspend fun awaitingSync(states: List<SyncState>): List<MessageEntity>

    @Query("UPDATE messages SET syncState = :state WHERE messageId = :messageId")
    suspend fun setSyncState(messageId: String, state: SyncState)

    @Query("UPDATE messages SET syncState = 'PENDING' WHERE syncState = 'UPLOADING'")
    suspend fun resetUploadingToPending()

    /** Records where an attachment ended up once it has been uploaded. */
    @Query(
        "UPDATE messages SET mediaUrl = :url, mediaPublicId = :publicId, localMediaPath = NULL " +
            "WHERE messageId = :messageId"
    )
    suspend fun setMedia(messageId: String, url: String, publicId: String)

    /** Messages from other people that the user has not opened yet. */
    @Query(
        "SELECT * FROM messages WHERE threadId = :threadId AND senderId != :viewerUid " +
            "AND readAt IS NULL"
    )
    suspend fun unreadFrom(threadId: String, viewerUid: String): List<MessageEntity>

    @Query("UPDATE messages SET readAt = :at WHERE messageId IN (:messageIds)")
    suspend fun markRead(messageIds: List<String>, at: Long)

    /** Unread totals for the thread list, keyed by thread. */
    @Query(
        "SELECT threadId, COUNT(*) AS count FROM messages " +
            "WHERE senderId != :viewerUid AND readAt IS NULL GROUP BY threadId"
    )
    fun observeUnreadCounts(viewerUid: String): Flow<List<ThreadCount>>

    /**
     * Text messages containing the pattern, newest first.
     *
     * LIKE with a leading wildcard cannot use an index, so the result is
     * capped. Search here is for finding a conversation again, not for
     * paging through a year of chat.
     */
    @Query(
        "SELECT * FROM messages WHERE type = 'TEXT' AND text LIKE :pattern ESCAPE '\\' " +
            "ORDER BY createdAt DESC LIMIT :limit"
    )
    suspend fun search(pattern: String, limit: Int): List<MessageEntity>
}

@Dao
interface TransactionDao {
    @Upsert suspend fun upsert(txn: TransactionEntity)

    @Upsert suspend fun upsertAll(txns: List<TransactionEntity>)

    @Query("SELECT * FROM transactions WHERE threadId = :threadId ORDER BY createdAt ASC")
    fun observeThread(threadId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE threadId = :threadId ORDER BY createdAt ASC")
    suspend fun forThread(threadId: String): List<TransactionEntity>

    /**
     * Every transaction, newest first, capped. The cap is raised as the feed is
     * scrolled; there is no cursor, because the whole page is re-read from Room
     * on any change anyway.
     */
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE txnId = :txnId")
    fun observe(txnId: String): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE txnId = :txnId LIMIT 1")
    suspend fun byId(txnId: String): TransactionEntity?

    /** Transactions whose note contains the pattern, newest first. */
    @Query(
        "SELECT * FROM transactions WHERE note LIKE :pattern ESCAPE '\\' " +
            "ORDER BY createdAt DESC LIMIT :limit"
    )
    suspend fun search(pattern: String, limit: Int): List<TransactionEntity>

    @Query("UPDATE transactions SET syncState = :state WHERE txnId = :txnId")
    suspend fun setSyncState(txnId: String, state: SyncState)

    @Query("UPDATE transactions SET syncState = 'PENDING' WHERE syncState = 'UPLOADING'")
    suspend fun resetUploadingToPending()

    @Query(
        "UPDATE transactions SET photoUrl = :url, photoPublicId = :publicId, " +
            "localPhotoPath = NULL WHERE txnId = :txnId"
    )
    suspend fun setPhoto(txnId: String, url: String, publicId: String)

    @Query("SELECT * FROM transactions WHERE unconfirmed = 1 ORDER BY createdAt ASC")
    fun observeUnconfirmed(): Flow<List<TransactionEntity>>

    /**
     * Entries this user recorded that the other person has not confirmed yet,
     * counted per conversation.
     */
    @Query(
        "SELECT threadId, COUNT(*) AS count FROM transactions " +
            "WHERE unconfirmed = 1 AND createdBy = :viewerUid GROUP BY threadId"
    )
    fun observeAwaitingConfirmation(viewerUid: String): Flow<List<ThreadCount>>

    @Query("SELECT * FROM transactions WHERE syncState IN (:states)")
    suspend fun awaitingSync(states: List<SyncState>): List<TransactionEntity>

    /**
     * Conversations holding an entry whose fate is still open.
     *
     * A PENDING entry is the one row on this device that another person can
     * change, and nothing local can know that they have. These are the only
     * conversations worth re-reading when no chat screen is listening.
     */
    @Query("SELECT DISTINCT threadId FROM transactions WHERE status = 'PENDING'")
    suspend fun threadsWithPending(): List<String>
}

@Dao
interface DeviceContactDao {
    @Upsert suspend fun upsertAll(contacts: List<DeviceContactEntity>)

    @Query("SELECT * FROM device_contacts WHERE linkedUid IS NOT NULL ORDER BY displayName ASC")
    fun observeRegistered(): Flow<List<DeviceContactEntity>>

    @Query("SELECT * FROM device_contacts WHERE linkedUid IS NULL ORDER BY displayName ASC")
    fun observeUnregistered(): Flow<List<DeviceContactEntity>>

    /**
     * Removes numbers no longer in the address book. Room has no "delete where
     * not in a large list", so the sync passes the timestamp it just wrote and
     * anything older is stale.
     */
    @Query("DELETE FROM device_contacts WHERE resolvedAt < :before")
    suspend fun deleteResolvedBefore(before: Long)

    @Query("SELECT * FROM device_contacts WHERE phone = :phone LIMIT 1")
    suspend fun byPhone(phone: String): DeviceContactEntity?
}

@Dao
interface LocalContactDao {
    @Upsert suspend fun upsert(contact: LocalContactEntity)

    @Query("SELECT * FROM local_contacts ORDER BY name ASC")
    fun observeAll(): Flow<List<LocalContactEntity>>

    @Query("SELECT * FROM local_contacts WHERE phone = :phone LIMIT 1")
    suspend fun byPhone(phone: String): LocalContactEntity?
}
