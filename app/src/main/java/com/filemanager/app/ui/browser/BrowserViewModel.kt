package com.filemanager.app.ui.browser

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filemanager.app.data.FileRepository
import com.filemanager.app.domain.ClipboardMode
import com.filemanager.app.domain.ClipboardState
import com.filemanager.app.domain.FileItem
import com.filemanager.app.domain.FileOperationResult
import com.filemanager.app.domain.SortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class BrowserUiState(
    val currentDirectory: File = Environment.getExternalStorageDirectory(),
    val items: List<FileItem> = emptyList(),
    val isLoading: Boolean = true,
    val sortOrder: SortOrder = SortOrder.NAME_ASC,
    val selectedPaths: Set<String> = emptySet(),
    val clipboard: ClipboardState? = null,
    val message: String? = null
)

class BrowserViewModel(
    private val repository: FileRepository = FileRepository()
) : ViewModel() {

    private val rootDirectory: File = Environment.getExternalStorageDirectory()

    private val _uiState = MutableStateFlow(BrowserUiState(currentDirectory = rootDirectory))
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val backStack = ArrayDeque<File>()

    init {
        loadCurrentDirectory()
    }

    val isSelectionMode: Boolean get() = _uiState.value.selectedPaths.isNotEmpty()

    fun canNavigateUp(): Boolean = backStack.isNotEmpty()

    fun open(item: FileItem) {
        if (item.isDirectory) {
            backStack.addLast(_uiState.value.currentDirectory)
            navigateTo(item.file)
        }
    }

    fun navigateUp(): Boolean {
        val previous = backStack.removeLastOrNull() ?: return false
        navigateTo(previous)
        return true
    }

    private fun navigateTo(directory: File) {
        _uiState.update { it.copy(currentDirectory = directory, selectedPaths = emptySet()) }
        loadCurrentDirectory()
    }

    fun refresh() = loadCurrentDirectory()

    private fun loadCurrentDirectory() {
        val directory = _uiState.value.currentDirectory
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val items = repository.listChildren(directory, _uiState.value.sortOrder)
            _uiState.update { it.copy(items = items, isLoading = false) }
        }
    }

    fun setSortOrder(sortOrder: SortOrder) {
        _uiState.update { it.copy(sortOrder = sortOrder) }
        loadCurrentDirectory()
    }

    fun toggleSelection(item: FileItem) {
        _uiState.update { state ->
            val selected = state.selectedPaths.toMutableSet()
            if (!selected.add(item.path)) selected.remove(item.path)
            state.copy(selectedPaths = selected)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPaths = emptySet()) }
    }

    fun selectAll() {
        _uiState.update { it.copy(selectedPaths = it.items.map { item -> item.path }.toSet()) }
    }

    private fun selectedItems(): List<FileItem> {
        val selected = _uiState.value.selectedPaths
        return _uiState.value.items.filter { it.path in selected }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val result = repository.createFolder(_uiState.value.currentDirectory, name)
            handleResult(result)
        }
    }

    fun rename(item: FileItem, newName: String) {
        viewModelScope.launch {
            val result = repository.rename(item.file, newName)
            handleResult(result)
        }
    }

    fun deleteSelected() {
        val targets = selectedItems().map { it.file }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            val result = repository.delete(targets)
            clearSelection()
            handleResult(result)
        }
    }

    fun copySelectedToClipboard() {
        _uiState.update { it.copy(clipboard = ClipboardState(selectedItems(), ClipboardMode.Copy)) }
        clearSelection()
    }

    fun cutSelectedToClipboard() {
        _uiState.update { it.copy(clipboard = ClipboardState(selectedItems(), ClipboardMode.Cut)) }
        clearSelection()
    }

    fun pasteClipboard() {
        val clipboard = _uiState.value.clipboard ?: return
        val destination = _uiState.value.currentDirectory
        val sources = clipboard.items.map { it.file }
        viewModelScope.launch {
            val result = when (clipboard.mode) {
                is ClipboardMode.Copy -> repository.copy(sources, destination)
                is ClipboardMode.Cut -> repository.move(sources, destination)
            }
            _uiState.update { it.copy(clipboard = null) }
            handleResult(result)
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun handleResult(result: FileOperationResult) {
        when (result) {
            is FileOperationResult.Success -> loadCurrentDirectory()
            is FileOperationResult.Error -> _uiState.update { it.copy(message = result.message) }
        }
    }
}
