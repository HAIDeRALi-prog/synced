package com.example.synced.feature.content.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.example.synced.core.common.AppResult
import com.example.synced.core.common.ErrorMapper
import com.example.synced.feature.content.data.local.CommentDao
import com.example.synced.feature.content.data.local.PostDao
import com.example.synced.feature.content.data.local.SyncedDatabase
import com.example.synced.feature.content.data.mapper.flattenThread
import com.example.synced.feature.content.data.mapper.toDomain
import com.example.synced.feature.content.data.mapper.toEntity
import com.example.synced.feature.content.data.remote.ContentApi
import com.example.synced.feature.content.domain.Comment
import com.example.synced.feature.content.domain.ContentRepository
import com.example.synced.feature.content.domain.Post
import com.example.synced.feature.content.domain.SearchSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

private const val COMMENTS_PER_PAGE = 100
private const val SEARCH_REMOTE_PER_PAGE = 20

/** The remote leg of a merged search: pending → results, or pending → failed. */
private data class OnlineSearch(
    val posts: List<Post> = emptyList(),
    val pending: Boolean = true,
    val failed: Boolean = false,
)

/**
 * Builds a SQL LIKE pattern from raw user input: wildcards the input may contain
 * (`%`, `_`, `\`) are escaped so they match literally, then the term is wrapped in
 * %…% for a substring search. Pair with `ESCAPE '\'` in the query.
 */
internal fun String.toLikePattern(): String {
    val escaped = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return "%$escaped%"
}

/**
 * Offline-first implementation: Room is the single source of truth — observe* methods
 * are pure Room flows, refresh* methods sync network → Room and only mutate the cache
 * after a fully successful fetch (failures leave cached data untouched).
 */
@Singleton
class ContentRepositoryImpl @Inject constructor(
    private val api: ContentApi,
    private val db: SyncedDatabase,
    private val postDao: PostDao,
    private val commentDao: CommentDao,
) : ContentRepository {

    override fun observePosts(): Flow<List<Post>> =
        postDao.observePosts().map { entities -> entities.map { it.toDomain() } }

    @OptIn(androidx.paging.ExperimentalPagingApi::class)
    override fun pagedPosts(): Flow<PagingData<Post>> = Pager(
        config = PagingConfig(
            pageSize = POSTS_PER_PAGE,
            initialLoadSize = POSTS_PER_PAGE,
            prefetchDistance = 10,
            enablePlaceholders = false,
        ),
        remoteMediator = ContentRemoteMediator(api, postDao),
        pagingSourceFactory = { postDao.pagingSource() },
    ).flow.map { paging -> paging.map { it.toDomain() } }

    override fun searchPosts(query: String): Flow<SearchSnapshot> = combine(
        postDao.search(query.toLikePattern()).map { entities -> entities.map { it.toDomain() } },
        remoteSearch(query),
    ) { local, online ->
        // Server relevance first (deduped by id), then cached-only extras id DESC.
        val onlineIds = online.posts.mapTo(mutableSetOf()) { it.id }
        SearchSnapshot(
            posts = online.posts + local.filter { it.id !in onlineIds },
            onlinePending = online.pending,
            onlineFailed = online.failed,
        )
    }

    /**
     * One-shot remote search that emits a pending state immediately (cached
     * matches render without waiting), then the server's results — or a failure
     * marker. Never throws: offline is a state, not an error.
     */
    private fun remoteSearch(query: String): Flow<OnlineSearch> = flow {
        emit(OnlineSearch())
        try {
            val posts = api.searchArticles(query, SEARCH_REMOTE_PER_PAGE).map { it.toDomain() }
            emit(OnlineSearch(posts = posts, pending = false))
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            emit(OnlineSearch(pending = false, failed = true))
        }
    }

    override fun observePost(id: Int): Flow<Post?> =
        postDao.observePost(id).map { it?.toDomain() }

    override suspend fun fetchPost(id: Int): AppResult<Post> = try {
        // Deliberately not persisted: a search-only post would add a row the
        // remote mediator's count % perPage page math never accounted for.
        AppResult.Success(api.getPost(id).toDomain())
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        AppResult.Error(ErrorMapper.map(t))
    }

    override fun observeComments(postId: Int): Flow<List<Comment>> =
        commentDao.observeComments(postId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun refresh(replaceCache: Boolean): AppResult<Unit> = try {
        // Dev.to authors ship embedded on each article — no /users join needed.
        // Page 1 = the newest 100; older pages come from the remote mediator on scroll.
        val posts = api.getPosts(perPage = POSTS_PER_PAGE, page = 1)
        val syncedAt = System.currentTimeMillis()

        db.withTransaction {
            if (replaceCache) {
                // Manual refresh: swap the cache only after the whole fetch succeeded,
                // atomically — a failed or partial refresh never wipes existing data.
                postDao.clear()
                postDao.insertAll(posts.map { it.toEntity(syncedAt) })
            } else {
                // Silent sync: write only rows that are new or actually changed.
                // REPLACE rewrites every row even when values match, and Room
                // invalidates on every write — that reload collapsed the paged
                // list's itemCount mid-scroll and clamped the scroll position.
                val changed = posts.map { it.toEntity(syncedAt) }.filter { incoming ->
                    val existing = postDao.getById(incoming.id)
                    existing == null ||
                        existing.title != incoming.title ||
                        existing.body != incoming.body ||
                        existing.authorName != incoming.authorName
                }
                if (changed.isNotEmpty()) postDao.insertAll(changed)
            }
        }
        AppResult.Success(Unit)
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        AppResult.Error(ErrorMapper.map(t))
    }

    override suspend fun refreshPost(id: Int): AppResult<Unit> = try {
        // Single-article endpoint also carries body_markdown — upserts just this row
        // so the detail screen can upgrade the cached excerpt to the full body.
        val post = api.getPost(id)
        postDao.insertAll(listOf(post.toEntity(System.currentTimeMillis())))
        AppResult.Success(Unit)
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        AppResult.Error(ErrorMapper.map(t))
    }

    override suspend fun refreshComments(postId: Int): AppResult<Unit> = try {
        val comments = api.getComments(postId, COMMENTS_PER_PAGE)
        db.withTransaction {
            commentDao.clearForPost(postId)
            commentDao.insertAll(comments.flattenThread().map { it.toEntity(postId) })
        }
        AppResult.Success(Unit)
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        AppResult.Error(ErrorMapper.map(t))
    }

    override suspend fun clearCache() {
        db.withTransaction {
            postDao.clear()
            commentDao.clearAll()
        }
    }
}
