package eu.kanade.domain.source.novel.interactor

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.domain.source.novel.model.Source

class ToggleNovelSourcePin(
    private val pinnedSources: Preference<Set<String>>,
) {

    fun await(source: Source) {
        val isPinned = source.id.toString() in pinnedSources.get()
        pinnedSources.getAndSet { pinned ->
            if (isPinned) pinned.minus("${source.id}") else pinned.plus("${source.id}")
        }
    }
}
