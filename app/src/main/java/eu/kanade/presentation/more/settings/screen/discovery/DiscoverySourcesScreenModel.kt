package eu.kanade.presentation.more.settings.screen.discovery

import android.content.Context
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.discovery.AppDiscoverySeedSources
import eu.kanade.tachiyomi.data.discovery.DiscoverySeedSources
import eu.kanade.tachiyomi.data.discovery.DiscoverySourcePolicyResolver
import eu.kanade.tachiyomi.data.discovery.DiscoveryUpdateJob
import eu.kanade.tachiyomi.data.discovery.computeDiscoveryConfigurationKey
import eu.kanade.tachiyomi.data.discovery.foldLearnedTasteProfile
import eu.kanade.tachiyomi.data.discovery.formatKeyCsv
import eu.kanade.tachiyomi.data.discovery.groupSourcesByPlugin
import eu.kanade.tachiyomi.data.discovery.parseKeyCsv
import eu.kanade.tachiyomi.data.discovery.reconcileDiscoverySourcePreferences
import eu.kanade.tachiyomi.data.discovery.selectPluginRepresentative
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoverySourcePolicy
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import tachiyomi.domain.discovery.service.DiscoverySourcePolicyProvider
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

/** Строка пикера: один плагин (расширение) со всеми его языковыми вариантами. */
data class SourcePickUi(
    val id: Long,
    val pluginKey: String,
    val name: String,
    val weight: Int,
    val isLastUsed: Boolean,
    /** Входит в первые три неисключённых по weightOrder — то, что авто-режим реально возьмёт. */
    val isTop3Auto: Boolean,
    val excluded: Boolean,
)

sealed interface DiscoverySourcesToast {
    data class Excluded(val name: String, val pluginKey: String) : DiscoverySourcesToast
    data object LastGuard : DiscoverySourcesToast
    data class DeselectAllKeep(val keptName: String) : DiscoverySourcesToast
    data object SelectAll : DiscoverySourcesToast
}

data class DiscoverySourcesUiState(
    val mediaType: DiscoveryMediaType = DiscoveryMediaType.ANIME,
    val mode: String = "auto",
    val query: String = "",
    val entries: List<SourcePickUi> = emptyList(),
    val counts: Map<DiscoveryMediaType, Int> = emptyMap(),
    val toast: DiscoverySourcesToast? = null,
    val isLoadingInventory: Boolean = true,
    val hasInventoryError: Boolean = false,
)

// ── Правила выбора и атомарные мутации настроек источников ─────────────────────

/**
 * Новый набор исключённых ключей плагинов при переключении [key]; null = сработала
 * защита последнего разрешённого плагина.
 */
internal fun toggleExcluded(currentExcluded: Set<String>, key: String, allKeys: Set<String>): Set<String>? {
    if (key in currentExcluded) return currentExcluded - key
    val allowed = allKeys - currentExcluded
    return if (allowed == setOf(key)) null else currentExcluded + key
}

/** «Снять все» с защитой минимума: исключается всё, кроме топ-1 по весу. */
internal fun deselectAllKeepTop1(allowedKeysWeightOrder: List<String>): Set<String> =
    allowedKeysWeightOrder.drop(1).toSet()

/** Persists a mode change and its installed snapshot within the shared source mutation boundary. */
internal fun setDiscoverySourceMode(
    preferences: DiscoveryPreferences,
    mediaType: DiscoveryMediaType,
    mode: String,
    installedKeys: Collection<String>,
): Boolean = preferences.withSourcePreferencesLock(mediaType) {
    val modePreference = preferences.discoverySourceMode(mediaType)
    if (modePreference.get() == mode) return@withSourcePreferencesLock false
    modePreference.set(mode)
    reconcileDiscoverySourcePreferences(preferences, mediaType, installedKeys)
    true
}

/** Returns whether the source became excluded; null means the last-source guard rejected the change. */
internal fun toggleDiscoverySource(
    preferences: DiscoveryPreferences,
    mediaType: DiscoveryMediaType,
    pluginKey: String,
    allKeys: Set<String>,
): Boolean? = preferences.withSourcePreferencesLock(mediaType) {
    val excludedPreference = preferences.discoverySourceExcluded(mediaType)
    val excluded = parseKeyCsv(excludedPreference.get())
    val next = toggleExcluded(excluded, pluginKey, allKeys) ?: return@withSourcePreferencesLock null
    excludedPreference.set(formatKeyCsv(next))
    pluginKey !in excluded
}

