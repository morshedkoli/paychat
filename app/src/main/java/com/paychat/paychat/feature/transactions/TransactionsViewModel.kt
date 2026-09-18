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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionsUiState(
    val summary: BalanceSummary = BalanceSummary.EMPTY,
    /** How many conversations still have money in them. */
    val people: Int = 0,
    val days: List<FeedDay> = emptyList(),
    val filter: FeedFilter = FeedFilter.ALL,
    val analytics: SpendingAnalytics = SpendingAnalytics.EMPTY,
    val showAnalytics: Boolean = false,
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
    private val preferences: com.paychat.paychat.data.settings.AppPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(TransactionsUiState())
    val state: StateFlow<TransactionsUiState> = _state.asStateFlow()

    private val limit = MutableStateFlow(PAGE)
    private val filter = MutableStateFlow(FeedFilter.ALL)
    private val showAnalytics = MutableStateFlow(false)

    /**
     * True once the table has handed back fewer rows than it was asked for,
     * which means there is nothing further to read. The feed's load-more
     * trigger keys on the filtered row count, so with a narrow filter it can
     * sit on screen and fire repeatedly; this latch stops that from walking
     * the limit up through the whole table.
     */
    private var exhausted = false

    init {
        val viewerUid = transactions.viewerUid()

        viewModelScope.launch {
            combine(
                limit.flatMapLatest { asked -> query.observeRecent(asked).map { it to asked } },
                threads.observeThreads(),
                transactions.observeBalances(),
                filter,
                showAnalytics,
            ) { (txns, asked), threadRows, balances, chosen, analyticsVisible ->
                exhausted = txns.size < asked

                // No session means nothing to attribute rows to, so show none
                // rather than guess a side.
                val rows = if (viewerUid == null) emptyList()
                else TransactionFeed.rows(txns, threadRows, viewerUid)

                // Only balances with a thread the user can actually open count:
                // on a fresh install the balance cache can land before the
                // threads do, and a total the user cannot drill into is worse
                // than a total that fills in a moment later.
                val balanceByThread = balances.associate { it.threadId to Money(it.amountMinor) }
                val balanceAmounts = threadRows.map { balanceByThread[it.threadId] ?: Money.ZERO }
                TransactionsUiState(
                    summary = BalanceCalculator.summarise(balanceAmounts),
                    people = balanceAmounts.count { !it.isZero },
                    days = TransactionFeed.group(TransactionFeed.filter(rows, chosen)),
                    filter = chosen,
                    analytics = SpendingAnalytics.compute(rows),
                    showAnalytics = analyticsVisible,
                    loading = false,
                )
            }.collect { next -> _state.update { next } }
        }
    }

    fun setFilter(next: FeedFilter) {
        filter.value = next
    }

    fun toggleAnalytics() {
        showAnalytics.update { !it }
    }

    fun toggleHideBalances(current: Boolean) {
        viewModelScope.launch { preferences.setHideBalances(!current) }
    }

    /** Reads one page further. Harmless to call at the end of the list. */
    fun loadMore() {
        if (exhausted) return
        limit.update { it + PAGE }
    }
}

