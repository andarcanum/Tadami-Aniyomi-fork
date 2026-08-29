package eu.kanade.tachiyomi.data.library

import android.content.Context
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.novel.NovelLibraryUpdateJob

object LibraryUpdateCoordinator {

    /**
     * Starts the enabled media library updates as independent WorkManager jobs.
     *
     * The three jobs are enqueued under their own unique names, so they run in parallel —
     * same as the auto-update path and the per-tab refreshes. Each job's own guard skips a
     * media that is already running or enqueued, so this returns true when at least one
     * media actually started.
     */
    fun startAll(
        context: Context,
        updateAnime: Boolean,
        updateManga: Boolean,
        updateNovel: Boolean,
    ): Boolean {
        var started = false
        if (updateAnime) {
            started = AnimeLibraryUpdateJob.startNow(context) || started
        }
        if (updateManga) {
            started = MangaLibraryUpdateJob.startNow(context) || started
        }
        if (updateNovel) {
            started = NovelLibraryUpdateJob.startNow(context) || started
        }
        return started
    }

    fun stop(context: Context) {
        AnimeLibraryUpdateJob.stop(context)
        MangaLibraryUpdateJob.stop(context)
        NovelLibraryUpdateJob.stop(context)
    }
}
