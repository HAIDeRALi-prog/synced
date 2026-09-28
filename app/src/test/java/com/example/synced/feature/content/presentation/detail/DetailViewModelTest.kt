package com.example.synced.feature.content.presentation.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.synced.core.common.AppError
import com.example.synced.core.common.AppResult
import com.example.synced.core.common.UiState
import com.example.synced.feature.content.domain.Comment
import com.example.synced.feature.content.domain.Post
import com.example.synced.testutil.FakeContentRepository
import com.example.synced.testutil.FakeNetworkMonitor
import com.example.synced.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val post = Post(
        id = 1,
        userId = 1,
        title = "Cached post",
        body = "Full body",
        authorName = "Leanne",
    )
    private val comment = Comment(
        id = 1,
        postId = 1,
        name = "Nice post",
        email = "spam@example.com",
        body = "Thanks!",
    )

    private fun viewModel(
        repository: FakeContentRepository,
        monitor: FakeNetworkMonitor = FakeNetworkMonitor(initialOnline = true),
        postId: Int = 1,
    ) = DetailViewModel(
        repository = repository,
        networkMonitor = monitor,
        savedStateHandle = SavedStateHandle(mapOf("postId" to postId)),
    )

    @Test
    fun `cached post renders and comments are fetched when online`() = runTest {
        val repository = FakeContentRepository().apply {
            seedPosts(listOf(post))
            commentsToReturn = listOf(comment)
        }
        val vm = viewModel(repository)

        vm.uiState.test {
            val initial = awaitItem()
            assertEquals(UiState.Loading, initial.post)
            assertEquals(UiState.Loading, initial.comments)

            val state = awaitItem()
            assertEquals(post, (state.post as UiState.Content).data)
            assertEquals(listOf(comment), (state.comments as UiState.Content).data)
        }
        advanceUntilIdle()
        assertEquals(1, repository.refreshCommentsCount)
        assertEquals(0, repository.refreshCount) // post was already cached — no full refetch
    }

    @Test
    fun `comments fetch failure keeps post visible with comments error`() = runTest {
        val repository = FakeContentRepository().apply {
            seedPosts(listOf(post))
            refreshCommentsResult = AppResult.Error(AppError.Offline)
        }
        val vm = viewModel(repository)

        vm.uiState.test {
            awaitItem() // initial Loading/Loading
            val state = awaitItem()
            assertEquals(post, (state.post as UiState.Content).data)
            assertTrue(state.comments is UiState.Error)
            assertEquals(
                AppError.Offline.userMessage,
                (state.comments as UiState.Error).message,
            )
        }
    }

    @Test
    fun `missing post with healthy server shows unavailable error`() = runTest {
        val repository = FakeContentRepository() // server also returns nothing for the post
        val vm = viewModel(repository)

        vm.uiState.test {
            awaitItem() // initial Loading/Loading
            val state = awaitItem()
            assertTrue(state.post is UiState.Error)
            assertEquals("This post isn't available.", (state.post as UiState.Error).message)
        }
        advanceUntilIdle()
        assertEquals(1, repository.fetchPostCount)
        assertEquals(0, repository.refreshCount) // never a feed-wide sync
    }

    @Test
    fun `uncached post is fetched in memory when online`() = runTest {
        val repository = FakeContentRepository().apply {
            postsToReturn = listOf(post) // server has it, cache doesn't (online search hit)
        }
        val vm = viewModel(repository)

        vm.uiState.test {
            awaitItem() // initial Loading/Loading
            val state = awaitItem()
            assertEquals(post, (state.post as UiState.Content).data)
        }
        advanceUntilIdle()
        assertEquals(1, repository.fetchPostCount)
        assertEquals(0, repository.refreshCount)
    }

    @Test
    fun `offline with empty cache surfaces offline messages`() = runTest {
        val repository = FakeContentRepository()
        val monitor = FakeNetworkMonitor(initialOnline = false)
        val vm = viewModel(repository, monitor)

        vm.uiState.test {
            awaitItem() // initial Loading/Loading
            val state = awaitItem()
            assertEquals(AppError.Offline.userMessage, (state.post as UiState.Error).message)
            assertEquals(AppError.Offline.userMessage, (state.comments as UiState.Error).message)
        }
        advanceUntilIdle()
        assertEquals(0, repository.fetchPostCount)
        assertEquals(0, repository.refreshCount)
        assertEquals(0, repository.refreshCommentsCount)
    }
}
