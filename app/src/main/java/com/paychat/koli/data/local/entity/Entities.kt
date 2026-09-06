package com.paychat.koli.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.paychat.koli.core.ledger.LedgerEntry
import com.paychat.koli.core.model.MessageType
import com.paychat.koli.core.model.SyncState
import com.paychat.koli.core.model.TxnDirection
import com.paychat.koli.core.model.TxnStatus

@Entity(tableName = "users", indices = [Index(value = ["phone"], unique = true)])
data class UserEntity(
    @PrimaryKey val uid: String,
    val phone: String,
    val name: String,
    val photoUrl: String? = null,
    val updatedAt: Long = 0L,
)

/**
 * A conversation. [peerUid] is null while the counterparty is only a local
 * contact; it is filled in when that phone number registers.
 */
@Entity(
    tableName = "threads",
    indices = [Index("peerUid"), Index("peerPhone"), Index("lastMessageAt")]
)
data class ThreadEntity(
    @PrimaryKey val threadId: String,
    val peerUid: String?,
    val peerPhone: String,
    val peerName: String,
    val peerPhotoUrl: String? = null,
    val isLocal: Boolean,
    /** Inherited history on this thread is waiting for the other side to review. */
    val awaitingConfirmation: Boolean = false,
    val lastMessageText: String? = null,
    val lastMessageAt: Long = 0L,
    val unreadCount: Int = 0,
    /** Cached, always recomputable from the transaction rows. */
    val balanceMinor: Long = 0L,
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "messages",
    indices = [Index(value = ["threadId", "createdAt"]), Index("syncState")]
)
data class MessageEntity(
    @PrimaryKey val messageId: String,
    val threadId: String,
    val senderId: String,
    val type: MessageType,
    val text: String? = null,
    val mediaUrl: String? = null,
    val mediaPublicId: String? = null,
    val localMediaPath: String? = null,
    val durationMs: Long? = null,
    val txnId: String? = null,
    val createdAt: Long,
    val deliveredAt: Long? = null,
    val readAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING,
)

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["threadId", "createdAt"]), Index("status"), Index("dueDate")]
)
data class TransactionEntity(
    @PrimaryKey val txnId: String,
    val threadId: String,
    override val createdBy: String,
    override val direction: TxnDirection,
    override val amountMinor: Long,
    val note: String? = null,
    val photoUrl: String? = null,
    val photoPublicId: String? = null,
    val localPhotoPath: String? = null,
    val dueDate: Long? = null,
    override val status: TxnStatus,
    override val unconfirmed: Boolean = false,
    val reversesId: String? = null,
    val reversedBy: String? = null,
    val createdAt: Long,
    val resolvedAt: Long? = null,
    val resolvedBy: String? = null,
    val syncState: SyncState = SyncState.PENDING,
) : LedgerEntry

/**
 * A number from the device address book, together with what we know about
 * whether it belongs to a PayChat account.
 *
 * Only the phone number is ever sent anywhere, and only to read `phoneIndex`.
 * The address book itself is never uploaded.
 */
@Entity(
    tableName = "device_contacts",
    indices = [Index(value = ["phone"], unique = true), Index("linkedUid")]
)
data class DeviceContactEntity(
    @PrimaryKey val phone: String,
    val displayName: String,
    /** The PayChat account using this number, when there is one. */
    val linkedUid: String? = null,
    val resolvedAt: Long = 0L,
)

@Entity(
    tableName = "local_contacts",
    indices = [Index(value = ["phone"], unique = true)]
)
data class LocalContactEntity(
    @PrimaryKey val contactId: String,
    val name: String,
    val phone: String,
    val threadId: String,
    val linkedUid: String? = null,
    val createdAt: Long,
)
