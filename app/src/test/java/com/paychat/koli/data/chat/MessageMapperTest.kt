package com.paychat.koli.data.chat

import com.paychat.koli.core.model.MessageType
import com.paychat.koli.core.model.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private const val ME = "uidMe"
private const val THEM = "uidThem"
private const val NOW = 1_700_000_000_000L

private fun remote(
    senderId: String,
    deliveredTo: List<String> = emptyList(),
    readBy: List<String> = emptyList(),
    type: String? = "TEXT",
) = MessageMapper.Remote(
    messageId = "m1",
    threadId = "t1",
    senderId = senderId,
    type = type,
    text = "hello",
    mediaUrl = null,
    mediaPublicId = null,
    durationMs = null,
    txnId = null,
    createdAt = NOW,
    deliveredTo = deliveredTo,
    readBy = readBy,
)

class MessageMapperTest {

    @Test
    fun `on a sent message the receipts describe the other person`() {
        val entity = MessageMapper.toEntity(
            remote(senderId = ME, deliveredTo = listOf(ME, THEM), readBy = listOf(ME, THEM)),
            viewerUid = ME,
            now = NOW,
        )
        assertNotNull(entity.deliveredAt)
        assertNotNull(entity.readAt)
    }

    @Test
    fun `the sender being in the lists does not count as delivered or read`() {
        // Every message lists its own sender, so reading those as receipts
        // would show every message as read the moment it was sent.
        val entity = MessageMapper.toEntity(
            remote(senderId = ME, deliveredTo = listOf(ME), readBy = listOf(ME)),
            viewerUid = ME,
            now = NOW,
        )
        assertNull(entity.deliveredAt)
        assertNull(entity.readAt)
    }

    @Test
    fun `delivered without read is reported as delivered only`() {
        val entity = MessageMapper.toEntity(
            remote(senderId = ME, deliveredTo = listOf(ME, THEM), readBy = listOf(ME)),
            viewerUid = ME,
            now = NOW,
        )
        assertNotNull(entity.deliveredAt)
        assertNull(entity.readAt)
    }

    @Test
    fun `on a received message readAt means the viewer read it`() {
        val unread = MessageMapper.toEntity(
            remote(senderId = THEM, readBy = listOf(THEM)),
            viewerUid = ME,
            now = NOW,
        )
        assertNull(unread.readAt)

        val read = MessageMapper.toEntity(
            remote(senderId = THEM, readBy = listOf(THEM, ME)),
            viewerUid = ME,
            now = NOW,
        )
        assertNotNull(read.readAt)
    }

    @Test
    fun `an unknown message type falls back to text rather than crashing`() {
        val entity = MessageMapper.toEntity(
            remote(senderId = ME, type = "SOMETHING_NEWER"),
            viewerUid = ME,
        )
        assertEquals(MessageType.TEXT, entity.type)
    }

    @Test
    fun `anything that came from the server is already synced`() {
        val entity = MessageMapper.toEntity(remote(senderId = ME), viewerUid = ME)
        assertEquals(SyncState.SYNCED, entity.syncState)
    }

    @Test
    fun `previews describe attachments instead of showing empty text`() {
        assertEquals("hi", MessageMapper.preview(MessageType.TEXT, "hi"))
        assertEquals("Photo", MessageMapper.preview(MessageType.IMAGE, null))
        assertEquals("Voice message", MessageMapper.preview(MessageType.VOICE, null))
        assertEquals("Transaction", MessageMapper.preview(MessageType.TXN, null))
    }
}
