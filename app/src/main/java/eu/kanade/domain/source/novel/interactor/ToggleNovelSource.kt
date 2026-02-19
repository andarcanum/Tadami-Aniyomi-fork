package eu.kanade.domain.source.novel.interactor

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.getAndSet

class ToggleNovelSource(
    private val disabledSources: Preference<Set<String>>,
) {

    fun await(sourceId: Long, enable: Boolean = isEnabled(sourceId)) {
        disabledSources.getAndSet { disabled ->
            if (enable) disabled.minus("$sourceId") else disabled.plus("$sourceId")
        }
    }

    private fun isEnabled(sourceId: Long): Boolean {
        return sourceId.toString() in disabledSources.get()
    }
}
