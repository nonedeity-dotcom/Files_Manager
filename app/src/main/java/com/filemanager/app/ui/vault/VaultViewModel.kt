package com.filemanager.app.ui.vault

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.filemanager.app.data.VaultRepository
import com.filemanager.app.domain.FileItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class VaultUiState(
    val items: List<FileItem> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val vaultRepository = VaultRepository(application)
    private val downloadsDir = File(Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DOWNLOADS
    ), "FilesManagerVault")

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val items = vaultRepository.listVaultItems()
            _uiState.update { it.copy(items = items, isLoading = false) }
        }
    }

    fun restore(item: FileItem) {
        viewModelScope.launch {
            downloadsDir.mkdirs()
            val success = vaultRepository.restoreFromVault(item.file, downloadsDir)
            _uiState.update {
                it.copy(
                    message = if (success) {
                        "Восстановлено в Загрузки/FilesManagerVault"
                    } else {
                        "Не удалось восстановить файл"
                    }
                )
            }
            refresh()
        }
    }

    fun delete(item: FileItem) {
        viewModelScope.launch {
            vaultRepository.deleteFromVault(item.file)
            refresh()
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
