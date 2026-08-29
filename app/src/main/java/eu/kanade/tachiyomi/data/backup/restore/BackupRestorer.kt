package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.BackupDecoder
import eu.kanade.tachiyomi.data.backup.BackupDiagnosticLog
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupCustomButtons
import eu.kanade.tachiyomi.data.backup.models.BackupExtension
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.restore.restorers.AchievementRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeCategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeExtensionRepoRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeExtensionStoreRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.CustomButtonRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.ExtensionsRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.FeedRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaCategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaExtensionRepoRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaExtensionStoreRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaSeriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.NovelCategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.NovelExtensionRepoRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.NovelExtensionStoreRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.NovelRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.NovelSeriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.PreferenceRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.ReelsFavoritesRestorer
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import java.io.File
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Media types tracked while restoring, used for the completeness report. */
private const val TYPE_MANGA = "manga"
private const val TYPE_ANIME = "anime"
private const val TYPE_NOVEL = "novel"

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
    private val animeExtensionStoreRestorer: AnimeExtensionStoreRestorer = AnimeExtensionStoreRestorer(),
    private val mangaExtensionStoreRestorer: MangaExtensionStoreRestorer = MangaExtensionStoreRestorer(),
    private val novelExtensionStoreRestorer: NovelExtensionStoreRestorer = NovelExtensionStoreRestorer(),
    private val customButtonRestorer: CustomButtonRestorer = CustomButtonRestorer(),
    private val animeRestorer: AnimeRestorer = AnimeRestorer(),
    private val mangaRestorer: MangaRestorer = MangaRestorer(),
    private val novelRestorer: NovelRestorer = NovelRestorer(),
    private val mangaSeriesRestorer: MangaSeriesRestorer = MangaSeriesRestorer(),
    private val novelSeriesRestorer: NovelSeriesRestorer = NovelSeriesRestorer(),
    private val feedRestorer: FeedRestorer = FeedRestorer(),
    private val reelsFavoritesRestorer: ReelsFavoritesRestorer = ReelsFavoritesRestorer(),
    private val extensionsRestorer: ExtensionsRestorer = ExtensionsRestorer(context),
    private val achievementRestorer: AchievementRestorer = AchievementRestorer(),
) {

    private var restoreAmount = 0

    // Library entries are restored from several coroutines at once, so progress, errors and the
    // per-type counters must all be safe to touch concurrently.
    private val restoreProgress = AtomicInteger(0)
    private val errors = Collections.synchronizedList(mutableListOf<Pair<Date, String>>())

    private val expectedByType = ConcurrentHashMap<String, AtomicInteger>()
    private val restoredByType = ConcurrentHashMap<String, AtomicInteger>()
    private val failedByType = ConcurrentHashMap<String, AtomicInteger>()

    private fun advanceProgress(): Int = restoreProgress.incrementAndGet()

    private fun expect(type: String, amount: Int) {
        if (amount <= 0) return
        expectedByType.getOrPut(type) { AtomicInteger(0) }.addAndGet(amount)
    }

    private fun markRestored(type: String) {
        restoredByType.getOrPut(type) { AtomicInteger(0) }.incrementAndGet()
    }

    private fun markFailed(type: String) {
        failedByType.getOrPut(type) { AtomicInteger(0) }.incrementAndGet()
    }

    private fun countOf(map: Map<String, AtomicInteger>, type: String): Int = map[type]?.get() ?: 0

    /**
     * Mapping of source ID to source name from backup data
     */
    private var animeSourceMapping: Map<Long, String> = emptyMap()
    private var mangaSourceMapping: Map<Long, String> = emptyMap()
    private var novelSourceMapping: Map<Long, String> = emptyMap()

    suspend fun restore(uri: Uri, options: RestoreOptions) {
        val startTime = System.currentTimeMillis()

        restoreFromFile(uri, options)
        verifyCompleteness()

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

    /**
     * Every entry the backup declared must end up either restored or reported as failed.
     *
     * Silently dropping entries is the one failure mode a user cannot detect on their own, so a
     * mismatch is surfaced in the restore report instead of being swallowed.
     */
    private fun verifyCompleteness() {
        val summary = expectedByType.keys.sorted().joinToString(" ") { type ->
            val expected = countOf(expectedByType, type)
            val restored = countOf(restoredByType, type)
            val failed = countOf(failedByType, type)

            if (restored + failed != expected) {
                errors.add(
                    Date() to "Partial restore of $type: " +
                        "$restored restored, $failed failed, $expected expected",
                )
            }
            "$type=$restored/$expected(failed=$failed)"
        }
        // Counts only. Titles, urls and quest data never reach the diagnostic log.
        BackupDiagnosticLog.log(context, "restore_summary", summary)
    }

    private suspend fun restoreFromFile(uri: Uri, options: RestoreOptions) {
        val decoded = BackupDecoder(context).decodeDetailed(uri, options.importPolicy())
        val backup = decoded.backup

        BackupDiagnosticLog.log(
            context,
            "restore_source",
            "origin=${decoded.origin} legacySisterFallback=${options.legacySisterFallback} " +
                "ambiguous=${decoded.ambiguousSourceIds} manga=${backup.backupManga.size} " +
                "anime=${backup.backupAnime.size} novel=${backup.backupNovel.size}",
        )

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

        // Stores are written to both the store fields and the legacy repo fields so an older build
        // can still read them; replaying both would insert every store twice.
        val animeExtensionStoreBackups = if (options.restoreAnime) backup.backupAnimeExtensionStore else emptyList()
        val mangaExtensionStoreBackups = if (options.restoreManga) backup.backupMangaExtensionStore else emptyList()
        val novelExtensionStoreBackups = if (options.restoreNovel) backup.backupNovelExtensionStore else emptyList()
        val animeExtensionRepoBackups = if (options.restoreAnime) {
            legacyExtensionRepoBackupsToRestore(backup.backupAnimeExtensionRepo, animeExtensionStoreBackups)
        } else {
            emptyList()
        }
        val mangaExtensionRepoBackups = if (options.restoreManga) {
            legacyExtensionRepoBackupsToRestore(backup.backupMangaExtensionRepo, mangaExtensionStoreBackups)
        } else {
            emptyList()
        }
        val novelExtensionRepoBackups = if (options.restoreNovel) {
            legacyExtensionRepoBackupsToRestore(backup.backupNovelExtensionRepo, novelExtensionStoreBackups)
        } else {
            emptyList()
        }

        if (options.libraryEntries) {
            if (options.restoreManga) {
                restoreAmount += backup.backupManga.size
                expect(TYPE_MANGA, backup.backupManga.size)
            }
            if (options.restoreAnime) {
                restoreAmount += backup.backupAnime.size
                expect(TYPE_ANIME, backup.backupAnime.size)
            }
            if (options.restoreNovel) {
                restoreAmount += backup.backupNovel.size
                expect(TYPE_NOVEL, backup.backupNovel.size)
            }
            if (options.restoreManga) restoreAmount += backup.backupMangaSeries.size
            if (options.restoreNovel) restoreAmount += backup.backupNovelSeries.size
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
            restoreAmount += animeExtensionRepoBackups.size + animeExtensionStoreBackups.size
            restoreAmount += mangaExtensionRepoBackups.size + mangaExtensionStoreBackups.size
            restoreAmount += novelExtensionRepoBackups.size + novelExtensionStoreBackups.size
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
                val libraryRestoreJobs = mutableListOf<kotlinx.coroutines.Job>()
                if (shouldRestoreAnime) {
                    libraryRestoreJobs += restoreAnime(
                        backup.backupAnime,
                        if (shouldRestoreAnimeCategories) backup.backupAnimeCategories else emptyList(),
                    )
                }
                if (shouldRestoreManga) {
                    libraryRestoreJobs += restoreManga(
                        backup.backupManga,
                        if (shouldRestoreMangaCategories) backup.backupCategories else emptyList(),
                    )
                }
                if (shouldRestoreNovel) {
                    libraryRestoreJobs += restoreNovel(
                        backup.backupNovel,
                        if (shouldRestoreNovelCategories) backup.backupNovelCategories else emptyList(),
                    )
                }
                libraryRestoreJobs.joinAll()
                if (shouldRestoreManga) {
                    restoreMangaSeries(backup.backupMangaSeries)
                }
                if (shouldRestoreNovel) {
                    restoreNovelSeries(backup.backupNovelSeries)
                }
            }
            if (options.extensionRepoSettings) {
                restoreExtensionStores(
                    animeExtensionStoreBackups,
                    mangaExtensionStoreBackups,
                    novelExtensionStoreBackups,
                )
                restoreExtensionRepos(
                    animeExtensionRepoBackups,
                    mangaExtensionRepoBackups,
                    novelExtensionRepoBackups,
                )
            }
            if (options.customButtons) {
                restoreCustomButtons(backup.backupCustomButton)
            }
            if (options.extensions) {
                restoreExtensions(backup.backupExtensions)
            }

            // Restore feeds (always if present in backup)
            if (backup.backupFeeds.isNotEmpty()) {
                feedRestorer.restoreFeeds(backup.backupFeeds)
            }

            // Restore reels favorites when the user kept the option enabled
            if (options.reelsFavorites && backup.backupReelsFavorites.isNotEmpty()) {
                reelsFavoritesRestorer.restoreReelsFavorites(backup.backupReelsFavorites)
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
            notifier.showRestoreProgress(
                context.stringResource(MR.strings.categories),
                advanceProgress(),
                restoreAmount,
                isSync,
            )
        }
        if (restoreMangaCategories) {
            if (backupMangaCategories.isNotEmpty()) {
                mangaCategoriesRestorer(backupMangaCategories)
            }
            notifier.showRestoreProgress(
                context.stringResource(MR.strings.categories),
                advanceProgress(),
                restoreAmount,
                isSync,
            )
        }
        if (restoreNovelCategories) {
            if (backupNovelCategories.isNotEmpty()) {
                novelCategoriesRestorer(backupNovelCategories)
            }
            notifier.showRestoreProgress(
                context.stringResource(MR.strings.categories),
                advanceProgress(),
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
                    markRestored(TYPE_ANIME)
                } catch (e: Exception) {
                    markFailed(TYPE_ANIME)
                    val sourceName = animeSourceMapping[it.source] ?: it.source.toString()
                    errors.add(Date() to "${it.title} [$sourceName]: ${e.message}")
                }

                notifier.showRestoreProgress(it.title, advanceProgress(), restoreAmount, isSync)
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
                    markRestored(TYPE_MANGA)
                } catch (e: Exception) {
                    markFailed(TYPE_MANGA)
                    val sourceName = mangaSourceMapping[it.source] ?: it.source.toString()
                    errors.add(Date() to "${it.title} [$sourceName]: ${e.message}")
                }

                notifier.showRestoreProgress(it.title, advanceProgress(), restoreAmount, isSync)
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
                    markRestored(TYPE_NOVEL)
                } catch (e: Exception) {
                    markFailed(TYPE_NOVEL)
                    val sourceName = novelSourceMapping[it.source] ?: it.source.toString()
                    errors.add(Date() to "${it.title} [$sourceName]: ${e.message}")
                }

                notifier.showRestoreProgress(it.title, advanceProgress(), restoreAmount, isSync)
            }
    }

    private suspend fun restoreMangaSeries(
        backupSeries: List<eu.kanade.tachiyomi.data.backup.models.BackupMangaSeries>,
    ) {
        backupSeries.forEach {
            try {
                mangaSeriesRestorer.restore(listOf(it))
            } catch (e: Exception) {
                errors.add(
                    Date() to "Manga series '${it.title}': ${e.message}",
                )
            }
            notifier.showRestoreProgress(it.title, advanceProgress(), restoreAmount, isSync)
        }
    }

    private suspend fun restoreNovelSeries(
        backupSeries: List<eu.kanade.tachiyomi.data.backup.models.BackupNovelSeries>,
    ) {
        backupSeries.forEach {
            try {
                novelSeriesRestorer.restore(listOf(it))
            } catch (e: Exception) {
                errors.add(
                    Date() to "Novel series '${it.title}': ${e.message}",
                )
            }
            notifier.showRestoreProgress(it.title, advanceProgress(), restoreAmount, isSync)
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

        notifier.showRestoreProgress(
            context.stringResource(MR.strings.app_settings),
            advanceProgress(),
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreSourcePreferences(preferences: List<BackupSourcePreferences>) = launch {
        ensureActive()
        preferenceRestorer.restoreSource(preferences)

        notifier.showRestoreProgress(
            context.stringResource(MR.strings.source_settings),
            advanceProgress(),
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
                    errors.add(
                        Date() to "Error Adding Anime Repo: ${it.name} : ${e.message}",
                    )
                }

                notifier.showRestoreProgress(
                    context.stringResource(MR.strings.extensionRepo_settings),
                    advanceProgress(),
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
                    errors.add(
                        Date() to "Error Adding Manga Repo: ${it.name} : ${e.message}",
                    )
                }

                notifier.showRestoreProgress(
                    context.stringResource(MR.strings.extensionRepo_settings),
                    advanceProgress(),
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
                    errors.add(
                        Date() to "Error Adding Novel Repo: ${it.name} : ${e.message}",
                    )
                }

                notifier.showRestoreProgress(
                    context.stringResource(MR.strings.extensionRepo_settings),
                    advanceProgress(),
                    restoreAmount,
                    isSync,
                )
            }
    }

    private fun CoroutineScope.restoreExtensionStores(
        backupAnimeExtensionStore: List<BackupExtensionStore>,
        backupMangaExtensionStore: List<BackupExtensionStore>,
        backupNovelExtensionStore: List<BackupExtensionStore>,
    ) = launch {
        backupAnimeExtensionStore
            .forEach {
                ensureActive()

                try {
                    animeExtensionStoreRestorer(it)
                } catch (e: Exception) {
                    errors.add(
                        Date() to "Error Adding Anime Store: ${it.name} : ${e.message}",
                    )
                }

                notifier.showRestoreProgress(
                    context.stringResource(MR.strings.extensionRepo_settings),
                    advanceProgress(),
                    restoreAmount,
                    isSync,
                )
            }

        backupMangaExtensionStore
            .forEach {
                ensureActive()

                try {
                    mangaExtensionStoreRestorer(it)
                } catch (e: Exception) {
                    errors.add(
                        Date() to "Error Adding Manga Store: ${it.name} : ${e.message}",
                    )
                }

                notifier.showRestoreProgress(
                    context.stringResource(MR.strings.extensionRepo_settings),
                    advanceProgress(),
                    restoreAmount,
                    isSync,
                )
            }

        backupNovelExtensionStore
            .forEach {
                ensureActive()

                try {
                    novelExtensionStoreRestorer(it)
                } catch (e: Exception) {
                    errors.add(
                        Date() to "Error Adding Novel Store: ${it.name} : ${e.message}",
                    )
                }

                notifier.showRestoreProgress(
                    context.stringResource(MR.strings.extensionRepo_settings),
                    advanceProgress(),
                    restoreAmount,
                    isSync,
                )
            }
    }

    private fun CoroutineScope.restoreCustomButtons(customButtons: List<BackupCustomButtons>) = launch {
        ensureActive()
        customButtonRestorer(customButtons)

        notifier.showRestoreProgress(
            context.stringResource(AYMR.strings.custom_button_settings),
            advanceProgress(),
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreExtensions(extensions: List<BackupExtension>) = launch {
        ensureActive()
        extensionsRestorer.restoreExtensions(extensions)

        notifier.showRestoreProgress(
            context.stringResource(MR.strings.source_settings),
            advanceProgress(),
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

/**
 * Which legacy repo entries of a backup still have to be replayed.
 *
 * A backup carries the same stores twice: under the store fields, and under the legacy repo fields
 * so an older build can still read them. Replaying both inserts every store a second time under a
 * fabricated `<base>/repo.json` index url, and makes both restorers report a signing-key clash.
 */
internal fun legacyExtensionRepoBackupsToRestore(
    repoBackups: List<BackupExtensionRepos>,
    storeBackups: List<BackupExtensionStore>,
): List<BackupExtensionRepos> {
    return if (storeBackups.isEmpty()) repoBackups else emptyList()
}
