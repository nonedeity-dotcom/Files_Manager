package com.filemanager.app.ui.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.filemanager.app.R
import com.filemanager.app.domain.FileItem
import com.filemanager.app.domain.FileType
import com.filemanager.app.domain.type
import com.filemanager.app.util.formatFileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onOpenFile: (FileItem) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateFolderDialog by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var showMenu by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        if (viewModel.isSelectionMode) {
            viewModel.clearSelection()
        } else if (!viewModel.navigateUp()) {
            onExit()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (viewModel.isSelectionMode) {
                        Text("${state.selectedPaths.size}")
                    } else {
                        Text(state.currentDirectory.name.ifEmpty { stringResource(R.string.storage_internal) })
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (viewModel.isSelectionMode) viewModel.clearSelection()
                        else if (!viewModel.navigateUp()) onExit()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (viewModel.isSelectionMode) {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.action_select_all))
                        }
                        IconButton(onClick = { viewModel.copySelectedToClipboard() }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.action_copy))
                        }
                        IconButton(onClick = { viewModel.cutSelectedToClipboard() }) {
                            Icon(Icons.Filled.ContentCut, contentDescription = stringResource(R.string.action_move))
                        }
                        IconButton(onClick = { viewModel.addSelectedToVault() }) {
                            Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.action_add_to_vault))
                        }
                        IconButton(onClick = { viewModel.archiveSelected() }) {
                            Icon(Icons.Filled.Archive, contentDescription = stringResource(R.string.action_archive))
                        }
                        if (viewModel.canExtractSelection()) {
                            IconButton(onClick = { viewModel.extractSelected() }) {
                                Icon(Icons.Filled.Unarchive, contentDescription = stringResource(R.string.action_extract))
                            }
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                        if (state.selectedPaths.size == 1) {
                            IconButton(onClick = {
                                renameTarget = state.items.firstOrNull { it.path in state.selectedPaths }
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_rename))
                            }
                        }
                    } else {
                        IconButton(onClick = onOpenSearch) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                        }
                        if (state.clipboard != null) {
                            IconButton(onClick = { viewModel.pasteClipboard() }) {
                                Icon(Icons.Filled.ContentPaste, contentDescription = stringResource(R.string.action_paste))
                            }
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = null)
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                SortOrderMenuItems(viewModel) { showMenu = false }
                                DropdownMenuItem(
                                    text = { Text("Настройки") },
                                    onClick = {
                                        showMenu = false
                                        onOpenSettings()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!viewModel.isSelectionMode) {
                FloatingActionButton(onClick = { showCreateFolderDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (state.items.isEmpty() && !state.isLoading) {
                Text(
                    text = stringResource(R.string.empty_folder),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.items, key = { it.path }) { item ->
                        FileRow(
                            item = item,
                            isSelected = item.path in state.selectedPaths,
                            isSelectionMode = viewModel.isSelectionMode,
                            onClick = {
                                if (viewModel.isSelectionMode) {
                                    viewModel.toggleSelection(item)
                                } else if (item.isDirectory) {
                                    viewModel.open(item)
                                } else {
                                    onOpenFile(item)
                                }
                            },
                            onLongClick = { viewModel.toggleSelection(item) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        NameInputDialog(
            title = "Новая папка",
            onConfirm = { name ->
                viewModel.createFolder(name)
                showCreateFolderDialog = false
            },
            onDismiss = { showCreateFolderDialog = false }
        )
    }

    renameTarget?.let { item ->
        NameInputDialog(
            title = stringResource(R.string.dialog_rename_title),
            initialValue = item.name,
            onConfirm = { name ->
                viewModel.rename(item, name)
                viewModel.clearSelection()
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = { Text(stringResource(R.string.dialog_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelected()
                    showDeleteConfirm = false
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun SortOrderMenuItems(viewModel: BrowserViewModel, onSelected: () -> Unit) {
    val options = listOf(
        "Имя (А-Я)" to com.filemanager.app.domain.SortOrder.NAME_ASC,
        "Имя (Я-А)" to com.filemanager.app.domain.SortOrder.NAME_DESC,
        "Дата (новые)" to com.filemanager.app.domain.SortOrder.DATE_DESC,
        "Дата (старые)" to com.filemanager.app.domain.SortOrder.DATE_ASC,
        "Размер (больше)" to com.filemanager.app.domain.SortOrder.SIZE_DESC,
        "Размер (меньше)" to com.filemanager.app.domain.SortOrder.SIZE_ASC
    )
    options.forEach { (label, order) ->
        DropdownMenuItem(text = { Text(label) }, onClick = {
            viewModel.setSortOrder(order)
            onSelected()
        })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    item: FileItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isSelectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null
            )
        } else {
            Icon(imageVector = iconFor(item), contentDescription = null, modifier = Modifier.size(28.dp))
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = item.name, style = MaterialTheme.typography.bodyLarge)
            if (!item.isDirectory) {
                Text(
                    text = formatFileSize(item.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun iconFor(item: FileItem) = when (item.type()) {
    FileType.FOLDER -> Icons.Filled.Folder
    FileType.IMAGE -> Icons.Filled.Image
    FileType.TEXT -> Icons.Filled.Description
    else -> Icons.Filled.InsertDriveFile
}

@Composable
private fun NameInputDialog(
    title: String,
    initialValue: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
