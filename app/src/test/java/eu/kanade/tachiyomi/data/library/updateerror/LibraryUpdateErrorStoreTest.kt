package eu.kanade.tachiyomi.data.library.updateerror

import android.app.Application
import android.content.SharedPreferences
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton

/**
 * Library updates call [LibraryUpdateErrorStore.markResolved] once per successfully updated
 * entry. When there is no error to clear (the common case), the store must not re-sort and
 * re-serialize the whole error list back into SharedPreferences.
 *
 * The store is a JVM singleton whose state survives between test methods inside one Robolectric
 * sandbox, and Injekt keeps the first registered Application mock, so the whole scenario runs
 * inside a single test method with entry ids local to it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class LibraryUpdateErrorStoreTest {

    private var persistWrites = 0

    @Before
    fun setUp() {
        val prefs = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { prefs.getString(any(), any()) } returns null
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } answers {
            persistWrites++
            editor
        }
        val app = mockk<Application> {
            every { getSharedPreferences(any<String>(), any<Int>()) } returns prefs
        }
        Injekt.addSingleton(app)
    }

    @Test
    fun `markResolved skips storage rewrite when there is nothing to resolve`() {
        // Seed: two real failures persisted to storage.
        LibraryUpdateErrorStore.upsert(
            media = LibraryUpdateErrorMedia.Novel,
            entryId = NOVEL_ID,
            title = "Novel",
            sourceId = 11L,
            sourceName = "Source",
            thumbnailUrl = null,
            message = "boom",
            runType = LibraryUpdateErrorRunType.Automatic,
        )
        LibraryUpdateErrorStore.upsert(
            media = LibraryUpdateErrorMedia.Anime,
            entryId = ANIME_ID,
            title = "Anime",
            sourceId = 12L,
            sourceName = "Source",
            thumbnailUrl = null,
            message = "boom",
            runType = LibraryUpdateErrorRunType.Automatic,
        )
        persistWrites = 0

        // Resolving an existing record must remove it from memory immediately and persist once.
        // I12: the disk write is debounced onto the store's IO scope now, so the counter
        // assertion waits out the debounce window (in-memory state stays synchronous).
        LibraryUpdateErrorStore.markResolved(LibraryUpdateErrorMedia.Novel, NOVEL_ID)
        LibraryUpdateErrorStore.errors.value.any {
            it.media == LibraryUpdateErrorMedia.Novel && it.entryId == NOVEL_ID
        } shouldBe false
        Thread.sleep(PERSIST_DEBOUNCE_WAIT_MILLIS)
        persistWrites shouldBe 1

        // The hot library-update path: resolving an entry that has no stored error
        // (already resolved above, or simply succeeded) must be a pure no-op.
        LibraryUpdateErrorStore.markResolved(LibraryUpdateErrorMedia.Novel, NOVEL_ID)
        persistWrites shouldBe 1

        // Same for a foreign media/id pair that was never recorded.
        LibraryUpdateErrorStore.markResolved(LibraryUpdateErrorMedia.Manga, MANGA_ID)
        persistWrites shouldBe 1
        LibraryUpdateErrorStore.errors.value.map { it.entryId }.filter { it == ANIME_ID || it == MANGA_ID } shouldBe
            listOf(ANIME_ID)
    }

    private companion object {
        const val NOVEL_ID = 910_001L
        const val ANIME_ID = 910_002L
        const val MANGA_ID = 910_003L

        // Comfortably above the store's 500 ms persist debounce + IO scheduling under
        // Robolectric.
        const val PERSIST_DEBOUNCE_WAIT_MILLIS = 1_200L
    }
}
