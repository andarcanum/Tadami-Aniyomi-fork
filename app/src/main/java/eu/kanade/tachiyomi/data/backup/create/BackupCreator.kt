package eu.kanade.tachiyomi.data.backup.create

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import com.tadami.aurora.BuildConfig
import eu.kanade.tachiyomi.data.backup.BackupDiagnosticLog
import eu.kanade.tachiyomi.data.backup.BackupOrigin
import eu.kanade.tachiyomi.data.backup.contentSummary
import eu.kanade.tachiyomi.data.backup.create.creators.AchievementBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.AnimeBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.AnimeCategoriesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.AnimeExtensionRepoBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.AnimeExtensionStoreBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.AnimeSourcesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.CustomButtonBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.ExtensionsBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.FeedBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaCategoriesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaExtensionRepoBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaExtensionStoreBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaSeriesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaSourcesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.NovelBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.NovelCategoriesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.NovelExtensionRepoBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.NovelExtensionStoreBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.NovelSeriesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.NovelSourcesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.PreferenceBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.ReelsFavoritesBackupCreator
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeSource
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupCustomButtons
import eu.kanade.tachiyomi.data.backup.models.BackupExtension
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.MihonBackup
import eu.kanade.tachiyomi.data.backup.models.toMihonBackup
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.achievement.handler.AchievementHandler
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.interactor.GetMangaFavorites
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

