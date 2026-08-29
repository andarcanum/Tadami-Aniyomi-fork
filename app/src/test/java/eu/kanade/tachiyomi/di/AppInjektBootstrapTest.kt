package eu.kanade.tachiyomi.di

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Regression: the library update workers resolve their dependencies from Injekt in their
 * constructors. WorkManager initializes from a ContentProvider and can dispatch a pending
 * worker immediately after process death -- before Application.onCreate() has imported any
 * module -- so every constructor crashed with InjektionException and auto-update silently
 * stopped working (crash log #bug 0.60: all three workers failed in a fresh background PID).
 *
 * bootstrapInjektModules() must be callable before onCreate() and must register everything
 * the three workers resolve at construction time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class AppInjektBootstrapTest {

    @Test
    fun `bootstrap registers every dependency the library workers resolve`() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        // Resolving the source managers spins up the extension managers, whose constructors
        // register a not-exported broadcast receiver; Robolectric requires the matching
        // manifest permission, so grant it for this JVM-only test.
        org.robolectric.Shadows.shadowOf(app)
            .grantPermissions("org.robolectric.default.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")

        // Simulate a fresh background process: only the bootstrap ran, no AppModule.
        // Override the folder provider inside the fresh scope: the real one needs localized
        // string resources unavailable on the JVM. PreferenceModule binds StoragePreferences
        // against the concrete class, so the mock must target AndroidStorageFolderProvider.
        val tempFolder = File(System.getProperty("java.io.tmpdir"), "tadami-bootstrap-test")
        val isMainProcess = app.bootstrapInjektModules {
            Injekt.addSingleton(
                mockk<tachiyomi.core.common.storage.AndroidStorageFolderProvider> {
                    every { directory() } returns tempFolder
                    every { path() } returns tempFolder.toURI().toString()
                },
            )
        }

        isMainProcess shouldBe true

        // MangaLibraryUpdateJob constructor dependencies:
        Injekt.get<tachiyomi.domain.source.manga.service.MangaSourceManager>()
        Injekt.get<tachiyomi.domain.library.service.LibraryPreferences>()
        Injekt.get<eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager>()
        Injekt.get<eu.kanade.tachiyomi.data.cache.MangaCoverCache>()
        Injekt.get<tachiyomi.domain.entries.manga.interactor.GetLibraryManga>()
        Injekt.get<tachiyomi.domain.entries.manga.interactor.GetManga>()
        Injekt.get<eu.kanade.domain.entries.manga.interactor.UpdateManga>()
        Injekt.get<eu.kanade.domain.items.chapter.interactor.SyncChaptersWithSource>()
        Injekt.get<tachiyomi.domain.entries.manga.interactor.MangaFetchInterval>()
        Injekt.get<mihon.domain.items.chapter.interactor.FilterChaptersForDownload>()
        Injekt.get<eu.kanade.domain.ui.UiPreferences>() // LibraryUpdatePacingPolicy

        // AnimeLibraryUpdateJob constructor dependencies:
        Injekt.get<tachiyomi.domain.source.anime.service.AnimeSourceManager>()
        Injekt.get<eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager>()
        Injekt.get<eu.kanade.tachiyomi.data.cache.AnimeCoverCache>()
        Injekt.get<eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache>()
        Injekt.get<tachiyomi.domain.entries.anime.interactor.GetLibraryAnime>()
        Injekt.get<tachiyomi.domain.entries.anime.interactor.GetAnime>()
        Injekt.get<eu.kanade.domain.entries.anime.interactor.UpdateAnime>()
        Injekt.get<eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource>()
        Injekt.get<tachiyomi.domain.entries.anime.interactor.AnimeFetchInterval>()
        Injekt.get<mihon.domain.items.episode.interactor.FilterEpisodesForDownload>()
        Injekt.get<tachiyomi.domain.items.season.interactor.GetAnimeSeasonsByParentId>()
        Injekt.get<eu.kanade.domain.entries.anime.interactor.AnimeRatingFetcher>()

        // NovelLibraryUpdateJob constructor dependencies:
        Injekt.get<tachiyomi.domain.source.novel.service.NovelSourceManager>()
        Injekt.get<tachiyomi.domain.download.service.DownloadPreferences>()
        Injekt.get<tachiyomi.domain.entries.novel.interactor.GetLibraryNovel>()
        Injekt.get<tachiyomi.domain.entries.novel.interactor.GetNovel>()
        Injekt.get<eu.kanade.domain.entries.novel.interactor.UpdateNovel>()
        Injekt.get<eu.kanade.domain.items.novelchapter.interactor.SyncNovelChaptersWithSource>()
        // Note: NovelLibraryUpdateJob constructs NovelDownloadManager directly
        // (its constructor falls back to Injekt internally), so it is not a binding here.
    }
}
