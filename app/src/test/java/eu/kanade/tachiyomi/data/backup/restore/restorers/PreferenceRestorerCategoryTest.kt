package eu.kanade.tachiyomi.data.backup.restore.restorers

import android.content.Context
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import eu.kanade.tachiyomi.data.library.LibraryAutoUpdateSchedulerJob
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.category.novel.model.NovelCategory
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences

class PreferenceRestorerCategoryTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        mockkObject(LibraryAutoUpdateSchedulerJob.Companion, BackupCreateJob.Companion)
        justRun { LibraryAutoUpdateSchedulerJob.setupTask(context) }
        justRun { BackupCreateJob.setupTask(context) }
    }

    @After
    fun teardown() {
        unmockkObject(LibraryAutoUpdateSchedulerJob.Companion, BackupCreateJob.Companion)
    }

    @Test
    fun `category key partitions cover every LibraryPreferences and DownloadPreferences category key`() {
        val handledKeys = PreferenceRestorer.MANGA_CATEGORY_SET_KEYS +
            PreferenceRestorer.ANIME_CATEGORY_SET_KEYS +
            PreferenceRestorer.NOVEL_CATEGORY_SET_KEYS +
            setOf(
                LibraryPreferences.DEFAULT_MANGA_CATEGORY_PREF_KEY,
                LibraryPreferences.DEFAULT_ANIME_CATEGORY_PREF_KEY,
                LibraryPreferences.DEFAULT_NOVEL_CATEGORY_PREF_KEY,
            )

        handledKeys shouldBe (LibraryPreferences.categoryPreferenceKeys + DownloadPreferences.categoryPreferenceKeys)
    }

    @Test
    fun `default and set category preferences remap strictly within each media type when backup ids collide`(): Unit =
        runBlocking {
            val store = PersistingPreferenceStore()
            val getMangaCategories = mockk<GetMangaCategories> {
                coEvery { await() } returns listOf(
                    Category(
                        id = 101L,
                        name = "Manga Only",
                        order = 0L,
                        flags = 0L,
                        hidden = false,
                        hiddenFromHomeHub = false,
                    ),
                )
            }
            val getAnimeCategories = mockk<GetAnimeCategories> {
                coEvery { await() } returns listOf(
                    Category(
                        id = 202L,
                        name = "Anime Only",
                        order = 0L,
                        flags = 0L,
                        hidden = false,
                        hiddenFromHomeHub = false,
                    ),
                )
            }
            val getNovelCategories = mockk<GetNovelCategories> {
                coEvery { await() } returns listOf(
                    NovelCategory(
                        id = 303L,
                        name = "Novel Only",
                        order = 0L,
                        flags = 0L,
                        hidden = false,
                        hiddenFromHomeHub = false,
                    ),
                )
            }
            val restorer = PreferenceRestorer(
                context = context,
                getMangaCategories = getMangaCategories,
                getAnimeCategories = getAnimeCategories,
                getNovelCategories = getNovelCategories,
                preferenceStore = store,
            )

            val backupMangaCats = listOf(BackupCategory(id = 1L, name = "Manga Only", order = 0L))
            val backupAnimeCats = listOf(BackupCategory(id = 1L, name = "Anime Only", order = 0L))
            val backupNovelCats = listOf(BackupCategory(id = 1L, name = "Novel Only", order = 0L))

            val prefs = listOf(
                BackupPreference(LibraryPreferences.DEFAULT_MANGA_CATEGORY_PREF_KEY, IntPreferenceValue(1)),
                BackupPreference(LibraryPreferences.DEFAULT_ANIME_CATEGORY_PREF_KEY, IntPreferenceValue(1)),
                BackupPreference(LibraryPreferences.DEFAULT_NOVEL_CATEGORY_PREF_KEY, IntPreferenceValue(1)),
                BackupPreference("library_update_categories", StringSetPreferenceValue(setOf("1"))),
                BackupPreference("animelib_update_categories", StringSetPreferenceValue(setOf("1"))),
                BackupPreference("novellib_update_categories", StringSetPreferenceValue(setOf("1"))),
                BackupPreference("download_new_anime_categories_exclude", StringSetPreferenceValue(setOf("1"))),
                BackupPreference("remove_exclude_novel_categories", StringSetPreferenceValue(setOf("1"))),
            )

            restorer.restoreApp(
                preferences = prefs,
                backupCategories = backupMangaCats,
                backupAnimeCategories = backupAnimeCats,
                backupNovelCategories = backupNovelCats,
            )

            val libraryPrefs = LibraryPreferences(store)
            val downloadPrefs = DownloadPreferences(store)

            libraryPrefs.defaultMangaCategory().get() shouldBe 101
            libraryPrefs.defaultAnimeCategory().get() shouldBe 202
            libraryPrefs.defaultNovelCategory().get() shouldBe 303
            libraryPrefs.mangaUpdateCategories().get() shouldBe setOf("101")
            libraryPrefs.animeUpdateCategories().get() shouldBe setOf("202")
            libraryPrefs.novelUpdateCategories().get() shouldBe setOf("303")
            downloadPrefs.downloadNewEpisodeCategoriesExclude().get() shouldBe setOf("202")
            downloadPrefs.removeExcludeNovelCategories().get() shouldBe setOf("303")
        }

    @Test
    fun `shared category names across media do not leak foreign ids into another media preference`(): Unit =
        runBlocking {
            val store = PersistingPreferenceStore()
            val getMangaCategories = mockk<GetMangaCategories> {
                coEvery { await() } returns listOf(
                    Category(
                        id = 11L,
                        name = "Reading",
                        order = 0L,
                        flags = 0L,
                        hidden = false,
                        hiddenFromHomeHub = false,
                    ),
                )
            }
            val getAnimeCategories = mockk<GetAnimeCategories> {
                coEvery { await() } returns listOf(
                    Category(
                        id = 22L,
                        name = "Reading",
                        order = 0L,
                        flags = 0L,
                        hidden = false,
                        hiddenFromHomeHub = false,
                    ),
                )
            }
            val getNovelCategories = mockk<GetNovelCategories> {
                coEvery { await() } returns listOf(
                    NovelCategory(
                        id = 33L,
                        name = "Reading",
                        order = 0L,
                        flags = 0L,
                        hidden = false,
                        hiddenFromHomeHub = false,
                    ),
                )
            }
            val restorer = PreferenceRestorer(
                context = context,
                getMangaCategories = getMangaCategories,
                getAnimeCategories = getAnimeCategories,
                getNovelCategories = getNovelCategories,
                preferenceStore = store,
            )

            restorer.restoreApp(
                preferences = listOf(
                    BackupPreference("library_update_categories", StringSetPreferenceValue(setOf("7"))),
                    BackupPreference("animelib_update_categories", StringSetPreferenceValue(setOf("8"))),
                    BackupPreference("novellib_update_categories", StringSetPreferenceValue(setOf("9"))),
                ),
                backupCategories = listOf(BackupCategory(id = 7L, name = "Reading", order = 0L)),
                backupAnimeCategories = listOf(BackupCategory(id = 8L, name = "Reading", order = 0L)),
                backupNovelCategories = listOf(BackupCategory(id = 9L, name = "Reading", order = 0L)),
            )

            val libraryPrefs = LibraryPreferences(store)
            libraryPrefs.mangaUpdateCategories().get() shouldBe setOf("11")
            libraryPrefs.animeUpdateCategories().get() shouldBe setOf("22")
            libraryPrefs.novelUpdateCategories().get() shouldBe setOf("33")
        }
}
