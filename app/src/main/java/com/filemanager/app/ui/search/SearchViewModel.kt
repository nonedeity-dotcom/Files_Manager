package com.filemanager.app.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.filemanager.app.data.AppSettings
import com.filemanager.app.data.FileRepository
import com.filemanager.app.data.StorageVolumes
import com.filemanager.app.domain.FileItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<FileItem> = emptyList(),
    val isSearching: Boolean = false
)

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FileRepository()
    private val settings = AppSettings(application)

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
            // Scanning the whole device on every keystroke is wasteful; wait for
            // a pause in typing first.
            delay(DEBOUNCE_MILLIS)
            val showHidden = settings.showHidden.first()
            _uiState.update { it.copy(results = emptyList(), isSearching = true) }

            // Publishing each hit separately makes the list thrash on a broad
            // query, so results are flushed in small batches.
            val batch = mutableListOf<FileItem>()
            repository.search(StorageVolumes.internalStorage(), query, showHidden)
                .collect { item ->
                    batch += item
                    if (batch.size >= BATCH_SIZE) {
                        val flushed = batch.toList()
                        batch.clear()
                        _uiState.update { it.copy(results = it.results + flushed) }
                    }
                }
            _uiState.update { it.copy(results = it.results + batch, isSearching = false) }
        }
    }

    override fun onCleared() {
        searchJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 300L
        const val BATCH_SIZE = 20
    }
}
