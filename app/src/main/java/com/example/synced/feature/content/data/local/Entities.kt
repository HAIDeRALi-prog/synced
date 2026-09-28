package com.example.synced.feature.content.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey val id: Int,
    val userId: Int,
    val title: String,
    val body: String,
    /** Denormalized from the article's embedded author at sync time. */
    val authorName: String?,
    /** Epoch millis of the last successful refresh that wrote this row. */
    val syncedAt: Long,
)

@Entity(
    tableName = "comments",
    indices = [Index("postId")],
)
data class CommentEntity(
    @PrimaryKey val id: Int,
    val postId: Int,
    val name: String,
    val email: String,
    val body: String,
)
