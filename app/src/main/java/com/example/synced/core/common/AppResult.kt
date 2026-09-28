package com.example.synced.core.common

/**
 * One-shot operation result so the UI/domain layer never touches raw exceptions.
 * [Loading] exists for operations the caller wants to render progress for;
 * long-lived streams are exposed as Flow<UiState<T>> instead.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Error(val error: AppError) : AppResult<Nothing>
    data object Loading : AppResult<Nothing>
}
