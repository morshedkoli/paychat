package com.paychat.paychat.feature.transaction

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paychat.paychat.core.errors.userMessage
import com.paychat.paychat.core.model.TransactionNote
import com.paychat.paychat.core.model.TxnCategory
import com.paychat.paychat.core.model.TxnDirection
import com.paychat.paychat.core.money.Money
import com.paychat.paychat.data.chat.ChatRepository
import com.paychat.paychat.data.media.MediaFiles
import com.paychat.paychat.data.transactions.TransactionRepository
import com.paychat.paychat.ui.nav.NavArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AddTransactionUiState(
    val threadId: String = "",
    val peerName: String = "",
    val isLocalThread: Boolean = false,
    val direction: TxnDirection = TxnDirection.SENT,
    val amountText: String = "",
    val category: TxnCategory = TxnCategory.GENERAL,
    val note: String = "",
    val trxId: String = "",
    val photoLocalPath: String? = null,
    val dueDate: Long? = null,
    val amountError: String? = null,
    val error: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
) {
    val amount: Money? get() = Money.parse(amountText)
    val canSave: Boolean get() = amount?.isPositive == true && !saving

    /** Transactions >= ৳10,000 (1,000,000 poisha) require biometric/screen lock confirmation */
    val isHighValue: Boolean get() = (amount?.minor ?: 0L) >= 1_000_000L

    /**
     * What will happen when this is saved, said plainly, because the answer
     * differs by direction and by whether the other person has an account.
     */
    val consequence: String
        get() = when {
            isLocalThread ->
                "$peerName is not on PayChat, so this applies now. They can review it when they join."
            direction == TxnDirection.SENT ->
                "$peerName has to accept this before it changes the balance."
            else ->
                "This applies straight away, because it lowers what $peerName owes you."
        }
}

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val chat: ChatRepository,
    private val mediaFiles: MediaFiles,
) : ViewModel() {

    private val threadId: String = savedStateHandle.get<String>(NavArgs.THREAD_ID).orEmpty()

    private val _state = MutableStateFlow(AddTransactionUiState(threadId = threadId))
    val state: StateFlow<AddTransactionUiState> = _state.asStateFlow()

    init {
        val initialDir = savedStateHandle.get<String>("direction")
            ?.let { runCatching { TxnDirection.valueOf(it) }.getOrNull() }
        val initialAmount = savedStateHandle.get<String>("amount")
        val initialNote = savedStateHandle.get<String>("note")
        val initialCategory = savedStateHandle.get<String>("category")
            ?.let { runCatching { TxnCategory.valueOf(it) }.getOrNull() }

        if (initialDir != null || initialAmount != null || initialNote != null || initialCategory != null) {
            _state.update {
                it.copy(
                    direction = initialDir ?: it.direction,
                    amountText = initialAmount ?: it.amountText,
                    note = initialNote ?: it.note,
                    category = initialCategory ?: it.category,
                )
            }
        }

        viewModelScope.launch {
            chat.observeThread(threadId).collect { thread ->
                _state.update {
                    it.copy(
                        peerName = thread?.peerName?.ifBlank { thread.peerPhone } ?: "They",
                        isLocalThread = thread?.isLocal ?: false,
                    )
                }
            }
        }
    }

    fun onDirectionChange(direction: TxnDirection) =
        _state.update { it.copy(direction = direction) }

    fun onAmountChange(value: String) =
        _state.update { it.copy(amountText = value, amountError = null) }

    fun onCategoryChange(category: TxnCategory) =
        _state.update { it.copy(category = category) }

    fun onNoteChange(value: String) = _state.update { it.copy(note = value) }

    fun onTrxIdChange(value: String) = _state.update { it.copy(trxId = value) }

    fun onDueDateChange(value: Long?) = _state.update { it.copy(dueDate = value) }

    fun onPhotoPicked(uri: Uri) {
        viewModelScope.launch {
            val copied = mediaFiles.copyIn(uri, UUID.randomUUID().toString(), extension = "jpg")
            if (copied == null) {
                _state.update { it.copy(error = "That photo could not be read.") }
                return@launch
            }
            _state.update { it.copy(photoLocalPath = copied.absolutePath) }
        }
    }

    fun removePhoto() {
        val path = _state.value.photoLocalPath
        mediaFiles.discard(path)
        _state.update { it.copy(photoLocalPath = null) }
    }

    fun save() {
        val current = _state.value
        if (current.saving) return

        val amount = current.amount
        if (amount == null || !amount.isPositive) {
            _state.update {
                it.copy(amountError = "Enter an amount greater than zero.")
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            val formattedNote = TransactionNote.format(
                category = current.category,
                noteText = current.note,
                trxId = current.trxId,
            )
            transactions.create(
                threadId = threadId,
                direction = current.direction,
                amount = amount,
                note = formattedNote,
                photoLocalPath = current.photoLocalPath,
                dueDate = current.dueDate,
            ).fold(
                onSuccess = { _state.update { it.copy(saving = false, saved = true) } },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            saving = false,
                            error = error.userMessage("Could not record that transaction."),
                        )
                    }
                },
            )
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
