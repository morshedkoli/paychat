package com.paychat.paychat.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paychat.paychat.core.ledger.BalanceCalculator
import com.paychat.paychat.core.ledger.BalanceSummary
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
)

data class HomeUiState(
    val summary: BalanceSummary = BalanceSummary.EMPTY,
    val threads: List<ThreadRow> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val threads: ThreadsRepository,
    private val transactions: TransactionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        // Room drives the screen, so it renders immediately from cache and
        // updates when the listeners bring something new.
        viewModelScope.launch {
            combine(
                threads.observeThreads(),
                threads.observeUnreadCounts(),
                transactions.observeBalances(),
            ) { rows, unread, balances ->
                val unreadByThread = unread.associate { it.threadId to it.count }
                val balanceByThread = balances.associate { it.threadId to Money(it.amountMinor) }
                rows.map {
                    it.toRow(
                        unread = unreadByThread[it.threadId] ?: 0,
                        balance = balanceByThread[it.threadId] ?: Money.ZERO,
                    )
                }
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

        // Without this a fresh install would show zero everywhere until each
        // conversation had been opened and its transactions downloaded.
        viewModelScope.launch {
            transactions.syncBalances().collect { transactions.persistBalances(it) }
        }
    }
}

private fun ThreadEntity.toRow(unread: Int, balance: Money) = ThreadRow(
    threadId = threadId,
    name = peerName.ifBlank { peerPhone },
    phone = peerPhone,
    photoUrl = peerPhotoUrl,
    lastMessage = lastMessageText.orEmpty(),
    lastMessageAt = lastMessageAt,
    unreadCount = unread,
    balance = balance,
    isLocal = isLocal,
)
