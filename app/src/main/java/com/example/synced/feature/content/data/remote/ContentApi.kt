package com.example.synced.feature.content.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit endpoints for the Dev.to API (base URL injected via BuildConfig). */
interface ContentApi {

    @GET("articles")
    suspend fun getPosts(
        @Query("per_page") perPage: Int,
        @Query("page") page: Int,
    ): List<PostDto>

    @GET("articles/{id}")
    suspend fun getPost(@Path("id") id: Int): PostDto

    /** Full-text article search (titles, tags, body) — server-side relevance ranking. */
    @GET("articles/search")
    suspend fun searchArticles(
        @Query("q") query: String,
        @Query("per_page") perPage: Int,
    ): List<PostDto>

    @GET("comments")
    suspend fun getComments(
        @Query("a_id") articleId: Int,
        @Query("per_page") perPage: Int,
    ): List<CommentDto>
}
