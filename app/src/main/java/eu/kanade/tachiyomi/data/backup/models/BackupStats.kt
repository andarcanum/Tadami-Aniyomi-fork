package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Full statistics backup including manga and anime stats
 */
@Serializable
data class BackupStats(
    // Manga stats
    @ProtoNumber(1) var mangaLibraryCount: Int = 0,
    @ProtoNumber(2) var mangaCompletedCount: Int = 0,
    @ProtoNumber(3) var mangaTotalReadDuration: Long = 0L,
    @ProtoNumber(4) var mangaStartedCount: Int = 0,
    @ProtoNumber(5) var mangaLocalCount: Int = 0,
    @ProtoNumber(6) var chaptersTotalCount: Int = 0,
    @ProtoNumber(7) var chaptersReadCount: Int = 0,
    @ProtoNumber(8) var chaptersDownloadedCount: Int = 0,

    // Anime stats
    @ProtoNumber(9) var animeLibraryCount: Int = 0,
    @ProtoNumber(10) var animeCompletedCount: Int = 0,
    @ProtoNumber(11) var animeTotalSeenDuration: Long = 0L,
    @ProtoNumber(12) var animeStartedCount: Int = 0,
    @ProtoNumber(13) var animeLocalCount: Int = 0,
    @ProtoNumber(14) var episodesTotalCount: Int = 0,
    @ProtoNumber(15) var episodesWatchedCount: Int = 0,
    @ProtoNumber(16) var episodesDownloadedCount: Int = 0,

    // Tracker stats
    @ProtoNumber(17) var trackedTitleCount: Int = 0,
    @ProtoNumber(18) var meanScore: Double = 0.0,
    @ProtoNumber(19) var trackerCount: Int = 0,

    // Global update stats
    @ProtoNumber(20) var mangaGlobalUpdateCount: Int = 0,
    @ProtoNumber(21) var animeGlobalUpdateCount: Int = 0,
)
