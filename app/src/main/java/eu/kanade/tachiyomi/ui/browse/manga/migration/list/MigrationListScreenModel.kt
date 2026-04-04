package eu.kanade.tachiyomi.ui.browse.manga.migration.list

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.manga.interactor.MigrateMangaUseCase
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.browse.manga.migration.MangaMigrationFlags
import eu.kanade.tachiyomi.ui.browse.manga.migration.list.search.SmartSourceSearchEngine
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrationListScreenModel(
    mangaIds: Collection<Long>,
    private val sourceIds: Collection<Long>,
    extraSearchQuery: String?,
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: MangaSourceManager = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val migrateManga: MigrateMangaUseCase = MigrateMangaUseCase(),
    private val preferenceStore: PreferenceStore = Injekt.get(),
) : StateScreenModel<MigrationListScreenModel.State>(State()) {

    private val smartSearchEngine = SmartSourceSearchEngine(extraSearchQuery)

    val items
        get() = state.value.items

    private val migrateFlags: Preference<Int> by lazy {
        preferenceStore.getInt("migrate_flags", Int.MAX_VALUE)
    }

    private var migrateJob: Job? = null

    init {
        screenModelScope.launchIO {
            val items = mangaIds
                .map { id ->
                    async {
                        val manga = getManga.await(id) ?: return@async null
                        val chapterInfo = getChapterInfo(id)
                        MigratingManga(
                            manga = manga,
                            chapterCount = chapterInfo.chapterCount,
                            latestChapter = chapterInfo.latestChapter,
                            source = sourceManager.getOrStub(manga.source).name,
                        )
                    }
                }
                .awaitAll()
                .filterNotNull()

            mutableState.update { it.copy(items = items.toImmutableList()) }

            mutableState.update { it.copy(isLoading = false) }

            runSearches(items)
        }
    }

    private suspend fun getChapterInfo(id: Long) = getChaptersByMangaId.await(id).let { chapters ->
        ChapterInfo(
            latestChapter = chapters.maxOfOrNull { it.chapterNumber },
            chapterCount = chapters.size,
        )
    }

    private suspend fun runSearches(items: List<MigratingManga>) {
        val sources = getEnabledSources()

        items.forEach { item ->
            val result = searchSource(item.manga, sources)
            val updatedItem = when (result) {
                null -> item.copy(searchResult = SearchResult.NotFound)
                else -> {
                    val chapterInfo = getChapterInfo(result.id)
                    item.copy(
                        searchResult = SearchResult.Success(
                            manga = result,
                            source = sourceManager.getOrStub(result.source).name,
                            chapterCount = chapterInfo.chapterCount,
                            latestChapter = chapterInfo.latestChapter,
                        ),
                    )
                }
            }

            val updatedItems = state.value.items.map { current ->
                if (current.manga.id == item.manga.id) updatedItem else current
            }.toImmutableList()
            val finishedCount = updatedItems.count { it.searchResult != SearchResult.Searching }
            val migrationComplete = finishedCount == updatedItems.size &&
                updatedItems.any { it.searchResult is SearchResult.Success }

            mutableState.update { state ->
                state.copy(
                    items = updatedItems,
                    finishedCount = finishedCount,
                    migrationComplete = migrationComplete,
                )
            }
        }
    }

    private suspend fun searchSource(manga: Manga, sources: List<CatalogueSource>): Manga? {
        for (source in sources) {
            val result = smartSearchEngine.regularSearch(source, manga.title)
                ?: smartSearchEngine.deepSearch(source, manga.title)
            if (result == null) continue
            if (result.url == manga.url && result.source == manga.source) continue
            return result
        }
        return null
    }

    private fun getEnabledSources(): List<CatalogueSource> {
        if (sourceIds.isNotEmpty()) {
            val byId = sourceManager.getCatalogueSources().associateBy { it.id }
            return sourceIds.mapNotNull { byId[it] }
        }

        val enabledLanguages = sourcePreferences.enabledLanguages().get()
        val disabledSources = sourcePreferences.disabledMangaSources().get()
        val pinnedSources = sourcePreferences.pinnedMangaSources().get()

        return sourceManager.getCatalogueSources()
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }
            .sortedWith(
                compareBy(
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }

    fun migrateMangas() {
        migrateMangas(replace = true)
    }

    fun copyMangas() {
        migrateMangas(replace = false)
    }

    fun migrateNow(mangaId: Long, replace: Boolean) {
        screenModelScope.launchIO {
            val item = items.find { it.manga.id == mangaId } ?: return@launchIO
            val target = (item.searchResult as? SearchResult.Success)?.manga ?: return@launchIO
            val defaultFlags = MangaMigrationFlags.getFlags(item.manga, migrateFlags.get())
            val flags = MangaMigrationFlags.getSelectedFlagsBitMap(
                selectedFlags = defaultFlags.map { it.isDefaultSelected },
                flags = defaultFlags,
            )
            migrateFlags.set(flags)
            migrateManga.migrateManga(item.manga, target, replace, flags)
            removeManga(item)
        }
    }

    fun removeManga(mangaId: Long) {
        screenModelScope.launchIO {
            val item = items.find { it.manga.id == mangaId } ?: return@launchIO
            removeManga(item)
        }
    }

    private fun migrateMangas(replace: Boolean) {
        migrateJob = screenModelScope.launchIO {
            val items = state.value.items
            mutableState.update { it.copy(isMigrating = true, migrationProgress = 0f) }

            try {
                items.forEachIndexed { index, item ->
                    val target = (item.searchResult as? SearchResult.Success)?.manga ?: return@forEachIndexed
                    val defaultFlags = MangaMigrationFlags.getFlags(item.manga, migrateFlags.get())
                    val flags = MangaMigrationFlags.getSelectedFlagsBitMap(
                        selectedFlags = defaultFlags.map { it.isDefaultSelected },
                        flags = defaultFlags,
                    )
                    migrateFlags.set(flags)
                    migrateManga.migrateManga(item.manga, target, replace, flags)
                    mutableState.update {
                        it.copy(migrationProgress = ((index + 1).toFloat() / items.size).coerceAtMost(1f))
                    }
                }
            } finally {
                mutableState.update { it.copy(isMigrating = false) }
                migrateJob = null
            }
        }
    }

    fun cancelMigrate() {
        migrateJob?.cancel()
        migrateJob = null
        mutableState.update { it.copy(isMigrating = false) }
    }

    private fun removeManga(item: MigratingManga) {
        mutableState.update { state ->
            val updatedItems = state.items.toPersistentList().remove(item)
            val finishedCount = updatedItems.count { it.searchResult != SearchResult.Searching }
            state.copy(
                items = updatedItems,
                finishedCount = finishedCount,
                migrationComplete = finishedCount == updatedItems.size &&
                    updatedItems.any { it.searchResult is SearchResult.Success },
            )
        }
    }

    @Immutable
    data class ChapterInfo(
        val latestChapter: Double?,
        val chapterCount: Int,
    )

    @Immutable
    data class MigratingManga(
        val manga: Manga,
        val chapterCount: Int,
        val latestChapter: Double?,
        val source: String,
        val searchResult: SearchResult = SearchResult.Searching,
    )

    sealed interface SearchResult {
        data object Searching : SearchResult
        data object NotFound : SearchResult
        data class Success(
            val manga: Manga,
            val source: String,
            val chapterCount: Int,
            val latestChapter: Double?,
        ) : SearchResult
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val items: ImmutableList<MigratingManga> = persistentListOf(),
        val finishedCount: Int = 0,
        val migrationComplete: Boolean = false,
        val isMigrating: Boolean = false,
        val migrationProgress: Float = 0f,
    )
}
