package eu.kanade.domain.track.novel.model

import tachiyomi.domain.track.novel.model.NovelTrack
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack as DbMangaTrack

fun DbMangaTrack.toNovelTrack(idRequired: Boolean = true): NovelTrack? {
    val trackId = id ?: if (!idRequired) -1 else return null
    return NovelTrack(
        id = trackId,
        novelId = manga_id,
        trackerId = tracker_id,
        remoteId = remote_id,
        libraryId = library_id,
        title = title,
        lastChapterRead = last_chapter_read,
        totalChapters = total_chapters,
        status = status,
        score = score,
        remoteUrl = tracking_url,
        startDate = started_reading_date,
        finishDate = finished_reading_date,
        private = private,
    )
}

fun NovelTrack.toDbTrack(): DbMangaTrack = DbMangaTrack.create(trackerId).also {
    it.id = id
    it.manga_id = novelId
    it.remote_id = remoteId
    it.library_id = libraryId
    it.title = title
    it.last_chapter_read = lastChapterRead
    it.total_chapters = totalChapters
    it.status = status
    it.score = score
    it.tracking_url = remoteUrl
    it.started_reading_date = startDate
    it.finished_reading_date = finishDate
    it.private = private
}
