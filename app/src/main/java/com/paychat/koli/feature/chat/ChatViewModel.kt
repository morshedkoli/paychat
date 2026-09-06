package com.paychat.koli.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paychat.koli.core.money.Money
import com.paychat.koli.data.auth.AuthRepository
import com.paychat.koli.data.chat.ChatRepository
import com.paychat.koli.data.local.entity.MessageEntity
import com.paychat.koli.ui.nav.NavArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val threadId: String = "",
    val viewerUid: String = "",
    val peerName: String = "",
    val peerPhone: String = "",
    val peerPhotoUrl: String? = null,
    val isLocal: Boolean = false,
    val balance: Money = Money.ZERO,
    /** Newest first, which is the order the list renders in. */
    val messages: List<MessageEntity> = emptyList(),
    val draft: String = "",
    val error: String? = null,
) {
    val canSend: Boolean get() = draft.isNotBlank()
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chat: ChatRepository,
    auth: AuthRepository,
) : ViewModel() {

    private val threadId: String = savedStateHandle.get<String>(NavArgs.THREAD_ID).orEmpty()

    private val _state = MutableStateFlow(
        ChatUiState(threadId = threadId, viewerUid = auth.currentUid.orEmpty())
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                chat.observeThread(threadId),
                chat.observeMessages(threadId),
            ) { thread, messages -> thread to messages }
                .collect { (thread, messages) ->
                    _state.update {
                        it.copy(
                            peerName = thread?.peerName?.ifBlank { thread.peerPhone }.orEmpty(),
                            peerPhone = thread?.peerPhone.orEmpty(),
                            peerPhotoUrl = thread?.peerPhotoUrl,
                            isLocal = thread?.isLocal ?: false,
                            balance = Money(thread?.balanceMinor ?: 0L),
                            messages = messages,
                        )
                    }
                }
        }

        viewModelScope.launch {
            chat.syncMessages(threadId).collect { incoming ->
                chat.persist(incoming)
                // Anything that arrived while the screen is open counts as read.
                chat.markRead(threadId)
            }
        }
    }

    fun onDraftChange(value: String) = _state.update { it.copy(draft = value) }

    fun send() {
        val body = _state.value.draft
        if (body.isBlank()) return

        // Cleared before the send completes: the message is written to the
        // local database first, so it appears immediately either way.
        _state.update { it.copy(draft = "") }

        viewModelScope.launch {
            chat.sendText(threadId, body).onFailure { error ->
                _state.update {
                    it.copy(
                        draft = body,
                        error = error.message ?: "Could not send that message.",
                    )
                }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
