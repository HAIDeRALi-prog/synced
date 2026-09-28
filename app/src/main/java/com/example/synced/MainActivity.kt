package com.example.synced

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.synced.core.ui.navigation.AppNavHost
import com.example.synced.core.ui.theme.SyncedTheme
import com.example.synced.feature.settings.domain.ThemeMode
import com.example.synced.feature.settings.presentation.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppContent() }
    }
}

/** Reads persisted theme settings and applies [SyncedTheme] around the nav graph. */
@Composable
private fun AppContent() {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    SyncedTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
        AppNavHost()
    }
}
