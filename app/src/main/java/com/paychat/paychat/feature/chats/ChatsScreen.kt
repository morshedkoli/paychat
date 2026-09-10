package com.paychat.paychat.feature.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paychat.paychat.ui.components.Avatar
import com.paychat.paychat.ui.components.EmptyState
import com.paychat.paychat.ui.components.Timestamps
import com.paychat.paychat.ui.theme.PayChatTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    onOpenThread: (String) -> Unit,
    onNewChat: () -> Unit,
    onSearch: () -> Unit,
    onReviewInherited: (String) -> Unit,
    viewModel: ChatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.errorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PayChat") },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewChat) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Start a chat")
            }
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {

            if (state.inheritedCount > 0) {
                InheritedBanner(
                    count = state.inheritedCount,
                    threadCount = state.inheritedThreadIds.size,
                    onReview = {
                        state.inheritedThreadIds.firstOrNull()?.let(onReviewInherited)
                    },
                )
            }

            HorizontalDivider()

            if (state.threads.isEmpty() && !state.loading) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    title = "No conversations yet",
                    body = "Start one with someone in your contacts, or add a " +
                        "number by hand and record money against it.",
                    actionLabel = "Start a chat",
                    onAction = onNewChat,
                )
            }

            LazyColumn(Modifier.fillMaxSize()) {
                items(state.threads, key = { it.threadId }) { thread ->
                    ThreadListItem(thread = thread, onClick = { onOpenThread(thread.threadId) })
                }
            }
        }
    }
}

@Composable
private fun ThreadListItem(thread: ThreadRow, onClick: () -> Unit) {
    val ledger = PayChatTheme.ledger

    ListItem(
        headlineContent = {
            Text(
                thread.name,
                fontWeight = if (thread.unreadCount > 0) FontWeight.SemiBold else null,
            )
        },
        supportingContent = {
            Text(
                text = when {
                    thread.awaitingConfirmation -> "Waiting for them to confirm your records"
                    thread.lastMessage.isNotBlank() -> thread.lastMessage
                    thread.isLocal -> "Not on PayChat yet"
                    else -> "No messages yet"
                },
                maxLines = 1,
            )
        },
        leadingContent = {
            Avatar(name = thread.name, key = thread.phone, photoUrl = thread.photoUrl)
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Timestamps.forThreadList(thread.lastMessageAt),
                    style = MaterialTheme.typography.labelSmall,
                )
                if (!thread.balance.isZero) {
                    Text(
                        text = thread.balance.formatSigned(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (thread.balance.isPositive) ledger.credit else ledger.debit,
                    )
                }
                if (thread.unreadCount > 0) {
                    Badge { Text(thread.unreadCount.toString()) }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * Money someone recorded against this user's number before they registered.
 * It counts for nobody until reviewed, so the banner stays until it is.
 */
@Composable
private fun InheritedBanner(count: Int, threadCount: Int = 1, onReview: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (count == 1) {
                        "1 entry was recorded before you joined"
                    } else if (threadCount > 1) {
                        "$count entries across $threadCount chats were recorded before you joined"
                    } else {
                        "$count entries were recorded before you joined"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    "They do not count until you review them.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            TextButton(onClick = onReview) { Text("Review") }
        }
    }
}
