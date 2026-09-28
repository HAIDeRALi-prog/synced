package com.example.synced.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.synced.core.common.UiEvent
import com.example.synced.feature.content.domain.ContentRepository
import com.example.synced.feature.settings.domain.AppSettings
import com.example.synced.feature.settings.domain.SettingsRepository
import com.example.synced.feature.settings.domain.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    // Cross-feature on purpose: "clear cache" is an app-level data action, and the
    // content repository is the only owner of the Room cache.
    private val contentRepository: ContentRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _events = MutableSharedFlow<UiEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events = _events.asSharedFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun clearCache() {
        viewModelScope.launch {
            contentRepository.clearCache()
            _events.tryEmit(UiEvent.ShowSnackbar("Cached content cleared"))
        }
    }
}
