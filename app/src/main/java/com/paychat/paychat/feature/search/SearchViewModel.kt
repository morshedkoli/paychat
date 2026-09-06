package com.paychat.paychat.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paychat.paychat.data.search.SearchRepository
import com.paychat.paychat.data.search.SearchResults
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: SearchResults = SearchResults(),
    val searching: Boolean = false,
) {
    /** True once a real query has run and found nothing. */
    val nothingFound: Boolean
        get() = !searching && query.trim().length >= 2 && results.isEmpty
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val search: SearchRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val typed = MutableStateFlow("")

    init {
        viewModelScope.launch {
            // Searching on every keystroke would run a table scan per letter.
            // A short pause after typing stops is enough to feel immediate.
            typed.debounce(200).distinctUntilChanged().collect { query ->
                _state.update { it.copy(searching = query.trim().length >= 2) }
                val results = search.search(query)
                // A slow search whose query has since changed is discarded,
                // so results never lag behind the box.
                if (typed.value == query) {
                    _state.update { it.copy(results = results, searching = false) }
                }
            }
        }
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        typed.value = value
    }

    fun clear() {
        _state.update { SearchUiState() }
        typed.value = ""
    }
}
