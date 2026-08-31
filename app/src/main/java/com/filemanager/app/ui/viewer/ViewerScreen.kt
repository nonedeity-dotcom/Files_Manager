package com.filemanager.app.ui.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.filemanager.app.data.FileRepository
import com.filemanager.app.domain.FileItem
import com.filemanager.app.domain.FileType
import com.filemanager.app.domain.type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    item: FileItem,
    repository: FileRepository = FileRepository(),
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (item.type()) {
                FileType.IMAGE -> ImageViewer(item)
                FileType.TEXT -> TextViewer(item, repository)
                else -> Text(
                    "Формат не поддерживается для просмотра",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                )
            }
        }
    }
}

@Composable
private fun ImageViewer(item: FileItem) {
    AsyncImage(
        model = item.file,
        contentDescription = item.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun TextViewer(item: FileItem, repository: FileRepository) {
    var content by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(item.path) {
        content = withContext(Dispatchers.IO) {
            runCatching { repository.readTextFile(item.file) }.getOrElse { "Не удалось прочитать файл: ${it.message}" }
        }
    }

    val text = content
    if (text == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            )
        }
    }
}
