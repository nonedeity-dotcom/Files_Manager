package com.filemanager.app.ui.browser

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.filemanager.app.data.AppSettings
import com.filemanager.app.data.ArchiveRepository
import com.filemanager.app.data.FileRepository
import com.filemanager.app.data.StorageRoot
import com.filemanager.app.data.StorageVolumes
import com.filemanager.app.data.VaultRepository
import com.filemanager.app.domain.ClipboardMode
import com.filemanager.app.domain.ClipboardState
import com.filemanager.app.domain.FileItem
import com.filemanager.app.domain.FileOperationResult
import com.filemanager.app.domain.SortOrder
import com.filemanager.app.util.uniqueDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class BrowserUiState(
    val currentDirectory: File = StorageVolumes.internalStorage(),
    val items: List<FileItem> = emptyList(),
    val isLoading: Boolean = true,
    val sortOrder: SortOrder = SortOrder.NAME_ASC,
    val selectedPaths: Set<String> = emptySet(),
    val clipboard: ClipboardState? = null,
    val message: String? = null,
    val showHidden: Boolean = false,
    val rootEnabled: Boolean = false,
    val storageRoots: List<StorageRoot> = emptyList()
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FileRepository()
    private val settings = AppSettings(application)
    private val vaultRepository = VaultRepository(application)
    private val archiveRepository = ArchiveRepository()

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val backStack = ArrayDeque<File>()

    init {
        loadCurrentDirectory()
        viewModelScope.launch {
            val roots = withContext(Dispatchers.IO) { StorageVolumes.roots() }
            _uiState.update { it.copy(storageRoots = roots) }
        }
        // Settings are shared with the settings screen, so keep observing them:
        // reading once would leave the browser stale after a toggle there.
        viewModelScope.launch {
            combine(settings.showHidden, settings.rootEnabled) { showHidden, rootEnabled ->
                showHidden to rootEnabled
            }.collect { (showHidden, rootEnabled) ->
                val changed = _uiState.value.showHidden != showHidden ||
                    _uiState.value.rootEnabled != rootEnabled
                _uiState.update { it.copy(showHidden = showHidden, rootEnabled = rootEnabled) }
                if (changed) loadCurrentDirectory()
            }
        }
    }

    val isSelectionMode: Boolean get() = _uiState.value.selectedPaths.isNotEmpty()

    fun canNavigateUp(): Boolean = backStack.isNotEmpty()

    fun open(item: FileItem) {
        if (item.isDirectory) {
            backStack.addLast(_uiState.value.currentDirectory)
            navigateTo(item.file)
        }
    }

    fun openStorageRoot(root: StorageRoot) {
        backStack.addLast(_uiState.value.currentDirectory)
        navigateTo(root.directory)
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
        val state = _uiState.value
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val items = repository.listChildren(
                directory = state.currentDirectory,
                sortOrder = state.sortOrder,
                showHidden = state.showHidden,
                allowRoot = state.rootEnabled
            )
            _uiState.update { it.copy(items = items, isLoading = false) }
        }
    }

    fun setSortOrder(sortOrder: SortOrder) {
        _uiState.update { it.copy(sortOrder = sortOrder) }
        loadCurrentDirectory()
    }

    fun setShowHidden(show: Boolean) {
        viewModelScope.launch { settings.setShowHidden(show) }
    }

    fun setRootEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setRootEnabled(enabled) }
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
            val result = repository.createFolder(
                _uiState.value.currentDirectory,
                name.trim(),
                _uiState.value.rootEnabled
            )
            handleResult(result)
        }
    }

    fun rename(item: FileItem, newName: String) {
        viewModelScope.launch {
            val result = repository.rename(item.file, newName.trim(), _uiState.value.rootEnabled)
            handleResult(result)
        }
    }

    fun deleteSelected() {
        val targets = selectedItems().map { it.file }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            val result = repository.delete(targets, _uiState.value.rootEnabled)
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
        val allowRoot = _uiState.value.rootEnabled
        viewModelScope.launch {
            val result = when (clipboard.mode) {
                is ClipboardMode.Copy -> repository.copy(sources, destination, allowRoot)
                is ClipboardMode.Cut -> repository.move(sources, destination, allowRoot)
            }
            _uiState.update { it.copy(clipboard = null) }
            handleResult(result)
        }
    }

    fun addSelectedToVault() {
        val targets = selectedItems().filter { !it.isDirectory }.map { it.file }
        if (targets.isEmpty()) return
        clearSelection()
        viewModelScope.launch {
            val failures = targets.count { !vaultRepository.addToVault(it) }
            loadCurrentDirectory()
            if (failures > 0) {
                _uiState.update { it.copy(message = "Не удалось добавить в хранилище: $failures") }
            }
        }
    }

    fun archiveSelected() {
        val targets = selectedItems().map { it.file }
        if (targets.isEmpty()) return
        val destinationDir = _uiState.value.currentDirectory
        val baseName = if (targets.size == 1) "${targets.first().name}.zip" else "archive.zip"
        clearSelection()
        viewModelScope.launch {
            // Never silently overwrite an archive that's already there.
            val archive = uniqueDestination(destinationDir, baseName)
            val result = archiveRepository.createZip(targets, archive)
            handleResult(result)
        }
    }

    fun canExtractSelection(): Boolean {
        val selected = selectedItems()
        return selected.size == 1 && !selected.first().isDirectory &&
            selected.first().name.endsWith(".zip", ignoreCase = true)
    }

    fun extractSelected() {
        val target = selectedItems().singleOrNull()?.file ?: return
        val destination = _uiState.value.currentDirectory
        clearSelection()
        viewModelScope.launch {
            val result = archiveRepository.extractZip(target, destination)
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
