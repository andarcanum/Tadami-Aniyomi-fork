package eu.kanade.tachiyomi.ui.updates.pacing

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.data.library.LibraryUpdatePacingPolicy
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

class LibraryUpdatePacingScreenModel(
    private val uiPreferences: UiPreferences = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val mangaSourceManager: MangaSourceManager = Injekt.get(),
    private val novelSourceManager: NovelSourceManager = Injekt.get(),
) : StateScreenModel<LibraryUpdatePacingState>(
    LibraryUpdatePacingState(timeoutSeconds = uiPreferences.libraryUpdatePacingTimeoutSeconds().get()),
) {

    private val pacingPolicy = LibraryUpdatePacingPolicy(uiPreferences)

    private val timeoutPreference = uiPreferences.libraryUpdatePacingTimeoutSeconds()
    private val selectedSourceKeysPreference = uiPreferences.libraryUpdatePacingSourceKeys()

    private val timeoutState = MutableStateFlow(timeoutPreference.get().coerceAtLeast(0))
    private val selectedSourceKeysState = MutableStateFlow(selectedSourceKeysPreference.get())
    private val searchQueryState = MutableStateFlow("")
    private val animeSourcesState = MutableStateFlow<List<AnimeCatalogueSource>>(emptyList())
    private val mangaSourcesState = MutableStateFlow<List<CatalogueSource>>(emptyList())
    private val novelSourcesState = MutableStateFlow<List<NovelCatalogueSource>>(emptyList())

    init {
        screenModelScope.launch {
            animeSourceManager.catalogueSources.collect {
                animeSourcesState.value = it
                rebuildState()
            }
        }
        screenModelScope.launch {
            mangaSourceManager.catalogueSources.collect {
                mangaSourcesState.value = it
                rebuildState()
            }
        }
        screenModelScope.launch {
            novelSourceManager.catalogueSources.collect {
                novelSourcesState.value = it
                rebuildState()
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        searchQueryState.value = query
        rebuildState()
    }

    fun setTimeoutSeconds(seconds: Int) {
        val clamped = seconds.coerceAtLeast(0)
        timeoutPreference.set(clamped)
        timeoutState.value = clamped
        rebuildState()
    }

    fun toggleSourceSelection(
        mediaType: LibraryUpdatePacingMediaType,
        sourceId: Long,
    ) {
        val sourceKey = pacingPolicy.sourceKey(mediaType.tag, sourceId)
        val updatedKeys = selectedSourceKeysPreference.get()
            .toMutableSet()
            .apply {
                if (contains(sourceKey)) {
                    remove(sourceKey)
                } else {
                    add(sourceKey)
                }
            }
            .toSet()

        selectedSourceKeysPreference.set(updatedKeys)
        selectedSourceKeysState.value = updatedKeys
        rebuildState()
    }

    private fun rebuildState() {
        mutableState.value = LibraryUpdatePacingState(
            timeoutSeconds = timeoutState.value.coerceAtLeast(0),
            searchQuery = searchQueryState.value,
            sources = buildSourceItems(
                animeSources = animeSourcesState.value,
                mangaSources = mangaSourcesState.value,
                novelSources = novelSourcesState.value,
                selectedSourceKeys = selectedSourceKeysState.value,
            ),
        )
    }

    private fun buildSourceItems(
        animeSources: List<AnimeCatalogueSource>,
        mangaSources: List<CatalogueSource>,
        novelSources: List<NovelCatalogueSource>,
        selectedSourceKeys: Set<String>,
    ): ImmutableList<LibraryUpdatePacingSourceItem> {
        return buildList {
            addAll(
                animeSources.map { source ->
                    source.toPacingItem(
                        mediaType = LibraryUpdatePacingMediaType.ANIME,
                        selectedSourceKeys = selectedSourceKeys,
                    )
                },
            )
            addAll(
                mangaSources.map { source ->
                    source.toPacingItem(
                        mediaType = LibraryUpdatePacingMediaType.MANGA,
                        selectedSourceKeys = selectedSourceKeys,
                    )
                },
            )
            addAll(
                novelSources.map { source ->
                    source.toPacingItem(
                        mediaType = LibraryUpdatePacingMediaType.NOVEL,
                        selectedSourceKeys = selectedSourceKeys,
                    )
                },
            )
        }
            .sortedWith(
                compareBy<LibraryUpdatePacingSourceItem>(
                    { it.mediaType.order },
                    { it.name.lowercase(Locale.ROOT) },
                    { it.sourceId },
                ),
            )
            .toImmutableList()
    }

    private fun AnimeCatalogueSource.toPacingItem(
        mediaType: LibraryUpdatePacingMediaType,
        selectedSourceKeys: Set<String>,
    ): LibraryUpdatePacingSourceItem {
        return LibraryUpdatePacingSourceItem(
            sourceKey = pacingPolicy.sourceKey(mediaType.tag, id),
            mediaType = mediaType,
            sourceId = id,
            name = name,
            lang = lang,
            selected = pacingPolicy.sourceKey(mediaType.tag, id) in selectedSourceKeys,
        )
    }

    private fun CatalogueSource.toPacingItem(
        mediaType: LibraryUpdatePacingMediaType,
        selectedSourceKeys: Set<String>,
    ): LibraryUpdatePacingSourceItem {
        return LibraryUpdatePacingSourceItem(
            sourceKey = pacingPolicy.sourceKey(mediaType.tag, id),
            mediaType = mediaType,
            sourceId = id,
            name = name,
            lang = lang,
            selected = pacingPolicy.sourceKey(mediaType.tag, id) in selectedSourceKeys,
        )
    }

    private fun NovelCatalogueSource.toPacingItem(
        mediaType: LibraryUpdatePacingMediaType,
        selectedSourceKeys: Set<String>,
    ): LibraryUpdatePacingSourceItem {
        return LibraryUpdatePacingSourceItem(
            sourceKey = pacingPolicy.sourceKey(mediaType.tag, id),
            mediaType = mediaType,
            sourceId = id,
            name = name,
            lang = lang,
            selected = pacingPolicy.sourceKey(mediaType.tag, id) in selectedSourceKeys,
        )
    }
}

@Immutable
data class LibraryUpdatePacingState(
    val timeoutSeconds: Int = 0,
    val searchQuery: String = "",
    val sources: ImmutableList<LibraryUpdatePacingSourceItem> = persistentListOf(),
) {
    val totalSourceCount: Int
        get() = sources.size

    val selectedSourceCount: Int
        get() = sources.count { it.selected }

    val filteredSources: ImmutableList<LibraryUpdatePacingSourceItem>
        get() = sources
            .filter { it.matches(searchQuery) }
            .toImmutableList()
}

@Immutable
data class LibraryUpdatePacingSourceItem(
    val sourceKey: String,
    val mediaType: LibraryUpdatePacingMediaType,
    val sourceId: Long,
    val name: String,
    val lang: String,
    val selected: Boolean,
) {
    val displayName: String
        get() = if (lang.isBlank()) {
            name
        } else {
            "$name (${lang.uppercase(Locale.ROOT)})"
        }

    fun matches(query: String): Boolean {
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) return true

        return listOf(
            sourceKey,
            name,
            lang,
            mediaType.name,
        ).any { it.lowercase(Locale.ROOT).contains(normalized) }
    }
}

enum class LibraryUpdatePacingMediaType(
    val tag: String,
    val order: Int,
) {
    ANIME(
        tag = LibraryUpdatePacingPolicy.MEDIA_ANIME,
        order = 0,
    ),
    MANGA(
        tag = LibraryUpdatePacingPolicy.MEDIA_MANGA,
        order = 1,
    ),
    NOVEL(
        tag = LibraryUpdatePacingPolicy.MEDIA_NOVEL,
        order = 2,
    ),
}
