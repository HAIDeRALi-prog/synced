package com.example.synced.feature.content.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {

    @Query("SELECT * FROM posts ORDER BY id DESC")
    fun observePosts(): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts WHERE id = :id")
    fun observePost(id: Int): Flow<PostEntity?>

    @Query("SELECT * FROM posts WHERE id = :id")
    suspend fun getById(id: Int): PostEntity?

    /**
     * Feed source for Paging 3. Newest-first (id DESC) mirrors the API's page order,
     * so page 1 renders at the top and each appended (older) page lands at the end.
     */
    @Query("SELECT * FROM posts ORDER BY id DESC")
    fun pagingSource(): PagingSource<Int, PostEntity>

    /**
     * Live search over the cached feed — title, body and author. SQLite LIKE is
     * ASCII case-insensitive, which is the matching contract. [pattern] is a fully
     * built LIKE pattern (wildcards in the user's input escaped by the caller and
     * wrapped in %…%). Emits on every cache write, so results stay current.
     */
    @Query(
        """
        SELECT * FROM posts
        WHERE title LIKE :pattern ESCAPE '\'
           OR body LIKE :pattern ESCAPE '\'
           OR authorName LIKE :pattern ESCAPE '\'
        ORDER BY id DESC
        """,
    )
    fun search(pattern: String): Flow<List<PostEntity>>

    /** Drives the remote mediator's next-page math: pages are full iff count % perPage == 0. */
    @Query("SELECT COUNT(*) FROM posts")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(posts: List<PostEntity>)

    @Query("DELETE FROM posts")
    suspend fun clear()
}

@Dao
interface CommentDao {

    @Query("SELECT * FROM comments WHERE postId = :postId ORDER BY id ASC")
    fun observeComments(postId: Int): Flow<List<CommentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(comments: List<CommentEntity>)

    @Query("DELETE FROM comments WHERE postId = :postId")
    suspend fun clearForPost(postId: Int)

    @Query("DELETE FROM comments")
    suspend fun clearAll()
}
