package com.filemanager.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.app.Activity
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.viewmodel.compose.viewModel
import com.filemanager.app.data.StoragePermission
import com.filemanager.app.domain.FileItem
import com.filemanager.app.ui.browser.BrowserScreen
import com.filemanager.app.ui.browser.BrowserViewModel
import com.filemanager.app.ui.permission.PermissionScreen
import com.filemanager.app.ui.search.SearchScreen
import com.filemanager.app.ui.search.SearchViewModel
import com.filemanager.app.ui.settings.SettingsScreen
import com.filemanager.app.ui.vault.VaultScreen
import com.filemanager.app.ui.vault.VaultViewModel
import com.filemanager.app.ui.viewer.ViewerScreen

private sealed class Screen {
    data object Browser : Screen()
    data object Search : Screen()
    data object Settings : Screen()
    data object Vault : Screen()
    data class Viewer(val item: FileItem) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true }
                ) {
                    FileManagerRoot()
                }
            }
        }
    }
}

@Composable
private fun FileManagerRoot() {
    // renamed to avoid clashing with the FileManagerApp Application subclass
    var hasPermission by remember { mutableStateOf(StoragePermission.hasAllFilesAccess()) }

    if (!hasPermission) {
        PermissionScreen(onGranted = { hasPermission = true })
        return
    }

    val browserViewModel: BrowserViewModel = viewModel()
    var screen by remember { mutableStateOf<Screen>(Screen.Browser) }
    val activity = LocalContext.current as? Activity

    when (val current = screen) {
        is Screen.Browser -> BrowserScreen(
            viewModel = browserViewModel,
            onOpenFile = { item -> screen = Screen.Viewer(item) },
            onOpenSearch = { screen = Screen.Search },
            onOpenSettings = { screen = Screen.Settings },
            onExit = { activity?.finish() }
        )
        is Screen.Search -> {
            val searchViewModel: SearchViewModel = viewModel()
            SearchScreen(
                viewModel = searchViewModel,
                onOpenItem = { item ->
                    if (item.isDirectory) {
                        browserViewModel.open(item)
                        screen = Screen.Browser
                    } else {
                        screen = Screen.Viewer(item)
                    }
                },
                onBack = { screen = Screen.Browser }
            )
        }
        is Screen.Viewer -> ViewerScreen(
            item = current.item,
            onBack = { screen = Screen.Browser }
        )
        is Screen.Settings -> SettingsScreen(
            onOpenVault = { screen = Screen.Vault },
            onBack = { screen = Screen.Browser }
        )
        is Screen.Vault -> {
            val vaultViewModel: VaultViewModel = viewModel()
            VaultScreen(
                viewModel = vaultViewModel,
                onBack = { screen = Screen.Settings }
            )
        }
    }
}
