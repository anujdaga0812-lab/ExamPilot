package com.example.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "exampilot_prefs")

class ThemePreferences(private val context: Context) {
    private val darkModeKey = booleanPreferencesKey("dark_mode_enabled")

    val isDarkMode: Flow<Boolean> = context.themeDataStore.data.map { prefs ->
        prefs[darkModeKey] ?: true
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.themeDataStore.edit { prefs ->
            prefs[darkModeKey] = enabled
        }
    }
}
