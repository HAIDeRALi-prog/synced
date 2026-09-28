package com.example.synced.feature.content.presentation.list

import app.cash.turbine.test
import com.example.synced.core.common.AppError
import com.example.synced.core.common.AppResult
import com.example.synced.core.common.UiEvent
import com.example.synced.core.common.UiState
import com.example.synced.feature.content.domain.Post
import com.example.synced.feature.content.domain.SearchSnapshot
import com.example.synced.testutil.FakeContentRepository
import com.example.synced.testutil.FakeNetworkMonitor
import com.example.synced.testutil.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContentViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val samplePosts = listOf(
        Post(id = 1, userId = 1, title = "First", body = "Body one", authorName = "Leanne"),
        Post(id = 2, userId = 2, title = "Second", body = "Body two", authorName = "Ervin"),
    )

    private fun viewModel(
        repository: FakeContentRepository,
        monitor: FakeNetworkMonitor,
    ) = ContentViewModel(repository, monitor)

    @Test
    fun `warm cache transitions loading to content and refreshes on startup`() = runTest {
        val repository = FakeContentRepository().apply {
            seedPosts(samplePosts)
            postsToReturn = samplePosts
        }
        val monitor = FakeNetworkMonitor(initialOnline = true)

        val vm = viewModel(repository, monitor)
        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            val content = awaitItem() as UiState.Content
            assertEquals(samplePosts, content.data)
            assertFalse(content.isRefreshing)
        }
        advanceUntilIdle()
        assertTrue("startup refresh should run while online", repository.refreshCount >= 1)
    }

    @Test
    fun `empty cache plus failed refresh shows error state`() = runTest {
        val repository = FakeContentRepository().apply {
            refreshResult = AppResult.Error(AppError.Offline)
        }
        val monitor = FakeNetworkMonitor(initialOnline = true)

        val vm = viewModel(repository, monitor)
        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            val state = awaitItem()
            assertTrue(state is UiState.Error)
            assertEquals(AppError.Offline.userMessage, (state as UiState.Error).message)
        }
    }

    @Test
    fun `successful refresh with no data shows empty state`() = runTest {
        val repository = FakeContentRepository() // server returns nothing
        val monitor = FakeNetworkMonitor(initialOnline = true)

        val vm = viewModel(repository, monitor)
        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            assertEquals(UiState.Empty, awaitItem())
        }
    }

    @Test
    fun `first refresh in flight keeps loading state until data arrives`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeContentRepository().apply {
            refreshBehavior = {
                gate.await()
                seedPosts(samplePosts)
                AppResult.Success(Unit)
            }
        }
        val monitor = FakeNetworkMonitor(initialOnline = true)

        val vm = viewModel(repository, monitor)
        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            gate.complete(Unit)
            advanceUntilIdle()
            val content = awaitItem() as UiState.Content
            assertEquals(samplePosts, content.data)
        }
    }

    @Test
    fun `refresh failure with cached content keeps content and emits snackbar`() = runTest {
        val repository = FakeContentRepository().apply {
            seedPosts(samplePosts)
            postsToReturn = samplePosts
            refreshResult = AppResult.Error(AppError.Offline)
        }
        val monitor = FakeNetworkMonitor(initialOnline = true)

        val vm = viewModel(repository, monitor)
        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            assertEquals(samplePosts, (awaitItem() as UiState.Content).data)

            // Manual refresh fails, but the cached list must stay on screen.
            vm.refresh()
            advanceUntilIdle()
            val refreshing = awaitItem() as UiState.Content
            assertTrue(refreshing.isRefreshing)
            val settled = awaitItem() as UiState.Content
            assertFalse(settled.isRefreshing)
            assertEquals(samplePosts, settled.data)
        }

        vm.events.test {
            vm.refresh()
            advanceUntilIdle()
            assertEquals(
                UiEvent.ShowSnackbar(AppError.Offline.userMessage),
                awaitItem(),
            )
        }
    }

    @Test
    fun `offline banner follows network monitor`() = runTest {
        val monitor = FakeNetworkMonitor(initialOnline = true)
        val vm = viewModel(FakeContentRepository(), monitor)
        vm.isOffline.test {
            assertEquals(false, awaitItem())
            monitor.setOnline(false)
            advanceUntilIdle()
            assertEquals(true, awaitItem())
        }
    }

    @Test
    fun `connectivity restored triggers automatic refresh`() = runTest {
        val repository = FakeContentRepository().apply { postsToReturn = samplePosts }
        val monitor = FakeNetworkMonitor(initialOnline = false)

        val vm = viewModel(repository, monitor)
        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()
            assertEquals(0, repository.refreshCount)

            monitor.setOnline(true)
            advanceUntilIdle()
            assertEquals(1, repository.refreshCount)
            assertEquals(samplePosts, (expectMostRecentItem() as UiState.Content).data)
        }
    }

    @Test
    fun `search input is debounced before the pipeline runs and the feed stream never switches`() = runTest {
        val repository = FakeContentRepository().apply {
            seedPosts(samplePosts)
            postsToReturn = samplePosts // startup refresh must keep the cache
            onlineSearchResults = listOf(
                Post(id = 5, userId = 5, title = "Online first", body = "server hit", authorName = "Ada"),
            )
        }
        val monitor = FakeNetworkMonitor(initialOnline = true)
        val vm = viewModel(repository, monitor)
        // Keep the pipelines subscribed (as the screen does).
        backgroundScope.launch { vm.appliedQuery.collect {} }
        backgroundScope.launch { vm.searchState.collect {} }
        runCurrent()

        vm.pagingData.test {
            awaitItem() // feed stream is live
            val feedCalls = repository.pagedPostsCount
            assertEquals(1, feedCalls)

            // Rapid keystrokes: only the settled value should reach the pipeline.
            vm.onSearchQueryChange("F")
            vm.onSearchQueryChange("Fi")
            vm.onSearchQueryChange("First")
            advanceTimeBy(300)
            runCurrent()

            assertEquals("First", vm.appliedQuery.value)
            assertEquals(1, repository.searchCount)
            assertEquals("First", repository.lastSearchQuery)
            // Searching never swaps the feed's paging stream.
            assertEquals(feedCalls, repository.pagedPostsCount)
        }

        // Merged results: online hits first, then cached-only matches (id DESC).
        val snapshot = vm.searchState.value
        assertFalse(snapshot.onlinePending)
        assertFalse(snapshot.onlineFailed)
        assertEquals(listOf(5, 1), snapshot.posts.map { it.id })

        // Clearing bypasses the debounce — the search pipeline goes idle instantly.
        vm.clearSearch()
        runCurrent()
        assertEquals("", vm.appliedQuery.value)
        assertEquals(SearchSnapshot(), vm.searchState.value)
        assertEquals(1, repository.pagedPostsCount) // feed stream untouched throughout
    }

    @Test
    fun `applied query holds the previous value until a new one settles`() = runTest {
        val repository = FakeContentRepository().apply { seedPosts(samplePosts) }
        val monitor = FakeNetworkMonitor(initialOnline = true)
        val vm = viewModel(repository, monitor)
        backgroundScope.launch { vm.appliedQuery.collect {} }
        backgroundScope.launch { vm.searchState.collect {} }
        runCurrent()

        vm.onSearchQueryChange("First")
        advanceTimeBy(300)
        runCurrent()
        assertEquals("First", vm.appliedQuery.value)
        assertEquals(1, repository.searchCount)

        // Mid-debounce the header must keep showing the list that is still rendered.
        vm.onSearchQueryChange("Second")
        advanceTimeBy(100)
        runCurrent()
        assertEquals("First", vm.appliedQuery.value)
        assertEquals(1, repository.searchCount)

        advanceTimeBy(200)
        runCurrent()
        assertEquals("Second", vm.appliedQuery.value)
        assertEquals(2, repository.searchCount)
        assertEquals("Second", repository.lastSearchQuery)
    }

    @Test
    fun `failed online search surfaces offline state and retry re-runs it`() = runTest {
        val repository = FakeContentRepository().apply {
            seedPosts(samplePosts)
            postsToReturn = samplePosts // startup refresh must keep the cache
            searchFailsOnline = true
        }
        val monitor = FakeNetworkMonitor(initialOnline = true)
        val vm = viewModel(repository, monitor)
        backgroundScope.launch { vm.searchState.collect {} }
        runCurrent()

        vm.onSearchQueryChange("First")
        advanceTimeBy(300)
        runCurrent()
        assertEquals(1, repository.searchCount)
        assertTrue(vm.searchState.value.onlineFailed)
        // Cached matches stay visible even when the remote leg fails.
        assertEquals(listOf(1), vm.searchState.value.posts.map { it.id })

        // Retry re-runs the settled query without retyping.
        vm.retrySearch()
        runCurrent()
        assertEquals(2, repository.searchCount)
        assertEquals("First", repository.lastSearchQuery)
    }

    @Test
    fun `cached plus online results merge online-first with dedup`() = runTest {
        val repository = FakeContentRepository().apply {
            seedPosts(samplePosts) // ids 1, 2 cached
            postsToReturn = samplePosts // startup refresh must keep the cache
            onlineSearchResults = listOf(
                Post(id = 5, userId = 5, title = "Online hit", body = "b", authorName = "Ada"),
                Post(id = 1, userId = 1, title = "Online view of one", body = "b", authorName = "Leanne"),
            )
        }
        val monitor = FakeNetworkMonitor(initialOnline = true)
        val vm = viewModel(repository, monitor)
        backgroundScope.launch { vm.searchState.collect {} }
        runCurrent()

        vm.onSearchQueryChange("anything")
        advanceTimeBy(300)
        runCurrent()

        val snapshot = vm.searchState.value
        // Server relevance first (id 1 deduped to its online version), then cached extras.
        assertEquals(listOf(5, 1), snapshot.posts.map { it.id })
        assertEquals("Online view of one", snapshot.posts.first { it.id == 1 }.title)
        assertTrue(snapshot.posts.none { it.id == 2 }) // no local match for this query
    }
}
