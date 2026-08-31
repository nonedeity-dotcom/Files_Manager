package com.filemanager.app.domain

sealed class FileOperationResult {
    data object Success : FileOperationResult()
    data class Error(val message: String) : FileOperationResult()
}

sealed class ClipboardMode {
    data object Copy : ClipboardMode()
    data object Cut : ClipboardMode()
}

data class ClipboardState(
    val items: List<FileItem>,
    val mode: ClipboardMode
)
