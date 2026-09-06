package com.paychat.paychat.feature.transaction

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paychat.paychat.core.errors.userMessage
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
    val note: String = "",
    val photoLocalPath: String? = null,
    val dueDate: Long? = null,
    val amountError: String? = null,
    val error: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
) {
    val amount: Money? get() = Money.parse(amountText)
    val canSave: Boolean get() = amount?.isPositive == true && !saving

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

    fun onNoteChange(value: String) = _state.update { it.copy(note = value) }

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
            transactions.create(
                threadId = threadId,
                direction = current.direction,
                amount = amount,
                note = current.note,
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
