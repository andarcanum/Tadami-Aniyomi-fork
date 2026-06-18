package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager

@Serializable
data class MihonBackup(
    @ProtoNumber(1) val backupManga: List<BackupManga> = emptyList(),
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
            backupManga = backupManga,
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
        backupManga = backupManga + backupNovel.map { it.toBackupManga() },
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

    backupManga.forEach { entry ->
        val sourceId = entry.source
        when {
            novelSourceClassifier(sourceId) -> novels.add(entry.toBackupNovel())
            animeSourceClassifier(sourceId) -> animes.add(entry.toBackupAnime())
            mangaSourceClassifier(sourceId) -> mangas.add(entry)
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
