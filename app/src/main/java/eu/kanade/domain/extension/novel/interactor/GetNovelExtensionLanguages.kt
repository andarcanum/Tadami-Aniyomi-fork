package eu.kanade.domain.extension.novel.interactor

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class GetNovelExtensionLanguages(
    private val preferences: SourcePreferences,
    private val extensionManager: NovelExtensionManager,
) {
    fun subscribe(): Flow<List<String>> {
        return combine(
            preferences.enabledLanguages().changes(),
            extensionManager.availablePluginsFlow,
        ) { enabledLanguages, availablePlugins ->
            availablePlugins
                .map { it.lang }
                .filter { it.isNotBlank() }
                .distinct()
                .sortedWith(
                    compareBy<String> { it !in enabledLanguages }.then(LocaleHelper.comparator),
                )
        }
    }
}
