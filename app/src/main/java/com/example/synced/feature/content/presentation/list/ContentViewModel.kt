package com.example.synced.feature.content.presentation.list

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.synced.core.common.AppError
import com.example.synced.core.common.AppResult
import com.example.synced.core.common.UiEvent
import com.example.synced.core.common.UiState
import com.example.synced.core.network.NetworkMonitor
import com.example.synced.feature.content.domain.ContentRepository
import com.example.synced.feature.content.domain.Post
import com.example.synced.feature.content.domain.SearchSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Search input settles for this long before the search pipeline runs. */
private const val SEARCH_DEBOUNCE_MS = 250L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContentViewModel @Inject constructor(
    private val repository: ContentRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    /** Tracks the last refresh attempt; manual flag controls the pull-to-refresh spinner. */
    private data class RefreshState(
        val inProgress: Boolean = false,
        val manual: Boolean = false,
        val error: String? = null,
        val attempted: Boolean = false,
    )

    private val refreshState = MutableStateFlow(RefreshState())

    private val _events = MutableSharedFlow<UiEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val events = _events.asSharedFlow()

    val isOffline: StateFlow<Boolean> = networkMonitor.isOnline
        .map { !it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = false)

    val uiState: StateFlow<UiState<List<Post>>> = combine(
        repository.observePosts(),
        refreshState,
        networkMonitor.isOnline,
    ) { posts, refresh, online ->
        when {
            // Cache wins: once we have data, refreshes never blank the screen.
            posts.isNotEmpty() -> UiState.Content(
                data = posts,
                isRefreshing = refresh.inProgress && refresh.manual,
            )

            refresh.inProgress -> UiState.Loading
            refresh.error != null -> UiState.Error(refresh.error)
            // Cold start with no cache and no connectivity — surface it immediately.
            !online && !refresh.attempted -> UiState.Error(AppError.Offline.userMessage)
            refresh.attempted -> UiState.Empty
            else -> UiState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private val _searchQuery = MutableStateFlow("")

    /** Raw search input (kept verbatim for the text field); blank = feed. */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * The query the search pipeline is actually showing. Debounced: while the
     * user types it stays on the previous value, so the screen header always
     * matches the rendered list; clearing falls through instantly (empty = feed).
     */
    private val appliedQueryFlow: Flow<String> = _searchQuery
        .map(String::trim)
        .distinctUntilChanged()
        .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }

    val appliedQuery: StateFlow<String> = appliedQueryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = "")

    /**
     * Paged feed for infinite scroll. [cachedIn] keeps the stream alive across
     * config/recreation so Room invalidations (refresh, appended pages)
     * propagate once. Search never touches this stream — swapping it per query
     * would reset the feed's anchors.
     */
    val pagingData: Flow<PagingData<Post>> = repository.pagedPosts().cachedIn(viewModelScope)

    /** Bumped by [retrySearch]; a new value re-runs the current query's remote leg. */
    private val searchAttempts = MutableStateFlow(0)

    /**
     * Merged local + online results for the settled query (idle while blank).
     * Starts with [SearchSnapshot.onlinePending] so the first frame after the
     * debounce is a loading state, never a premature "no matches".
     */
    val searchState: StateFlow<SearchSnapshot> =
        combine(appliedQueryFlow, searchAttempts) { query, attempt -> query to attempt }
            .distinctUntilChanged()
            .flatMapLatest { (query, _) ->
                if (query.isEmpty()) flowOf(SearchSnapshot()) else repository.searchPosts(query)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                SearchSnapshot(onlinePending = true),
            )

    /** Search input changed (fires on every keystroke — debounced downstream). */
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    /** Clears the query and returns the feed; keeps the search field open. */
    fun clearSearch() {
        _searchQuery.value = ""
    }

    /** Re-runs the settled query's online leg (offline retry) — no retyping. */
    fun retrySearch() {
        searchAttempts.value++
    }

    init {
        viewModelScope.launch {
            // First true emission = startup refresh; later false→true edges = reconnected.
            // While offline nothing runs — the feed simply keeps serving Room data.
            networkMonitor.isOnline
                .distinctUntilChanged()
                .collect { online -> if (online) refresh(manual = false) }
        }
    }

    /** Pull-to-refresh / retry entry point (shows progress). */
    fun refresh() = refresh(manual = true)

    /**
     * Silent sync entry point (startup / reconnect): upserts the newest page without
     * dropping pages the user has paged in — their scroll position survives.
     */
    private fun refresh(manual: Boolean) {
        viewModelScope.launch {
            if (refreshState.value.inProgress) return@launch
            Log.i("SYNCED", "refresh start manual=$manual")
            refreshState.update {
                RefreshState(inProgress = true, manual = manual, attempted = true)
            }
            when (val result = repository.refresh(replaceCache = manual)) {
                is AppResult.Success -> {
                    Log.i("SYNCED", "refresh done manual=$manual")
                    refreshState.value = RefreshState(attempted = true)
                }

                is AppResult.Error -> {
                    Log.i("SYNCED", "refresh error manual=$manual")
                    val message = result.error.userMessage
                    refreshState.value = RefreshState(error = message, attempted = true)
                    // With a visible cache a failed refresh is only a hiccup — tell the
                    // user via snackbar instead of replacing the content with an error.
                    if (repository.observePosts().first().isNotEmpty()) {
                        _events.tryEmit(UiEvent.ShowSnackbar(message))
                    }
                }

                AppResult.Loading -> Unit
            }
        }
    }
}
