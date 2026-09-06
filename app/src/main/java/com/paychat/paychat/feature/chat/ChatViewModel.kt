package com.paychat.paychat.feature.chat

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paychat.paychat.core.model.MessageType
import com.paychat.paychat.core.money.Money
import com.paychat.paychat.data.auth.AuthRepository
import com.paychat.paychat.data.chat.ChatRepository
import com.paychat.paychat.data.local.entity.MessageEntity
import com.paychat.paychat.data.local.entity.TransactionEntity
import com.paychat.paychat.data.media.MediaFiles
import com.paychat.paychat.data.media.VoiceRecorder
import com.paychat.paychat.data.moderation.ModerationRepository
import com.paychat.paychat.data.moderation.ReportReason
import com.paychat.paychat.data.notifications.VisibleThread
import com.paychat.paychat.data.transactions.TransactionRepository
import com.paychat.paychat.ui.nav.NavArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class ChatUiState(
    val threadId: String = "",
    val viewerUid: String = "",
    val peerName: String = "",
    val peerPhone: String = "",
    val peerPhotoUrl: String? = null,
    val isLocal: Boolean = false,
    val blockedByMe: Boolean = false,
    val blockedByPeer: Boolean = false,
    val balance: Money = Money.ZERO,
    /** Newest first, which is the order the list renders in. */
    val messages: List<MessageEntity> = emptyList(),
    /** The transaction each TXN message points at, keyed by its id. */
    val transactions: Map<String, TransactionEntity> = emptyMap(),
    val draft: String = "",
    /** Set while a voice message is being recorded. */
    val recordingMessageId: String? = null,
    val error: String? = null,
    /** Something worth saying that is not a failure, such as a filed report. */
    val notice: String? = null,
) {
    /** Blocking closes the conversation in both directions. */
    val blocked: Boolean get() = blockedByMe || blockedByPeer

    val canSend: Boolean get() = draft.isNotBlank() && !blocked
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chat: ChatRepository,
    private val mediaFiles: MediaFiles,
    private val voiceRecorder: VoiceRecorder,
    private val transactions: TransactionRepository,
    private val visibleThread: VisibleThread,
    private val moderation: ModerationRepository,
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
                transactions.observeBalance(threadId),
            ) { thread, messages, balance ->
                Triple(thread, messages, balance)
            }.collect { (thread, messages, balance) ->
                _state.update {
                    it.copy(
                        peerName = thread?.peerName?.ifBlank { thread.peerPhone }.orEmpty(),
                        peerPhone = thread?.peerPhone.orEmpty(),
                        peerPhotoUrl = thread?.peerPhotoUrl,
                        isLocal = thread?.isLocal ?: false,
                        blockedByMe = thread?.blockedByMe ?: false,
                        blockedByPeer = thread?.blockedByPeer ?: false,
                        balance = Money(balance?.amountMinor ?: 0L),
                        messages = messages,
                    )
                }
            }
        }

        viewModelScope.launch {
            transactions.observeThread(threadId).collect { rows ->
                _state.update { state ->
                    state.copy(transactions = rows.associateBy { it.txnId })
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

        viewModelScope.launch {
            transactions.syncTransactions(threadId).collect { incoming ->
                transactions.persist(threadId, incoming)
            }
        }
    }

    // ------------------------------------------------------- notifications

    /**
     * Tied to the screen resuming rather than to this ViewModel, so a chat
     * left open behind another app still raises notifications.
     */
    fun screenResumed() {
        visibleThread.opened(threadId)
        // The screen may have been away while messages arrived.
        viewModelScope.launch { chat.markRead(threadId) }
    }

    fun screenPaused() = visibleThread.closed(threadId)

    // ----------------------------------------------------------- transactions

    fun acceptTransaction(txnId: String) = actOnTransaction { transactions.accept(txnId) }
    fun rejectTransaction(txnId: String) = actOnTransaction { transactions.reject(txnId) }
    fun cancelTransaction(txnId: String) = actOnTransaction { transactions.cancel(txnId) }

    private fun actOnTransaction(block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            block().onFailure { error ->
                _state.update { it.copy(error = error.message ?: "That did not work.") }
            }
        }
    }

    // --------------------------------------------------------- blocking

    fun setBlocked(blocked: Boolean) {
        viewModelScope.launch {
            val result =
                if (blocked) moderation.block(threadId) else moderation.unblock(threadId)
            result.onFailure { error ->
                _state.update {
                    it.copy(error = error.message ?: "That did not work.")
                }
            }
        }
    }

    fun report(reason: ReportReason, detail: String?) {
        viewModelScope.launch {
            moderation.report(threadId, reason, detail)
                .onSuccess {
                    _state.update { it.copy(notice = "Reported. Thank you.") }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = error.message ?: "That report was not filed.")
                    }
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

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    // ------------------------------------------------------------ attachments

    /**
     * Reserves the id for an attachment before it exists.
     *
     * The camera writes to a file named after the id, and the upload signature
     * is bound to it, so the id has to be decided first.
     */
    fun newAttachmentId(): String = UUID.randomUUID().toString()

    fun cameraFileFor(messageId: String): File = mediaFiles.newCameraFile(messageId)

    /** Queues a photo the user picked from their gallery. */
    fun sendPickedImage(uri: Uri) {
        viewModelScope.launch {
            val messageId = newAttachmentId()
            val copied = mediaFiles.copyIn(uri, messageId, extension = "jpg")
            if (copied == null) {
                _state.update { it.copy(error = "That photo could not be read.") }
                return@launch
            }
            queueMedia(messageId, MessageType.IMAGE, copied.absolutePath)
        }
    }

    /** Queues a photo the user just took, already written to [file]. */
    fun sendCapturedImage(messageId: String, file: File) {
        if (!file.exists()) {
            _state.update { it.copy(error = "The photo was not saved.") }
            return
        }
        viewModelScope.launch { queueMedia(messageId, MessageType.IMAGE, file.absolutePath) }
    }

    fun startRecording() {
        val messageId = newAttachmentId()
        voiceRecorder.start(mediaFiles.newVoiceFile(messageId)).fold(
            onSuccess = { _state.update { it.copy(recordingMessageId = messageId) } },
            onFailure = {
                _state.update { it.copy(error = "Could not start recording.") }
            },
        )
    }

    fun stopRecording() {
        val messageId = _state.value.recordingMessageId ?: return
        val recording = voiceRecorder.stop()
        _state.update { it.copy(recordingMessageId = null) }

        if (recording == null) {
            // Too short to be a message, or the recorder failed. Either way
            // there is nothing worth sending and no file left behind.
            return
        }
        viewModelScope.launch {
            queueMedia(
                messageId = messageId,
                type = MessageType.VOICE,
                localPath = recording.file.absolutePath,
                durationMs = recording.durationMs,
            )
        }
    }

    fun cancelRecording() {
        voiceRecorder.cancel()
        _state.update { it.copy(recordingMessageId = null) }
    }

    private suspend fun queueMedia(
        messageId: String,
        type: MessageType,
        localPath: String,
        durationMs: Long? = null,
    ) {
        chat.sendMedia(threadId, messageId, type, localPath, durationMs).onFailure { error ->
            _state.update {
                it.copy(error = error.message ?: "Could not attach that file.")
            }
        }
    }
}
