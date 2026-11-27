package eu.kanade.tachiyomi.data.backup.restore.restorers

import android.content.Context
import android.util.Log
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.FloatPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.novel.NovelLibraryUpdateJob
import eu.kanade.tachiyomi.source.sourcePreferences
import tachiyomi.core.common.preference.AndroidPreferenceStore
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.plusAssign
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class PreferenceRestorer(
    private val context: Context,
    private val getMangaCategories: GetMangaCategories = Injekt.get(),
    private val getAnimeCategories: GetAnimeCategories = Injekt.get(),
    private val getNovelCategories: GetNovelCategories = Injekt.get(),
    private val preferenceStore: PreferenceStore = Injekt.get(),
) {
    suspend fun restoreApp(
        preferences: List<BackupPreference>,
        backupCategories: List<BackupCategory>? = null,
        backupAnimeCategories: List<BackupCategory>? = null,
        backupNovelCategories: List<BackupCategory>? = null,
    ) {
        val sourceKeys = restoreDiscoverySourcePreferences(preferences)
        restorePreferences(
            preferences.filterNot {
                it.key in sourceKeys ||
                    DiscoveryPreferences.isRuntimeStateKey(it.key)
            },
            preferenceStore,
            backupCategories,
            backupAnimeCategories,
            backupNovelCategories,
        )

        AnimeLibraryUpdateJob.setupTask(context)
        MangaLibraryUpdateJob.setupTask(context)
        NovelLibraryUpdateJob.setupTask(context)
        BackupCreateJob.setupTask(context)
    }

    /** Source selection is one mutation per media, not three unrelated generic preference writes. */
    private fun restoreDiscoverySourcePreferences(toRestore: List<BackupPreference>): Set<String> {
        val discoveryPreferences = DiscoveryPreferences(preferenceStore)
        val sourceKeys = LinkedHashSet<String>()
        DiscoveryMediaType.entries.forEach { mediaType ->
            val targets = listOf(
                discoveryPreferences.discoverySourceMode(mediaType),
                discoveryPreferences.discoverySourceExcluded(mediaType),
                discoveryPreferences.discoverySourceKnown(mediaType),
            ).associateBy { it.key() }
            sourceKeys += targets.keys
            val supplied = toRestore.filter { it.key in targets }
            if (supplied.isEmpty()) return@forEach
            discoveryPreferences.withSourcePreferencesLock(mediaType) {
                val existing = preferenceStore.getAll()
                supplied.forEach { (key, value) ->
                    if (value is StringPreferenceValue && existing[key] is String?) {
                        targets.getValue(key).set(value.value)
                    }
                }
            }
        }
        return sourceKeys
    }

    suspend fun restoreSource(preferences: List<BackupSourcePreferences>) {
        preferences.forEach {
            val sourcePrefs = AndroidPreferenceStore(context, sourcePreferences(it.sourceKey))
            restorePreferences(it.prefs, sourcePrefs)
        }
    }

    private suspend fun restorePreferences(
        toRestore: List<BackupPreference>,
        preferenceStore: PreferenceStore,
        backupMangaCategories: List<BackupCategory>? = null,
        backupAnimeCategories: List<BackupCategory>? = null,
        backupNovelCategories: List<BackupCategory>? = null,
    ) {
        val allMangaCategories = if (backupMangaCategories != null) getMangaCategories.await() else emptyList()
        val allAnimeCategories = if (backupAnimeCategories != null) getAnimeCategories.await() else emptyList()
        val allNovelCategories = if (backupNovelCategories != null) {
            getNovelCategories.await().map {
                Category(
                    id = it.id,
                    name = it.name,
                    order = it.order,
                    flags = it.flags,
                    hidden = it.hidden,
                    hiddenFromHomeHub = false,
                )
            }
        } else {
            emptyList()
        }

        val mangaCategoriesByName = allMangaCategories.associateBy { it.name }
        val animeCategoriesByName = allAnimeCategories.associateBy { it.name }
        val novelCategoriesByName = allNovelCategories.associateBy { it.name }
        val backupMangaCategoriesById = backupMangaCategories?.associateBy { it.id.toString() }.orEmpty()
        val backupAnimeCategoriesById = backupAnimeCategories?.associateBy { it.id.toString() }.orEmpty()
        val backupNovelCategoriesById = backupNovelCategories?.associateBy { it.id.toString() }.orEmpty()

        val prefs = preferenceStore.getAll()
        toRestore.forEach { (key, value) ->
            try {
                when (value) {
                    is IntPreferenceValue -> {
                        if (prefs[key] is Int?) {
                            val newValue = if (key == LibraryPreferences.DEFAULT_MANGA_CATEGORY_PREF_KEY) {
                                backupMangaCategoriesById[value.value.toString()]
                                    ?.let { mangaCategoriesByName[it.name]?.id?.toInt() }
                            } else if (key == LibraryPreferences.DEFAULT_ANIME_CATEGORY_PREF_KEY) {
                                backupAnimeCategoriesById[value.value.toString()]
                                    ?.let { animeCategoriesByName[it.name]?.id?.toInt() }
                            } else if (key == LibraryPreferences.DEFAULT_NOVEL_CATEGORY_PREF_KEY) {
                                backupNovelCategoriesById[value.value.toString()]
                                    ?.let { novelCategoriesByName[it.name]?.id?.toInt() }
                            } else {
                                value.value
                            }

                            newValue?.let { preferenceStore.getInt(key).set(it) }
                        }
                    }
                    is LongPreferenceValue -> {
                        if (prefs[key] is Long?) {
                            preferenceStore.getLong(key).set(value.value)
                        }
                    }
                    is FloatPreferenceValue -> {
                        if (prefs[key] is Float?) {
                            preferenceStore.getFloat(key).set(value.value)
                        }
                    }
                    is StringPreferenceValue -> {
                        if (prefs[key] is String?) {
                            preferenceStore.getString(key).set(value.value)
                        }
                    }
                    is BooleanPreferenceValue -> {
                        if (prefs[key] is Boolean?) {
                            preferenceStore.getBoolean(key).set(value.value)
                        }
                    }
                    is StringSetPreferenceValue -> {
                        if (prefs[key] is Set<*>?) {
                            val restored = restoreCategoriesPreference(
                                key,
                                value.value,
                                preferenceStore,
                                backupMangaCategoriesById,
                                backupAnimeCategoriesById,
                                backupNovelCategoriesById,
                                mangaCategoriesByName,
                                animeCategoriesByName,
                                novelCategoriesByName,
                            )
                            if (!restored) preferenceStore.getStringSet(key).set(value.value)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PreferenceRestorer", "Failed to restore preference <$key>", e)
            }
        }
    }

    private fun restoreCategoriesPreference(
        key: String,
        value: Set<String>,
        preferenceStore: PreferenceStore,
        backupMangaCategoriesById: Map<String, BackupCategory>,
        backupAnimeCategoriesById: Map<String, BackupCategory>,
        backupNovelCategoriesById: Map<String, BackupCategory>,
        mangaCategoriesByName: Map<String, Category>,
        animeCategoriesByName: Map<String, Category>,
        novelCategoriesByName: Map<String, Category>,
    ): Boolean {
        val categoryPreferences = LibraryPreferences.categoryPreferenceKeys + DownloadPreferences.categoryPreferenceKeys
        if (key !in categoryPreferences) return false

        val (backupById, dbByName) = when (key) {
            in MANGA_CATEGORY_SET_KEYS -> backupMangaCategoriesById to mangaCategoriesByName
            in ANIME_CATEGORY_SET_KEYS -> backupAnimeCategoriesById to animeCategoriesByName
            in NOVEL_CATEGORY_SET_KEYS -> backupNovelCategoriesById to novelCategoriesByName
            else -> return true
        }

        val ids = value.mapNotNull {
            backupById[it]?.name?.let { name ->
                dbByName[name]?.id?.toString()
            }
        }

        if (ids.isNotEmpty()) {
            preferenceStore.getStringSet(key) += ids
        }
        return true
    }

    companion object {
        internal val MANGA_CATEGORY_SET_KEYS = setOf(
            "library_update_categories",
            "library_update_categories_exclude",
            "remove_exclude_categories",
            "download_new_categories",
            "download_new_categories_exclude",
        )
        internal val ANIME_CATEGORY_SET_KEYS = setOf(
            "animelib_update_categories",
            "animelib_update_categories_exclude",
            "remove_exclude_anime_categories",
            "download_new_anime_categories",
            "download_new_anime_categories_exclude",
        )
        internal val NOVEL_CATEGORY_SET_KEYS = setOf(
            "novellib_update_categories",
            "novellib_update_categories_exclude",
            "remove_exclude_novel_categories",
            "download_new_novel_categories",
            "download_new_novel_categories_exclude",
        )
    }
}
