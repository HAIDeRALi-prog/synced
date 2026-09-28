package com.example.synced.feature.content.data

import com.example.synced.core.common.ApiException
import com.example.synced.core.common.AppError
import com.example.synced.core.common.AppResult
import com.example.synced.feature.content.data.remote.CommentDto
import com.example.synced.feature.content.data.remote.ContentApi
import com.example.synced.feature.content.data.remote.PostDto
import com.example.synced.feature.content.data.remote.UserDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Repository tests against a real (in-memory) Room database + a fake API —
 * proves the offline-first contract: reads always come from Room, refreshes
 * only replace the cache on success.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContentRepositoryTest {

    private lateinit var db: com.example.synced.feature.content.data.local.SyncedDatabase
    private lateinit var api: FakeContentApi
    private lateinit var repository: ContentRepositoryImpl

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.synced.feature.content.data.local.SyncedDatabase::class.java,
        ).allowMainThreadQueries().build()
        api = FakeContentApi()
        repository = ContentRepositoryImpl(api, db, db.postDao(), db.commentDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun article(
        id: Int,
        title: String,
        description: String? = null,
        bodyMarkdown: String? = null,
        author: String? = null,
    ) = PostDto(
        id = id,
        title = title,
        description = description,
        body_markdown = bodyMarkdown,
        user = author?.let { UserDto(name = it, username = it.lowercase().replace(' ', '-')) },
    )

    @Test
    fun `first successful refresh populates the cache with embedded authors`() = runTest {
        api.posts = listOf(article(id = 10, title = "Hello", description = "World", author = "  Leanne Graham "))

        val result = repository.refresh(replaceCache = true)
        assertTrue(result is AppResult.Success)

        val posts = repository.observePosts().first()
        assertEquals(1, posts.size)
        assertEquals(10, posts[0].id)
        assertEquals("Leanne Graham", posts[0].authorName)
        assertEquals("World", posts[0].body)
    }

    @Test
    fun `offline reads are served from the Room cache`() = runTest {
        // 1. Online: sync once so the cache has data.
        api.posts = listOf(article(id = 10, title = "Hello", description = "World"))
        repository.refresh(replaceCache = true)

        // 2. Network dies — every request throws.
        api.failure = java.io.IOException("offline")
        val refreshResult = repository.refresh(replaceCache = true)
        assertTrue(refreshResult is AppResult.Error)
        assertTrue((refreshResult as AppResult.Error).error is AppError.Offline)

        // 3. Reads still work and return exactly what was cached.
        val posts = repository.observePosts().first()
        assertEquals(1, posts.size)
        assertEquals("Hello", posts[0].title)
    }

    @Test
    fun `network failure never clears the cache`() = runTest {
        api.posts = listOf(article(id = 1, title = "A", description = "a"))
        repository.refresh(replaceCache = true)
        val before = repository.observePosts().first()

        api.failure = ApiException(503, "HTTP 503")
        val result = repository.refresh(replaceCache = true)
        assertTrue(result is AppResult.Error)
        assertEquals(503, ((result as AppResult.Error).error as AppError.Http).code)

        val after = repository.observePosts().first()
        assertEquals(before, after)
    }

    @Test
    fun `silent refresh upserts page one without dropping appended pages`() = runTest {
        api.posts = listOf(article(id = 10, title = "Hello"), article(id = 11, title = "Old"))
        repository.refresh(replaceCache = true)

        // Simulate an older page the user paged in before the reconnect sync.
        db.postDao().insertAll(
            listOf(
                com.example.synced.feature.content.data.local.PostEntity(
                    id = 5, userId = 1, title = "Paged in", body = "b", authorName = null, syncedAt = 0,
                ),
            ),
        )

        api.posts = listOf(article(id = 10, title = "Hello v2"), article(id = 12, title = "New"))
        assertTrue(repository.refresh(replaceCache = false) is AppResult.Success)

        // Appended row survives; page-one rows are refreshed in place.
        val ids = repository.observePosts().first().map { it.id }.sorted()
        assertEquals(listOf(5, 10, 11, 12), ids)
        assertEquals("Hello v2", repository.observePost(10).first()!!.title)
    }

    @Test
    fun `refreshPost upgrades the cached excerpt to the full body`() = runTest {
        api.posts = listOf(article(id = 10, title = "Hello", description = "Excerpt…"))
        repository.refresh(replaceCache = true)
        assertEquals("Excerpt…", repository.observePost(10).first()!!.body)

        // The single-article endpoint carries body_markdown.
        api.posts = listOf(
            article(id = 10, title = "Hello", description = "Excerpt…", bodyMarkdown = "## Full\n\nThe whole article."),
        )
        assertTrue(repository.refreshPost(10) is AppResult.Success)

        val full = repository.observePost(10).first()!!.body
        assertEquals("Full\n\nThe whole article.", full)
    }

    @Test
    fun `comments are cached per post with html stripped and replies flattened`() = runTest {
        api.commentsByArticle = mapOf(
            1 to listOf(
                CommentDto(
                    id_code = "aaaaa",
                    body_html = "<p>nice <strong>read</strong></p>",
                    user = UserDto(name = "First", username = "first"),
                    children = listOf(
                        CommentDto(
                            id_code = "bbbbbb",
                            body_html = "<p>reply &amp; more</p>",
                            user = UserDto(name = "Kid", username = "kid"),
                        ),
                    ),
                ),
            ),
            2 to listOf(
                CommentDto(id_code = "ccccc", body_html = "<p>meh</p>", user = UserDto(name = "Second")),
            ),
        )

        assertTrue(repository.refreshComments(1) is AppResult.Success)

        val forPost1 = repository.observeComments(1).first()
        assertEquals(2, forPost1.size) // parent + flattened reply
        assertEquals("nice read", forPost1[0].body)
        assertEquals("reply & more", forPost1[1].body)
        assertEquals(0, repository.observeComments(2).first().size)
    }

    @Test
    fun `clearCache wipes posts and comments`() = runTest {
        api.posts = listOf(article(id = 1, title = "A", description = "a"))
        api.commentsByArticle = mapOf(
            1 to listOf(CommentDto(id_code = "zzzzz", body_html = "<p>b</p>")),
        )
        repository.refresh(replaceCache = true)
        repository.refreshComments(1)

        repository.clearCache()

        assertEquals(0, repository.observePosts().first().size)
        assertEquals(0, repository.observeComments(1).first().size)
    }

    @Test
    fun `search matches title, body and author case-insensitively with escaped wildcards`() = runTest {
        api.posts = listOf(
            article(id = 1, title = "Room is the source of truth", description = "cache notes", author = "Ada Lovelace"),
            article(id = 2, title = "100% coverage", description = "underscores _ and percent", author = "Grace Hopper"),
            article(id = 3, title = "Untouched", description = "unrelated", author = "Alan Turing"),
        )
        assertTrue(repository.refresh(replaceCache = true) is AppResult.Success)

        // Title match, ASCII case-insensitive (pattern is uppercase).
        assertEquals(listOf(1), searchIds("SOURCE".toLikePattern()))
        // Body (excerpt) match.
        assertEquals(listOf(1), searchIds("cache".toLikePattern()))
        // Author match; multi-hit results keep the feed's id DESC order.
        assertEquals(listOf(3, 2, 1), searchIds("a".toLikePattern()))
        // No match.
        assertEquals(emptyList<Int>(), searchIds("nothing-here".toLikePattern()))

        // Wildcards in the user's input match literally, not as SQL patterns:
        // raw "%" would match every row, escaped "%" only the row containing it.
        assertEquals(listOf(2), searchIds("%".toLikePattern()))
        // Same for "_": unescaped it matches any single character in every row.
        assertEquals(listOf(2), searchIds("_".toLikePattern()))
    }

    /** Loads the DAO's live search flow once and returns the matched ids. */
    private suspend fun searchIds(pattern: String): List<Int> =
        db.postDao().search(pattern).first().map { it.id }

    @Test
    fun `online search merges server results ahead of cached matches without persisting`() = runTest {
        // Cache: two matches from an earlier sync, plus an unrelated row.
        api.posts = listOf(
            article(id = 1, title = "Cached kotlin notes", description = "local one"),
            article(id = 3, title = "Another kotlin thing", description = "local three"),
            article(id = 9, title = "Unrelated", description = "x"),
        )
        assertTrue(repository.refresh(replaceCache = true) is AppResult.Success)

        // Server ranks two articles first; one overlaps a cached id.
        api.searchResults = listOf(
            article(id = 5, title = "Fresh kotlin post", description = "online"),
            article(id = 1, title = "Cached kotlin notes (server title)", description = "online view"),
        )

        val snapshot = repository.searchPosts("kotlin").first { !it.onlinePending }
        assertFalse(snapshot.onlineFailed)
        // Server relevance first (overlap deduped to the server's copy), then cached extras.
        assertEquals(listOf(5, 1, 3), snapshot.posts.map { it.id })
        assertEquals("Cached kotlin notes (server title)", snapshot.posts.first { it.id == 1 }.title)

        // Online-only rows never land in Room — the mediator's count math depends on it.
        assertNull(repository.observePost(5).first())
        assertEquals(3, repository.observePosts().first().size)
    }

    @Test
    fun `failed online search falls back to local matches`() = runTest {
        api.posts = listOf(article(id = 1, title = "Kotlin cache", description = "only local"))
        assertTrue(repository.refresh(replaceCache = true) is AppResult.Success)

        api.failure = java.io.IOException("offline")

        val snapshot = repository.searchPosts("kotlin").first { !it.onlinePending }
        assertTrue(snapshot.onlineFailed)
        assertEquals(listOf(1), snapshot.posts.map { it.id })
    }

    @Test
    fun `fetchPost returns a single article without writing to the cache`() = runTest {
        api.posts = listOf(
            article(id = 42, title = "From the server", description = "full body", author = "Ada"),
        )

        val result = repository.fetchPost(42)
        assertTrue(result is AppResult.Success)
        assertEquals("From the server", (result as AppResult.Success).data.title)
        // In-memory only: the feed's row count must not see search-only posts.
        assertEquals(0, repository.observePosts().first().size)

        api.failure = java.io.IOException("offline")
        assertTrue(repository.fetchPost(7) is AppResult.Error)
    }
}

/** In-memory fake with a switchable failure. */
private class FakeContentApi : ContentApi {
    var posts: List<PostDto> = emptyList()
    var searchResults: List<PostDto> = emptyList()
    var commentsByArticle: Map<Int, List<CommentDto>> = emptyMap()
    var failure: Throwable? = null

    override suspend fun getPosts(perPage: Int, page: Int): List<PostDto> =
        failure?.let { throw it } ?: if (page == 1) posts else emptyList()

    override suspend fun getPost(id: Int): PostDto =
        failure?.let { throw it } ?: posts.first { it.id == id }

    override suspend fun searchArticles(query: String, perPage: Int): List<PostDto> =
        failure?.let { throw it } ?: searchResults

    override suspend fun getComments(articleId: Int, perPage: Int): List<CommentDto> =
        failure?.let { throw it } ?: commentsByArticle[articleId].orEmpty()
}
