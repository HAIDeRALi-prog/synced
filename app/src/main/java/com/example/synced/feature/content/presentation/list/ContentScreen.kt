package com.example.synced.feature.content.presentation.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.example.synced.core.common.UiEvent
import com.example.synced.core.common.UiState
import com.example.synced.core.ui.theme.LocalSpacing
import com.example.synced.feature.content.domain.SearchSnapshot
import com.example.synced.ui.components.ContentCard
import com.example.synced.ui.components.LoadingState
import com.example.synced.ui.components.OfflineBanner
import com.example.synced.ui.components.StateMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentScreen(
    onPostClick: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ContentViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val pagingItems = viewModel.pagingData.collectAsLazyPagingItems()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val appliedQuery by viewModel.appliedQuery.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val searching = appliedQuery.isNotBlank()
    var searchActive by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // Predictable back: the system back gesture closes search (and clears the
    // query) before it would navigate away from the feed.
    BackHandler(enabled = searchActive) {
        searchActive = false
        viewModel.clearSearch()
    }
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val dlog: (String) -> Unit = remember(appContext) {
        { msg: String ->
            try {
                val f = java.io.File(appContext.filesDir, "debug.log")
                if (f.length() > 200_000) f.delete()
                f.appendText("${System.currentTimeMillis() % 1000000} $msg\n")
            } catch (_: Throwable) {
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { uiState }.collect {
            dlog("state=${it::class.simpleName} size=${(it as? UiState.Content)?.data?.size} refresh=${(it as? UiState.Content)?.isRefreshing}")
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { pagingItems.itemCount to pagingItems.loadState.append }.collect {
            dlog("items=${it.first} append=${it.second::class.simpleName}")
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect {
            dlog("idx=$it")
        }
    }

    var anchorPagingPos by remember { mutableStateOf(-1) }
    var anchorListIndex by remember { mutableStateOf(0) }
    var anchorCountFloor by remember { mutableStateOf(0) }
    var collapsing by remember { mutableStateOf(false) }

    // Remember the user's place only while the presented list is at its healthy
    // size. A post-invalidation reload dips itemCount for a few frames; following
    // the clamped index during (or right after) that dip would clobber the anchor.
    // Search renders its own list, so freeze (and reset) the feed anchors until
    // the query clears.
    LaunchedEffect(pagingItems, listState, searching) {
        if (searching) {
            anchorPagingPos = -1
            anchorListIndex = 0
            anchorCountFloor = 0
            collapsing = false
            return@LaunchedEffect
        }
        snapshotFlow { listState.firstVisibleItemIndex to pagingItems.itemCount }.collect { (li, count) ->
            when {
                count < anchorCountFloor -> collapsing = true

                collapsing -> {
                    // Stay frozen until the restore has put us back near the anchor.
                    if (li >= anchorListIndex - 5) collapsing = false
                }

                count > 0 && li <= count -> {
                    val pos = (li - 1).coerceAtLeast(0)
                    if (pagingItems[pos] != null) {
                        anchorListIndex = li
                        anchorPagingPos = pos
                        anchorCountFloor = count
                    }
                }
            }
        }
    }

    // A post-invalidation reload collapses itemCount for a few frames, during
    // which LazyListState clamps the scroll index. Once the window has refilled
    // past the anchor, jump back so the user doesn't lose their place.
    LaunchedEffect(pagingItems.itemCount, searching) {
        if (searching) return@LaunchedEffect
        val count = pagingItems.itemCount
        if (anchorPagingPos >= 0 && count >= anchorPagingPos + 1 &&
            listState.firstVisibleItemIndex < anchorListIndex - 5
        ) {
            dlog("restore idx=${listState.firstVisibleItemIndex} -> $anchorListIndex (items=$count)")
            listState.scrollToItem(anchorListIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        Spacer(
                            Modifier
                                .size(12.dp)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                        Text(
                            text = "Synced",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { searchActive = true }) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (searchActive) {
                SearchField(
                    query = searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onClose = {
                        searchActive = false
                        viewModel.clearSearch()
                    },
                )
            }
            OfflineBanner(visible = isOffline)
            PullToRefreshBox(
                isRefreshing = (uiState as? UiState.Content)?.isRefreshing == true,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                // Plain `when`, not Crossfade: Crossfade recreates this subtree on
                // every non-equal uiState emission (e.g. appended posts), which
                // destroyed the LazyColumn's remembered state and snapped the feed
                // back to the top after every append.
                val state = uiState
                if (searching) {
                    // Active query: cached matches stream in immediately while one
                    // remote request merges the server's relevance-ranked results.
                    SearchResults(
                        query = appliedQuery,
                        snapshot = searchState,
                        onPostClick = onPostClick,
                        onClearSearch = viewModel::clearSearch,
                        onRetry = viewModel::retrySearch,
                    )
                } else when (state) {
                        is UiState.Loading -> LoadingState()
                        is UiState.Error -> StateMessage(
                            icon = Icons.Outlined.ErrorOutline,
                            message = state.message,
                            actionLabel = "Retry",
                            onAction = viewModel::refresh,
                        )

                        UiState.Empty -> StateMessage(
                            icon = Icons.Outlined.Article,
                            message = "Nothing here yet.",
                            actionLabel = "Refresh",
                            onAction = viewModel::refresh,
                        )

                        is UiState.Content -> {
                            LaunchedEffect(Unit) { dlog("content-branch composed") }
                            Box(Modifier.fillMaxSize()) {
                            // The list stays mounted at all times: swapping it out for a
                            // full-screen spinner during a paging (re)load would reset the
                            // scroll position. The overlay only shows while the list is
                            // genuinely empty and still loading.
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    horizontal = spacing.sm,
                                    vertical = spacing.xs,
                                ),
                                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                            ) {
                                item(key = "feed-header") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = spacing.xxs),
                                    ) {
                                        Text(
                                            text = "All posts".uppercase(java.util.Locale.ROOT),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            text = pagingItems.itemCount.toString(),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.tertiary,
                                        )
                                    }
                                }
                                items(
                                    count = pagingItems.itemCount,
                                    key = pagingItems.itemKey { it.id },
                                ) { index ->
                                    pagingItems[index]?.let { post ->
                                        ContentCard(
                                            post = post,
                                            onClick = { onPostClick(post.id) },
                                        )
                                    }
                                }
                                // Infinite-scroll tail: spinner while the mediator fetches
                                // the next 100 posts, retry row if that fetch failed.
                                when (pagingItems.loadState.append) {
                                    is LoadState.Loading -> item(key = "loading-more") {
                                        LoadingMoreFooter()
                                    }

                                    is LoadState.Error -> item(key = "load-more-error") {
                                        LoadMoreErrorFooter(onRetry = pagingItems::retry)
                                    }

                                    else -> Unit
                                }
                            }
                            if (pagingItems.itemCount == 0 &&
                                pagingItems.loadState.refresh is LoadState.Loading
                            ) {
                                LoadingState()
                            }
                            }
                        }
                    }
            }
        }
    }
}

/** Tail row shown while the remote mediator fetches the next page of posts. */
@Composable
private fun LoadingMoreFooter() {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.md),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(spacing.xs))
        Text(
            text = "Loading more…",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Tail row when the next-page fetch failed (offline, server hiccup) — keeps the loaded list. */
@Composable
private fun LoadMoreErrorFooter(onRetry: () -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xs),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Couldn't load more",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(spacing.xs))
        TextButton(
            onClick = onRetry,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text("Retry")
        }
    }
}

