package com.paychat.paychat.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paychat.paychat.core.ledger.BalanceCalculator
import com.paychat.paychat.core.ledger.BalanceSummary
import com.paychat.paychat.core.money.Money
import com.paychat.paychat.data.chat.ThreadsRepository
import com.paychat.paychat.data.transactions.TransactionQuery
import com.paychat.paychat.data.transactions.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionsUiState(
    val summary: BalanceSummary = BalanceSummary.EMPTY,
    /** How many conversations still have money in them. */
    val people: Int = 0,
    val days: List<FeedDay> = emptyList(),
    val filter: FeedFilter = FeedFilter.ALL,
    val loading: Boolean = true,
)

/** How many rows the feed reads, and how much further each request goes. */
private const val PAGE = 100

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val query: TransactionQuery,
    private val transactions: TransactionRepository,
    private val threads: ThreadsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TransactionsUiState())
    val state: StateFlow<TransactionsUiState> = _state.asStateFlow()

    private val limit = MutableStateFlow(PAGE)
    private val filter = MutableStateFlow(FeedFilter.ALL)

    init {
        val viewerUid = transactions.viewerUid()

        viewModelScope.launch {
            combine(
                limit.flatMapLatest { query.observeRecent(it) },
                threads.observeThreads(),
                transactions.observeBalances(),
                filter,
            ) { txns, threadRows, balances, chosen ->
                // No session means nothing to attribute rows to, so show none
                // rather than guess a side.
                val rows = if (viewerUid == null) emptyList()
                else TransactionFeed.rows(txns, threadRows, viewerUid)

                val balanceAmounts = balances.map { Money(it.amountMinor) }
                TransactionsUiState(
                    summary = BalanceCalculator.summarise(balanceAmounts),
                    people = balanceAmounts.count { !it.isZero },
                    days = TransactionFeed.group(TransactionFeed.filter(rows, chosen)),
                    filter = chosen,
                    loading = false,
                )
            }.collect { next -> _state.update { next } }
        }
    }

    fun setFilter(next: FeedFilter) {
        filter.value = next
    }

    /** Reads one page further. Harmless to call at the end of the list. */
    fun loadMore() {
        limit.update { it + PAGE }
    }
}
