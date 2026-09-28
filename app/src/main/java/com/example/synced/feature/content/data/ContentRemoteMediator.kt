package com.example.synced.feature.content.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.example.synced.feature.content.data.local.PostDao
import com.example.synced.feature.content.data.local.PostEntity
import com.example.synced.feature.content.data.mapper.toEntity
import com.example.synced.feature.content.data.remote.ContentApi
import java.util.concurrent.CancellationException

/** Page size for the article feed — Dev.to's maximum. */
internal const val POSTS_PER_PAGE = 100

/**
 * Keeps Paging 3 in sync with Dev.to while Room stays the single source of truth.
 *
 * Division of labour with [ContentRepositoryImpl]:
 *  - The **repository** owns every network → Room page-1 sync (startup, reconnect,
 *    pull-to-refresh): manual refreshes swap the cache, silent ones upsert into it.
 *  - This mediator only *appends* older pages when the user scrolls past what Room
 *    has, and on [LoadType.REFRESH] never touches the network — it just reports
 *    "more may exist", so a Room write can't trigger a fetch loop.
 *
 * Page numbers derive from the row count (`count / perPage + 1`). That deliberately
 * overlap-fetches one window when a silent sync inserted newly published articles
 * (count no longer divides evenly) — upserts dedupe the overlap. [MediatorResult.Success]
 * ends pagination when the server returns a partial page or a fetch adds no new rows.
 */
@OptIn(ExperimentalPagingApi::class)
class ContentRemoteMediator(
    private val api: ContentApi,
    private val postDao: PostDao,
) : RemoteMediator<Int, PostEntity>() {

    /** Initial load serves the Room cache straight away; the repository refreshes in parallel. */
    override suspend fun initialize(): InitializeAction = InitializeAction.SKIP_INITIAL_REFRESH

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, PostEntity>,
    ): MediatorResult {
        return when (loadType) {
            // Network syncs are the repository's job; after any Room write we only
            // re-open pagination and let the source reload from the updated cache.
            LoadType.REFRESH, LoadType.PREPEND -> MediatorResult.Success(
                endOfPaginationReached = loadType == LoadType.PREPEND,
            )

            LoadType.APPEND -> {
                val cached = postDao.count()
                val page = cached / POSTS_PER_PAGE + 1
                try {
                    val posts = api.getPosts(perPage = POSTS_PER_PAGE, page = page)
                    postDao.insertAll(posts.map { it.toEntity(System.currentTimeMillis()) })
                    // Partial page = server ran out; zero growth = we already hold
                    // this whole window (both stop pagination without looping).
                    MediatorResult.Success(
                        endOfPaginationReached = posts.size < POSTS_PER_PAGE ||
                            postDao.count() == cached,
                    )
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    MediatorResult.Error(t)
                }
            }
        }
    }
}
