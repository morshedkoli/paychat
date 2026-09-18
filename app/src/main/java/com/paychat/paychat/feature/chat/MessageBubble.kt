package com.paychat.paychat.feature.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import coil.compose.AsyncImage
import com.paychat.paychat.core.model.MessageType
import com.paychat.paychat.core.model.SyncState
import com.paychat.paychat.data.local.entity.MessageEntity
import com.paychat.paychat.ui.components.Timestamps
import com.paychat.paychat.ui.components.ZoomableImageViewer
import com.paychat.paychat.ui.theme.PayChatTheme
import com.paychat.paychat.ui.theme.WhatsAppBlueTicks
import java.io.File

/**
 * WhatsApp-style authentic message bubble.
 */
@Composable
fun MessageBubble(
    message: MessageEntity,
    viewerUid: String,
    playback: VoicePlaybackState,
    onReply: (QuotedMessage) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val outgoing = message.senderId == viewerUid
    val ledger = PayChatTheme.ledger

    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (outgoing) 16.dp else 3.dp,
        bottomEnd = if (outgoing) 3.dp else 16.dp,
    )

    val bubbleBg = if (outgoing) ledger.outgoingBubble else ledger.incomingBubble
    val textColor = MaterialTheme.colorScheme.onSurface

    var dragOffset by remember { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(targetValue = dragOffset, label = "swipeToReply")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragOffset > 75f) {
                            val author = if (outgoing) "You" else "Them"
                            val snippet = when (message.type) {
                                MessageType.IMAGE -> "Photo"
                                MessageType.VOICE -> "Voice note"
                                else -> QuotedMessageCodec.decode(message.text).body
                            }
                            onReply(QuotedMessage(author, snippet))
                        }
                        dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        dragOffset = (dragOffset + dragAmount).coerceIn(0f, 110f)
                    }
                )
            }
    ) {
        if (animatedOffset > 20f) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Reply,
                contentDescription = "Reply",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 14.dp)
                    .size(22.dp),
            )
        }

        Row(
            modifier = Modifier
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .fillMaxWidth()
                .padding(
                    start = if (outgoing) 48.dp else 8.dp,
                    end = if (outgoing) 8.dp else 48.dp,
                    top = 2.dp,
                    bottom = 2.dp,
                ),
            horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 310.dp)
                    .shadow(elevation = 0.5.dp, shape = bubbleShape)
                    .clip(bubbleShape)
                    .background(bubbleBg)
                    .padding(
                        horizontal = if (message.type == MessageType.IMAGE) 4.dp else 12.dp,
                        vertical = if (message.type == MessageType.IMAGE) 4.dp else 7.dp,
                    ),
            ) {
                when (message.type) {
                    MessageType.IMAGE -> ImageContent(message)
                    MessageType.VOICE -> VoiceContent(message, playback, outgoing)
                    else -> {
                        val parsed = remember(message.text) { QuotedMessageCodec.decode(message.text) }
                        if (parsed.quote != null) {
                            QuotedBubblePreview(parsed.quote)
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(
                            text = parsed.body,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.sp,
                                lineHeight = 20.sp,
                            ),
                            color = textColor,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(
                            top = if (message.type == MessageType.IMAGE) 4.dp else 2.dp,
                            start = 12.dp,
                            end = 2.dp,
                            bottom = 1.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = Timestamps.forMessage(message.createdAt),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    )
                    if (outgoing) DeliveryTick(message)
                }
            }
        }
    }
}

@Composable
fun QuotedBubblePreview(quote: QuotedMessage) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Column {
                Text(
                    text = quote.authorName,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize = 11.sp,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Text(
                    text = quote.textSnippet,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ImageContent(message: MessageEntity) {
    val source = message.mediaUrl ?: message.localMediaPath?.let { File(it) }
    var showViewer by remember { mutableStateOf(false) }

    if (showViewer && source != null) {
        ZoomableImageViewer(
            model = source,
            onDismiss = { showViewer = false },
        )
    }

    Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { showViewer = true }) {
        AsyncImage(
            model = source,
            contentDescription = "Photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .height(230.dp),
        )
        if (message.syncState != SyncState.SYNCED) {
            LinearProgressIndicator(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun VoiceContent(message: MessageEntity, playback: VoicePlaybackState, outgoing: Boolean) {
    val playingId by playback.playingId
    val playing = playingId == message.messageId
    val source = message.mediaUrl ?: message.localMediaPath

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.width(210.dp).padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
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
                modifier = Modifier.size(22.dp),
            )
        }

        Column(Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { if (playing) 1f else 0f },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatDuration(message.durationMs ?: 0L),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * WhatsApp signature delivery ticks (single grey, double grey, double blue).
 */
@Composable
private fun DeliveryTick(message: MessageEntity) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)

    val (icon, tint, description) = when {
        message.syncState == SyncState.FAILED ->
            Triple(Icons.Default.ErrorOutline, MaterialTheme.colorScheme.error, "Not sent")

        message.syncState != SyncState.SYNCED ->
            Triple(Icons.Default.Schedule, muted, "Sending")

        message.readAt != null ->
            Triple(Icons.Default.DoneAll, WhatsAppBlueTicks, "Read")

        message.deliveredAt != null ->
            Triple(Icons.Default.DoneAll, muted, "Delivered")

        else -> Triple(Icons.Default.Check, muted, "Sent")
    }

    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = Modifier.size(15.dp),
    )
}
