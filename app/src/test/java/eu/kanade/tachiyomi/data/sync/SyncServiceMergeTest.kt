package eu.kanade.tachiyomi.data.sync

import android.content.Context
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupEpisode
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.sync.service.SyncService
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.PreferenceStore

class SyncServiceMergeTest {

    private val syncService by lazy {
        TestSyncService(
            context = mockk(relaxed = true),
            json = Json { encodeDefaults = true },
            syncPreferences = SyncPreferences(mockk<PreferenceStore>(relaxed = true)),
        )
    }

    @Test
    fun `testMergeMangaLists keeps local when same device`() {
        val localManga = listOf(
            BackupManga(
                url = "https://example.com/manga/1",
                title = "Manga 1",
                source = 1L,
                version = 2,
            ),
        )
        val remoteManga = emptyList<BackupManga>()

        val result = syncService.mergeMangaPublic(
            localManga,
            remoteManga,
            emptyList(),
            emptyList(),
            emptyList(),
        )

        assertEquals(1, result.size)
        assertEquals("Manga 1", result[0].title)
    }

    @Test
    fun `testMergeMangaLists merges chapters from both sources`() {
        val localManga = listOf(
            BackupManga(
                url = "https://example.com/manga/1",
                title = "Manga 1",
                source = 1L,
                version = 1,
                chapters = listOf(
                    BackupChapter(
                        url = "https://example.com/chapter/1",
                        name = "Chapter 1",
                        chapterNumber = 1F,
                        version = 1,
                    ),
                ),
            ),
        )
        val remoteManga = listOf(
            BackupManga(
                url = "https://example.com/manga/1",
                title = "Manga 1",
                source = 1L,
                version = 1,
                chapters = listOf(
                    BackupChapter(
                        url = "https://example.com/chapter/2",
                        name = "Chapter 2",
                        chapterNumber = 2F,
                        version = 1,
                    ),
                ),
            ),
        )

        val result = syncService.mergeMangaPublic(
            localManga,
            remoteManga,
            emptyList(),
            emptyList(),
            emptyList(),
        )

        assertEquals(1, result.size)
        assertEquals(2, result[0].chapters.size)
    }

    @Test
    fun `testMergeAnimeLists works correctly`() {
        val localAnime = listOf(
            BackupAnime(
                url = "https://example.com/anime/1",
                title = "Anime 1",
                source = 1L,
                version = 1,
                episodes = listOf(
                    BackupEpisode(
                        url = "https://example.com/episode/1",
                        name = "Episode 1",
                        episodeNumber = 1F,
                        version = 1,
                    ),
                ),
            ),
        )
        val remoteAnime = emptyList<BackupAnime>()

        val result = syncService.mergeAnimePublic(
            localAnime,
            remoteAnime,
            emptyList(),
            emptyList(),
            emptyList(),
        )

        assertEquals(1, result.size)
        assertEquals("Anime 1", result[0].title)
    }

    @Test
    fun `testMergeNovelLists works correctly`() {
        val localNovel = listOf(
            BackupNovel(
                url = "https://example.com/novel/1",
                title = "Novel 1",
                source = 1L,
                version = 1,
            ),
        )
        val remoteNovel = emptyList<BackupNovel>()

        val result = syncService.mergeNovelPublic(
            localNovel,
            remoteNovel,
            emptyList(),
            emptyList(),
            emptyList(),
        )

        assertEquals(1, result.size)
        assertEquals("Novel 1", result[0].title)
    }

    @Test
    fun `testMergeCategories picks higher order`() {
        val localCategories = listOf(
            BackupCategory(name = "Action", order = 1),
            BackupCategory(name = "Comedy", order = 2),
        )
        val remoteCategories = listOf(
            BackupCategory(name = "Action", order = 2),
            BackupCategory(name = "Romance", order = 1),
        )

        val result = syncService.mergeCategoriesPublic(localCategories, remoteCategories)

        assertEquals(3, result.size)
        val actionCategory = result.find { it.name == "Action" }
        assertNotNull(actionCategory)
        assertEquals(2, actionCategory?.order)
    }

    @Test
    fun `testSyncData creation`() {
        val backup = Backup(
            backupManga = listOf(
                BackupManga(
                    url = "https://example.com/manga/1",
                    title = "Manga 1",
                    source = 1L,
                ),
            ),
        )
        val syncData = SyncData(
            deviceId = "test-device-id",
            backup = backup,
        )

        assertEquals("test-device-id", syncData.deviceId)
        assertNotNull(syncData.backup)
        assertEquals(1, syncData.backup?.backupManga?.size)
    }

    @Test
    fun `testFullBackupSyncData includes all content types`() {
        val backup = Backup(
            backupManga = listOf(
                BackupManga(
                    url = "https://example.com/manga/1",
                    title = "Manga 1",
                    source = 1L,
                ),
            ),
            backupAnime = listOf(
                BackupAnime(
                    url = "https://example.com/anime/1",
                    title = "Anime 1",
                    source = 1L,
                ),
            ),
            backupNovel = listOf(
                BackupNovel(
                    url = "https://example.com/novel/1",
                    title = "Novel 1",
                    source = 1L,
                ),
            ),
        )
        val syncData = SyncData(
            deviceId = "test-device-id",
            backup = backup,
        )

        assertEquals(1, syncData.backup?.backupManga?.size)
        assertEquals(1, syncData.backup?.backupAnime?.size)
        assertEquals(1, syncData.backup?.backupNovel?.size)
    }

    private class TestSyncService(
        context: Context,
        json: Json,
        syncPreferences: SyncPreferences,
    ) : SyncService(context, json, syncPreferences) {
        override suspend fun doSync(syncData: SyncData): Backup? = null

        fun mergeMangaPublic(
            localMangaList: List<BackupManga>?,
            remoteMangaList: List<BackupManga>?,
            localCategories: List<BackupCategory>,
            remoteCategories: List<BackupCategory>,
            mergedCategories: List<BackupCategory>,
        ): List<BackupManga> = mergeMangaLists(
            localMangaList,
            remoteMangaList,
            localCategories,
            remoteCategories,
            mergedCategories,
        )

        fun mergeAnimePublic(
            localAnimeList: List<BackupAnime>?,
            remoteAnimeList: List<BackupAnime>?,
            localCategories: List<BackupCategory>,
            remoteCategories: List<BackupCategory>,
            mergedCategories: List<BackupCategory>,
        ): List<BackupAnime> = mergeAnimeLists(
            localAnimeList,
            remoteAnimeList,
            localCategories,
            remoteCategories,
            mergedCategories,
        )

        fun mergeNovelPublic(
            localNovelList: List<BackupNovel>?,
            remoteNovelList: List<BackupNovel>?,
            localCategories: List<BackupCategory>,
            remoteCategories: List<BackupCategory>,
            mergedCategories: List<BackupCategory>,
        ): List<BackupNovel> = mergeNovelLists(
            localNovelList,
            remoteNovelList,
            localCategories,
            remoteCategories,
            mergedCategories,
        )

        fun mergeCategoriesPublic(
            localCategoriesList: List<BackupCategory>?,
            remoteCategoriesList: List<BackupCategory>?,
        ): List<BackupCategory> = mergeCategoriesLists(localCategoriesList, remoteCategoriesList)
    }
}
