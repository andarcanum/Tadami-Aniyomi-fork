package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.BackupDecoder
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupCustomButtons
import eu.kanade.tachiyomi.data.backup.models.BackupExtension
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.restore.restorers.AchievementRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeCategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeExtensionRepoRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.CustomButtonRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.ExtensionsRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaCategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaExtensionRepoRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.NovelCategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.NovelExtensionRepoRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.NovelRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.PreferenceRestorer
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupRestorer(
    private val context: Context,
    private val notifier: BackupNotifier,
    private val isSync: Boolean,

    private val animeCategoriesRestorer: AnimeCategoriesRestorer = AnimeCategoriesRestorer(),
    private val mangaCategoriesRestorer: MangaCategoriesRestorer = MangaCategoriesRestorer(),
    private val novelCategoriesRestorer: NovelCategoriesRestorer = NovelCategoriesRestorer(),
    private val preferenceRestorer: PreferenceRestorer = PreferenceRestorer(context),
    private val animeExtensionRepoRestorer: AnimeExtensionRepoRestorer = AnimeExtensionRepoRestorer(),
    private val mangaExtensionRepoRestorer: MangaExtensionRepoRestorer = MangaExtensionRepoRestorer(),
    private val novelExtensionRepoRestorer: NovelExtensionRepoRestorer = NovelExtensionRepoRestorer(),
    private val customButtonRestorer: CustomButtonRestorer = CustomButtonRestorer(),
    private val animeRestorer: AnimeRestorer = AnimeRestorer(),
    private val mangaRestorer: MangaRestorer = MangaRestorer(),
    private val novelRestorer: NovelRestorer = NovelRestorer(),
    private val extensionsRestorer: ExtensionsRestorer = ExtensionsRestorer(context),
    private val achievementRestorer: AchievementRestorer = AchievementRestorer(),
) {

    private var restoreAmount = 0
    private var restoreProgress = 0
    private val errors = mutableListOf<Pair<Date, String>>()

    /**
     * Mapping of source ID to source name from backup data
     */
    private var animeSourceMapping: Map<Long, String> = emptyMap()
    private var mangaSourceMapping: Map<Long, String> = emptyMap()
    private var novelSourceMapping: Map<Long, String> = emptyMap()

    suspend fun restore(uri: Uri, options: RestoreOptions) {
        val startTime = System.currentTimeMillis()

        restoreFromFile(uri, options)

        val time = System.currentTimeMillis() - startTime

        val logFile = writeErrorLog()

        notifier.showRestoreComplete(
            time,
            errors.size,
            logFile.parent,
            logFile.name,
            isSync,
        )
    }

    private suspend fun restoreFromFile(uri: Uri, options: RestoreOptions) {
        val backup = BackupDecoder(context).decode(uri)

        // Store source mapping for error messages
        val backupAnimeMaps = backup.backupAnimeSources
        animeSourceMapping = backupAnimeMaps.associate { it.sourceId to it.name }
        val backupMangaMaps = backup.backupSources
        mangaSourceMapping = backupMangaMaps.associate { it.sourceId to it.name }
        val backupNovelMaps = backup.backupNovelSources
        novelSourceMapping = backupNovelMaps.associate { it.sourceId to it.name }

        val shouldRestoreManga = options.libraryEntries && options.restoreManga
        val shouldRestoreAnime = options.libraryEntries && options.restoreAnime
        val shouldRestoreNovel = options.libraryEntries && options.restoreNovel
        val includeAllCategories = !options.libraryEntries
        val shouldRestoreMangaCategories = options.categories && (includeAllCategories || options.restoreManga)
        val shouldRestoreAnimeCategories = options.categories && (includeAllCategories || options.restoreAnime)
        val shouldRestoreNovelCategories = options.categories && (includeAllCategories || options.restoreNovel)

        if (options.libraryEntries) {
            if (options.restoreManga) restoreAmount += backup.backupManga.size
            if (options.restoreAnime) restoreAmount += backup.backupAnime.size
            if (options.restoreNovel) restoreAmount += backup.backupNovel.size
        }
        if (options.categories) {
            if (shouldRestoreAnimeCategories) restoreAmount += 1
            if (shouldRestoreMangaCategories) restoreAmount += 1
            if (shouldRestoreNovelCategories) restoreAmount += 1
        }
        if (options.appSettings) {
            restoreAmount += 1
        }
        if (options.extensionRepoSettings) {
            if (options.restoreAnime) restoreAmount += backup.backupAnimeExtensionRepo.size
            if (options.restoreManga) restoreAmount += backup.backupMangaExtensionRepo.size
            if (options.restoreNovel) restoreAmount += backup.backupNovelExtensionRepo.size
        }
        if (options.customButtons) {
            restoreAmount += 1
        }
        if (options.sourceSettings) {
            restoreAmount += 1
        }
        if (options.extensions) {
            restoreAmount += 1
        }

        coroutineScope {
            if (options.categories) {
                restoreCategories(
                    backupAnimeCategories = if (shouldRestoreAnimeCategories) {
                        backup.backupAnimeCategories
                    } else {
                        emptyList()
                    },
                    backupMangaCategories = if (shouldRestoreMangaCategories) backup.backupCategories else emptyList(),
                    backupNovelCategories = if (shouldRestoreNovelCategories) {
                        backup.backupNovelCategories
                    } else {
                        emptyList()
                    },
                    restoreAnimeCategories = shouldRestoreAnimeCategories,
                    restoreMangaCategories = shouldRestoreMangaCategories,
                    restoreNovelCategories = shouldRestoreNovelCategories,
                )
            }
            if (options.appSettings) {
                restoreAppPreferences(backup.backupPreferences, backup.backupCategories.takeIf { options.categories })
            }
            if (options.sourceSettings) {
                restoreSourcePreferences(backup.backupSourcePreferences)
            }
            if (options.libraryEntries) {
                if (shouldRestoreAnime) {
                    restoreAnime(
                        backup.backupAnime,
                        if (shouldRestoreAnimeCategories) backup.backupAnimeCategories else emptyList(),
                    )
                }
                if (shouldRestoreManga) {
                    restoreManga(
                        backup.backupManga,
                        if (shouldRestoreMangaCategories) backup.backupCategories else emptyList(),
                    )
                }
                if (shouldRestoreNovel) {
                    restoreNovel(
                        backup.backupNovel,
                        if (shouldRestoreNovelCategories) backup.backupNovelCategories else emptyList(),
                    )
                }
            }
            if (options.extensionRepoSettings) {
                restoreExtensionRepos(
                    if (options.restoreAnime) backup.backupAnimeExtensionRepo else emptyList(),
                    if (options.restoreManga) backup.backupMangaExtensionRepo else emptyList(),
                    if (options.restoreNovel) backup.backupNovelExtensionRepo else emptyList(),
                )
            }
            if (options.customButtons) {
                restoreCustomButtons(backup.backupCustomButton)
            }
            if (options.extensions) {
                restoreExtensions(backup.backupExtensions)
            }

            // Restore achievements if option enabled
            if (options.achievements || options.stats) {
                restoreAchievements(
                    achievements = if (options.achievements) backup.backupAchievements else emptyList(),
                    userProfile = backup.backupUserProfile.takeIf { options.achievements },
                    activityLog = if (options.achievements) backup.backupActivityLog else emptyList(),
                    stats = backup.backupStats.takeIf { options.stats },
                )
            }

            // TODO: optionally trigger online library + tracker update
        }
    }

    private fun CoroutineScope.restoreCategories(
        backupAnimeCategories: List<BackupCategory>,
        backupMangaCategories: List<BackupCategory>,
        backupNovelCategories: List<BackupCategory>,
        restoreAnimeCategories: Boolean,
        restoreMangaCategories: Boolean,
        restoreNovelCategories: Boolean,
    ) = launch {
        ensureActive()
        if (restoreAnimeCategories) {
            if (backupAnimeCategories.isNotEmpty()) {
                animeCategoriesRestorer(backupAnimeCategories)
            }
            restoreProgress += 1
            notifier.showRestoreProgress(
                context.stringResource(MR.strings.categories),
                restoreProgress,
                restoreAmount,
                isSync,
            )
        }
        if (restoreMangaCategories) {
            if (backupMangaCategories.isNotEmpty()) {
                mangaCategoriesRestorer(backupMangaCategories)
            }
            restoreProgress += 1
            notifier.showRestoreProgress(
                context.stringResource(MR.strings.categories),
                restoreProgress,
                restoreAmount,
                isSync,
            )
        }
        if (restoreNovelCategories) {
            if (backupNovelCategories.isNotEmpty()) {
                novelCategoriesRestorer(backupNovelCategories)
            }
            restoreProgress += 1
            notifier.showRestoreProgress(
                context.stringResource(MR.strings.categories),
                restoreProgress,
                restoreAmount,
                isSync,
            )
        }
    }

    private fun CoroutineScope.restoreAnime(
        backupAnimes: List<BackupAnime>,
        backupAnimeCategories: List<BackupCategory>,
    ) = launch {
        animeRestorer.sortByNew(backupAnimes)
            .forEach {
                ensureActive()

                val seasons = backupAnimes.filter { s -> s.parentId == it.id }
                try {
                    animeRestorer.restore(it, backupAnimeCategories, seasons)
                } catch (e: Exception) {
                    val sourceName = animeSourceMapping[it.source] ?: it.source.toString()
                    errors.add(Date() to "${it.title} [$sourceName]: ${e.message}")
                }

                restoreProgress += 1
                notifier.showRestoreProgress(it.title, restoreProgress, restoreAmount, isSync)
            }
    }

    private fun CoroutineScope.restoreManga(
        backupMangas: List<BackupManga>,
        backupMangaCategories: List<BackupCategory>,
    ) = launch {
        mangaRestorer.sortByNew(backupMangas)
            .forEach {
                ensureActive()

                try {
                    mangaRestorer.restore(it, backupMangaCategories)
                } catch (e: Exception) {
                    val sourceName = mangaSourceMapping[it.source] ?: it.source.toString()
                    errors.add(Date() to "${it.title} [$sourceName]: ${e.message}")
                }

                restoreProgress += 1
                notifier.showRestoreProgress(it.title, restoreProgress, restoreAmount, isSync)
            }
    }

    private fun CoroutineScope.restoreNovel(
        backupNovels: List<BackupNovel>,
        backupNovelCategories: List<BackupCategory>,
    ) = launch {
        novelRestorer.sortByNew(backupNovels)
            .forEach {
                ensureActive()

                try {
                    novelRestorer.restore(it, backupNovelCategories)
                } catch (e: Exception) {
                    val sourceName = novelSourceMapping[it.source] ?: it.source.toString()
                    errors.add(Date() to "${it.title} [$sourceName]: ${e.message}")
                }

                restoreProgress += 1
                notifier.showRestoreProgress(it.title, restoreProgress, restoreAmount, isSync)
            }
    }

    private fun CoroutineScope.restoreAppPreferences(
        preferences: List<BackupPreference>,
        categories: List<BackupCategory>?,
    ) = launch {
        ensureActive()
        preferenceRestorer.restoreApp(
            preferences,
            categories,
        )

        restoreProgress += 1
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.app_settings),
            restoreProgress,
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreSourcePreferences(preferences: List<BackupSourcePreferences>) = launch {
        ensureActive()
        preferenceRestorer.restoreSource(preferences)

        restoreProgress += 1
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.source_settings),
            restoreProgress,
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreExtensionRepos(
        backupAnimeExtensionRepo: List<BackupExtensionRepos>,
        backupMangaExtensionRepo: List<BackupExtensionRepos>,
        backupNovelExtensionRepo: List<BackupExtensionRepos>,
    ) = launch {
        backupAnimeExtensionRepo
            .forEach {
                ensureActive()

                try {
                    animeExtensionRepoRestorer(it)
                } catch (e: Exception) {
                    errors.add(Date() to "Error Adding Anime Repo: ${it.name} : ${e.message}")
                }

                restoreProgress += 1
                notifier.showRestoreProgress(
                    context.stringResource(MR.strings.extensionRepo_settings),
                    restoreProgress,
                    restoreAmount,
                    isSync,
                )
            }

        backupMangaExtensionRepo
            .forEach {
                ensureActive()

                try {
                    mangaExtensionRepoRestorer(it)
                } catch (e: Exception) {
                    errors.add(Date() to "Error Adding Manga Repo: ${it.name} : ${e.message}")
                }

                restoreProgress += 1
                notifier.showRestoreProgress(
                    context.stringResource(MR.strings.extensionRepo_settings),
                    restoreProgress,
                    restoreAmount,
                    isSync,
                )
            }

        backupNovelExtensionRepo
            .forEach {
                ensureActive()

                try {
                    novelExtensionRepoRestorer(it)
                } catch (e: Exception) {
                    errors.add(Date() to "Error Adding Novel Repo: ${it.name} : ${e.message}")
                }

                restoreProgress += 1
                notifier.showRestoreProgress(
                    context.stringResource(MR.strings.extensionRepo_settings),
                    restoreProgress,
                    restoreAmount,
                    isSync,
                )
            }
    }

    private fun CoroutineScope.restoreCustomButtons(customButtons: List<BackupCustomButtons>) = launch {
        ensureActive()
        customButtonRestorer(customButtons)

        restoreProgress += 1
        notifier.showRestoreProgress(
            context.stringResource(AYMR.strings.custom_button_settings),
            restoreProgress,
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreExtensions(extensions: List<BackupExtension>) = launch {
        ensureActive()
        extensionsRestorer.restoreExtensions(extensions)

        restoreProgress += 1
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.source_settings),
            restoreProgress,
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreAchievements(
        achievements: List<eu.kanade.tachiyomi.data.backup.models.BackupAchievement>,
        userProfile: eu.kanade.tachiyomi.data.backup.models.BackupUserProfile?,
        activityLog: List<eu.kanade.tachiyomi.data.backup.models.BackupDayActivity>,
        stats: eu.kanade.tachiyomi.data.backup.models.BackupStats?,
    ) = launch {
        ensureActive()
        achievementRestorer.restoreAchievements(achievements, userProfile, activityLog, stats)
    }

    private fun writeErrorLog(): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("aniyomi_restore_error.txt")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

                file.bufferedWriter().use { out ->
                    errors.forEach { (date, message) ->
                        out.write("[${sdf.format(date)}] $message\n")
                    }
                }
                return file
            }
        } catch (e: Exception) {
            // Empty
        }
        return File("")
    }
}
