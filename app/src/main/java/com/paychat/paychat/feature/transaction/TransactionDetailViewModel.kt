package com.paychat.paychat.feature.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paychat.paychat.core.errors.userMessage
import com.paychat.paychat.core.ledger.BalanceCalculator
import com.paychat.paychat.core.ledger.TransactionRules
import com.paychat.paychat.core.model.TxnStatus
import com.paychat.paychat.data.auth.AuthRepository
import com.paychat.paychat.data.local.entity.TransactionEntity
import com.paychat.paychat.data.transactions.TransactionRepository
import com.paychat.paychat.ui.components.Timestamps
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

    val canReviewInherited: Boolean
        get() = transaction?.let {
            TransactionRules.canReviewInherited(it.unconfirmed, it.createdBy, viewerUid)
        } ?: false

    val canSendReminder: Boolean
        get() = transaction?.let {
            it.status == TxnStatus.ACCEPTED || (it.status == TxnStatus.PENDING && it.createdBy == viewerUid)
        } ?: false

    val showDecisionActions: Boolean
        get() = canAccept || canReviewInherited
}

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val chat: com.paychat.paychat.data.chat.ChatRepository,
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

    fun accept() = act {
        val txn = _state.value.transaction
        if (txn?.unconfirmed == true) {
            transactions.reviewInherited(txnId, accepted = true)
        } else {
            transactions.accept(txnId)
        }
    }

    fun reject() = act {
        val txn = _state.value.transaction
        if (txn?.unconfirmed == true) {
            transactions.reviewInherited(txnId, accepted = false)
        } else {
            transactions.reject(txnId)
        }
    }

    fun cancel() = act { transactions.cancel(txnId) }

    fun sendReminder(onResult: (String) -> Unit) {
        val txn = _state.value.transaction ?: return
        val amountStr = com.paychat.paychat.core.money.Money(txn.amountMinor).format()
        val noteText = txn.note?.takeIf { it.isNotBlank() }?.let { " for '$it'" }.orEmpty()
        val dueText = txn.dueDate?.let { " (Due: ${Timestamps.daySeparator(it)})" }.orEmpty()
        val reminderMsg = "Friendly reminder regarding: $amountStr$noteText$dueText. Please settle up when convenient! 😊"

        viewModelScope.launch {
            chat.sendText(txn.threadId, reminderMsg).fold(
                onSuccess = { onResult("Reminder sent in chat") },
                onFailure = { onResult("Failed to send reminder") },
            )
        }
    }

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
