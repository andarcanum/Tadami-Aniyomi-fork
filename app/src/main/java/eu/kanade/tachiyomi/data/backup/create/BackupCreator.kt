package eu.kanade.tachiyomi.data.backup.create

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.backup.BackupFileValidator
import eu.kanade.tachiyomi.data.backup.create.creators.AchievementBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.AnimeBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.AnimeCategoriesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.AnimeExtensionRepoBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.AnimeSourcesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.CustomButtonBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.ExtensionsBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaCategoriesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaExtensionRepoBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaSourcesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.NovelBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.NovelCategoriesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.NovelExtensionRepoBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.NovelSourcesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.PreferenceBackupCreator
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeSource
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupCustomButtons
import eu.kanade.tachiyomi.data.backup.models.BackupExtension
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import okio.buffer
import okio.gzip
import okio.sink
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.achievement.handler.AchievementHandler
import tachiyomi.data.achievement.model.AchievementEvent
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
import java.io.FileOutputStream
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
    private val customButtonBackupCreator: CustomButtonBackupCreator = CustomButtonBackupCreator(),
    private val animeSourcesBackupCreator: AnimeSourcesBackupCreator = AnimeSourcesBackupCreator(),
    private val mangaSourcesBackupCreator: MangaSourcesBackupCreator = MangaSourcesBackupCreator(),
    private val novelSourcesBackupCreator: NovelSourcesBackupCreator = NovelSourcesBackupCreator(),
    private val extensionsBackupCreator: ExtensionsBackupCreator = ExtensionsBackupCreator(context),
    private val achievementBackupCreator: AchievementBackupCreator = AchievementBackupCreator(),
    private val achievementHandler: AchievementHandler = Injekt.get(),
) {

    suspend fun backup(uri: Uri, options: BackupOptions): String {
        var file: UniFile? = null
        try {
            file = if (isAutoBackup) {
                // Get dir of file and create
                val dir = UniFile.fromUri(context, uri)
                // Delete older backups
                dir?.listFiles { _, filename -> FILENAME_REGEX.matches(filename) }
                    .orEmpty()
                    .sortedByDescending { it.name }
                    .drop(MAX_AUTO_BACKUPS - 1)
                    .forEach { it.delete() }
                // Create new file to place backup
                dir?.createFile(getFilename())
            } else {
                UniFile.fromUri(context, uri)
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
            val backupAnime = backupAnimes(
                animes = if (shouldBackupAnime) getAnimeFavorites.await() + nonFavoriteAnime else emptyList(),
                options = options,
            )
            val nonFavoriteManga = if (options.readEntries && shouldBackupManga) {
                mangaRepository.getReadMangaNotInLibrary()
            } else {
                emptyList()
            }
            val backupManga = backupMangas(
                mangas = if (shouldBackupManga) getMangaFavorites.await() + nonFavoriteManga else emptyList(),
                options = options,
            )
            val nonFavoriteNovel = if (options.readEntries && shouldBackupNovel) {
                novelRepository.getReadNovelNotInLibrary()
            } else {
                emptyList()
            }
            val backupNovel = backupNovels(
                novels = if (shouldBackupNovel) novelRepository.getNovelFavorites() + nonFavoriteNovel else emptyList(),
                options = options,
            )

            val achievementData = achievementBackupCreator(options)

            val backup = Backup(
                backupManga = backupManga,
                backupCategories = backupMangaCategories(options, includeMangaCategories),
                backupSources = backupMangaSources(backupManga),
                backupPreferences = backupAppPreferences(options),
                backupSourcePreferences = backupSourcePreferences(options),
                backupMangaExtensionRepo = backupMangaExtensionRepos(options, includeMangaType),

                isLegacy = false,
                backupAnime = backupAnime,
                backupAnimeCategories = backupAnimeCategories(options, includeAnimeCategories),
                backupAnimeSources = backupAnimeSources(backupAnime),
                backupNovel = backupNovel,
                backupNovelCategories = backupNovelCategories(options, includeNovelCategories),
                backupNovelSources = backupNovelSources(backupNovel),
                backupExtensions = backupExtensions(options),
                backupAnimeExtensionRepo = backupAnimeExtensionRepos(options, includeAnimeType),
                backupCustomButton = backupCustomButtons(options),
                backupNovelExtensionRepo = backupNovelExtensionRepos(options, includeNovelType),
                backupAchievements = achievementData.achievements,
                backupUserProfile = achievementData.userProfile,
                backupActivityLog = achievementData.activityLog,
                backupStats = achievementData.stats,
            )

            val byteArray = parser.encodeToByteArray(Backup.serializer(), backup)
            if (byteArray.isEmpty()) {
                throw IllegalStateException(context.stringResource(MR.strings.empty_backup_error))
            }

            file.openOutputStream()
                .also {
                    // Force overwrite old file
                    (it as? FileOutputStream)?.channel?.truncate(0)
                }
                .sink().gzip().buffer().use {
                    it.write(byteArray)
                }
            val fileUri = file.uri

            // Make sure it's a valid backup file
            BackupFileValidator(context).validate(fileUri)

            if (isAutoBackup) {
                backupPreferences.lastAutoBackupTimestamp().set(Instant.now().toEpochMilli())
            }

            // Track backup achievement for manual backups only
            if (!isAutoBackup) {
                achievementHandler.trackFeatureUsed(AchievementEvent.Feature.BACKUP)
            }

            return fileUri.toString()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            file?.delete()
            throw e
        }
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

    companion object {
        private const val MAX_AUTO_BACKUPS: Int = 4
        private val FILENAME_REGEX = """${BuildConfig.APPLICATION_ID}_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}.tachibk""".toRegex()

        fun getFilename(): String {
            val date = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ENGLISH).format(Date())
            return "${BuildConfig.APPLICATION_ID}_$date.tachibk"
        }
    }
}
