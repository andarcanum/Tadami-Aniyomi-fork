package mihon.core.migration.migrations

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.library.service.LibraryPreferences

class DefaultChapterEpisodeSortNumberMigrationTest {

    @Test
    fun `migration flips chapter and episode defaults to number sort`() = runTest {
        val store = InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreferenceStore.InMemoryPreference(
                    "default_episode_sort_by_source_or_number",
                    Anime.EPISODE_SORTING_SOURCE,
                    Anime.EPISODE_SORTING_SOURCE,
                ),
                InMemoryPreferenceStore.InMemoryPreference(
                    "default_chapter_sort_by_source_or_number",
                    Manga.CHAPTER_SORTING_SOURCE,
                    Manga.CHAPTER_SORTING_SOURCE,
                ),
            ),
        )
        val prefs = LibraryPreferences(store)
        val episodeSortPref = prefs.sortEpisodeBySourceOrNumber()
        val chapterSortPref = prefs.sortChapterBySourceOrNumber()

        var animeApplied = false
        var mangaApplied = false

        applyDefaultChapterEpisodeSortToNumber(
            episodeSortPreference = episodeSortPref,
            chapterSortPreference = chapterSortPref,
            applyToExistingAnime = { animeApplied = true },
            applyToExistingManga = { mangaApplied = true },
        )

        episodeSortPref.get() shouldBe Anime.EPISODE_SORTING_NUMBER
        chapterSortPref.get() shouldBe Manga.CHAPTER_SORTING_NUMBER
        animeApplied shouldBe true
        mangaApplied shouldBe true
    }
}
