package com.paychat.koli.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paychat.koli.core.ledger.BalanceCalculator
import com.paychat.koli.core.ledger.BalanceSummary
import com.paychat.koli.core.money.Money
import com.paychat.koli.data.chat.ThreadsRepository
import com.paychat.koli.data.local.entity.ThreadEntity
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
)

data class HomeUiState(
    val summary: BalanceSummary = BalanceSummary.EMPTY,
    val threads: List<ThreadRow> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val threads: ThreadsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        // Room drives the screen, so it renders immediately from cache and
        // updates when the listener brings something new.
        viewModelScope.launch {
            combine(
                threads.observeThreads(),
                threads.observeUnreadCounts(),
            ) { rows, unread ->
                val unreadByThread = unread.associate { it.threadId to it.count }
                rows.map { it.toRow(unreadByThread[it.threadId] ?: 0) }
            }.collect { rows ->
                _state.update {
                    it.copy(
                        threads = rows,
                        summary = BalanceCalculator.summarise(rows.map { row -> row.balance }),
                        loading = false,
                    )
                }
            }
        }

        viewModelScope.launch {
            threads.syncThreads().collect { threads.persist(it) }
        }
    }
}

private fun ThreadEntity.toRow(unread: Int) = ThreadRow(
    threadId = threadId,
    name = peerName.ifBlank { peerPhone },
    phone = peerPhone,
    photoUrl = peerPhotoUrl,
    lastMessage = lastMessageText.orEmpty(),
    lastMessageAt = lastMessageAt,
    unreadCount = unread,
    balance = Money(balanceMinor),
    isLocal = isLocal,
)
