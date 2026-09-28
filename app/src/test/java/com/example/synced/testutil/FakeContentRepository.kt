package com.example.synced.testutil

import androidx.paging.PagingData
import com.example.synced.core.common.AppError
import com.example.synced.core.common.AppResult
import com.example.synced.feature.content.domain.Comment
import com.example.synced.feature.content.domain.ContentRepository
import com.example.synced.feature.content.domain.Post
import com.example.synced.feature.content.domain.SearchSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.yield

/**
 * In-memory fake: [postsToReturn]/[commentsToReturn] represent the "server", while
 * [seedPosts] pre-populates the cache (as if the app had synced earlier).
 * refresh() success copies server data into the cache, mirroring the real impl.
 */
class FakeContentRepository : ContentRepository {

    var postsToReturn: List<Post> = emptyList()
    var commentsToReturn: List<Comment> = emptyList()
    var refreshResult: AppResult<Unit> = AppResult.Success(Unit)
    var refreshCommentsResult: AppResult<Unit> = AppResult.Success(Unit)

    /** "Server" results for the online leg of search. */
    var onlineSearchResults: List<Post> = emptyList()

    /** Makes the online leg of search fail (offline / server error). */
    var searchFailsOnline: Boolean = false

    /** Full override for refresh (e.g. tests that need to suspend on a gate). */
    var refreshBehavior: (suspend () -> AppResult<Unit>)? = null

    var refreshCount = 0
    var refreshPostCount = 0
    var refreshPostResult: AppResult<Unit> = AppResult.Success(Unit)
    var refreshCommentsCount = 0
    var clearCacheCount = 0
    var fetchPostCount = 0

    /** Full override for fetchPost (e.g. offline failures). */
    var fetchPostResult: AppResult<Post>? = null

    var pagedPostsCount = 0
    var searchCount = 0
    var lastSearchQuery: String? = null

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    private val _commentsByPost = MutableStateFlow<Map<Int, List<Comment>>>(emptyMap())

    fun seedPosts(posts: List<Post>) {
        _posts.value = posts
    }

    override fun observePosts(): Flow<List<Post>> = _posts

    /** Empty paging data — paging itself is covered by ContentRemoteMediatorTest. */
    override fun pagedPosts(): Flow<PagingData<Post>> {
        pagedPostsCount++
        return flowOf(PagingData.empty())
    }

    /**
     * Mirrors the real merge contract: emits pending first (local matches show
     * immediately), then the snapshot with online results ahead of local-only
     * extras — or a failure marker when [searchFailsOnline] is set.
     */
    override fun searchPosts(query: String): Flow<SearchSnapshot> {
        searchCount++
        lastSearchQuery = query
        return flow {
            emit(SearchSnapshot(onlinePending = true))
            val cached = _posts.value.filter { post ->
                post.title.contains(query, ignoreCase = true) ||
                    post.body.contains(query, ignoreCase = true) ||
                    post.authorName?.contains(query, ignoreCase = true) == true
            }
            emit(
                SearchSnapshot(
                    posts = onlineSearchResults +
                        cached.filter { local -> onlineSearchResults.none { it.id == local.id } },
                    onlinePending = false,
                    onlineFailed = searchFailsOnline,
                ),
            )
        }
    }

    override fun observePost(id: Int): Flow<Post?> =
        _posts.map { posts -> posts.firstOrNull { it.id == id } }

    override fun observeComments(postId: Int): Flow<List<Comment>> =
        _commentsByPost.map { it[postId].orEmpty() }

    override suspend fun refresh(replaceCache: Boolean): AppResult<Unit> {
        refreshCount++
        // Yield like a real network call would suspend — otherwise back-to-back state
        // updates get conflated and collectors never observe the inProgress transition.
        yield()
        refreshBehavior?.let { return it() }
        val result = refreshResult
        if (result is AppResult.Success) _posts.value = postsToReturn
        return result
    }

    override suspend fun refreshPost(id: Int): AppResult<Unit> {
        refreshPostCount++
        yield()
        return refreshPostResult
    }

    override suspend fun fetchPost(id: Int): AppResult<Post> {
        fetchPostCount++
        yield()
        fetchPostResult?.let { return it }
        return postsToReturn.firstOrNull { it.id == id }?.let { AppResult.Success(it) }
            ?: AppResult.Error(AppError.Http(404, "Not Found"))
    }

    override suspend fun refreshComments(postId: Int): AppResult<Unit> {
        refreshCommentsCount++
        yield()
        val result = refreshCommentsResult
        if (result is AppResult.Success) {
            _commentsByPost.value = _commentsByPost.value + (postId to commentsToReturn)
        }
        return result
    }

    override suspend fun clearCache() {
        clearCacheCount++
        _posts.value = emptyList()
        _commentsByPost.value = emptyMap()
    }
}
