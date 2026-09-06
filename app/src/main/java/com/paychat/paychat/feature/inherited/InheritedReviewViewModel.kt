package com.paychat.paychat.feature.inherited

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paychat.paychat.core.ledger.BalanceCalculator
import com.paychat.paychat.core.money.Money
import com.paychat.paychat.data.auth.AuthRepository
import com.paychat.paychat.data.chat.ChatRepository
import com.paychat.paychat.data.local.entity.TransactionEntity
import com.paychat.paychat.data.transactions.TransactionRepository
import com.paychat.paychat.ui.nav.NavArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InheritedItem(
    val transaction: TransactionEntity,
    val viewerIsPayer: Boolean,
) {
    val amount: Money get() = Money(transaction.amountMinor)
}

data class InheritedReviewUiState(
    val threadId: String = "",
    val peerName: String = "",
    val items: List<InheritedItem> = emptyList(),
    val working: Boolean = false,
    val error: String? = null,
    val loading: Boolean = true,
) {
    /** What accepting everything would do to this conversation's balance. */
    val netIfAccepted: Money
        get() = items.fold(Money.ZERO) { total, item ->
            total + if (item.viewerIsPayer) item.amount else -item.amount
        }

    val done: Boolean get() = !loading && items.isEmpty()
}

/**
 * The screen a newly registered user sees for money someone recorded against
 * their number before they joined.
 */
@HiltViewModel
class InheritedReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val chat: ChatRepository,
    auth: AuthRepository,
) : ViewModel() {

    private val threadId: String = savedStateHandle.get<String>(NavArgs.THREAD_ID).orEmpty()
    private val viewerUid: String = auth.currentUid.orEmpty()

    private val _state = MutableStateFlow(InheritedReviewUiState(threadId = threadId))
    val state: StateFlow<InheritedReviewUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            chat.observeThread(threadId).collect { thread ->
                _state.update {
                    it.copy(peerName = thread?.peerName?.ifBlank { thread.peerPhone }.orEmpty())
                }
            }
        }

        viewModelScope.launch {
            transactions.observeInherited().collect { all ->
                val mine = all.filter { it.threadId == threadId }
                _state.update { current ->
                    current.copy(
                        items = mine.map {
                            InheritedItem(
                                transaction = it,
                                viewerIsPayer = BalanceCalculator.viewerIsPayer(it, viewerUid),
                            )
                        },
                        loading = false,
                    )
                }
            }
        }

        // The entries live on the server; without this the screen would be
        // empty until the conversation had been opened.
        viewModelScope.launch {
            transactions.syncTransactions(threadId).collect {
                transactions.persist(threadId, it)
            }
        }
    }

    fun accept(txnId: String) = act { transactions.reviewInherited(txnId, accepted = true) }
    fun reject(txnId: String) = act { transactions.reviewInherited(txnId, accepted = false) }
    fun acceptAll() = act { transactions.acceptAllInherited(threadId).map { } }

    private fun act(block: suspend () -> Result<Unit>) {
        if (_state.value.working) return
        viewModelScope.launch {
            _state.update { it.copy(working = true, error = null) }
            block().fold(
                onSuccess = { _state.update { it.copy(working = false) } },
                onFailure = { error ->
                    _state.update {
                        it.copy(working = false, error = error.message ?: "That did not work.")
                    }
                },
            )
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
