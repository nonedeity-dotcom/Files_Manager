package com.filemanager.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class AppSettings(private val context: Context) {

    private val rootEnabledKey = booleanPreferencesKey("root_enabled")
    private val showHiddenKey = booleanPreferencesKey("show_hidden")

    val rootEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[rootEnabledKey] ?: false }

    val showHidden: Flow<Boolean> =
        context.dataStore.data.map { it[showHiddenKey] ?: false }

    suspend fun setRootEnabled(enabled: Boolean) {
        context.dataStore.edit { it[rootEnabledKey] = enabled }
    }

    suspend fun setShowHidden(show: Boolean) {
        context.dataStore.edit { it[showHiddenKey] = show }
    }
}
