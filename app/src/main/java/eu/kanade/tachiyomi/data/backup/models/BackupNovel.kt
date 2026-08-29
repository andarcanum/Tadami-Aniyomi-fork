package eu.kanade.tachiyomi.data.backup.models

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.entries.novel.model.Novel

@Serializable
data class BackupNovel(
    @ProtoNumber(1) var source: Long,
    @ProtoNumber(2) var url: String,
    @ProtoNumber(3) var title: String = "",
    @ProtoNumber(4) var author: String? = null,
    @ProtoNumber(5) var description: String? = null,
    @ProtoNumber(110) var notes: String? = null,
    @ProtoNumber(6) var genre: List<String> = emptyList(),
    @ProtoNumber(7) var status: Int = 0,
    @ProtoNumber(8) var thumbnailUrl: String? = null,
    @ProtoNumber(13) var dateAdded: Long = 0,
    @ProtoNumber(16) var chapters: List<BackupChapter> = emptyList(),
    @ProtoNumber(17) var categories: List<Long> = emptyList(),
    @ProtoNumber(100) var favorite: Boolean = true,
    @ProtoNumber(101) var chapterFlags: Int = 0,
    @ProtoNumber(103) var viewerFlags: Int = 0,
    @ProtoNumber(104) var history: List<BackupHistory> = emptyList(),
    @ProtoNumber(105) var updateStrategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE,
    @ProtoNumber(106) var lastModifiedAt: Long = 0,
    @ProtoNumber(107) var favoriteModifiedAt: Long? = null,
    @ProtoNumber(108) var excludedScanlators: List<String> = emptyList(),
    @ProtoNumber(109) var version: Long = 0,
    @ProtoNumber(800) var customTitle: String? = null,
    @ProtoNumber(802) var customAuthor: String? = null,
    @ProtoNumber(803) var customDescription: String? = null,
    @ProtoNumber(804) var customGenre: List<String>? = null,
    @ProtoNumber(805) var customStatus: Long? = null,
    /**
     * Compiled-book state for this novel. The artifact itself is never backed up (it is derived
     * data and can weigh hundreds of megabytes); only the reading position and the flags needed to
     * detect that the book must be rebuilt travel with the backup.
     */
    @ProtoNumber(900) var bookState: BackupNovelBookState? = null,
    /** Saved text highlights; chapters travel by URL (see [BackupNovelHighlight]). */
    @ProtoNumber(901) var highlights: List<BackupNovelHighlight> = emptyList(),
) {
    fun getNovelImpl(): Novel {
        return Novel.create().copy(
            url = url,
            title = title,
            author = author,
            description = description,
            notes = notes ?: "",
            genre = genre,
            status = status.toLong(),
            thumbnailUrl = thumbnailUrl,
            favorite = favorite,
            source = source,
            dateAdded = dateAdded,
            viewerFlags = viewerFlags.toLong(),
            chapterFlags = chapterFlags.toLong(),
            updateStrategy = updateStrategy,
            lastModifiedAt = lastModifiedAt,
            favoriteModifiedAt = favoriteModifiedAt,
            version = version,
            customTitle = customTitle,
            customAuthor = customAuthor,
            customDescription = customDescription,
            customGenre = customGenre,
            customStatus = customStatus,
        )
    }
}
