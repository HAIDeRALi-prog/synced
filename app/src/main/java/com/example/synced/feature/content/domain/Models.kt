package com.example.synced.feature.content.domain

/**
 * Domain model for a feed post. [authorName] is denormalized from /users at sync
 * time so the feed never needs a join to render an author label.
 */
data class Post(
    val id: Int,
    val userId: Int,
    val title: String,
    val body: String,
    val authorName: String?,
)

data class Comment(
    val id: Int,
    val postId: Int,
    val name: String,
    val email: String,
    val body: String,
)

/**
 * One frame of merged search: cached matches stream live from Room while a
 * single remote request adds the server's relevance-ranked results (remote
 * first, deduped by id). Online results are never persisted — the feed's
 * count-based paging math depends on the Room row count staying untouched.
 */
data class SearchSnapshot(
    val posts: List<Post> = emptyList(),
    /** The remote leg is still in flight — cached matches render immediately. */
    val onlinePending: Boolean = false,
    /** The remote leg failed (offline, server) — results are local-only. */
    val onlineFailed: Boolean = false,
)
