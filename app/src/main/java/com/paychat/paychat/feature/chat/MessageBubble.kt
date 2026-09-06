package com.paychat.paychat.feature.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.paychat.paychat.core.model.MessageType
import com.paychat.paychat.core.model.SyncState
import com.paychat.paychat.data.local.entity.MessageEntity
import com.paychat.paychat.ui.components.Timestamps
import com.paychat.paychat.ui.theme.PayChatTheme
import java.io.File

@Composable
fun MessageBubble(
    message: MessageEntity,
    viewerUid: String,
    playback: VoicePlaybackState,
    modifier: Modifier = Modifier,
) {
    val outgoing = message.senderId == viewerUid
    val ledger = PayChatTheme.ledger

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (outgoing) 16.dp else 4.dp,
                        bottomEnd = if (outgoing) 4.dp else 16.dp,
                    )
                )
                .background(if (outgoing) ledger.outgoingBubble else ledger.incomingBubble)
                .padding(
                    horizontal = if (message.type == MessageType.IMAGE) 4.dp else 12.dp,
                    vertical = if (message.type == MessageType.IMAGE) 4.dp else 8.dp,
                ),
        ) {
            when (message.type) {
                MessageType.IMAGE -> ImageContent(message)
                MessageType.VOICE -> VoiceContent(message, playback)
                else -> Text(
                    text = message.text.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Row(
                modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = Timestamps.forMessage(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (outgoing) DeliveryTick(message)
            }
        }
    }
}

/**
 * Shows the local copy until the upload finishes, so a photo appears the
 * instant it is picked rather than after a round trip.
 */
@Composable
private fun ImageContent(message: MessageEntity) {
    val source = message.mediaUrl ?: message.localMediaPath?.let { File(it) }

    Box {
        AsyncImage(
            model = source,
            contentDescription = "Photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .widthIn(max = 260.dp)
                .height(220.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        if (message.syncState != SyncState.SYNCED) {
            LinearProgressIndicator(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun VoiceContent(message: MessageEntity, playback: VoicePlaybackState) {
    val playingId by playback.playingId
    val playing = playingId == message.messageId
    val source = message.mediaUrl ?: message.localMediaPath

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.width(200.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(enabled = source != null) {
                    source?.let { playback.toggle(message.messageId, it) }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { if (playing) 1f else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = formatDuration(message.durationMs ?: 0L),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The state of an outgoing message: waiting to upload, sent, delivered, read,
 * or failed. Only shown on the user's own messages, since it describes what
 * happened to something they sent.
 */
@Composable
private fun DeliveryTick(message: MessageEntity) {
    val ledger = PayChatTheme.ledger
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val (icon, tint, description) = when {
        message.syncState == SyncState.FAILED ->
            Triple(Icons.Default.ErrorOutline, MaterialTheme.colorScheme.error, "Not sent")

        message.syncState != SyncState.SYNCED ->
            Triple(Icons.Default.Schedule, muted, "Sending")

        message.readAt != null ->
            Triple(Icons.Default.DoneAll, ledger.credit, "Read")

        message.deliveredAt != null ->
            Triple(Icons.Default.DoneAll, muted, "Delivered")

        else -> Triple(Icons.Default.Check, muted, "Sent")
    }

    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = Modifier.size(14.dp),
    )
}
