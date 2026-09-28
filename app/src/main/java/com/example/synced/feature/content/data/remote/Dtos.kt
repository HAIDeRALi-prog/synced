package com.example.synced.feature.content.data.remote

import com.squareup.moshi.JsonClass

// Dev.to article/comment shapes (https://dev.to/api). Moshi codegen skips unknown
// keys, so DTOs only declare the fields the app actually uses.

@JsonClass(generateAdapter = true)
data class PostDto(
    val id: Int,
    val title: String,
    /** Short excerpt — present on both list and single-article responses. */
    val description: String? = null,
    /** Full article body (markdown) — only the single-article endpoint returns it. */
    val body_markdown: String? = null,
    /** Embedded author. The list endpoint does not always include user_id. */
    val user: UserDto? = null,
)

@JsonClass(generateAdapter = true)
data class UserDto(
    val user_id: Int? = null,
    val name: String? = null,
    val username: String? = null,
)

@JsonClass(generateAdapter = true)
data class CommentDto(
    /** Base62 string id — Dev.to's numeric comment id is always null. */
    val id_code: String,
    val body_html: String? = null,
    val user: UserDto? = null,
    /** Threaded replies — flattened to a flat list at sync time. */
    val children: List<CommentDto>? = null,
)
