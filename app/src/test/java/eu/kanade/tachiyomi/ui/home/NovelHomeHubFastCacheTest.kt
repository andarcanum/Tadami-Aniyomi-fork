package eu.kanade.tachiyomi.ui.home

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NovelHomeHubFastCacheTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var cache: NovelHomeHubFastCache

    @BeforeEach
    fun setup() {
        context = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        editor = mockk(relaxed = true)

        every { context.getSharedPreferences("novel_home_hub_cache", Context.MODE_PRIVATE) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.apply() } returns Unit

        cache = NovelHomeHubFastCache(context)
    }

    @AfterEach
    fun tearDown() {
        verify(atLeast = 1) { prefs.edit() }
    }

    @Test
    fun `save and load preserve warm state in memory`() {
        val state = CachedNovelHomeState(
            hero = CachedNovelHeroItem(
                novelId = 7L,
                title = "Novel Hero",
                chapterNumber = 12.5,
                coverUrl = "https://example.org/hero.jpg",
                coverLastModified = 99L,
                chapterId = 70L,
            ),
            history = listOf(
                CachedNovelHistoryItem(
                    novelId = 8L,
                    title = "History One",
                    chapterNumber = 8.0,
                    coverUrl = "https://example.org/history.jpg",
                    coverLastModified = 88L,
                ),
            ),
            recommendations = listOf(
                CachedNovelRecommendationItem(
                    novelId = 9L,
                    title = "Rec One",
                    coverUrl = "https://example.org/rec.jpg",
                    coverLastModified = 77L,
                    totalCount = 10L,
                    readCount = 4L,
                ),
            ),
            userName = "Reader",
            userAvatar = "/tmp/avatar.jpg",
            isInitialized = true,
        )

        cache.save(state)
        cache.updateUserName("Archivist")
        cache.updateUserAvatar("/tmp/avatar-2.jpg")
        cache.markInitialized()

        val loaded = cache.load()

        assertEquals(7L, loaded.hero?.novelId)
        assertEquals(70L, loaded.hero?.chapterId)
        assertEquals("Novel Hero", loaded.hero?.title)
        assertEquals(1, loaded.history.size)
        assertEquals(8L, loaded.history.first().novelId)
        assertEquals(1, loaded.recommendations.size)
        assertEquals(4L, loaded.recommendations.first().readCount)
        assertEquals("Archivist", loaded.userName)
        assertEquals("/tmp/avatar-2.jpg", loaded.userAvatar)
        assertTrue(loaded.isInitialized)
        assertFalse(loaded.isEmpty)

        verify { editor.putString("user_name", "Archivist") }
        verify { editor.putString("user_avatar", "/tmp/avatar-2.jpg") }
        verify { editor.putBoolean("initialized", true) }
    }
}
