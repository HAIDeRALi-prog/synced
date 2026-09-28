package com.example.synced.core.common

/**
 * Screen-level UI state collected from ViewModels as StateFlow.
 * [Content] carries an isRefreshing flag so pull-to-refresh can show progress
 * without dropping already-cached items.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Content<T>(val data: T, val isRefreshing: Boolean = false) : UiState<T>
    data object Empty : UiState<Nothing>
    data class Error(val message: String) : UiState<Nothing>
}
