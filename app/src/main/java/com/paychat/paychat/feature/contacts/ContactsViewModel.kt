package com.paychat.paychat.feature.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paychat.paychat.data.contacts.ContactsRepository
import com.paychat.paychat.data.local.entity.DeviceContactEntity
import com.paychat.paychat.data.local.entity.LocalContactEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactsUiState(
    val permissionGranted: Boolean = false,
    val syncing: Boolean = false,
    val query: String = "",
    val error: String? = null,
    /** Set when a conversation is ready to open. */
    val openThreadId: String? = null,
    private val allOnPayChat: List<DeviceContactEntity> = emptyList(),
    private val allNotOnPayChat: List<DeviceContactEntity> = emptyList(),
    private val allAddedByHand: List<LocalContactEntity> = emptyList(),
) {
    // Filtering is derived rather than stored, so typing in the search box and
    // a database update can never disagree about what the list holds.
    val onPayChat: List<DeviceContactEntity> get() = allOnPayChat.matching(query)
    val notOnPayChat: List<DeviceContactEntity> get() = allNotOnPayChat.matching(query)
    val addedByHand: List<LocalContactEntity>
        get() = if (query.isBlank()) {
            allAddedByHand
        } else {
            allAddedByHand.filter {
                it.name.contains(query, ignoreCase = true) || it.phone.contains(query)
            }
        }

    val isEmpty: Boolean
        get() = onPayChat.isEmpty() && notOnPayChat.isEmpty() && addedByHand.isEmpty()

    internal fun withContacts(
        registered: List<DeviceContactEntity>,
        unregistered: List<DeviceContactEntity>,
        local: List<LocalContactEntity>,
    ) = copy(
        allOnPayChat = registered,
        allNotOnPayChat = unregistered,
        allAddedByHand = local,
    )

    private fun List<DeviceContactEntity>.matching(query: String) =
        if (query.isBlank()) {
            this
        } else {
            filter { it.displayName.contains(query, ignoreCase = true) || it.phone.contains(query) }
        }
}

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contacts: ContactsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ContactsUiState(permissionGranted = contacts.hasContactsPermission())
    )
    val state: StateFlow<ContactsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                contacts.observeRegistered(),
                contacts.observeUnregistered(),
                contacts.observeLocalContacts(),
            ) { registered, unregistered, local ->
                Triple(registered, unregistered, local)
            }.collect { (registered, unregistered, local) ->
                _state.update { it.withContacts(registered, unregistered, local) }
            }
        }
        if (_state.value.permissionGranted) sync()
    }

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(permissionGranted = granted) }
        if (granted) sync()
    }

    fun sync() {
        if (_state.value.syncing) return
        viewModelScope.launch {
            _state.update { it.copy(syncing = true, error = null) }
            val result = contacts.syncDeviceContacts()
            _state.update { state ->
                state.copy(
                    syncing = false,
                    error = if (result.isFailure) {
                        "Could not check which contacts use PayChat."
                    } else {
                        null
                    },
                )
            }
        }
    }

    /** Opens the conversation for a contact, creating it the first time. */
    fun openConversation(phone: String, name: String) {
        viewModelScope.launch {
            contacts.openOrCreateThread(phone, name).fold(
                onSuccess = { threadId -> _state.update { it.copy(openThreadId = threadId) } },
                onFailure = { error ->
                    _state.update {
                        it.copy(error = error.message ?: "Could not open that conversation.")
                    }
                },
            )
        }
    }

    fun onThreadOpened() = _state.update { it.copy(openThreadId = null) }

    fun dismissError() = _state.update { it.copy(error = null) }
}
