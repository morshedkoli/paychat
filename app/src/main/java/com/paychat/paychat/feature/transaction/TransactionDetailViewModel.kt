package com.paychat.paychat.feature.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paychat.paychat.core.errors.userMessage
import com.paychat.paychat.core.ledger.BalanceCalculator
import com.paychat.paychat.core.ledger.TransactionRules
import com.paychat.paychat.data.auth.AuthRepository
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

data class TransactionDetailUiState(
    val viewerUid: String = "",
    val transaction: TransactionEntity? = null,
    val working: Boolean = false,
    val error: String? = null,
    val closed: Boolean = false,
) {
    val viewerIsPayer: Boolean
        get() = transaction?.let { BalanceCalculator.viewerIsPayer(it, viewerUid) } ?: false

    val canAccept: Boolean
        get() = transaction?.let {
            TransactionRules.canAccept(it.status, it.createdBy, viewerUid)
        } ?: false

    val canCancel: Boolean
        get() = transaction?.let {
            TransactionRules.canCancel(it.status, it.createdBy, viewerUid)
        } ?: false

    val canReverse: Boolean
        get() = transaction?.let {
            TransactionRules.canReverse(it.status, it.reversedBy != null)
        } ?: false
}

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactions: TransactionRepository,
    auth: AuthRepository,
) : ViewModel() {

    private val txnId: String = savedStateHandle.get<String>(NavArgs.TXN_ID).orEmpty()

    private val _state = MutableStateFlow(
        TransactionDetailUiState(viewerUid = auth.currentUid.orEmpty())
    )
    val state: StateFlow<TransactionDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            transactions.observe(txnId).collect { transaction ->
                _state.update { it.copy(transaction = transaction) }
            }
        }
    }

    fun accept() = act { transactions.accept(txnId) }
    fun reject() = act { transactions.reject(txnId) }
    fun cancel() = act { transactions.cancel(txnId) }

    /** Corrects an accepted transaction with an opposite entry. */
    fun reverse(note: String?) = act { transactions.reverse(txnId, note).map { } }

    private fun act(block: suspend () -> Result<Unit>) {
        if (_state.value.working) return
        viewModelScope.launch {
            _state.update { it.copy(working = true, error = null) }
            block().fold(
                onSuccess = { _state.update { it.copy(working = false) } },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            working = false,
                            error = error.userMessage("That did not work."),
                        )
                    }
                },
            )
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
