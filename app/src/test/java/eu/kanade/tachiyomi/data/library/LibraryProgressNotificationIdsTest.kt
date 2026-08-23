package eu.kanade.tachiyomi.data.library

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateNotifier
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateNotifier
import eu.kanade.tachiyomi.data.library.novel.NovelLibraryUpdateNotifier
import eu.kanade.tachiyomi.data.notification.Notifications
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tachiyomi.core.common.preference.InMemoryPreferenceStore

/**
 * Library update jobs run in parallel, so each media must post its progress under its own
 * notification id. A shared id would make the three jobs overwrite each other's progress and
 * let the first finishing job cancel the notification of the still-running ones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class LibraryProgressNotificationIdsTest {

    @Test
    fun `parallel library updates use distinct per-media progress notification ids`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val securityPreferences = SecurityPreferences(InMemoryPreferenceStore())

        val animeId = AnimeLibraryUpdateNotifier(
            context = context,
            securityPreferences = securityPreferences,
            sourceManager = mockk(relaxed = true),
        ).progressNotificationId

        val mangaId = MangaLibraryUpdateNotifier(
            context = context,
            securityPreferences = securityPreferences,
            sourceManager = mockk(relaxed = true),
        ).progressNotificationId

        val novelId = NovelLibraryUpdateNotifier(context).progressNotificationId

        val ids = listOf(animeId, mangaId, novelId)

        // Pairwise distinct...
        ids.distinct().size shouldBe 3
        // ...exactly matching the per-media update constants...
        ids shouldContainExactlyInAnyOrder listOf(
            Notifications.ID_ANIME_LIBRARY_UPDATE_PROGRESS,
            Notifications.ID_MANGA_LIBRARY_UPDATE_PROGRESS,
            Notifications.ID_NOVEL_LIBRARY_UPDATE_PROGRESS,
        )
        // ...and never colliding with ids owned by other notifications.
        val foreignIds = listOf(
            Notifications.ID_LIBRARY_PROGRESS,
            Notifications.ID_ANIME_LIBRARY_PROGRESS,
            Notifications.ID_NOVEL_LIBRARY_PROGRESS,
            Notifications.ID_LIBRARY_ERROR,
            Notifications.ID_ANIME_LIBRARY_ERROR,
            Notifications.ID_NOVEL_LIBRARY_ERROR,
            Notifications.ID_LIBRARY_SIZE_WARNING,
            Notifications.ID_ANIME_LIBRARY_SIZE_WARNING,
        )
        ids.intersect(foreignIds.toSet()) shouldBe emptySet()
    }
}
