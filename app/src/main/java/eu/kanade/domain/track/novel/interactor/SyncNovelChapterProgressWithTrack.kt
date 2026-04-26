package eu.kanade.domain.track.novel.interactor

import eu.kanade.domain.track.novel.model.toDbTrack
import eu.kanade.domain.track.novel.model.toNovelTrack
import eu.kanade.tachiyomi.data.track.MangaTracker
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.items.novelchapter.interactor.GetNovelChapters
import tachiyomi.domain.items.novelchapter.model.toNovelChapterUpdate
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.track.novel.interactor.InsertNovelTrack
import tachiyomi.domain.track.novel.model.NovelTrack

class SyncNovelChapterProgressWithTrack(
    private val novelChapterRepository: NovelChapterRepository,
    private val insertTrack: InsertNovelTrack,
    private val getNovelChapters: GetNovelChapters,
) {

    suspend fun await(
        novelId: Long,
        remoteTrack: NovelTrack,
        tracker: MangaTracker,
    ) {
        val sortedChapters = getNovelChapters.await(novelId)
            .sortedBy { it.chapterNumber }
            .filter { it.isRecognizedNumber }

        val chapterUpdates = sortedChapters
            .filter { chapter -> chapter.chapterNumber <= remoteTrack.lastChapterRead && !chapter.read }
            .map { it.copy(read = true).toNovelChapterUpdate() }

        try {
            novelChapterRepository.updateAllChapters(chapterUpdates)
            insertTrack.await(remoteTrack)
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e)
        }
    }
}
