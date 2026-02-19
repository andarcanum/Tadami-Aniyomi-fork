package eu.kanade.domain.source.novel.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.source.novel.model.Pin
import tachiyomi.domain.source.novel.model.Pins
import tachiyomi.domain.source.novel.model.Source
import tachiyomi.domain.source.novel.repository.NovelSourceRepository

class GetEnabledNovelSources(
    private val repository: NovelSourceRepository,
    private val enabledLanguages: Preference<Set<String>>,
    private val disabledSources: Preference<Set<String>>,
    private val pinnedSources: Preference<Set<String>>,
    private val lastUsedSource: Preference<Long>,
) {

    fun subscribe(): Flow<List<Source>> {
        return combine(
            enabledLanguages.changes(),
            disabledSources.changes(),
            pinnedSources.changes(),
            lastUsedSource.changes(),
            repository.getNovelSources(),
        ) { enabledLangs, disabledIds, pinnedIds, lastUsedId, sources ->
            sources
                .filter { it.lang in enabledLangs }
                .filterNot { it.id.toString() in disabledIds }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .flatMap { source ->
                    val flag = if ("${source.id}" in pinnedIds) Pins.pinned else Pins.unpinned
                    val updated = source.copy(pin = flag)
                    val toFlatten = mutableListOf(updated)
                    if (updated.id == lastUsedId) {
                        toFlatten.add(updated.copy(isUsedLast = true, pin = updated.pin - Pin.Actual))
                    }
                    toFlatten
                }
        }
            .distinctUntilChanged()
    }
}
