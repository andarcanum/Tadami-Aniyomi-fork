package tachiyomi.domain.category.anime.interactor

import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.series.anime.repository.AnimeSeriesRepository

class DeleteAnimeCategoryTest {

    private val categoryRepository: AnimeCategoryRepository = mockk()
    private val seriesRepository: AnimeSeriesRepository = mockk()
    private val libraryPreferences: LibraryPreferences = mockk()
    private val downloadPreferences: DownloadPreferences = mockk()

    private val interactor = DeleteAnimeCategory(
        categoryRepository = categoryRepository,
        libraryPreferences = libraryPreferences,
        downloadPreferences = downloadPreferences,
        seriesRepository = seriesRepository,
    )

    @Test
    fun `resets series category to default when category is deleted`() = runTest {
        val categoryId = 42L

        coJustRun { categoryRepository.deleteAnimeCategory(categoryId) }
        coJustRun { seriesRepository.moveSeriesFromCategoryToDefault(categoryId) }
        coEvery { categoryRepository.getAllAnimeCategories() } returns emptyList()
        coJustRun { categoryRepository.updatePartialAnimeCategories(emptyList()) }

        val defaultCategoryPref: Preference<Int> = mockk(relaxed = true)
        every { libraryPreferences.defaultAnimeCategory() } returns defaultCategoryPref
        every { defaultCategoryPref.get() } returns 0

        val stringSetPref: Preference<Set<String>> = mockk(relaxed = true)
        every { stringSetPref.get() } returns emptySet()
        every { libraryPreferences.animeUpdateCategories() } returns stringSetPref
        every { libraryPreferences.animeUpdateCategoriesExclude() } returns stringSetPref
        every { downloadPreferences.removeExcludeAnimeCategories() } returns stringSetPref
        every { downloadPreferences.downloadNewEpisodeCategories() } returns stringSetPref
        every { downloadPreferences.downloadNewEpisodeCategoriesExclude() } returns stringSetPref

        val result = interactor.await(categoryId)

        result shouldBe DeleteAnimeCategory.Result.Success
        coVerify(exactly = 1) { categoryRepository.deleteAnimeCategory(categoryId) }
        coVerify(exactly = 1) { seriesRepository.moveSeriesFromCategoryToDefault(categoryId) }
    }
}

private infix fun Any?.shouldBe(expected: Any?) {
    assert(this == expected) { "Expected $expected but got $this" }
}
