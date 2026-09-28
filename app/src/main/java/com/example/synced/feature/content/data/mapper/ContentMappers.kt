package com.example.synced.feature.content.data.mapper

import com.example.synced.feature.content.data.local.CommentEntity
import com.example.synced.feature.content.data.local.PostEntity
import com.example.synced.feature.content.data.remote.CommentDto
import com.example.synced.feature.content.data.remote.PostDto
import com.example.synced.feature.content.domain.Comment
import com.example.synced.feature.content.domain.Post

// Strict layer boundaries: DTOs never leak past data, entities never reach the UI.

/** Feed rows carry only the excerpt; the single-article fetch upgrades it to the full body. */
fun PostDto.toDomain() = Post(
    id = id,
    userId = user?.user_id ?: 0,
    title = title,
    body = body_markdown?.let(::markdownToPlain) ?: description.orEmpty(),
    authorName = user?.name?.trim()?.takeIf { it.isNotEmpty() } ?: user?.username,
)

fun Post.toEntity(syncedAt: Long) = PostEntity(
    id = id,
    userId = userId,
    title = title,
    body = body,
    authorName = authorName,
    syncedAt = syncedAt,
)

fun PostDto.toEntity(syncedAt: Long) = toDomain().toEntity(syncedAt)

fun PostEntity.toDomain() = Post(
    id = id,
    userId = userId,
    title = title,
    body = body,
    authorName = authorName,
)

/** Depth-first flatten of a Dev.to comment thread (parents + nested replies). */
fun List<CommentDto>.flattenThread(): List<CommentDto> = flatMap { comment ->
    listOf(comment) + (comment.children?.flattenThread().orEmpty())
}

fun CommentDto.toEntity(postId: Int) = CommentEntity(
    id = id_code.fnv1a32(),
    postId = postId,
    name = user?.name?.trim()?.takeIf { it.isNotEmpty() } ?: user?.username ?: "Unknown",
    email = user?.username.orEmpty(),
    body = htmlToPlain(body_html.orEmpty()),
)

fun CommentEntity.toDomain() = Comment(
    id = id,
    postId = postId,
    name = name,
    email = email,
    body = body,
)

/** Stable 32-bit FNV-1a — Dev.to comments only expose base62 string ids. */
internal fun String.fnv1a32(): Int {
    var hash = -2128331035 // 0x811C9DC5, the FNV-1a 32-bit offset basis
    for (char in this) {
        hash = hash xor char.code
        hash *= 16777619 // FNV-1a 32-bit prime
    }
    return hash
}
