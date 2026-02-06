package eu.kanade.domain.source.novel.interactor

import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tachiyomi.domain.source.novel.model.Source
import tachiyomi.domain.source.novel.repository.NovelSourceRepository
import java.util.SortedMap

class GetLanguagesWithNovelSources(
    private val repository: NovelSourceRepository,
    private val enabledLanguages: tachiyomi.core.common.preference.Preference<Set<String>>,
    private val disabledSources: tachiyomi.core.common.preference.Preference<Set<String>>,
) {

    fun subscribe(): Flow<SortedMap<String, List<Source>>> {
        return combine(
            enabledLanguages.changes(),
            disabledSources.changes(),
            repository.getOnlineNovelSources(),
        ) { enabledLanguage, disabledSource, onlineSources ->
            val sortedSources = onlineSources.sortedWith(
                compareBy<Source> { it.id.toString() in disabledSource }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )

            sortedSources
                .filter { it.lang.isNotBlank() }
                .groupBy { it.lang }
                .toSortedMap(
                    compareBy<String> { it !in enabledLanguage }.then(LocaleHelper.comparator),
                )
        }
    }
}
