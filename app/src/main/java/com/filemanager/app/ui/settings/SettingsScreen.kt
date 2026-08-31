package com.filemanager.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.filemanager.app.data.AppSettings
import com.filemanager.app.data.RootFileOperations
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenVault: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val scope = rememberCoroutineScope()

    var showHidden by remember { mutableStateOf(false) }
    var rootEnabled by remember { mutableStateOf(false) }
    var rootStatus by remember { mutableStateOf<Boolean?>(null) }

    BackHandler { onBack() }

    LaunchedEffect(Unit) {
        settings.showHidden.collect { showHidden = it }
    }
    LaunchedEffect(Unit) {
        settings.rootEnabled.collect { rootEnabled = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            ListItem(
                headlineContent = { Text("Показывать скрытые файлы") },
                supportingContent = { Text("Файлы и папки, начинающиеся с точки") },
                leadingContent = { Icon(Icons.Filled.Visibility, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = showHidden,
                        onCheckedChange = { checked ->
                            scope.launch { settings.setShowHidden(checked) }
                        },
                        modifier = Modifier.testTag("switch_show_hidden")
                    )
                }
            )

            ListItem(
                headlineContent = { Text("Root-доступ") },
                supportingContent = {
                    Text(
                        when (rootStatus) {
                            null -> "Используется для файлов вне обычного доступа (при наличии root на устройстве)"
                            true -> "Root доступен"
                            false -> "Root недоступен на этом устройстве"
                        }
                    )
                },
                leadingContent = { Icon(Icons.Filled.Security, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = rootEnabled,
                        onCheckedChange = { checked ->
                            scope.launch {
                                settings.setRootEnabled(checked)
                                if (checked) {
                                    rootStatus = RootFileOperations.isRootAvailable()
                                }
                            }
                        },
                        modifier = Modifier.testTag("switch_root")
                    )
                }
            )

            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_open_vault")
                    .clickable(onClick = onOpenVault),
                headlineContent = { Text("Приватное хранилище") },
                supportingContent = { Text("Зашифрованные файлы (AES-256, ключ в Android Keystore)") },
                leadingContent = { Icon(Icons.Filled.Lock, contentDescription = null) },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) }
            )
        }
    }
}
