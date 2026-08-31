package com.filemanager.app.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.filemanager.app.R
import com.filemanager.app.domain.FileItem
import com.filemanager.app.domain.FileType
import com.filemanager.app.domain.type
import com.filemanager.app.util.formatFileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onOpenItem: (FileItem) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChanged,
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_field")
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isSearching && state.results.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.search_searching),
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
                state.query.isNotBlank() && state.results.isEmpty() && !state.isSearching -> {
                    Text(
                        text = stringResource(R.string.search_empty),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.results, key = { it.path }) { item ->
                            SearchResultRow(item = item, onClick = { onOpenItem(item) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(item: FileItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_result_${item.name}")
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val icon = when (item.type()) {
            FileType.FOLDER -> Icons.Filled.Folder
            FileType.IMAGE -> Icons.Filled.Image
            FileType.TEXT -> Icons.Filled.Description
            else -> Icons.AutoMirrored.Filled.InsertDriveFile
        }
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(28.dp))
        Column {
            Text(text = item.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = item.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
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
