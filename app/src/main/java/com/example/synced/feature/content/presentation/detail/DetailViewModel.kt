package com.example.synced.feature.content.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.synced.core.common.AppError
import com.example.synced.core.common.AppResult
import com.example.synced.core.common.UiState
import com.example.synced.core.network.NetworkMonitor
import com.example.synced.feature.content.domain.Comment
import com.example.synced.feature.content.domain.ContentRepository
import com.example.synced.feature.content.domain.Post
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val post: UiState<Post> = UiState.Loading,
    val comments: UiState<List<Comment>> = UiState.Loading,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: ContentRepository,
    networkMonitor: NetworkMonitor,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val postId: Int = requireNotNull(savedStateHandle["postId"])

    private data class Attempt(
        val inProgress: Boolean = false,
        val error: String? = null,
        val attempted: Boolean = false,
    )

    private val postAttempt = MutableStateFlow(Attempt())
    private val commentsAttempt = MutableStateFlow(Attempt())

    /**
     * In-memory copy for posts that aren't in the Room cache (e.g. online
     * search results). Never promoted to the cache — the mediator's page math
     * depends on the row count, so only feed syncs may write posts.
     */
    private val fetchedPost = MutableStateFlow<Post?>(null)

    // The post side is folded into one stream because combine() only types up
    // to 5 flows.
    private val postState = combine(
        repository.observePost(postId),
        fetchedPost,
        postAttempt,
    ) { cached, fetched, tryState -> Triple(cached, fetched, tryState) }

    val uiState: StateFlow<DetailUiState> = combine(
        postState,
        repository.observeComments(postId),
        commentsAttempt,
        networkMonitor.isOnline,
    ) { (cached, fetched, postTry), comments, commentTry, online ->
        val current = cached ?: fetched
        DetailUiState(
            post = when {
                current != null -> UiState.Content(current)
                postTry.error != null -> UiState.Error(postTry.error)
                // First load is in flight (or about to be) while we're online.
                !postTry.attempted && online -> UiState.Loading
                postTry.inProgress -> UiState.Loading
                online -> UiState.Error("This post isn't available.")
                else -> UiState.Error(AppError.Offline.userMessage)
            },
            comments = when {
                comments.isNotEmpty() -> UiState.Content(comments)
                commentTry.inProgress -> UiState.Loading
                commentTry.error != null -> UiState.Error(commentTry.error)
                commentTry.attempted -> UiState.Empty
                !online -> UiState.Error(AppError.Offline.userMessage)
                else -> UiState.Loading
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    init {
        viewModelScope.launch {
            networkMonitor.isOnline
                .distinctUntilChanged()
                .collect { online ->
                    if (online) {
                        if (repository.observePost(postId).first() == null) {
                            // Not cached (deep link / online search result): fetch this
                            // one article in memory — no feed-wide sync needed.
                            if (fetchedPost.value == null) fetchPost()
                        } else {
                            // Silently upgrade the cached excerpt to the full article
                            // body; the excerpt already on screen stays if this fails.
                            viewModelScope.launch { repository.refreshPost(postId) }
                        }
                        refreshComments()
                    }
                }
        }
    }

    /**
     * Retry for the post itself: in-memory fetch when it isn't cached, silent
     * full-body upgrade when it is.
     */
    fun refreshPost() {
        viewModelScope.launch {
            if (postAttempt.value.inProgress) return@launch
            if (repository.observePost(postId).first() == null) {
                fetchPost()
            } else {
                repository.refreshPost(postId)
            }
        }
    }

    /** Fetches an uncached post into [fetchedPost]; failures leave it null. */
    private fun fetchPost() {
        viewModelScope.launch {
            if (postAttempt.value.inProgress) return@launch
            postAttempt.update { Attempt(inProgress = true, attempted = true) }
            when (val result = repository.fetchPost(postId)) {
                is AppResult.Success -> {
                    fetchedPost.value = result.data
                    postAttempt.value = Attempt(attempted = true)
                }

                is AppResult.Error -> {
                    // Offline surfaces its own message; anything else (404, server)
                    // falls through to the generic "isn't available" state.
                    postAttempt.value = if (result.error is AppError.Offline) {
                        Attempt(error = AppError.Offline.userMessage, attempted = true)
                    } else {
                        Attempt(attempted = true)
                    }
                }

                AppResult.Loading -> Unit
            }
        }
    }

    fun refreshComments() {
        viewModelScope.launch {
            if (commentsAttempt.value.inProgress) return@launch
            commentsAttempt.update { Attempt(inProgress = true, attempted = true) }
            when (val result = repository.refreshComments(postId)) {
                is AppResult.Success -> commentsAttempt.value = Attempt(attempted = true)
                is AppResult.Error -> commentsAttempt.value =
                    Attempt(error = result.error.userMessage, attempted = true)

                AppResult.Loading -> Unit
            }
        }
    }
}
