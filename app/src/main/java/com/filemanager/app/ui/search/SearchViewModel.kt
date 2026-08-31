package com.filemanager.app.ui.search

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filemanager.app.data.FileRepository
import com.filemanager.app.domain.FileItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<FileItem> = emptyList(),
    val isSearching: Boolean = false
)

class SearchViewModel(
    private val repository: FileRepository = FileRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(results = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(results = emptyList(), isSearching = true) }
            repository.search(Environment.getExternalStorageDirectory(), query).collect { item ->
                _uiState.update { it.copy(results = it.results + item) }
            }
            _uiState.update { it.copy(isSearching = false) }
        }
    }

    override fun onCleared() {
        searchJob?.cancel()
        super.onCleared()
    }
}
