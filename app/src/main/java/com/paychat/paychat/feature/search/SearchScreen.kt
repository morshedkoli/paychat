package com.paychat.paychat.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paychat.paychat.data.search.MessageHit
import com.paychat.paychat.data.search.PersonHit
import com.paychat.paychat.data.search.TransactionHit
import com.paychat.paychat.ui.components.Avatar
import com.paychat.paychat.ui.components.EmptyState
import com.paychat.paychat.ui.components.Timestamps
import com.paychat.paychat.ui.theme.AmountStyle

/**
 * One box over everything the device holds: people, message text, and the
 * notes written on transactions.
 *
 * Every hit opens the conversation it belongs to. A transaction opens its own
 * detail, because that is where the amount can be acted on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenThread: (String) -> Unit,
    onOpenTransaction: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    // The screen exists to be typed into, so it opens with the caret waiting.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        placeholder = { Text("Search chats and transactions") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = viewModel::clear) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {

            // A thin bar rather than a spinner in the middle: results from the
            // previous keystroke stay readable while the next one runs.
            if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())

            when {
                state.nothingFound -> EmptyState(
                    icon = Icons.Default.SearchOff,
                    title = "Nothing matched",
                    body = "No person, message or note contains " +
                        "\"" + state.results.query + "\".",
                )

                state.query.isBlank() -> EmptyState(
                    icon = Icons.Default.Search,
                    title = "Search everything on this device",
                    body = "Names and numbers, the words in a message, and the " +
                        "note written on a transaction.",
                )
            }

            LazyColumn(Modifier.fillMaxSize()) {
                val results = state.results

                if (results.people.isNotEmpty()) {
                    item { SectionHeading("People") }
                    items(results.people, key = { it.threadId }) { hit ->
                        PersonRow(hit) { onOpenThread(hit.threadId) }
                    }
                }

                if (results.transactions.isNotEmpty()) {
                    item { SectionHeading("Transactions") }
                    items(results.transactions, key = { it.txnId }) { hit ->
                        TransactionRow(hit) { onOpenTransaction(hit.txnId) }
                    }
                }

                if (results.messages.isNotEmpty()) {
                    item { SectionHeading("Messages") }
                    items(results.messages, key = { it.messageId }) { hit ->
                        MessageRow(hit) { onOpenThread(hit.threadId) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Column {
        HorizontalDivider()
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PersonRow(hit: PersonHit, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Avatar(name = hit.name, key = hit.threadId, photoUrl = hit.photoUrl) },
        headlineContent = { Text(hit.name, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(hit.phone) },
    )
}

@Composable
private fun TransactionRow(hit: TransactionHit, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(hit.note.orEmpty().ifBlank { "No note" }) },
        supportingContent = { Text("${hit.counterparty} · ${Timestamps.forThreadList(hit.at)}") },
        trailingContent = { Text(hit.amount.format(), style = AmountStyle) },
    )
}

@Composable
private fun MessageRow(hit: MessageHit, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(hit.text, maxLines = 2) },
        supportingContent = { Text(hit.counterparty) },
        trailingContent = { Text(Timestamps.forThreadList(hit.at)) },
    )
}
