package com.paychat.paychat.feature.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paychat.paychat.core.money.Money
import com.paychat.paychat.data.chat.ThreadsRepository
import com.paychat.paychat.data.local.entity.ThreadEntity
import com.paychat.paychat.data.transactions.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ThreadRow(
    val threadId: String,
    val name: String,
    val phone: String,
    val photoUrl: String?,
    val lastMessage: String,
    val lastMessageAt: Long,
    val unreadCount: Int,
    val balance: Money,
    val isLocal: Boolean,
    /** This user recorded history the other person has not confirmed yet. */
    val awaitingConfirmation: Boolean = false,
)

data class ChatsUiState(
    val threads: List<ThreadRow> = emptyList(),
    /**
     * Conversations holding money someone recorded against this user's number
     * before they registered, waiting to be reviewed.
     */
    val inheritedThreadIds: List<String> = emptyList(),
    val inheritedCount: Int = 0,
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val threads: ThreadsRepository,
    private val transactions: TransactionRepository,
    private val preferences: com.paychat.paychat.data.settings.AppPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatsUiState())
    val state: StateFlow<ChatsUiState> = _state.asStateFlow()

    fun toggleHideBalances(current: Boolean) {
        viewModelScope.launch { preferences.setHideBalances(!current) }
    }

    init {
        // Room drives the screen, so it renders immediately from cache and
        // updates when the listeners bring something new.
        viewModelScope.launch {
            combine(
                threads.observeThreads(),
                threads.observeUnreadCounts(),
                transactions.observeBalances(),
                transactions.observeAwaitingConfirmation(),
            ) { rows, unread, balances, awaiting ->
                val unreadByThread = unread.associate { it.threadId to it.count }
                val balanceByThread = balances.associate { it.threadId to Money(it.amountMinor) }
                val awaitingThreads = awaiting.map { it.threadId }.toSet()
                rows.map {
                    it.toRow(
                        unread = unreadByThread[it.threadId] ?: 0,
                        balance = balanceByThread[it.threadId] ?: Money.ZERO,
                        // Only meaningful once the other person has an account;
                        // before that there is nobody who could confirm.
                        awaitingConfirmation = !it.isLocal && it.threadId in awaitingThreads,
                    )
                }
            }.collect { rows ->
                _state.update {
                    it.copy(
                        threads = rows,
                        loading = false,
                    )
                }
            }
        }

        viewModelScope.launch {
            transactions.observeInherited().collect { inherited ->
                _state.update {
                    it.copy(
                        inheritedThreadIds = inherited.map { row -> row.threadId }.distinct(),
                        inheritedCount = inherited.size,
                    )
                }
            }
        }

        viewModelScope.launch {
            threads.syncThreads().collect { threads.persist(it) }
        }

        // A decision taken by the other person while this device was not
        // running arrives as a push, and a push can be missed — the app was
        // force stopped, or notifications are off. Anything still shown as
        // pending is therefore re-read once when the list opens.
        viewModelScope.launch { transactions.refreshPending() }

        // Without this a fresh install would show zero everywhere until each
        // conversation had been opened and its transactions downloaded.
        viewModelScope.launch {
            transactions.syncBalances().collect { transactions.persistBalances(it) }
        }
    }

    fun errorShown() = _state.update { it.copy(error = null) }
}

private fun ThreadEntity.toRow(
    unread: Int,
    balance: Money,
    awaitingConfirmation: Boolean,
) = ThreadRow(
    threadId = threadId,
    name = peerName.ifBlank { peerPhone },
    phone = peerPhone,
    photoUrl = peerPhotoUrl,
    lastMessage = lastMessageText.orEmpty(),
    lastMessageAt = lastMessageAt,
    unreadCount = unread,
    balance = balance,
    isLocal = isLocal,
    awaitingConfirmation = awaitingConfirmation,
)
