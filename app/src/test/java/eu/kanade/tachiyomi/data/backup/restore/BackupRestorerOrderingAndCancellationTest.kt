package eu.kanade.tachiyomi.data.backup.restore

import android.app.Application
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.BackupDiagnosticLog
import eu.kanade.tachiyomi.data.backup.BackupOrigin
import eu.kanade.tachiyomi.data.backup.DecodedBackup
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeSeries
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeCategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeSeriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaCategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.NovelCategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.PreferenceRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.TranslationConfigurationRestorer
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class BackupRestorerOrderingAndCancellationTest {

    @Before
    fun setup() {
        mockkObject(BackupDiagnosticLog)
        justRun { BackupDiagnosticLog.log(any(), any(), any()) }
    }

    @After
    fun teardown() {
        unmockkObject(BackupDiagnosticLog)
    }

    @Test
    fun `categories finish restoring before preferences and library entries begin`() = runTest {
        val categoriesStarted = CompletableDeferred<Unit>()
        val releaseCategories = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        val animeCategoriesRestorer = mockk<AnimeCategoriesRestorer>()
        coEvery { animeCategoriesRestorer.invoke(any()) } coAnswers {
            events += "categories:start"
            categoriesStarted.complete(Unit)
            releaseCategories.await()
            events += "categories:done"
        }

        val preferenceRestorer = mockk<PreferenceRestorer>()
        coEvery { preferenceRestorer.restoreApp(any(), any(), any(), any()) } coAnswers {
            events += "prefs"
        }

        val animeRestorer = mockk<AnimeRestorer>()
        coEvery { animeRestorer.sortByNew(any()) } answers { firstArg() }
        coEvery { animeRestorer.restore(any(), any(), any()) } coAnswers {
            events += "anime"
        }

        val translationRestorer = mockk<TranslationConfigurationRestorer>()
        coEvery { translationRestorer.restore(any(), any()) } returns null

        val payload = Backup(
            backupAnime = listOf(BackupAnime(source = 1L, url = "/a1", title = "Anime 1")),
            backupAnimeCategories = listOf(BackupCategory(id = 1L, name = "Watching", order = 0L)),
            backupPreferences = listOf(BackupPreference("default_anime_category", IntPreferenceValue(1))),
        )

        val restorer = BackupRestorer(
            context = mockk(relaxed = true),
            notifier = mockk(relaxed = true),
            isSync = false,
            animeCategoriesRestorer = animeCategoriesRestorer,
            mangaCategoriesRestorer = mockk<MangaCategoriesRestorer>(relaxed = true),
            novelCategoriesRestorer = mockk<NovelCategoriesRestorer>(relaxed = true),
            preferenceRestorer = preferenceRestorer,
            animeExtensionRepoRestorer = mockk(),
            mangaExtensionRepoRestorer = mockk(),
            novelExtensionRepoRestorer = mockk(),
            animeExtensionStoreRestorer = mockk(),
            mangaExtensionStoreRestorer = mockk(),
            novelExtensionStoreRestorer = mockk(),
            customButtonRestorer = mockk(),
            animeRestorer = animeRestorer,
            mangaRestorer = mockk(relaxed = true),
            novelRestorer = mockk(relaxed = true),
            animeSeriesRestorer = mockk(relaxed = true),
            mangaSeriesRestorer = mockk(relaxed = true),
            novelSeriesRestorer = mockk(relaxed = true),
            feedRestorer = mockk(),
            reelsFavoritesRestorer = mockk(),
            reelsFollowsRestorer = mockk(),
            discoveryRestorer = mockk(),
            extensionsRestorer = mockk(),
            achievementRestorer = mockk(),
            translationConfigurationRestorer = translationRestorer,
            decodeBackup = { _, policy -> DecodedBackup(payload, BackupOrigin.TADAMI, policy) },
            afterRestoreDiscovery = {},
        )

        val options = RestoreOptions(
            libraryEntries = true,
            categories = true,
            appSettings = true,
            extensionRepoSettings = false,
            customButtons = false,
            sourceSettings = false,
            extensions = false,
            achievements = false,
            stats = false,
            reelsFavorites = false,
            reelsFollows = false,
            discoveryData = false,
            restoreAnime = true,
            restoreManga = false,
            restoreNovel = false,
        )

        val job = async { restorer.restoreFromFile(Uri.EMPTY, options) }
        categoriesStarted.await()
        runCurrent()

        events shouldBe listOf("categories:start")

        releaseCategories.complete(Unit)
        job.await()

        events.first() shouldBe "categories:start"
        events[1] shouldBe "categories:done"
        events.drop(2).toSet() shouldBe setOf("prefs", "anime")
    }

    @Test
    fun `CancellationException from series restorer propagates instead of being swallowed`() = runTest {
        val animeSeriesRestorer = mockk<AnimeSeriesRestorer>()
        coEvery { animeSeriesRestorer.restore(any()) } throws CancellationException("cancelled")

        val translationRestorer = mockk<TranslationConfigurationRestorer>()
        coEvery { translationRestorer.restore(any(), any()) } returns null

        val payload = Backup(
            backupAnimeSeries = listOf(BackupAnimeSeries(title = "Series 1")),
        )

        val restorer = BackupRestorer(
            context = mockk(relaxed = true),
            notifier = mockk(relaxed = true),
            isSync = false,
            animeCategoriesRestorer = mockk(relaxed = true),
            mangaCategoriesRestorer = mockk(relaxed = true),
            novelCategoriesRestorer = mockk(relaxed = true),
            preferenceRestorer = mockk(relaxed = true),
            animeExtensionRepoRestorer = mockk(),
            mangaExtensionRepoRestorer = mockk(),
            novelExtensionRepoRestorer = mockk(),
            animeExtensionStoreRestorer = mockk(),
            mangaExtensionStoreRestorer = mockk(),
            novelExtensionStoreRestorer = mockk(),
            customButtonRestorer = mockk(),
            animeRestorer = mockk(relaxed = true),
            mangaRestorer = mockk(relaxed = true),
            novelRestorer = mockk(relaxed = true),
            animeSeriesRestorer = animeSeriesRestorer,
            mangaSeriesRestorer = mockk(relaxed = true),
            novelSeriesRestorer = mockk(relaxed = true),
            feedRestorer = mockk(),
            reelsFavoritesRestorer = mockk(),
            reelsFollowsRestorer = mockk(),
            discoveryRestorer = mockk(),
            extensionsRestorer = mockk(),
            achievementRestorer = mockk(),
            translationConfigurationRestorer = translationRestorer,
            decodeBackup = { _, policy -> DecodedBackup(payload, BackupOrigin.TADAMI, policy) },
            afterRestoreDiscovery = {},
        )

        val options = RestoreOptions(
            libraryEntries = true,
            categories = false,
            appSettings = false,
            extensionRepoSettings = false,
            customButtons = false,
            sourceSettings = false,
            extensions = false,
            achievements = false,
            stats = false,
            reelsFavorites = false,
            reelsFollows = false,
            discoveryData = false,
            restoreAnime = true,
            restoreManga = false,
            restoreNovel = false,
        )

        assertFailsWith<CancellationException> {
            restorer.restoreFromFile(Uri.EMPTY, options)
        }
    }
}