/** Explicitly selects the displayed extensions without clearing newly installed or removed keys outside that set. */
internal fun selectAllDiscoverySources(
    preferences: DiscoveryPreferences,
    mediaType: DiscoveryMediaType,
    installedKeys: Collection<String>,
) = preferences.withSourcePreferencesLock(mediaType) {
    reconcileDiscoverySourcePreferences(preferences, mediaType, installedKeys)
    val excludedPreference = preferences.discoverySourceExcluded(mediaType)
    excludedPreference.set(formatKeyCsv(parseKeyCsv(excludedPreference.get()) - installedKeys.toSet()))
}

internal fun deselectAllDiscoverySources(
    preferences: DiscoveryPreferences,
    mediaType: DiscoveryMediaType,
    orderedKeys: List<String>,
): String? = preferences.withSourcePreferencesLock(mediaType) {
    val excludedPreference = preferences.discoverySourceExcluded(mediaType)
    val excluded = parseKeyCsv(excludedPreference.get())
    val allowed = orderedKeys.filterNot { it in excluded }
    val kept = allowed.firstOrNull() ?: return@withSourcePreferencesLock null
    excludedPreference.set(formatKeyCsv(excluded + deselectAllKeepTop1(allowed)))
    kept
}

/** Undo affects only the extension from that action, not a stale snapshot of other consumers' choices. */
internal fun undoDiscoverySourceExclusion(
    preferences: DiscoveryPreferences,
    mediaType: DiscoveryMediaType,
    pluginKey: String,
) = preferences.withSourcePreferencesLock(mediaType) {
    val excludedPreference = preferences.discoverySourceExcluded(mediaType)
    excludedPreference.set(formatKeyCsv(parseKeyCsv(excludedPreference.get()) - pluginKey))
}

internal fun filterSourcePicks(entries: List<SourcePickUi>, query: String): List<SourcePickUi> {
    val q = query.trim()
    if (q.isEmpty()) return entries
    return entries.filter { it.name.contains(q, ignoreCase = true) }
}

/** Soft-cap предупреждение: больше 8 участников — обновление ленты дороже. */
internal fun isOverloadWarn(entries: List<SourcePickUi>): Boolean =
    entries.count { !it.excluded } > 8

/**
 * Ручной режим: выбранные плагины поднимаются наверх списка, исключённые уходят вниз.
 * sortedBy стабилен — внутри групп сохраняется порядок по весу библиотеки.
 */
internal fun selectedFirst(entries: List<SourcePickUi>): List<SourcePickUi> =
    entries.sortedBy { it.excluded }

/** AUTO badges reflect the same effective representatives used by generation, not raw library weights. */
internal fun projectDiscoverySourcePicks(
    entries: List<SourcePickUi>,
    sourcePolicy: DiscoverySourcePolicy,
): List<SourcePickUi> {
    val representatives = sourcePolicy.representativeSourceIds.take(3).toSet()
    val top3Auto = sourcePolicy.pluginMembers.filterValues { members ->
        members.any { it in representatives }
    }.keys
    return entries.map { it.copy(isTop3Auto = it.pluginKey in top3Auto) }
}

internal fun resolveSourcesMediaType(savedKey: String): DiscoveryMediaType =
    DiscoveryMediaType.fromKey(savedKey) ?: DiscoveryMediaType.ANIME

/**
 * ScreenModel пикера «Источники подборок»: установленные плагины (расширения) на медиатип,
 * веса библиотеки, режим/исключения из prefs. Единица участия — расширение: все языковые
 * варианты источника схлопываются в одну строку и один источник в рядах ленты.
 * Каждая мутация персистит prefs и запускает фоновую перегенерацию ленты
 * ([DiscoveryUpdateJob.refreshNow]).
 */
