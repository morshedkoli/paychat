package com.paychat.koli.data.chat

import com.paychat.koli.core.model.MessageType
import com.paychat.koli.core.model.SyncState
import com.paychat.koli.data.local.entity.MessageEntity

/**
 * Converts a message document into the row the UI reads.
 *
 * The two receipt columns mean different things depending on who sent the
 * message, and getting that backwards is the classic chat bug, so the rule
 * lives here rather than being repeated at each call site:
 *
 * - on a message the user sent, they record what the *other* person has done
 * - on a message the user received, [MessageEntity.readAt] records when the
 *   user themselves read it, which is what the unread count counts
 */
object MessageMapper {

    data class Remote(
        val messageId: String,
        val threadId: String,
        val senderId: String,
        val type: String?,
        val text: String?,
        val mediaUrl: String?,
        val mediaPublicId: String?,
        val durationMs: Long?,
        val txnId: String?,
        val createdAt: Long,
        val deliveredTo: List<String>,
        val readBy: List<String>,
    )

    fun toEntity(remote: Remote, viewerUid: String, now: Long = System.currentTimeMillis()): MessageEntity {
        val outgoing = remote.senderId == viewerUid
        val others = { list: List<String> -> list.any { it != viewerUid } }

        return MessageEntity(
            messageId = remote.messageId,
            threadId = remote.threadId,
            senderId = remote.senderId,
            type = remote.type?.let { runCatching { MessageType.valueOf(it) }.getOrNull() }
                ?: MessageType.TEXT,
            text = remote.text,
            mediaUrl = remote.mediaUrl,
            mediaPublicId = remote.mediaPublicId,
            durationMs = remote.durationMs,
            txnId = remote.txnId,
            createdAt = remote.createdAt,
            deliveredAt = if (outgoing && others(remote.deliveredTo)) now else null,
            readAt = when {
                outgoing -> if (others(remote.readBy)) now else null
                else -> if (remote.readBy.contains(viewerUid)) now else null
            },
            syncState = SyncState.SYNCED,
        )
    }

    /** The one-line preview shown in the thread list. */
    fun preview(type: MessageType, text: String?): String = when (type) {
        MessageType.TEXT -> text.orEmpty()
        MessageType.IMAGE -> "Photo"
        MessageType.VOICE -> "Voice message"
        MessageType.TXN -> "Transaction"
        MessageType.SYSTEM -> text.orEmpty()
    }
}
