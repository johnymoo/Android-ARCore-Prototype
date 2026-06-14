package com.johnymoo.arverify.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.johnymoo.arverify.session.CaptureLibraryRepository
import com.johnymoo.arverify.session.SessionEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryUiState(
    val loading: Boolean = true,
    val filter: LibraryFilter = LibraryFilter.ALL,
    val all: List<SessionEntry> = emptyList(),
) {
    val visible: List<SessionEntry> get() = LibraryFiltering.apply(all, filter)
}

class LibraryViewModel(private val repo: CaptureLibraryRepository) : ViewModel() {
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    fun refresh() {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) { repo.listSessions() }
            _state.value = _state.value.copy(loading = false, all = entries)
        }
    }

    fun setFilter(f: LibraryFilter) { _state.value = _state.value.copy(filter = f) }

    fun delete(entry: SessionEntry) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(entry) }
            refresh()
        }
    }
}
