package com.example.synced.feature.content.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.example.synced.feature.content.data.local.PostEntity
import com.example.synced.feature.content.data.local.SyncedDatabase
import com.example.synced.feature.content.data.remote.CommentDto
import com.example.synced.feature.content.data.remote.ContentApi
import com.example.synced.feature.content.data.remote.PostDto
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Mediator contract: REFRESH never touches the network (the repository owns page-1
 * syncs — this keeps Room writes from retriggering fetches), APPEND fetches the page
 * implied by the row count into Room, and pagination ends when the server returns a
 * partial page or a fetch adds nothing new.
 */
@OptIn(ExperimentalPagingApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContentRemoteMediatorTest {

    private lateinit var db: SyncedDatabase
    private lateinit var api: FakePagingApi
    private lateinit var mediator: ContentRemoteMediator

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            SyncedDatabase::class.java,
        ).allowMainThreadQueries().build()
        api = FakePagingApi()
        mediator = ContentRemoteMediator(api, db.postDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(id: Int) = PostEntity(
        id = id,
        userId = 1,
        title = "T$id",
        body = "body",
        authorName = null,
        syncedAt = 0L,
    )

    private fun dto(id: Int) = PostDto(id = id, title = "T$id", description = null)

    private fun state() = PagingState<Int, PostEntity>(
        pages = emptyList(),
        anchorPosition = null,
        config = PagingConfig(pageSize = POSTS_PER_PAGE),
        leadingPlaceholderCount = 0,
    )

    private suspend fun seed(count: Int) =
        db.postDao().insertAll((1..count).map { entity(it) })

    @Test
    fun `refresh never hits the network and always keeps pagination open`() = runTest {
        // Empty cache: bootstrap path (repository sync or the append fetch feeds it).
        val empty = mediator.load(LoadType.REFRESH, state())
        assertTrue((empty as RemoteMediator.MediatorResult.Success).endOfPaginationReached.not())

        // Full page, partial page — refresh only re-opens pagination after Room writes.
        seed(POSTS_PER_PAGE)
        val full = mediator.load(LoadType.REFRESH, state())
        assertTrue((full as RemoteMediator.MediatorResult.Success).endOfPaginationReached.not())

        seed(42)
        val partial = mediator.load(LoadType.REFRESH, state())
        assertTrue((partial as RemoteMediator.MediatorResult.Success).endOfPaginationReached.not())
        assertTrue(api.requestedPages.isEmpty())
    }

    @Test
    fun `append fetches the next page and stores it in room`() = runTest {
        seed(POSTS_PER_PAGE)
        // Ids 101..200 — distinct from the seeded page so inserts aren't REPLACE no-ops.
        api.pages = mapOf(2 to (POSTS_PER_PAGE + 1..POSTS_PER_PAGE * 2).map { dto(it) })

        val result = mediator.load(LoadType.APPEND, state())

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertFalse((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(listOf(2), api.requestedPages)
        assertEquals(POSTS_PER_PAGE * 2, db.postDao().count())
    }

    @Test
    fun `append ends pagination after a partial page from the server`() = runTest {
        seed(POSTS_PER_PAGE)
        api.pages = mapOf(2 to (POSTS_PER_PAGE + 1..POSTS_PER_PAGE + 30).map { dto(it) })

        val result = mediator.load(LoadType.APPEND, state())

        assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(POSTS_PER_PAGE + 30, db.postDao().count())
    }

    @Test
    fun `append overlap-fetches the page implied by a non-aligned cache`() = runTest {
        // A silent sync inserted 10 newly published articles → count is 110, not a
        // multiple of 100. Next page must still be 2 (overlap deduped by upsert).
        seed(POSTS_PER_PAGE + 10)
        api.pages = mapOf(2 to (POSTS_PER_PAGE + 1..POSTS_PER_PAGE * 2).map { dto(it) })

        val result = mediator.load(LoadType.APPEND, state())

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertFalse((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(listOf(2), api.requestedPages)
        assertEquals(POSTS_PER_PAGE * 2, db.postDao().count())
    }

    @Test
    fun `append ends pagination when a full page adds no new rows`() = runTest {
        // Whole window already cached (overlap-only fetch) — stop instead of looping.
        seed(POSTS_PER_PAGE)
        api.pages = mapOf(2 to (1..POSTS_PER_PAGE).map { dto(it) })

        val result = mediator.load(LoadType.APPEND, state())

        assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(POSTS_PER_PAGE, db.postDao().count())
    }

    @Test
    fun `append surfaces network failures as errors so the UI can offer retry`() = runTest {
        seed(POSTS_PER_PAGE)
        api.failure = IOException("offline")

        val result = mediator.load(LoadType.APPEND, state())

        assertTrue(result is RemoteMediator.MediatorResult.Error)
        // A failed fetch must leave the cache exactly as it was.
        assertEquals(POSTS_PER_PAGE, db.postDao().count())
    }
}

/** Fake Dev.to API: serves per-page buckets and records every page it is asked for. */
private class FakePagingApi : ContentApi {
    var pages: Map<Int, List<PostDto>> = emptyMap()
    var failure: Throwable? = null
    val requestedPages = mutableListOf<Int>()

    override suspend fun getPosts(perPage: Int, page: Int): List<PostDto> {
        requestedPages += page
        failure?.let { throw it }
        return pages[page].orEmpty()
    }

    override suspend fun getPost(id: Int): PostDto =
        throw UnsupportedOperationException("not used in these tests")

    override suspend fun searchArticles(query: String, perPage: Int): List<PostDto> =
        throw UnsupportedOperationException("not used in these tests")

    override suspend fun getComments(articleId: Int, perPage: Int): List<CommentDto> =
        throw UnsupportedOperationException("not used in these tests")
}
