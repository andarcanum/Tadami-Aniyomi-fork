package eu.kanade.tachiyomi.data.backup.models

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager

@Serializable
data class MihonBackup(
    @ProtoNumber(1) val backupManga: List<MihonBackupManga> = emptyList(),
    @ProtoNumber(2) var backupCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(101) var backupSources: List<BackupSource> = emptyList(),
    @ProtoNumber(104) var backupPreferences: List<BackupPreference> = emptyList(),
    @ProtoNumber(105) var backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),
    @ProtoNumber(106) var backupExtensionRepo: List<BackupExtensionRepos> = emptyList(),
) {
    fun toTadamiBackup(
        mangaSourceManager: MangaSourceManager,
        novelSourceManager: NovelSourceManager,
        animeSourceManager: AnimeSourceManager,
    ): Backup {
        return toTadamiBackup(
            mangaSourceClassifier = { sourceId ->
                mangaSourceManager.get(sourceId) != null ||
                    mangaSourceManager.getStubSources().any { it.id == sourceId }
            },
            novelSourceClassifier = { sourceId ->
                novelSourceManager.get(sourceId) != null ||
                    novelSourceManager.getStubSources().any { it.id == sourceId }
            },
            animeSourceClassifier = { sourceId ->
                animeSourceManager.get(sourceId) != null ||
                    animeSourceManager.getStubSources().any { it.id == sourceId }
            },
        )
    }

    internal fun toTadamiBackup(
        mangaSourceClassifier: (Long) -> Boolean,
        novelSourceClassifier: (Long) -> Boolean,
        animeSourceClassifier: (Long) -> Boolean,
    ): Backup {
        return Backup(
            backupManga = backupManga.map { it.toBackupManga() },
            backupCategories = backupCategories,
            backupSources = backupSources,
            backupPreferences = backupPreferences,
            backupSourcePreferences = backupSourcePreferences,
            backupMangaExtensionRepo = backupExtensionRepo,
            isLegacy = false,
        ).routeSharedMangaEntriesBySource(
            mangaSourceClassifier = mangaSourceClassifier,
            novelSourceClassifier = novelSourceClassifier,
            animeSourceClassifier = animeSourceClassifier,
        )
    }
}

internal fun Backup.toMihonBackup(): MihonBackup {
    return MihonBackup(
        backupManga = (backupManga + backupNovel.map { it.toBackupManga() })
            .map { it.toMihonBackupManga() },
        backupCategories = backupCategories,
        backupSources = backupSources,
        backupPreferences = backupPreferences,
        backupSourcePreferences = backupSourcePreferences,
        backupExtensionRepo = backupMangaExtensionRepo,
    )
}

internal fun Backup.routeSharedMangaEntriesBySource(
    mangaSourceClassifier: (Long) -> Boolean,
    novelSourceClassifier: (Long) -> Boolean,
    animeSourceClassifier: (Long) -> Boolean,
): Backup {
    val mangas = mutableListOf<BackupManga>()
    val novels = backupNovel.toMutableList()
    val animes = backupAnime.toMutableList()

    val knownAnimeSourceIds = backupAnimeSources.map { it.sourceId }.toSet()
    val knownNovelSourceIds = backupNovelSources.map { it.sourceId }.toSet()

    backupManga.forEach { entry ->
        val sourceId = entry.source
        when {
            novelSourceClassifier(sourceId) -> novels.add(entry.toBackupNovel())
            animeSourceClassifier(sourceId) -> animes.add(entry.toBackupAnime())
            mangaSourceClassifier(sourceId) -> mangas.add(entry)
            sourceId in knownNovelSourceIds -> novels.add(entry.toBackupNovel())
            sourceId in knownAnimeSourceIds -> animes.add(entry.toBackupAnime())
            else -> mangas.add(entry)
        }
    }

    val routedNovelSources = novels
        .map { novel ->
            backupSources.firstOrNull { it.sourceId == novel.source }
                ?: BackupSource(name = "", sourceId = novel.source)
        }
        .distinctBy { it.sourceId }
    val routedAnimeSources = animes
        .map { anime ->
            val source = backupSources.firstOrNull { it.sourceId == anime.source }
            BackupAnimeSource(name = source?.name.orEmpty(), sourceId = anime.source)
        }
        .distinctBy { it.sourceId }

    return copy(
        backupManga = mangas,
        backupNovel = novels,
        backupAnime = animes,
        backupNovelCategories = backupNovelCategories.ifEmpty {
            if (novels.isNotEmpty()) backupCategories else emptyList()
        },
        backupAnimeCategories = backupAnimeCategories.ifEmpty {
            if (animes.isNotEmpty()) backupCategories else emptyList()
        },
        backupNovelSources = (backupNovelSources + routedNovelSources).distinctBy { it.sourceId },
        backupAnimeSources = (backupAnimeSources + routedAnimeSources).distinctBy { it.sourceId },
    )
}