class BackupCreator(
    private val context: Context,
    private val isAutoBackup: Boolean,

    private val parser: ProtoBuf = Injekt.get(),
    private val getAnimeFavorites: GetAnimeFavorites = Injekt.get(),
    private val getMangaFavorites: GetMangaFavorites = Injekt.get(),
    private val backupPreferences: BackupPreferences = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val animeRepository: AnimeRepository = Injekt.get(),
    private val novelRepository: NovelRepository = Injekt.get(),

    private val animeCategoriesBackupCreator: AnimeCategoriesBackupCreator = AnimeCategoriesBackupCreator(),
    private val mangaCategoriesBackupCreator: MangaCategoriesBackupCreator = MangaCategoriesBackupCreator(),
    private val novelCategoriesBackupCreator: NovelCategoriesBackupCreator = NovelCategoriesBackupCreator(),
    private val animeBackupCreator: AnimeBackupCreator = AnimeBackupCreator(),
    private val mangaBackupCreator: MangaBackupCreator = MangaBackupCreator(),
    private val novelBackupCreator: NovelBackupCreator = NovelBackupCreator(),
    private val preferenceBackupCreator: PreferenceBackupCreator = PreferenceBackupCreator(),
    private val animeExtensionRepoBackupCreator: AnimeExtensionRepoBackupCreator = AnimeExtensionRepoBackupCreator(),
    private val mangaExtensionRepoBackupCreator: MangaExtensionRepoBackupCreator = MangaExtensionRepoBackupCreator(),
    private val novelExtensionRepoBackupCreator: NovelExtensionRepoBackupCreator = NovelExtensionRepoBackupCreator(),
    private val animeExtensionStoreBackupCreator: AnimeExtensionStoreBackupCreator = AnimeExtensionStoreBackupCreator(),
    private val mangaExtensionStoreBackupCreator: MangaExtensionStoreBackupCreator = MangaExtensionStoreBackupCreator(),
    private val novelExtensionStoreBackupCreator: NovelExtensionStoreBackupCreator = NovelExtensionStoreBackupCreator(),
    private val customButtonBackupCreator: CustomButtonBackupCreator = CustomButtonBackupCreator(),
    private val animeSourcesBackupCreator: AnimeSourcesBackupCreator = AnimeSourcesBackupCreator(),
    private val mangaSourcesBackupCreator: MangaSourcesBackupCreator = MangaSourcesBackupCreator(),
    private val novelSourcesBackupCreator: NovelSourcesBackupCreator = NovelSourcesBackupCreator(),
    private val mangaSeriesBackupCreator: MangaSeriesBackupCreator = MangaSeriesBackupCreator(),
    private val novelSeriesBackupCreator: NovelSeriesBackupCreator = NovelSeriesBackupCreator(),
    private val feedBackupCreator: FeedBackupCreator = FeedBackupCreator(),
    private val reelsFavoritesBackupCreator: ReelsFavoritesBackupCreator = ReelsFavoritesBackupCreator(),
    private val extensionsBackupCreator: ExtensionsBackupCreator = ExtensionsBackupCreator(context),
    private val achievementBackupCreator: AchievementBackupCreator = AchievementBackupCreator(),
    private val achievementHandler: AchievementHandler = Injekt.get(),
) {

    suspend fun backup(uri: Uri, options: BackupOptions): String {
        var file: UniFile? = null
        // Only the file this run created may be deleted on failure; a file the user picked is not
        // ours to remove.
        var createdFile: UniFile? = null
        try {
            file = BackupDiagnosticLog.measure(context, "prepare_file") {
                if (isAutoBackup) {
                    // Get dir of file and create
                    val dir = UniFile.fromUri(context, uri)
                    // Older backups are pruned only after the new one is written and verified,
                    // so a failure here can never leave the user with fewer backups than before.
                    dir?.createFile(getFilename())?.also { createdFile = it }
                } else {
                    UniFile.fromUri(context, uri)
                }
            }

            if (file == null || !file.isFile) {
                throw IllegalStateException(context.stringResource(MR.strings.create_backup_file_error))
            }

            val shouldBackupAnime = options.libraryEntries && options.backupAnime
            val shouldBackupManga = options.libraryEntries && options.backupManga
            val shouldBackupNovel = options.libraryEntries && options.backupNovel
            val includeAnimeType = if (options.libraryEntries) options.backupAnime else true
            val includeMangaType = if (options.libraryEntries) options.backupManga else true
            val includeNovelType = if (options.libraryEntries) options.backupNovel else true
            val includeAnimeCategories = options.categories && includeAnimeType
            val includeMangaCategories = options.categories && includeMangaType
            val includeNovelCategories = options.categories && includeNovelType

            val nonFavoriteAnime = if (options.readEntries && shouldBackupAnime) {
                animeRepository.getWatchedAnimeNotInLibrary()
            } else {
                emptyList()
            }
            val backupAnime = BackupDiagnosticLog.measure(context, "collect_anime") {
                backupAnimes(
                    animes = if (shouldBackupAnime) getAnimeFavorites.await() + nonFavoriteAnime else emptyList(),
                    options = options,
                )
            }
            val nonFavoriteManga = if (options.readEntries && shouldBackupManga) {
                mangaRepository.getReadMangaNotInLibrary()
            } else {
                emptyList()
            }
            val backupManga = BackupDiagnosticLog.measure(context, "collect_manga") {
                backupMangas(
                    mangas = if (shouldBackupManga) getMangaFavorites.await() + nonFavoriteManga else emptyList(),
                    options = options,
                )
            }
            val nonFavoriteNovel = if (options.readEntries && shouldBackupNovel) {
                novelRepository.getReadNovelNotInLibrary()
            } else {
                emptyList()
            }
            val backupNovel = BackupDiagnosticLog.measure(context, "collect_novel") {
                backupNovels(
                    novels = if (shouldBackupNovel) {
                        novelRepository.getNovelFavorites() + nonFavoriteNovel
                    } else {
                        emptyList()
                    },
                    options = options,
                )
            }

            val achievementData = BackupDiagnosticLog.measure(context, "collect_achievements") {
                achievementBackupCreator(options)
            }
            val backupMangaSeries = BackupDiagnosticLog.measure(context, "collect_manga_series") {
                if (shouldBackupManga) mangaSeriesBackupCreator() else emptyList()
            }
            val backupNovelSeries = BackupDiagnosticLog.measure(context, "collect_novel_series") {
                if (shouldBackupNovel) novelSeriesBackupCreator() else emptyList()
            }
            val backupFeeds = BackupDiagnosticLog.measure(context, "collect_feeds") {
                feedBackupCreator()
            }
            // sisterAppCompatible exports drop reels favorites anyway — skip the table read.
            val backupReelsFavorites = BackupDiagnosticLog.measure(context, "collect_reels_favorites") {
                if (options.reelsFavorites && !options.sisterAppCompatible) {
                    reelsFavoritesBackupCreator()
                } else {
                    emptyList()
                }
            }

            val finalBackupManga = if (options.sisterAppCompatible) {
                backupManga + backupNovel.map { it.toBackupManga() }
            } else {
                backupManga
            }
            val mangaCats = backupMangaCategories(options, includeMangaCategories)
            val novelCats = backupNovelCategories(options, includeNovelCategories)
            val finalCategories = if (options.sisterAppCompatible) {
                (mangaCats + novelCats).distinctBy { it.name }
            } else {
                mangaCats
            }

            val backup = Backup(
                backupManga = finalBackupManga,
                backupCategories = finalCategories,
                backupSources = backupMangaSources(finalBackupManga),
                backupPreferences = backupAppPreferences(options),
                backupSourcePreferences = backupSourcePreferences(options),
                backupMangaExtensionRepo = backupMangaExtensionRepos(
                    options,
                    includeMangaType,
                ),

                isLegacy = false,
                backupAnime = if (options.sisterAppCompatible) emptyList() else backupAnime,
                backupAnimeCategories = if (options.sisterAppCompatible) {
                    emptyList()
                } else {
                    backupAnimeCategories(
                        options,
                        includeAnimeCategories,
                    )
                },
                backupAnimeSources = if (options.sisterAppCompatible) emptyList() else backupAnimeSources(backupAnime),
                backupNovel = if (options.sisterAppCompatible) emptyList() else backupNovel,
                backupNovelCategories = if (options.sisterAppCompatible) {
                    emptyList()
                } else {
                    backupNovelCategories(
                        options,
                        includeNovelCategories,
                    )
                },
                backupNovelSources = if (options.sisterAppCompatible) emptyList() else backupNovelSources(backupNovel),
                backupExtensions = if (options.sisterAppCompatible) emptyList() else backupExtensions(options),
                backupAnimeExtensionRepo = backupAnimeExtensionRepos(
                    options,
                    includeAnimeType,
                ),
                backupCustomButton = if (options.sisterAppCompatible) emptyList() else backupCustomButtons(options),
                backupNovelExtensionRepo = backupNovelExtensionRepos(
                    options,
                    includeNovelType,
                ),
                backupAnimeExtensionStore = backupAnimeExtensionStores(options, includeAnimeType),
                backupMangaExtensionStore = backupMangaExtensionStores(options, includeMangaType),
                backupNovelExtensionStore = backupNovelExtensionStores(options, includeNovelType),
                backupAchievements = if (options.sisterAppCompatible) emptyList() else achievementData.achievements,
                backupUserProfile = if (options.sisterAppCompatible) null else achievementData.userProfile,
                backupActivityLog = if (options.sisterAppCompatible) emptyList() else achievementData.activityLog,
                backupStats = if (options.sisterAppCompatible) null else achievementData.stats,
                backupMangaSeries = if (options.sisterAppCompatible) emptyList() else backupMangaSeries,
                backupNovelSeries = if (options.sisterAppCompatible) emptyList() else backupNovelSeries,
                backupFeeds = if (options.sisterAppCompatible) emptyList() else backupFeeds,
                backupReelsFavorites = if (options.sisterAppCompatible) {
                    emptyList()
                } else {
                    backupReelsFavorites
                },
            )

            val byteArray = BackupDiagnosticLog.measure(context, "serialize") {
                if (options.sisterAppCompatible) {
                    parser.encodeToByteArray(MihonBackup.serializer(), backup.toMihonBackup())
                } else {
                    parser.encodeToByteArray(Backup.serializer(), backup)
                }
            }
            if (byteArray.isEmpty()) {
                throw IllegalStateException(context.stringResource(MR.strings.empty_backup_error))
            }
            BackupDiagnosticLog.log(context, "serialize_size", "bytes=${byteArray.size}")

            val expectedOrigin = if (options.sisterAppCompatible) {
                BackupOrigin.TADAMI_SISTER
            } else {
                BackupOrigin.TADAMI
            }
            // In sister mode novels travel inside the shared manga section, so the expected split
            // is the one the file itself declares, not the one we started from.
            val expectedSummary = backup.contentSummary().let {
                if (options.sisterAppCompatible) it.copy(novelCount = 0, animeCount = 0) else it
            }

            // Writes to a staging file, verifies it decodes back to exactly this content, and only
            // then replaces the destination.
            BackupWriter(context).write(
                destination = file,
                payload = byteArray,
                expected = expectedSummary,
                expectedOrigin = expectedOrigin,
            )
            val fileUri = file.uri

            if (isAutoBackup) {
                pruneOldAutoBackups(uri, keep = file)
                backupPreferences.lastAutoBackupTimestamp().set(Instant.now().toEpochMilli())
            }

            // Track backup achievement for manual backups only
            if (!isAutoBackup) {
                achievementHandler.trackFeatureUsed(AchievementEvent.Feature.BACKUP)
            }

            BackupDiagnosticLog.log(context, "creator_done", "origin=$expectedOrigin")

            return fileUri.toString()
        } catch (e: CancellationException) {
            BackupDiagnosticLog.log(context, "creator_cancelled")
            createdFile?.delete()
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            BackupDiagnosticLog.logError(context, "creator_failed", e)
            createdFile?.delete()
            throw e
        }
    }

    /**
     * Drop the oldest auto backups, keeping the freshly written one.
     *
     * Runs only after a verified write: an aborted or corrupt backup must never cost the user a
     * known-good older file.
     */
    private fun pruneOldAutoBackups(dirUri: Uri, keep: UniFile?) {
        val limit = backupPreferences.numberOfBackupsToKeep().get()
        if (limit <= 0) return
        val dir = UniFile.fromUri(context, dirUri) ?: return
        dir.listFiles { _, filename -> FILENAME_REGEX.matches(filename) }
            .orEmpty()
            .filter { it.uri != keep?.uri }
            .sortedByDescending { it.name }
            .drop(limit - 1)
            .forEach { it.delete() }
    }

    private suspend fun backupAnimeCategories(options: BackupOptions, includeType: Boolean): List<BackupCategory> {
        if (!options.categories || !includeType) return emptyList()

        return animeCategoriesBackupCreator()
    }

    private suspend fun backupMangaCategories(options: BackupOptions, includeType: Boolean): List<BackupCategory> {
        if (!options.categories || !includeType) return emptyList()

        return mangaCategoriesBackupCreator()
    }

    private suspend fun backupNovelCategories(options: BackupOptions, includeType: Boolean): List<BackupCategory> {
        if (!options.categories || !includeType) return emptyList()

        return novelCategoriesBackupCreator()
    }

    private suspend fun backupMangas(mangas: List<Manga>, options: BackupOptions): List<BackupManga> {
        if (!options.libraryEntries || !options.backupManga) return emptyList()

        return mangaBackupCreator(mangas, options)
    }

    private suspend fun backupAnimes(animes: List<Anime>, options: BackupOptions): List<BackupAnime> {
        if (!options.libraryEntries || !options.backupAnime) return emptyList()

        return animeBackupCreator(animes, options)
    }

    private suspend fun backupNovels(novels: List<Novel>, options: BackupOptions): List<BackupNovel> {
        if (!options.libraryEntries || !options.backupNovel) return emptyList()

        return novelBackupCreator(novels, options)
    }

    private fun backupAnimeSources(animes: List<BackupAnime>): List<BackupAnimeSource> {
        return animeSourcesBackupCreator(animes)
    }
    private fun backupMangaSources(mangas: List<BackupManga>): List<BackupSource> {
        return mangaSourcesBackupCreator(mangas)
    }

    private fun backupNovelSources(novels: List<BackupNovel>): List<BackupSource> {
        return novelSourcesBackupCreator(novels)
    }

    private fun backupAppPreferences(options: BackupOptions): List<BackupPreference> {
        if (!options.appSettings) return emptyList()

        return preferenceBackupCreator.createApp(includePrivatePreferences = options.privateSettings)
    }

    private suspend fun backupAnimeExtensionRepos(
        options: BackupOptions,
        includeType: Boolean,
    ): List<BackupExtensionRepos> {
        if (!options.extensionRepoSettings || !includeType) return emptyList()

        return animeExtensionRepoBackupCreator()
    }

    private suspend fun backupMangaExtensionRepos(
        options: BackupOptions,
        includeType: Boolean,
    ): List<BackupExtensionRepos> {
        if (!options.extensionRepoSettings || !includeType) return emptyList()

        return mangaExtensionRepoBackupCreator()
    }

    private suspend fun backupNovelExtensionRepos(
        options: BackupOptions,
        includeType: Boolean,
    ): List<BackupExtensionRepos> {
        if (!options.extensionRepoSettings || !includeType) return emptyList()

        return novelExtensionRepoBackupCreator()
    }

    private suspend fun backupAnimeExtensionStores(
        options: BackupOptions,
        includeType: Boolean,
    ): List<BackupExtensionStore> {
        if (!shouldBackupExtensionStores(options, includeType)) return emptyList()

        return animeExtensionStoreBackupCreator()
    }

    private suspend fun backupMangaExtensionStores(
        options: BackupOptions,
        includeType: Boolean,
    ): List<BackupExtensionStore> {
        if (!shouldBackupExtensionStores(options, includeType)) return emptyList()

        return mangaExtensionStoreBackupCreator()
    }

    private suspend fun backupNovelExtensionStores(
        options: BackupOptions,
        includeType: Boolean,
    ): List<BackupExtensionStore> {
        if (!shouldBackupExtensionStores(options, includeType)) return emptyList()

        return novelExtensionStoreBackupCreator()
    }

    private suspend fun backupCustomButtons(options: BackupOptions): List<BackupCustomButtons> {
        if (!options.customButton) return emptyList()

        return customButtonBackupCreator()
    }

    private fun backupSourcePreferences(options: BackupOptions): List<BackupSourcePreferences> {
        if (!options.sourceSettings) return emptyList()

        return preferenceBackupCreator.createSource(includePrivatePreferences = options.privateSettings)
    }

    private fun backupExtensions(options: BackupOptions): List<BackupExtension> {
        if (!options.extensions) return emptyList()

        return extensionsBackupCreator()
    }

    private fun BackupNovel.toBackupManga(): BackupManga {
        return BackupManga(
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
            viewer = this.viewerFlags,
            viewer_flags = this.viewerFlags,
            history = this.history,
            updateStrategy = this.updateStrategy,
            lastModifiedAt = this.lastModifiedAt,
            favoriteModifiedAt = this.favoriteModifiedAt,
            excludedScanlators = this.excludedScanlators,
            version = this.version,
            customTitle = this.customTitle,
            customAuthor = this.customAuthor,
            customDescription = this.customDescription,
            customGenre = this.customGenre,
            customStatus = this.customStatus,
            completedAt = this.completedAt,
        )
    }

    companion object {
        private const val MAX_AUTO_BACKUPS: Int = 4
        private val FILENAME_REGEX = """${BuildConfig.APPLICATION_ID}_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}.tachibk""".toRegex()

        fun getFilename(): String {
            val date = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ENGLISH).format(Date())
            return "${BuildConfig.APPLICATION_ID}_$date.tachibk"
        }
    }
}

/**
 * Extension stores follow the same toggle as the legacy extension repos: they are the same rows in
 * a different shape, so exporting them while the user unchecked the option would leak the very data
 * they excluded - and re-insert it on the next restore.
 */
internal fun shouldBackupExtensionStores(options: BackupOptions, includeType: Boolean): Boolean {
    return options.extensionRepoSettings && includeType
}
