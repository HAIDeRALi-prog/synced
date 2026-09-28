package com.example.synced.feature.content.domain

import androidx.paging.PagingData
import com.example.synced.core.common.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth contract for feed content. Observes always read from the
 * local cache (offline-first); [refresh] methods sync network → cache and report
 * success/failure without ever exposing network data directly to the UI.
 */
interface ContentRepository {

    fun observePosts(): Flow<List<Post>>

    /**
     * Paged feed for infinite scroll (Paging 3). Reads pages straight from the Room
     * cache; older pages are appended from the network by the remote mediator as the
     * user scrolls. Still offline-first: with no connectivity this simply pages
     * through whatever is cached.
     */
    fun pagedPosts(): Flow<PagingData<Post>>

    /**
     * Merged local + online search: cached matches stream live from Room while
     * one remote request adds the server's relevance-ranked results (remote
     * first, deduped by id). The first emission is local-only with
     * [SearchSnapshot.onlinePending] set, so cached matches appear instantly.
     * Online results are never written to the cache — search keeps the
     * mediator's count-based page math intact.
     */
    fun searchPosts(query: String): Flow<SearchSnapshot>

    /**
     * Fetches a single article *without persisting it* — how the detail screen
     * opens posts that aren't in the cache (e.g. online search results).
     */
    suspend fun fetchPost(id: Int): AppResult<Post>

    fun observePost(id: Int): Flow<Post?>

    fun observeComments(postId: Int): Flow<List<Comment>>

    /**
     * Fetches the article feed's first page into the cache.
     *
     * [replaceCache] = true (manual pull-to-refresh / retry): swaps the whole cache
     * for the freshest page 1 — the user is at the top and expects a clean reset.
     * [replaceCache] = false (silent startup/reconnect sync): upserts page 1 only,
     * so older pages the user paged through — and their scroll position — survive.
     * Never clears cache on failure.
     */
    suspend fun refresh(replaceCache: Boolean): AppResult<Unit>

    /** Fetches one article (full body) and upserts it — detail-screen body upgrade. */
    suspend fun refreshPost(id: Int): AppResult<Unit>

    /** Fetches comments for one post and replaces that post's cached comments. */
    suspend fun refreshComments(postId: Int): AppResult<Unit>

    /** Wipes cached posts and comments (Settings → clear cache). */
    suspend fun clearCache()
}
