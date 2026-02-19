package eu.kanade.tachiyomi.ui.browse.novel.extension.details

import android.content.Context
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.extension.novel.interactor.GetNovelExtensionSources
import eu.kanade.domain.extension.novel.interactor.NovelExtensionSourceItem
import eu.kanade.domain.source.novel.interactor.ToggleNovelIncognito
import eu.kanade.domain.source.novel.interactor.ToggleNovelSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.novel.NovelSiteSource
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.extension.novel.model.NovelPlugin
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelExtensionDetailsScreenModel(
    pluginId: String,
    context: Context,
    private val network: NetworkHelper = Injekt.get(),
    private val extensionManager: NovelExtensionManager = Injekt.get(),
    private val getExtensionSources: GetNovelExtensionSources = Injekt.get(),
    private val toggleSource: ToggleNovelSource = Injekt.get(),
    private val toggleIncognito: ToggleNovelIncognito = Injekt.get(),
    private val preferences: SourcePreferences = Injekt.get(),
) : StateScreenModel<NovelExtensionDetailsScreenModel.State>(State()) {

    private val _events: Channel<NovelExtensionDetailsEvent> = Channel()
    val events: Flow<NovelExtensionDetailsEvent> = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            launch {
                extensionManager.installedPluginsFlow
                    .map { it.firstOrNull { plugin -> plugin.id == pluginId } }
                    .collectLatest { extension ->
                        if (extension == null) {
                            _events.send(NovelExtensionDetailsEvent.Uninstalled)
                            return@collectLatest
                        }
                        mutableState.update { state -> state.copy(extension = extension) }
                    }
            }
            launch {
                state.collectLatest { state ->
                    if (state.extension == null) return@collectLatest
                    getExtensionSources.subscribe(state.extension)
                        .map {
                            it.sortedWith(
                                compareBy(
                                    { !it.enabled },
                                    { item ->
                                        item.source.name.takeIf { item.labelAsName }
                                            ?: LocaleHelper.getSourceDisplayName(
                                                item.source.lang,
                                                context,
                                            ).lowercase()
                                    },
                                ),
                            )
                        }
                        .catch { throwable ->
                            logcat(LogPriority.ERROR, throwable)
                            mutableState.update { it.copy(_sources = persistentListOf()) }
                        }
                        .collectLatest { sources ->
                            mutableState.update { it.copy(_sources = sources.toImmutableList()) }
                        }
                }
            }
            launch {
                preferences.incognitoNovelExtensions()
                    .changes()
                    .map { pluginId in it }
                    .distinctUntilChanged()
                    .collectLatest { isIncognito ->
                        mutableState.update { it.copy(isIncognito = isIncognito) }
                    }
            }
        }
    }

    fun clearCookies() {
        val extension = state.value.extension ?: return
        val sourceUrls = state.value.sources.mapNotNull { sourceItem ->
            (sourceItem.source as? NovelSiteSource)?.siteUrl
        }
        val urls = (sourceUrls + extension.site)
            .mapNotNull { url -> normalizeUrl(url) }
            .distinct()

        val cleared = urls.sumOf { url ->
            try {
                network.cookieJar.remove(url)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to clear cookies for $url" }
                0
            }
        }

        logcat { "Cleared $cleared cookies for: ${urls.joinToString()}" }
    }

    fun uninstallExtension() {
        val extension = state.value.extension ?: return
        screenModelScope.launch {
            extensionManager.uninstallPlugin(extension)
        }
    }

    fun toggleSource(sourceId: Long) {
        toggleSource.await(sourceId)
    }

    fun toggleSources(enable: Boolean) {
        state.value.sources
            .map { it.source.id }
            .let { sourceIds ->
                sourceIds.forEach { sourceId -> toggleSource.await(sourceId, enable) }
            }
    }

    fun toggleIncognito(enable: Boolean) {
        state.value.extension?.id?.let { id ->
            toggleIncognito.await(id, enable)
        }
    }

    @Immutable
    data class State(
        val extension: NovelPlugin.Installed? = null,
        val isIncognito: Boolean = false,
        private val _sources: ImmutableList<NovelExtensionSourceItem>? = null,
    ) {

        val sources: ImmutableList<NovelExtensionSourceItem>
            get() = _sources ?: persistentListOf()

        val isLoading: Boolean
            get() = extension == null || _sources == null
    }

    private fun normalizeUrl(rawUrl: String?): okhttp3.HttpUrl? {
        val normalized = rawUrl?.trim().orEmpty()
        if (normalized.isBlank()) return null
        val value = if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            normalized
        } else {
            "https://$normalized"
        }
        return value.toHttpUrlOrNull()
    }
}

sealed interface NovelExtensionDetailsEvent {
    data object Uninstalled : NovelExtensionDetailsEvent
}
