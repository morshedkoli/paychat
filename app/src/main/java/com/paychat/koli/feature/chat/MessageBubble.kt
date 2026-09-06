package com.paychat.koli.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.paychat.koli.core.model.SyncState
import com.paychat.koli.data.local.entity.MessageEntity
import com.paychat.koli.ui.components.Timestamps
import com.paychat.koli.ui.theme.PayChatTheme

@Composable
fun MessageBubble(
    message: MessageEntity,
    viewerUid: String,
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = message.text.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = Timestamps.forMessage(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (outgoing) {
                    DeliveryTick(message)
                }
            }
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

    Box {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
    }
}
