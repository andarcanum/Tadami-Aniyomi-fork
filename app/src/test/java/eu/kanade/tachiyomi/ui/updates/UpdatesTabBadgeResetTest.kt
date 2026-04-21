package eu.kanade.tachiyomi.ui.updates

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.library.service.LibraryPreferences

class UpdatesTabBadgeResetTest {

    @Test
    fun `clearUpdatesBadgeCounts clears all counters`() {
        val libraryPreferences = mockk<LibraryPreferences>()
        val animeCount = mockk<Preference<Int>>()
        val mangaCount = mockk<Preference<Int>>()
        val novelCount = mockk<Preference<Int>>()

        every { libraryPreferences.newAnimeUpdatesCount() } returns animeCount
        every { libraryPreferences.newMangaUpdatesCount() } returns mangaCount
        every { libraryPreferences.newNovelUpdatesCount() } returns novelCount
        every { animeCount.set(any()) } returns Unit
        every { mangaCount.set(any()) } returns Unit
        every { novelCount.set(any()) } returns Unit

        clearUpdatesBadgeCounts(libraryPreferences)

        verify(exactly = 1) { animeCount.set(0) }
        verify(exactly = 1) { mangaCount.set(0) }
        verify(exactly = 1) { novelCount.set(0) }
    }
}