/**
 * Search input row shown while search is open — a floating label (it persists
 * after typing, unlike a placeholder), leading search glyph, clear button, and
 * an explicit Cancel that closes the field. Autofocused so the keyboard opens.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            label = { Text("Search posts") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search,
            ),
        )
        TextButton(
            onClick = onClose,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text("Cancel")
        }
    }
}

/**
 * Search results — a dedicated LazyColumn (fresh scroll position per query, no
 * feed scroll anchoring). Cached matches render the instant the query settles;
 * one remote request then merges the server's relevance-ranked results ahead
 * of them. States cover the whole online lifecycle: pending, remote failure
 * with or without local matches, and "no matches" with a clear action.
 */
@Composable
private fun SearchResults(
    query: String,
    snapshot: SearchSnapshot,
    onPostClick: (Int) -> Unit,
    onClearSearch: () -> Unit,
    onRetry: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val listState = rememberLazyListState()
    val posts = snapshot.posts

    Box(Modifier.fillMaxSize()) {
        when {
            posts.isEmpty() && snapshot.onlinePending -> LoadingState()

            posts.isEmpty() && snapshot.onlineFailed -> StateMessage(
                icon = Icons.Outlined.ErrorOutline,
                message = "Couldn't search right now.",
                actionLabel = "Retry",
                onAction = onRetry,
            )

            posts.isEmpty() -> StateMessage(
                icon = Icons.Outlined.SearchOff,
                message = "No posts match \u201C$query\u201D.",
                actionLabel = "Clear search",
                onAction = onClearSearch,
            )

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = spacing.sm,
                    vertical = spacing.xs,
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                item(key = "search-header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = spacing.xxs),
                    ) {
                        Text(
                            text = "Results for \u201C$query\u201D"
                                .uppercase(java.util.Locale.ROOT),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = posts.size.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                if (snapshot.onlinePending) item(key = "search-pending") {
                    OnlineStatusRow(text = "Searching online\u2026", pending = true)
                }
                if (snapshot.onlineFailed) item(key = "search-offline") {
                    OnlineStatusRow(text = "Offline \u2014 saved matches only", pending = false)
                }
                items(
                    count = posts.size,
                    key = { posts[it].id },
                ) { index ->
                    val post = posts[index]
                    ContentCard(
                        post = post,
                        onClick = { onPostClick(post.id) },
                    )
                }
            }
        }
    }
}

/** One-line status under the results header: online leg in flight, or local-only. */
@Composable
private fun OnlineStatusRow(text: String, pending: Boolean) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        if (pending) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                Icons.Outlined.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
