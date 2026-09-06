package com.paychat.paychat.feature.ledger

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paychat.paychat.core.ledger.LedgerStatement
import com.paychat.paychat.core.ledger.StatementLine
import com.paychat.paychat.core.model.TxnStatus
import com.paychat.paychat.core.money.Money
import com.paychat.paychat.data.auth.AuthRepository
import com.paychat.paychat.data.chat.ChatRepository
import com.paychat.paychat.data.export.StatementExporter
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

data class LedgerUiState(
    val threadId: String = "",
    val viewerUid: String = "",
    val peerName: String = "",
    val closingBalance: Money = Money.ZERO,
    /** Newest first, which is how the screen reads. */
    val lines: List<StatementLine<TransactionEntity>> = emptyList(),
    val showSettled: Boolean = true,
    /** How many entries the filter is hiding, so nothing vanishes silently. */
    val hiddenCount: Int = 0,
    val loading: Boolean = true,
    val exporting: Boolean = false,
    /** Set once a statement is written, and cleared when it has been handed on. */
    val statement: Uri? = null,
    val error: String? = null,
)

@HiltViewModel
class LedgerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val chat: ChatRepository,
    private val exporter: StatementExporter,
    auth: AuthRepository,
) : ViewModel() {

    private val threadId: String = savedStateHandle.get<String>(NavArgs.THREAD_ID).orEmpty()
    private val viewerUid: String = auth.currentUid.orEmpty()

    /** Everything recorded, oldest first. The filter only changes what is shown. */
    private var allRows: List<TransactionEntity> = emptyList()

    private val _state = MutableStateFlow(
        LedgerUiState(threadId = threadId, viewerUid = viewerUid)
    )
    val state: StateFlow<LedgerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            chat.observeThread(threadId).collect { thread ->
                _state.update {
                    it.copy(peerName = thread?.peerName?.ifBlank { thread.peerPhone }.orEmpty())
                }
            }
        }

        viewModelScope.launch {
            transactions.observeThread(threadId).collect { rows ->
                allRows = rows
                rebuild()
            }
        }

        // The ledger can be opened without going through the chat, so it syncs
        // for itself rather than relying on that having happened.
        viewModelScope.launch {
            transactions.syncTransactions(threadId).collect {
                transactions.persist(threadId, it)
            }
        }
    }

    /**
     * Writes this conversation's statement and hands back a shareable file.
     *
     * The work is a database read and some drawing, so it is quick, but it is
     * still reported as in progress: a button that does nothing visible for a
     * moment reads as broken.
     */
    fun exportStatement() {
        if (_state.value.exporting) return
        _state.update { it.copy(exporting = true, error = null) }

        viewModelScope.launch {
            exporter.exportThread(threadId)
                .onSuccess { uri -> _state.update { it.copy(exporting = false, statement = uri) } }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            exporting = false,
                            error = error.message ?: "Could not create that statement.",
                        )
                    }
                }
        }
    }

    fun statementShared() = _state.update { it.copy(statement = null) }

    fun errorShown() = _state.update { it.copy(error = null) }

    /** Shows or hides the entries that came to nothing. */
    fun toggleSettled() {
        _state.update { it.copy(showSettled = !it.showSettled) }
        rebuild()
    }

    private fun rebuild() {
        // The running balance is accumulated over every entry, whatever the
        // filter shows. Hiding a row must never change the arithmetic of the
        // rows around it.
        val all = LedgerStatement.build(allRows, viewerUid, newestFirst = true)
        val visible = if (_state.value.showSettled) {
            all
        } else {
            all.filter { it.entry.status == TxnStatus.PENDING || it.entry.status.affectsBalance }
        }

        _state.update {
            it.copy(
                lines = visible,
                hiddenCount = all.size - visible.size,
                closingBalance = LedgerStatement.closingBalance(allRows, viewerUid),
                loading = false,
            )
        }
    }
}