fun BackupManga.toBackupNovel(): BackupNovel {
    return BackupNovel(
        source = this.source,
        url = this.url,
        title = this.title,
        author = this.author,
        description = this.description,
        notes = this.notes,
        genre = this.genre,
        status = this.status,
        thumbnailUrl = this.thumbnailUrl,
        dateAdded = this.dateAdded,
        chapters = this.chapters,
        categories = this.categories,
        favorite = this.favorite,
        chapterFlags = this.chapterFlags,
        viewerFlags = this.viewer_flags ?: this.viewer,
        history = this.history,
        updateStrategy = this.updateStrategy,
        lastModifiedAt = this.lastModifiedAt,
        favoriteModifiedAt = this.favoriteModifiedAt,
        excludedScanlators = this.excludedScanlators,
        version = this.version,
    )
}

fun BackupNovel.toBackupManga(): BackupManga {
    return BackupManga(
        source = this.source,
        url = this.url,
        title = this.title,
        artist = null,
        author = this.author,
        description = this.description,
        notes = this.notes.orEmpty(),
        genre = this.genre,
        status = this.status,
        thumbnailUrl = this.thumbnailUrl,
        dateAdded = this.dateAdded,
        chapters = this.chapters,
        categories = this.categories,
        favorite = this.favorite,
        chapterFlags = this.chapterFlags,
        viewer_flags = this.viewerFlags,
        history = this.history,
        updateStrategy = this.updateStrategy,
        lastModifiedAt = this.lastModifiedAt,
        favoriteModifiedAt = this.favoriteModifiedAt,
        excludedScanlators = this.excludedScanlators,
        version = this.version,
    )
}

fun BackupManga.toBackupAnime(): BackupAnime {
    return BackupAnime(
        source = this.source,
        url = this.url,
        title = this.title,
        artist = this.artist,
        author = this.author,
        description = this.description,
        notes = this.notes,
        genre = this.genre,
        status = this.status,
        thumbnailUrl = this.thumbnailUrl,
        dateAdded = this.dateAdded,
        episodes = this.chapters.map {
            BackupEpisode(
                url = it.url,
                name = it.name,
                scanlator = it.scanlator,
                seen = it.read,
                bookmark = it.bookmark,
                lastSecondSeen = it.lastPageRead,
                totalSeconds = 0,
                dateFetch = it.dateFetch,
                dateUpload = it.dateUpload,
                episodeNumber = it.chapterNumber,
                sourceOrder = it.sourceOrder,
                lastModifiedAt = it.lastModifiedAt,
                version = it.version,
            )
        },
        categories = this.categories,
        tracking = this.tracking.map {
            BackupAnimeTracking(
                syncId = it.syncId,
                libraryId = it.libraryId,
                trackingUrl = it.trackingUrl,
                title = it.title,
                lastEpisodeSeen = it.lastChapterRead,
                totalEpisodes = it.totalChapters,
                score = it.score,
                status = it.status,
                startedWatchingDate = it.startedReadingDate,
                finishedWatchingDate = it.finishedReadingDate,
                private = it.private,
                mediaId = it.mediaId,
            )
        },
        favorite = this.favorite,
        episodeFlags = this.chapterFlags,
        viewer_flags = this.viewer_flags ?: this.viewer,
        history = this.history.map {
            BackupAnimeHistory(
                url = it.url,
                lastRead = it.lastRead,
                readDuration = it.readDuration,
            )
        },
        updateStrategy = when (this.updateStrategy) {
            eu.kanade.tachiyomi.source.model.UpdateStrategy.ALWAYS_UPDATE ->
                eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy.ALWAYS_UPDATE
            eu.kanade.tachiyomi.source.model.UpdateStrategy.ONLY_FETCH_ONCE ->
                eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy.ONLY_FETCH_ONCE
        },
        lastModifiedAt = this.lastModifiedAt,
        favoriteModifiedAt = this.favoriteModifiedAt,
        version = this.version,
    )
}

