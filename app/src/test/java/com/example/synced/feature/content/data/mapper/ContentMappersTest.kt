package com.example.synced.feature.content.data.mapper

import com.example.synced.feature.content.data.local.PostEntity
import com.example.synced.feature.content.data.remote.CommentDto
import com.example.synced.feature.content.data.remote.PostDto
import com.example.synced.feature.content.data.remote.UserDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DTO → Entity → Domain round-trips: no fields lost, payloads cleaned. */
class ContentMappersTest {

    @Test
    fun `post excerpt becomes the body when no full body exists`() {
        val dto = PostDto(
            id = 42,
            title = "Title",
            description = "A short excerpt.",
            user = UserDto(user_id = 7, name = "  Janet  ", username = "janet"),
        )
        val domain = dto.toEntity(syncedAt = 123L).toDomain()

        assertEquals(42, domain.id)
        assertEquals(7, domain.userId)
        assertEquals("Title", domain.title)
        assertEquals("A short excerpt.", domain.body)
        assertEquals("Janet", domain.authorName)
        assertEquals(123L, dto.toEntity(syncedAt = 123L).syncedAt)
    }

    @Test
    fun `full markdown body is cleaned and preferred over the excerpt`() {
        val dto = PostDto(
            id = 1,
            title = "T",
            description = "excerpt",
            body_markdown = "## Hello\n\nRead [this](https://example.com) **now**.",
        )
        val entity = dto.toEntity(syncedAt = 0L)
        assertEquals("Hello\n\nRead this now.", entity.body)
    }

    @Test
    fun `author falls back to username and null when user is absent`() {
        val named = PostDto(id = 1, title = "T", user = UserDto(name = "   ", username = "x")).toEntity(0)
        assertEquals("x", named.authorName)
        assertNull(PostDto(id = 1, title = "T").toEntity(0).authorName)
    }

    @Test
    fun `comment maps from html, base62 id and embedded user`() {
        val dto = CommentDto(
            id_code = "3fg56",
            body_html = "<p>Hello <strong>world</strong> &amp; friends</p>",
            user = UserDto(name = "Daniel Nwaneri", username = "dannwaneri"),
        )
        val entity = dto.toEntity(postId = 9)

        assertEquals("Hello world & friends", entity.body)
        assertEquals("Daniel Nwaneri", entity.name)
        assertEquals("dannwaneri", entity.email)
        assertEquals(9, entity.postId)
        // Deterministic id from the base62 id_code.
        assertEquals(entity.id, dto.toEntity(postId = 9).id)
    }

    @Test
    fun `comment threads flatten depth first`() {
        val thread = listOf(
            CommentDto(
                id_code = "parent",
                body_html = "<p>p</p>",
                children = listOf(
                    CommentDto(id_code = "child", body_html = "<p>c</p>",
                        children = listOf(CommentDto(id_code = "grand", body_html = "<p>g</p>"))),
                ),
            ),
        )
        val flat = thread.flattenThread()
        assertEquals(listOf("parent", "child", "grand"), flat.map { it.id_code })
    }

    @Test
    fun `sanitizers decode entities and strip markup`() {
        assertEquals("Tom & Jerry’s 50%", htmlToPlain("<p>Tom &amp; Jerry&#8217;s 50&#37;</p>"))
        assertEquals("Bullet\nNext", htmlToPlain("<ul><li>Bullet</li></ul><p>Next</p>"))
        assertTrue(markdownToPlain("```kotlin\ncode()\n```").contains("code()"))
        assertEquals("- one\n- two", markdownToPlain("- one\n- two"))
    }

    @Test
    fun `entity without author maps to null author`() {
        val entity = PostEntity(
            id = 1, userId = 1, title = "t", body = "b",
            authorName = null, syncedAt = 0L,
        )
        assertNull(entity.toDomain().authorName)
    }
}
