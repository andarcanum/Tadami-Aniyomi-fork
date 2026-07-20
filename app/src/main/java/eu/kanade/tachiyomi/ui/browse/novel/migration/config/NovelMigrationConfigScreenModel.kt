package eu.kanade.tachiyomi.ui.browse.novel.migration.config

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.novel.model.Source
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelMigrationConfigScreenModel(
    val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
) : StateScreenModel<NovelMigrationConfigScreenModel.State>(State()) {

    init {
        screenModelScope.launchIO {
            val includedSources = sourcePreferences.migrationSourcesNovel().get()
                .mapNotNull { it.toLongOrNull() }
                .toSet()
            val pinnedSources = sourcePreferences.pinnedNovelSources().get()
                .mapNotNull { it.toLongOrNull() }
                .toSet()
            val enabledLanguages = sourcePreferences.enabledLanguages().get()
            val disabledSources = sourcePreferences.disabledNovelSources().get()

            val sources = sourceManager.getCatalogueSources()
                .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }
                .map { source ->
                    MigrationSource(
                        source = Source(
                            id = source.id,
                            lang = source.lang,
                            name = source.name,
                            supportsLatest = source.supportsLatest,
                            isStub = false,
                        ),
                        isSelected = when {
                            includedSources.isNotEmpty() -> source.id in includedSources
                            pinnedSources.isNotEmpty() -> source.id in pinnedSources
                            else -> true
                        },
                    )
                }

            mutableState.update { it.copy(sources = sources.sortedWith(sourceComparator())) }
            mutableState.update { it.copy(isLoading = false) }
        }
    }

    fun toggleSelection(config: SelectionConfig) {
        val pinnedSources = sourcePreferences.pinnedNovelSources().get()
            .mapNotNull { it.toLongOrNull() }
            .toSet()
        val disabledSources = sourcePreferences.disabledNovelSources().get()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

        updateSources { sources ->
            sources.map { source ->
                val selected = when (config) {
                    SelectionConfig.All -> true
                    SelectionConfig.None -> false
                    SelectionConfig.Pinned -> source.id in pinnedSources
                    SelectionConfig.Enabled -> source.id !in disabledSources
                }
                source.copy(isSelected = selected)
            }
        }
    }

    fun toggleSelection(id: Long) {
        updateSources { sources ->
            sources.map { source ->
                if (source.id == id) source.copy(isSelected = !source.isSelected) else source
            }
        }
    }

    fun saveSources() {
        sourcePreferences.migrationSourcesNovel().set(
            state.value.sources
                .filter { it.isSelected }
                .map { it.id.toString() }
                .toSet(),
        )
    }

    private fun updateSources(action: (List<MigrationSource>) -> List<MigrationSource>) {
        mutableState.update { state ->
            val updated = action(state.sources)
            state.copy(sources = updated.sortedWith(sourceComparator()))
        }
        saveSources()
    }

    private fun sourceComparator() = compareBy<MigrationSource>(
        { !it.isSelected },
        { "${it.source.name.lowercase()} (${it.source.lang})" },
    )

    data class State(
        val isLoading: Boolean = true,
        val sources: List<MigrationSource> = emptyList(),
    ) {
        val selectedSources: ImmutableList<MigrationSource>
            get() = sources.filter { it.isSelected }.toImmutableList()

        val availableSources: ImmutableList<MigrationSource>
            get() = sources.filterNot { it.isSelected }.toImmutableList()

        val selectedSourceIds: List<Long>
            get() = sources.filter { it.isSelected }.map { it.id }
    }

    enum class SelectionConfig {
        All,
        None,
        Pinned,
        Enabled,
    }

    data class MigrationSource(
        val source: Source,
        val isSelected: Boolean,
    ) {
        val id: Long
            get() = source.id
    }
}