/**
 * Chapter entry as serialized by Mihon / Tachiyomi(-derived) apps.
 *
 * Identical to [BackupChapter] on fields 1..12. Mihon adds @ProtoNumber(13) as a
 * `memo` (ByteArray), whereas Tadami uses 13 for `dateUploadRaw` (String). Both are
 * length-delimited so there is no decode crash, but reading Mihon's memo bytes into
 * a String would corrupt dateUploadRaw - so field 13 is intentionally omitted here
 * and skipped by the decoder.
 */
@Serializable
data class MihonBackupChapter(
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var name: String,
    @ProtoNumber(3) var scanlator: String? = null,
    @ProtoNumber(4) var read: Boolean = false,
    @ProtoNumber(5) var bookmark: Boolean = false,
    @ProtoNumber(6) var lastPageRead: Long = 0,
    @ProtoNumber(7) var dateFetch: Long = 0,
    @ProtoNumber(8) var dateUpload: Long = 0,
    @ProtoNumber(9) var chapterNumber: Float = 0F,
    @ProtoNumber(10) var sourceOrder: Long = 0,
    @ProtoNumber(11) var lastModifiedAt: Long = 0,
    @ProtoNumber(12) var version: Long = 0,
    // 13 (memo: ByteArray) intentionally omitted - see kdoc.
) {
    fun toBackupChapter(): BackupChapter = BackupChapter(
        url = this.url,
        name = this.name,
        scanlator = this.scanlator,
        read = this.read,
        bookmark = this.bookmark,
        lastPageRead = this.lastPageRead,
        dateFetch = this.dateFetch,
        dateUpload = this.dateUpload,
        chapterNumber = this.chapterNumber,
        sourceOrder = this.sourceOrder,
        lastModifiedAt = this.lastModifiedAt,
        version = this.version,
    )
}

fun BackupChapter.toMihonBackupChapter(): MihonBackupChapter = MihonBackupChapter(
    url = this.url,
    name = this.name,
    scanlator = this.scanlator,
    read = this.read,
    bookmark = this.bookmark,
    lastPageRead = this.lastPageRead,
    dateFetch = this.dateFetch,
    dateUpload = this.dateUpload,
    chapterNumber = this.chapterNumber,
    sourceOrder = this.sourceOrder,
    lastModifiedAt = this.lastModifiedAt,
    version = this.version,
)

/**
 * Manga entry as serialized by Mihon / Tachiyomi(-derived) apps.
 *
 * Mihon and Tadami/Aniyomi share field numbers 1..109 but DIVERGE afterwards.
 * Decoding a Mihon backup with Tadami's [BackupManga] throws a wire-type mismatch
 * (Tadami expects `rating: Float` (fixed32) at 110 and `notes: String` at 111,
 * while Mihon stores `notes: String` at 110 and `initialized: Boolean` at 111).
 *
 * Real Mihon layout (verified against mihonapp/mihon):
 *   108 -> excludedScanlators (same as Tadami)
 *   109 -> version            (same as Tadami)
 *   110 -> notes              (Tadami: rating)
 *   111 -> initialized        (Tadami: notes)      -- omitted, skipped on decode
 *   112 -> memo               (Tadami: none)       -- omitted, skipped on decode
 */
