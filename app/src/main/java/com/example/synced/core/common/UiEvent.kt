package com.example.synced.core.common

/** One-off UI events (things that shouldn't survive recomposition or config changes). */
sealed interface UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent
}