class DiscoverySourcesScreenModel(
    private val context: Context,
    private val discoveryPreferences: DiscoveryPreferences = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val seedSources: DiscoverySeedSources = AppDiscoverySeedSources(),
    sourcePolicies: DiscoverySourcePolicyProvider? = null,
    private val repositoryProvider: () -> DiscoveryRepository = { Injekt.get() },
) : StateScreenModel<DiscoverySourcesUiState>(DiscoverySourcesUiState()) {

    private val sourcePolicyProvider by lazy { sourcePolicies ?: Injekt.get<DiscoverySourcePolicyProvider>() }

    private data class PluginEntry(
        val key: String,
        val name: String,
        val repId: Long,
        val memberIds: Set<Long>,
        val weight: Int,
    )

    private data class MediaData(
        val ordered: List<PluginEntry>,
        val lastUsedId: Long,
        val mangaIcons: Map<String, MangaExtension.Installed> = emptyMap(),
        val animeIcons: Map<String, AnimeExtension.Installed> = emptyMap(),
        val novelIcons: Map<String, NovelPlugin.Installed> = emptyMap(),
    )

    private val mediaData = ConcurrentHashMap<DiscoveryMediaType, MediaData>()
    private val failedMedia = ConcurrentHashMap<DiscoveryMediaType, Boolean>()
    private val retryTrigger = MutableStateFlow(0)
    private val initialConfigKeys = DiscoveryMediaType.entries.associateWith { media ->
        computeDiscoveryConfigurationKey(discoveryPreferences, media, sourcePreferences)
    }

    @Volatile
    private var refreshOnDispose: Boolean = true

    internal fun setRefreshOnDispose(enabled: Boolean) {
        refreshOnDispose = enabled
    }

    init {
        val initialMedia = resolveSourcesMediaType(discoveryPreferences.lastSourcesMediaType().get())
        mutableState.update { it.copy(mediaType = initialMedia) }
        screenModelScope.launchIO {
            val loadedPluginMembers = mutableMapOf<DiscoveryMediaType, Map<String, Set<Long>>>()
            val policyFlows = DiscoveryMediaType.entries.map { media ->
                val failureFlow = (sourcePolicyProvider as? DiscoverySourcePolicyResolver)
                    ?.observeInventoryFailure(media)
                    ?: flowOf(null)
                combine(sourcePolicyProvider.observe(media), failureFlow) { policy, failure ->
                    policy to failure
                }
            }
            // Sequential collection: preparing IO weights emits a policy update, but must not cancel/restart that preparation.
            combine(combine(policyFlows) { it.toList() }, retryTrigger) { snapshots, _ ->
                snapshots
            }.collect { snapshots ->
                for ((snapshot, failure) in snapshots) {
                    val media = snapshot.mediaType
                    try {
                        val current = sourcePolicyProvider.current(media)
                        if (!current.inventoryComplete) {
                            mediaData[media] = MediaData(emptyList(), -1L)
                            loadedPluginMembers.remove(media)
                            failedMedia[media] = (failure != null)
                            continue
                        }
                        failedMedia[media] = false
                        if (loadedPluginMembers[media] != current.pluginMembers) {
                            reconcileDiscoverySourcePreferences(discoveryPreferences, media, current.pluginMembers.keys)
                            val prepared = sourcePolicyProvider.current(media)
                            if (!prepared.inventoryComplete) {
                                mediaData[media] = MediaData(emptyList(), -1L)
                                loadedPluginMembers.remove(media)
                                continue
                            }
                            mediaData[media] = loadMedia(media, prepared)
                            loadedPluginMembers[media] = prepared.pluginMembers
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        mediaData[media] = MediaData(emptyList(), -1L)
                        loadedPluginMembers.remove(media)
                        failedMedia[media] = true
                    }
                }
                rebuild()
            }
        }
    }

    private suspend fun loadMedia(mediaType: DiscoveryMediaType, sourcePolicy: DiscoverySourcePolicy): MediaData {
        val lastUsedId = when (mediaType) {
            DiscoveryMediaType.ANIME -> sourcePreferences.lastUsedAnimeSource().get()
            DiscoveryMediaType.MANGA -> sourcePreferences.lastUsedMangaSource().get()
            DiscoveryMediaType.NOVEL -> sourcePreferences.lastUsedNovelSource().get()
        }
        if (sourcePolicy.pluginMembers.isEmpty()) return MediaData(emptyList(), lastUsedId)
        data class PluginRaw(val key: String, val name: String, val memberIds: Set<Long>, val langs: Map<Long, String>)
        val raws: List<PluginRaw>
        var mangaIcons = emptyMap<String, MangaExtension.Installed>()
        var animeIcons = emptyMap<String, AnimeExtension.Installed>()
        var novelIcons = emptyMap<String, NovelPlugin.Installed>()
        when (mediaType) {
            DiscoveryMediaType.MANGA -> {
                mangaIcons =
                    Injekt.get<MangaExtensionManager>().installedExtensionsFlow.first().associateBy { it.pkgName }
                raws = sourcePolicy.pluginMembers.map { (key, ids) ->
                    val extension = mangaIcons[key]
                    PluginRaw(
                        key,
                        extension?.name ?: key,
                        ids,
                        extension?.sources.orEmpty().filter {
                            it.id in ids
                        }.associate {
                            it.id to
                                it.lang
                        },
                    )
                }
            }
            DiscoveryMediaType.ANIME -> {
                animeIcons =
                    Injekt.get<AnimeExtensionManager>().installedExtensionsFlow.first().associateBy { it.pkgName }
                raws = sourcePolicy.pluginMembers.map { (key, ids) ->
                    val extension = animeIcons[key]
                    PluginRaw(
                        key,
                        extension?.name ?: key,
                        ids,
                        extension?.sources.orEmpty().filter {
                            it.id in ids
                        }.associate {
                            it.id to
                                it.lang
                        },
                    )
                }
            }
            DiscoveryMediaType.NOVEL -> {
                novelIcons = Injekt.get<NovelExtensionManager>().installedPluginsFlow.first().associateBy { it.id }
                raws = sourcePolicy.pluginMembers.map { (key, ids) ->
                    val plugin = novelIcons[key]
                    PluginRaw(key, plugin?.name ?: key, ids, ids.associateWith { plugin?.lang.orEmpty() })
                }
            }
        }
        val candidates = seedSources.candidates(mediaType)
        val weights = candidates.filter { it.sourceId > 0 }.groupingBy { it.sourceId }.eachCount()
        val affinity = foldLearnedTasteProfile(signals = repositoryProvider().getSignals(mediaType)).sourceAffinity
        (sourcePolicyProvider as? DiscoverySourcePolicyResolver)?.updateRetrievalWeights(mediaType, weights, affinity)
        val rankedPolicy = sourcePolicyProvider.current(mediaType)
        val ordered = raws.mapNotNull { raw ->
            val representative = rankedPolicy.sourceWeights.keys.firstOrNull { it in raw.memberIds }
                ?: selectPluginRepresentative(
                    raw.memberIds,
                    weights,
                    raw.langs,
                    java.util.Locale.getDefault().language.lowercase(),
                )
                ?: return@mapNotNull null
            PluginEntry(raw.key, raw.name, representative, raw.memberIds, raw.memberIds.sumOf { weights[it] ?: 0 })
        }.filter { it.memberIds.isNotEmpty() }
            .sortedWith(compareByDescending<PluginEntry> { it.weight }.thenBy { it.repId })
        return MediaData(ordered, lastUsedId, mangaIcons, animeIcons, novelIcons)
    }

    private fun rebuild() {
        val rebuilt = DiscoveryMediaType.entries.mapNotNull { media ->
            val data = mediaData[media] ?: return@mapNotNull null
            val excluded = parseKeyCsv(discoveryPreferences.discoverySourceExcluded(media).get())
            val picks = data.ordered.map { entry ->
                SourcePickUi(
                    id = entry.repId,
                    pluginKey = entry.key,
                    name = entry.name,
                    weight = entry.weight,
                    isLastUsed = data.lastUsedId in entry.memberIds,
                    isTop3Auto = false,
                    excluded = entry.key in excluded,
                )
            }
            media to projectDiscoverySourcePicks(picks, sourcePolicyProvider.current(media))
        }.toMap()
        // mediaType читается внутри update-лямбды: rebuild() вызывается конкурентно
        // из init-launchIO и switchMedia — запись должна идти в активный таб.
        mutableState.update {
            val current = it.mediaType
            val policyComplete = sourcePolicyProvider.current(current).inventoryComplete
            val hasError = failedMedia[current] == true
            val isLoading = !policyComplete && !hasError
            it.copy(
                mode = discoveryPreferences.discoverySourceMode(current).get(),
                entries = rebuilt[current].orEmpty(),
                counts = rebuilt.mapValues { entry -> entry.value.size },
                isLoadingInventory = isLoading,
                hasInventoryError = hasError,
            )
        }
    }

    fun retryInventoryLoad() {
        val mediaType = state.value.mediaType
        failedMedia[mediaType] = false
        rebuild()
        (sourcePolicyProvider as? DiscoverySourcePolicyResolver)?.retryInventoryLoad(mediaType)
        retryTrigger.update { it + 1 }
    }

    private fun flushConfigurationRefresh() {
        DiscoveryMediaType.entries.forEach { mediaType ->
            val currentKey = computeDiscoveryConfigurationKey(discoveryPreferences, mediaType, sourcePreferences)
            if (currentKey != initialConfigKeys[mediaType]) {
                runCatching<Unit> {
                    DiscoveryUpdateJob.refreshConfiguration(context, mediaType)
                }
            }
        }
    }

    override fun onDispose() {
        if (refreshOnDispose) {
            flushConfigurationRefresh()
        }
        super.onDispose()
    }

    fun switchMedia(mediaType: DiscoveryMediaType) {
        discoveryPreferences.lastSourcesMediaType().set(mediaType.key)
        mutableState.update { it.copy(mediaType = mediaType, query = "", toast = null) }
        rebuild()
    }

    fun setMode(mode: String) {
        val mediaType = state.value.mediaType
        val changed = setDiscoverySourceMode(
            preferences = discoveryPreferences,
            mediaType = mediaType,
            mode = mode,
            installedKeys = mediaData[mediaType]?.ordered.orEmpty().map { it.key },
        )
        if (!changed) return
        rebuild()
    }

    fun setQuery(query: String) {
        mutableState.update { it.copy(query = query) }
    }

    fun toggleSource(pluginKey: String) {
        val mediaType = state.value.mediaType
        val data = mediaData[mediaType] ?: return
        val becameExcluded = toggleDiscoverySource(
            preferences = discoveryPreferences,
            mediaType = mediaType,
            pluginKey = pluginKey,
            allKeys = data.ordered.mapTo(HashSet()) { it.key },
        )
        if (becameExcluded == null) {
            mutableState.update { it.copy(toast = DiscoverySourcesToast.LastGuard) }
            return
        }
        val name = data.ordered.firstOrNull { it.key == pluginKey }?.name.orEmpty()
        val toast = if (becameExcluded) {
            DiscoverySourcesToast.Excluded(name, pluginKey)
        } else {
            null
        }
        rebuild()
        mutableState.update { it.copy(toast = toast) }
    }

    fun selectAll() {
        val mediaType = state.value.mediaType
        val data = mediaData[mediaType] ?: return
        selectAllDiscoverySources(
            preferences = discoveryPreferences,
            mediaType = mediaType,
            installedKeys = data.ordered.map { it.key },
        )
        rebuild()
        mutableState.update { it.copy(toast = DiscoverySourcesToast.SelectAll) }
    }

    fun deselectAll() {
        val mediaType = state.value.mediaType
        val data = mediaData[mediaType] ?: return
        val keep = deselectAllDiscoverySources(
            preferences = discoveryPreferences,
            mediaType = mediaType,
            orderedKeys = data.ordered.map { it.key },
        ) ?: return
        rebuild()
        val keepName = data.ordered.firstOrNull { it.key == keep }?.name.orEmpty()
        mutableState.update { it.copy(toast = DiscoverySourcesToast.DeselectAllKeep(keepName)) }
    }

    fun undoLast() {
        val toast = state.value.toast as? DiscoverySourcesToast.Excluded ?: return
        val mediaType = state.value.mediaType
        undoDiscoverySourceExclusion(discoveryPreferences, mediaType, toast.pluginKey)
        rebuild()
        mutableState.update { it.copy(toast = null) }
    }

    fun clearToast() {
        mutableState.update { it.copy(toast = null) }
    }

    // Иконки плагинов: объекты расширений под существующие *ExtensionIcon-composable.

    fun mangaPlugin(key: String): MangaExtension.Installed? =
        mediaData[DiscoveryMediaType.MANGA]?.mangaIcons?.get(key)

    fun animePlugin(key: String): AnimeExtension.Installed? =
        mediaData[DiscoveryMediaType.ANIME]?.animeIcons?.get(key)

    fun novelPlugin(key: String): NovelPlugin.Installed? =
        mediaData[DiscoveryMediaType.NOVEL]?.novelIcons?.get(key)
}