@Serializable
data class MihonBackupManga(
    @ProtoNumber(1) var source: Long,
    @ProtoNumber(2) var url: String,
    @ProtoNumber(3) var title: String = "",
    @ProtoNumber(4) var artist: String? = null,
    @ProtoNumber(5) var author: String? = null,
    @ProtoNumber(6) var description: String? = null,
    @ProtoNumber(7) var genre: List<String> = emptyList(),
    @ProtoNumber(8) var status: Int = 0,
    @ProtoNumber(9) var thumbnailUrl: String? = null,
    @ProtoNumber(13) var dateAdded: Long = 0,
    @ProtoNumber(14) var viewer: Int = 0,
    @ProtoNumber(16) var chapters: List<MihonBackupChapter> = emptyList(),
    @ProtoNumber(17) var categories: List<Long> = emptyList(),
    @ProtoNumber(18) var tracking: List<BackupTracking> = emptyList(),
    @ProtoNumber(100) var favorite: Boolean = true,
    @ProtoNumber(101) var chapterFlags: Int = 0,
    @ProtoNumber(103) var viewer_flags: Int? = null,
    @ProtoNumber(104) var history: List<BackupHistory> = emptyList(),
    @ProtoNumber(105) var updateStrategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE,
    @ProtoNumber(106) var lastModifiedAt: Long = 0,
    @ProtoNumber(107) var favoriteModifiedAt: Long? = null,
    @ProtoNumber(108) var excludedScanlators: List<String> = emptyList(),
    @ProtoNumber(109) var version: Long = 0,
    @ProtoNumber(110) var notes: String = "",
    // 111 (initialized: Boolean) and 112 (memo: ByteArray) intentionally omitted -
    // Tadami has no equivalent; the decoder skips them.
) {
    fun toBackupManga(): BackupManga = BackupManga(
        source = this.source,
        url = this.url,
        title = this.title,
        artist = this.artist,
        author = this.author,
        description = this.description,
        genre = this.genre,
        status = this.status,
        thumbnailUrl = this.thumbnailUrl,
        dateAdded = this.dateAdded,
        viewer = this.viewer,
        chapters = this.chapters.map { it.toBackupChapter() },
        categories = this.categories,
        tracking = this.tracking,
        favorite = this.favorite,
        chapterFlags = this.chapterFlags,
        viewer_flags = this.viewer_flags,
        history = this.history,
        updateStrategy = this.updateStrategy,
        lastModifiedAt = this.lastModifiedAt,
        favoriteModifiedAt = this.favoriteModifiedAt,
        excludedScanlators = this.excludedScanlators,
        version = this.version,
        notes = this.notes.ifBlank { null },
    )
}

fun BackupManga.toMihonBackupManga(): MihonBackupManga = MihonBackupManga(
    source = this.source,
    url = this.url,
    title = this.title,
    artist = this.artist,
    author = this.author,
    description = this.description,
    genre = this.genre,
    status = this.status,
    thumbnailUrl = this.thumbnailUrl,
    dateAdded = this.dateAdded,
    viewer = this.viewer,
    chapters = this.chapters.map { it.toMihonBackupChapter() },
    categories = this.categories,
    tracking = this.tracking,
    favorite = this.favorite,
    chapterFlags = this.chapterFlags,
    viewer_flags = this.viewer_flags,
    history = this.history,
    updateStrategy = this.updateStrategy,
    lastModifiedAt = this.lastModifiedAt,
    favoriteModifiedAt = this.favoriteModifiedAt,
    excludedScanlators = this.excludedScanlators,
    version = this.version,
    notes = this.notes.orEmpty(),
)
