package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupEpisode
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.sync.SyncData
import kotlinx.serialization.json.Json
import logcat.LogPriority
import logcat.logcat

/**
 * Abstract base class for sync services that provides common merge logic
 * for synchronizing data between local and remote sources.
 *
 * @param context The application context
 * @param json The JSON serializer for preferences
 * @param syncPreferences The sync preferences containing device ID and settings
 */
abstract class SyncService(
    val context: Context,
    val json: Json,
    val syncPreferences: SyncPreferences,
) {
    companion object {
        private const val TAG = "SyncService"
    }

    /**
     * Performs the sync operation between local and remote data.
     *
     * @param syncData The local sync data to be synchronized
     * @return The merged backup data after synchronization, or null if sync failed
     */
    abstract suspend fun doSync(syncData: SyncData): Backup?

    /**
     * Merges local and remote sync data, selecting the most recent versions
     * of each item based on version numbers.
     *
     * @param localSyncData The local sync data
     * @param remoteSyncData The remote sync data from sync service
     * @return The merged SyncData object
     */
    protected fun mergeSyncData(localSyncData: SyncData, remoteSyncData: SyncData): SyncData {
        logcat(LogPriority.DEBUG, TAG) { "Starting sync data merge" }

        val mergedMangaCategoriesList = mergeCategoriesLists(
            localSyncData.backup?.backupCategories,
            remoteSyncData.backup?.backupCategories,
        )
        val mergedAnimeCategoriesList = mergeCategoriesLists(
            localSyncData.backup?.backupAnimeCategories,
            remoteSyncData.backup?.backupAnimeCategories,
        )
        val mergedNovelCategoriesList = mergeCategoriesLists(
            localSyncData.backup?.backupNovelCategories,
            remoteSyncData.backup?.backupNovelCategories,
        )

        val mergedMangaList = mergeMangaLists(
            localSyncData.backup?.backupManga,
            remoteSyncData.backup?.backupManga,
            localSyncData.backup?.backupCategories ?: emptyList(),
            remoteSyncData.backup?.backupCategories ?: emptyList(),
            mergedMangaCategoriesList,
        )

        val mergedAnimeList = mergeAnimeLists(
            localSyncData.backup?.backupAnime,
            remoteSyncData.backup?.backupAnime,
            localSyncData.backup?.backupAnimeCategories ?: emptyList(),
            remoteSyncData.backup?.backupAnimeCategories ?: emptyList(),
            mergedAnimeCategoriesList,
        )

        val mergedNovelList = mergeNovelLists(
            localSyncData.backup?.backupNovel,
            remoteSyncData.backup?.backupNovel,
            localSyncData.backup?.backupNovelCategories ?: emptyList(),
            remoteSyncData.backup?.backupNovelCategories ?: emptyList(),
            mergedNovelCategoriesList,
        )

        val mergedSourcesList = mergeSourcesLists(
            localSyncData.backup?.backupSources,
            remoteSyncData.backup?.backupSources,
        )

        val mergedAnimeSourcesList = mergeAnimeSourcesLists(
            localSyncData.backup?.backupAnimeSources,
            remoteSyncData.backup?.backupAnimeSources,
        )

        val mergedNovelSourcesList = mergeSourcesLists(
            localSyncData.backup?.backupNovelSources,
            remoteSyncData.backup?.backupNovelSources,
        )

        val mergedPreferencesList = mergePreferencesLists(
            localSyncData.backup?.backupPreferences,
            remoteSyncData.backup?.backupPreferences,
        )

        val mergedSourcePreferencesList = mergeSourcePreferencesLists(
            localSyncData.backup?.backupSourcePreferences,
            remoteSyncData.backup?.backupSourcePreferences,
        )

        val mergedBackup = Backup(
            backupManga = mergedMangaList,
            backupCategories = mergedMangaCategoriesList,
            backupSources = mergedSourcesList,
            backupPreferences = mergedPreferencesList,
            backupSourcePreferences = mergedSourcePreferencesList,
            backupAnime = mergedAnimeList,
            backupAnimeCategories = mergedAnimeCategoriesList,
            backupAnimeSources = mergedAnimeSourcesList,
            backupNovel = mergedNovelList,
            backupNovelCategories = mergedNovelCategoriesList,
            backupNovelSources = mergedNovelSourcesList,
            isLegacy = false,
        )

        return SyncData(
            deviceId = syncPreferences.uniqueDeviceID(),
            backup = mergedBackup,
        )
    }

    /**
     * Merges two lists of BackupManga objects, selecting the most recent version
     * based on the version field and merging chapters.
     */
    protected fun mergeMangaLists(
        localMangaList: List<BackupManga>?,
        remoteMangaList: List<BackupManga>?,
        localCategories: List<BackupCategory>,
        remoteCategories: List<BackupCategory>,
        mergedCategories: List<BackupCategory>,
    ): List<BackupManga> {
        val localMangaListSafe = localMangaList.orEmpty()
        val remoteMangaListSafe = remoteMangaList.orEmpty()

        fun mangaCompositeKey(manga: BackupManga): String {
            return "${manga.source}|${manga.url}|${manga.title.lowercase().trim()}|${manga.author?.lowercase()?.trim()}"
        }

        val localMangaMap = localMangaListSafe.associateBy { mangaCompositeKey(it) }
        val remoteMangaMap = remoteMangaListSafe.associateBy { mangaCompositeKey(it) }

        val localCategoriesMapByOrder = localCategories.associateBy { it.order }
        val remoteCategoriesMapByOrder = remoteCategories.associateBy { it.order }
        val mergedCategoriesMapByName = mergedCategories.associateBy { it.name }

        fun updateCategories(theManga: BackupManga, theMap: Map<Long, BackupCategory>): BackupManga {
            return theManga.copy(
                categories = theManga.categories.mapNotNull {
                    theMap[it]?.let { category ->
                        mergedCategoriesMapByName[category.name]?.order
                    }
                },
            )
        }

        val mergedList = (localMangaMap.keys + remoteMangaMap.keys).distinct().mapNotNull { compositeKey ->
            val local = localMangaMap[compositeKey]
            val remote = remoteMangaMap[compositeKey]

            when {
                local != null && remote == null -> updateCategories(local, localCategoriesMapByOrder)
                local == null && remote != null -> updateCategories(remote, remoteCategoriesMapByOrder)
                local != null && remote != null -> {
                    if (local.version >= remote.version) {
                        updateCategories(
                            local.copy(chapters = mergeChapters(local.chapters, remote.chapters)),
                            localCategoriesMapByOrder,
                        )
                    } else {
                        updateCategories(
                            remote.copy(chapters = mergeChapters(local.chapters, remote.chapters)),
                            remoteCategoriesMapByOrder,
                        )
                    }
                }
                else -> null
            }
        }

        logcat(LogPriority.DEBUG, TAG) {
            "Manga merge completed. Total: ${mergedList.size}, Local: ${localMangaListSafe.size}, Remote: ${remoteMangaListSafe.size}"
        }

        return mergedList
    }

    /**
     * Merges two lists of BackupAnime objects.
     */
    protected fun mergeAnimeLists(
        localAnimeList: List<BackupAnime>?,
        remoteAnimeList: List<BackupAnime>?,
        localCategories: List<BackupCategory>,
        remoteCategories: List<BackupCategory>,
        mergedCategories: List<BackupCategory>,
    ): List<BackupAnime> {
        val localAnimeListSafe = localAnimeList.orEmpty()
        val remoteAnimeListSafe = remoteAnimeList.orEmpty()

        fun animeCompositeKey(anime: BackupAnime): String {
            return "${anime.source}|${anime.url}|${anime.title.lowercase().trim()}|${anime.author?.lowercase()?.trim()}"
        }

        val localAnimeMap = localAnimeListSafe.associateBy { animeCompositeKey(it) }
        val remoteAnimeMap = remoteAnimeListSafe.associateBy { animeCompositeKey(it) }

        val localCategoriesMapByOrder = localCategories.associateBy { it.order }
        val remoteCategoriesMapByOrder = remoteCategories.associateBy { it.order }
        val mergedCategoriesMapByName = mergedCategories.associateBy { it.name }

        fun updateCategories(theAnime: BackupAnime, theMap: Map<Long, BackupCategory>): BackupAnime {
            return theAnime.copy(
                categories = theAnime.categories.mapNotNull {
                    theMap[it]?.let { category ->
                        mergedCategoriesMapByName[category.name]?.order
                    }
                },
            )
        }

        val mergedList = (localAnimeMap.keys + remoteAnimeMap.keys).distinct().mapNotNull { compositeKey ->
            val local = localAnimeMap[compositeKey]
            val remote = remoteAnimeMap[compositeKey]

            when {
                local != null && remote == null -> updateCategories(local, localCategoriesMapByOrder)
                local == null && remote != null -> updateCategories(remote, remoteCategoriesMapByOrder)
                local != null && remote != null -> {
                    if (local.version >= remote.version) {
                        updateCategories(
                            local.copy(episodes = mergeEpisodes(local.episodes, remote.episodes)),
                            localCategoriesMapByOrder,
                        )
                    } else {
                        updateCategories(
                            remote.copy(episodes = mergeEpisodes(local.episodes, remote.episodes)),
                            remoteCategoriesMapByOrder,
                        )
                    }
                }
                else -> null
            }
        }

        logcat(LogPriority.DEBUG, TAG) {
            "Anime merge completed. Total: ${mergedList.size}, Local: ${localAnimeListSafe.size}, Remote: ${remoteAnimeListSafe.size}"
        }

        return mergedList
    }

    /**
     * Merges two lists of BackupNovel objects.
     */
    protected fun mergeNovelLists(
        localNovelList: List<BackupNovel>?,
        remoteNovelList: List<BackupNovel>?,
        localCategories: List<BackupCategory>,
        remoteCategories: List<BackupCategory>,
        mergedCategories: List<BackupCategory>,
    ): List<BackupNovel> {
        val localNovelListSafe = localNovelList.orEmpty()
        val remoteNovelListSafe = remoteNovelList.orEmpty()

        fun novelCompositeKey(novel: BackupNovel): String {
            return "${novel.source}|${novel.url}|${novel.title.lowercase().trim()}|${novel.author?.lowercase()?.trim()}"
        }

        val localNovelMap = localNovelListSafe.associateBy { novelCompositeKey(it) }
        val remoteNovelMap = remoteNovelListSafe.associateBy { novelCompositeKey(it) }

        val localCategoriesMapByOrder = localCategories.associateBy { it.order }
        val remoteCategoriesMapByOrder = remoteCategories.associateBy { it.order }
        val mergedCategoriesMapByName = mergedCategories.associateBy { it.name }

        fun updateCategories(theNovel: BackupNovel, theMap: Map<Long, BackupCategory>): BackupNovel {
            return theNovel.copy(
                categories = theNovel.categories.mapNotNull {
                    theMap[it]?.let { category ->
                        mergedCategoriesMapByName[category.name]?.order
                    }
                },
            )
        }

        val mergedList = (localNovelMap.keys + remoteNovelMap.keys).distinct().mapNotNull { compositeKey ->
            val local = localNovelMap[compositeKey]
            val remote = remoteNovelMap[compositeKey]

            when {
                local != null && remote == null -> updateCategories(local, localCategoriesMapByOrder)
                local == null && remote != null -> updateCategories(remote, remoteCategoriesMapByOrder)
                local != null && remote != null -> {
                    if (local.version >= remote.version) {
                        updateCategories(local, localCategoriesMapByOrder)
                    } else {
                        updateCategories(remote, remoteCategoriesMapByOrder)
                    }
                }
                else -> null
            }
        }

        logcat(LogPriority.DEBUG, TAG) {
            "Novel merge completed. Total: ${mergedList.size}, Local: ${localNovelListSafe.size}, Remote: ${remoteNovelListSafe.size}"
        }

        return mergedList
    }

    /**
     * Merges chapters from local and remote sources.
     */
    protected fun mergeChapters(
        localChapters: List<BackupChapter>,
        remoteChapters: List<BackupChapter>,
    ): List<BackupChapter> {
        fun chapterCompositeKey(chapter: BackupChapter): String {
            return "${chapter.url}|${chapter.name}|${chapter.chapterNumber}"
        }

        val localChapterMap = localChapters.associateBy { chapterCompositeKey(it) }
        val remoteChapterMap = remoteChapters.associateBy { chapterCompositeKey(it) }

        return (localChapterMap.keys + remoteChapterMap.keys).distinct().mapNotNull { compositeKey ->
            val localChapter = localChapterMap[compositeKey]
            val remoteChapter = remoteChapterMap[compositeKey]

            when {
                localChapter != null && remoteChapter == null -> localChapter
                localChapter == null && remoteChapter != null -> remoteChapter
                localChapter != null && remoteChapter != null -> {
                    if (localChapter.version >= remoteChapter.version) {
                        localChapter
                    } else {
                        remoteChapter
                    }
                }
                else -> null
            }
        }
    }

    /**
     * Merges episodes from local and remote sources.
     */
    protected fun mergeEpisodes(
        localEpisodes: List<BackupEpisode>,
        remoteEpisodes: List<BackupEpisode>,
    ): List<BackupEpisode> {
        fun episodeCompositeKey(episode: BackupEpisode): String {
            return "${episode.url}|${episode.name}|${episode.episodeNumber}"
        }

        val localEpisodeMap = localEpisodes.associateBy { episodeCompositeKey(it) }
        val remoteEpisodeMap = remoteEpisodes.associateBy { episodeCompositeKey(it) }

        return (localEpisodeMap.keys + remoteEpisodeMap.keys).distinct().mapNotNull { compositeKey ->
            val localEpisode = localEpisodeMap[compositeKey]
            val remoteEpisode = remoteEpisodeMap[compositeKey]

            when {
                localEpisode != null && remoteEpisode == null -> localEpisode
                localEpisode == null && remoteEpisode != null -> remoteEpisode
                localEpisode != null && remoteEpisode != null -> {
                    if (localEpisode.version >= remoteEpisode.version) {
                        localEpisode
                    } else {
                        remoteEpisode
                    }
                }
                else -> null
            }
        }
    }

    /**
     * Merges categories lists, prioritizing the category with the higher order value.
     */
    protected fun mergeCategoriesLists(
        localCategoriesList: List<BackupCategory>?,
        remoteCategoriesList: List<BackupCategory>?,
    ): List<BackupCategory> {
        if (localCategoriesList == null) return remoteCategoriesList ?: emptyList()
        if (remoteCategoriesList == null) return localCategoriesList

        val localCategoriesMap = localCategoriesList.associateBy { it.name }
        val remoteCategoriesMap = remoteCategoriesList.associateBy { it.name }

        val mergedCategoriesMap = mutableMapOf<String, BackupCategory>()

        localCategoriesMap.forEach { (name, localCategory) ->
            val remoteCategory = remoteCategoriesMap[name]
            if (remoteCategory != null) {
                mergedCategoriesMap[name] = if (localCategory.order > remoteCategory.order) {
                    localCategory
                } else {
                    remoteCategory
                }
            } else {
                mergedCategoriesMap[name] = localCategory
            }
        }

        remoteCategoriesMap.forEach { (name, remoteCategory) ->
            if (!mergedCategoriesMap.containsKey(name)) {
                mergedCategoriesMap[name] = remoteCategory
            }
        }

        return mergedCategoriesMap.values.toList()
    }

    /**
     * Merges sources lists.
     */
    protected fun mergeSourcesLists(
        localSources: List<eu.kanade.tachiyomi.data.backup.models.BackupSource>?,
        remoteSources: List<eu.kanade.tachiyomi.data.backup.models.BackupSource>?,
    ): List<eu.kanade.tachiyomi.data.backup.models.BackupSource> {
        val localSourceMap = localSources?.associateBy { it.sourceId } ?: emptyMap()
        val remoteSourceMap = remoteSources?.associateBy { it.sourceId } ?: emptyMap()

        return (localSourceMap.keys + remoteSourceMap.keys).distinct().mapNotNull { sourceId ->
            localSourceMap[sourceId] ?: remoteSourceMap[sourceId]
        }
    }

    /**
     * Merges anime sources lists.
     */
    protected fun mergeAnimeSourcesLists(
        localSources: List<eu.kanade.tachiyomi.data.backup.models.BackupAnimeSource>?,
        remoteSources: List<eu.kanade.tachiyomi.data.backup.models.BackupAnimeSource>?,
    ): List<eu.kanade.tachiyomi.data.backup.models.BackupAnimeSource> {
        val localSourceMap = localSources?.associateBy { it.sourceId } ?: emptyMap()
        val remoteSourceMap = remoteSources?.associateBy { it.sourceId } ?: emptyMap()

        return (localSourceMap.keys + remoteSourceMap.keys).distinct().mapNotNull { sourceId ->
            localSourceMap[sourceId] ?: remoteSourceMap[sourceId]
        }
    }

    /**
     * Merges preferences lists.
     */
    protected fun mergePreferencesLists(
        localPreferences: List<BackupPreference>?,
        remotePreferences: List<BackupPreference>?,
    ): List<BackupPreference> {
        val localPreferencesMap = localPreferences?.associateBy { it.key } ?: emptyMap()
        val remotePreferencesMap = remotePreferences?.associateBy { it.key } ?: emptyMap()

        return (localPreferencesMap.keys + remotePreferencesMap.keys).distinct().mapNotNull { key ->
            localPreferencesMap[key] ?: remotePreferencesMap[key]
        }
    }

    /**
     * Merges source preferences lists.
     */
    protected fun mergeSourcePreferencesLists(
        localPreferences: List<BackupSourcePreferences>?,
        remotePreferences: List<BackupSourcePreferences>?,
    ): List<BackupSourcePreferences> {
        val localPreferencesMap = localPreferences?.associateBy { it.sourceKey } ?: emptyMap()
        val remotePreferencesMap = remotePreferences?.associateBy { it.sourceKey } ?: emptyMap()

        return (localPreferencesMap.keys + remotePreferencesMap.keys).distinct().mapNotNull { sourceKey ->
            val localSourcePreference = localPreferencesMap[sourceKey]
            val remoteSourcePreference = remotePreferencesMap[sourceKey]

            when {
                localSourcePreference != null && remoteSourcePreference == null -> localSourcePreference
                localSourcePreference == null && remoteSourcePreference != null -> remoteSourcePreference
                else -> null
            }
        }
    }
}

/**
 * Internal implementation of SyncService for testing purposes.
 */
class SyncServiceImpl(
    context: Context,
    json: Json,
    syncPreferences: SyncPreferences,
) : SyncService(context, json, syncPreferences) {
    override suspend fun doSync(syncData: SyncData): Backup? = null
}
