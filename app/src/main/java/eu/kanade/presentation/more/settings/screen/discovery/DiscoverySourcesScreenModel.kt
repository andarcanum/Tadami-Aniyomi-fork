package eu.kanade.presentation.more.settings.screen.discovery

import android.content.Context
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.discovery.AppDiscoverySeedSources
import eu.kanade.tachiyomi.data.discovery.DiscoveryUpdateJob
import eu.kanade.tachiyomi.data.discovery.formatKeyCsv
import eu.kanade.tachiyomi.data.discovery.groupSourcesByPlugin
import eu.kanade.tachiyomi.data.discovery.parseKeyCsv
import eu.kanade.tachiyomi.data.discovery.selectPluginRepresentative
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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
    data class Excluded(val name: String, val undoCsv: String) : DiscoverySourcesToast
    data object LastGuard : DiscoverySourcesToast
    data class DeselectAllKeep(val keptName: String) : DiscoverySourcesToast
    data object SelectAll : DiscoverySourcesToast
}

data class DiscoverySourcesUiState(
    val mediaType: DiscoveryMediaType = DiscoveryMediaType.NOVEL,
    val mode: String = "auto",
    val query: String = "",
    val entries: List<SourcePickUi> = emptyList(),
    val counts: Map<DiscoveryMediaType, Int> = emptyMap(),
    val toast: DiscoverySourcesToast? = null,
)

// ── Чистая UI-логика (internal — под DiscoverySourcesLogicTest) ─────────────────

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

/**
 * ScreenModel пикера «Источники подборок»: установленные плагины (расширения) на медиатип,
 * веса библиотеки, режим/исключения из prefs. Единица участия — расширение: все языковые
 * варианты источника схлопываются в одну строку и один источник в рядах ленты.
 * Каждая мутация персистит prefs и запускает фоновую перегенерацию ленты
 * ([DiscoveryUpdateJob.refreshNow]).
 */
class DiscoverySourcesScreenModel(
    private val context: Context,
) : StateScreenModel<DiscoverySourcesUiState>(DiscoverySourcesUiState()) {

    private val discoveryPreferences = Injekt.get<DiscoveryPreferences>()
    private val sourcePreferences = Injekt.get<SourcePreferences>()
    private val seedSources = AppDiscoverySeedSources()

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

    private var mediaData: Map<DiscoveryMediaType, MediaData> = emptyMap()

    init {
        screenModelScope.launchIO {
            mediaData = DiscoveryMediaType.entries.associateWith { loadMedia(it) }
            rebuild()
        }
    }

    private suspend fun loadMedia(mediaType: DiscoveryMediaType): MediaData {
        data class PluginRaw(
            val key: String,
            val name: String,
            val memberIds: List<Long>,
            val langs: Map<Long, String> = emptyMap(),
        )
        val catalogueIds: Set<Long>
        val raws: List<PluginRaw>
        var mangaIcons = emptyMap<String, MangaExtension.Installed>()
        var animeIcons = emptyMap<String, AnimeExtension.Installed>()
        var novelIcons = emptyMap<String, NovelPlugin.Installed>()
        when (mediaType) {
            DiscoveryMediaType.MANGA -> {
                catalogueIds = Injekt.get<MangaSourceManager>().getCatalogueSources().mapTo(HashSet()) { it.id }
                val extensions = Injekt.get<MangaExtensionManager>().installedExtensionsFlow.first()
                mangaIcons = extensions.associateBy { it.pkgName }
                raws = extensions.map { ext ->
                    PluginRaw(
                        key = ext.pkgName,
                        name = ext.name,
                        memberIds = ext.sources.map { it.id }.filter { it in catalogueIds },
                        langs = ext.sources.filter { it.id in catalogueIds }.associate { it.id to it.lang },
                    )
                }
            }
            DiscoveryMediaType.ANIME -> {
                catalogueIds = Injekt.get<AnimeSourceManager>().getCatalogueSources().mapTo(HashSet()) { it.id }
                val extensions = Injekt.get<AnimeExtensionManager>().installedExtensionsFlow.first()
                animeIcons = extensions.associateBy { it.pkgName }
                raws = extensions.map { ext ->
                    PluginRaw(
                        key = ext.pkgName,
                        name = ext.name,
                        memberIds = ext.sources.map { it.id }.filter { it in catalogueIds },
                        langs = ext.sources.filter { it.id in catalogueIds }.associate { it.id to it.lang },
                    )
                }
            }
            DiscoveryMediaType.NOVEL -> {
                val novelSourceManager = Injekt.get<NovelSourceManager>()
                catalogueIds = novelSourceManager.getCatalogueSources().mapTo(HashSet()) { it.id }
                val manager = Injekt.get<NovelExtensionManager>()
                val plugins = manager.installedPluginsFlow.first()
                novelIcons = plugins.associateBy { it.id }
                raws = groupSourcesByPlugin(catalogueIds) { manager.getPluginId(it) }.map { (key, ids) ->
                    val langs = ids.mapNotNull { id ->
                        runCatching { id to novelSourceManager.getOrStub(id).lang }.getOrNull()
                    }.toMap()
                    PluginRaw(
                        key = key,
                        name = plugins.firstOrNull { it.id == key }?.name ?: key,
                        memberIds = ids,
                        langs = langs,
                    )
                }
            }
        }
        val candidates = seedSources.candidates(mediaType)
        val weights = candidates.filter { it.sourceId > 0 }.groupingBy { it.sourceId }.eachCount()
        val ordered = raws.mapNotNull { raw ->
            val representative = selectPluginRepresentative(
                memberIds = raw.memberIds.toSet(),
                weights = weights,
                langs = raw.langs,
                appLang = java.util.Locale.getDefault().language.lowercase(),
            ) ?: return@mapNotNull null
            PluginEntry(
                key = raw.key,
                name = raw.name,
                repId = representative,
                memberIds = raw.memberIds.toSet(),
                weight = raw.memberIds.sumOf { weights[it] ?: 0 },
            )
        }
            .filter { it.memberIds.isNotEmpty() }
            .sortedWith(compareByDescending<PluginEntry> { it.weight }.thenBy { it.repId })
        val lastUsedId = when (mediaType) {
            DiscoveryMediaType.ANIME -> sourcePreferences.lastUsedAnimeSource().get()
            DiscoveryMediaType.MANGA -> sourcePreferences.lastUsedMangaSource().get()
            DiscoveryMediaType.NOVEL -> sourcePreferences.lastUsedNovelSource().get()
        }
        return MediaData(ordered, lastUsedId, mangaIcons, animeIcons, novelIcons)
    }

    private fun rebuild() {
        val rebuilt = DiscoveryMediaType.entries.mapNotNull { media ->
            val data = mediaData[media] ?: return@mapNotNull null
            val excluded = parseKeyCsv(discoveryPreferences.discoverySourceExcluded(media).get())
            val top3Auto = data.ordered.filterNot { it.key in excluded }.take(3).map { it.key }.toSet()
            media to data.ordered.map { entry ->
                SourcePickUi(
                    id = entry.repId,
                    pluginKey = entry.key,
                    name = entry.name,
                    weight = entry.weight,
                    isLastUsed = data.lastUsedId in entry.memberIds,
                    isTop3Auto = entry.key in top3Auto,
                    excluded = entry.key in excluded,
                )
            }
        }.toMap()
        // mediaType читается внутри update-лямбды: rebuild() вызывается конкурентно
        // из init-launchIO и switchMedia — запись должна идти в активный таб.
        mutableState.update {
            val current = it.mediaType
            it.copy(
                mode = discoveryPreferences.discoverySourceMode(current).get(),
                entries = rebuilt[current].orEmpty(),
                counts = rebuilt.mapValues { entry -> entry.value.size },
            )
        }
    }

    fun switchMedia(mediaType: DiscoveryMediaType) {
        mutableState.update { it.copy(mediaType = mediaType, query = "", toast = null) }
        rebuild()
    }

    fun setMode(mode: String) {
        val mediaType = state.value.mediaType
        // Повторный тап по активному сегменту не должен дёргать refreshNow
        // (isManualRefresh ротирует сиды/страницы — только реальные изменения).
        if (discoveryPreferences.discoverySourceMode(mediaType).get() == mode) return
        discoveryPreferences.discoverySourceMode(mediaType).set(mode)
        DiscoveryUpdateJob.refreshNow(context, mediaType)
        rebuild()
    }

    fun setQuery(query: String) {
        mutableState.update { it.copy(query = query) }
    }

    fun toggleSource(pluginKey: String) {
        val mediaType = state.value.mediaType
        val data = mediaData[mediaType] ?: return
        val currentExcluded = parseKeyCsv(discoveryPreferences.discoverySourceExcluded(mediaType).get())
        val next = toggleExcluded(currentExcluded, pluginKey, data.ordered.mapTo(HashSet()) { it.key })
        if (next == null) {
            mutableState.update { it.copy(toast = DiscoverySourcesToast.LastGuard) }
            return
        }
        discoveryPreferences.discoverySourceExcluded(mediaType).set(formatKeyCsv(next))
        DiscoveryUpdateJob.refreshNow(context, mediaType)
        val becameExcluded = pluginKey !in currentExcluded
        val name = data.ordered.firstOrNull { it.key == pluginKey }?.name.orEmpty()
        val toast = if (becameExcluded) {
            DiscoverySourcesToast.Excluded(name, formatKeyCsv(currentExcluded))
        } else {
            null
        }
        rebuild()
        mutableState.update { it.copy(toast = toast) }
    }

    fun selectAll() {
        val mediaType = state.value.mediaType
        if (mediaData[mediaType] == null) return
        discoveryPreferences.discoverySourceExcluded(mediaType).set("")
        DiscoveryUpdateJob.refreshNow(context, mediaType)
        rebuild()
        mutableState.update { it.copy(toast = DiscoverySourcesToast.SelectAll) }
    }

    fun deselectAll() {
        val mediaType = state.value.mediaType
        val data = mediaData[mediaType] ?: return
        val excluded = parseKeyCsv(discoveryPreferences.discoverySourceExcluded(mediaType).get())
        val allowedOrder = data.ordered.filterNot { it.key in excluded }.map { it.key }
        val keep = allowedOrder.firstOrNull() ?: return
        // Союз, а не перезапись: stale-ключи удалённых плагинов остаются в CSV
        // (переустановка = снова исключён).
        discoveryPreferences.discoverySourceExcluded(mediaType)
            .set(formatKeyCsv(excluded + deselectAllKeepTop1(allowedOrder)))
        DiscoveryUpdateJob.refreshNow(context, mediaType)
        rebuild()
        val keepName = data.ordered.firstOrNull { it.key == keep }?.name.orEmpty()
        mutableState.update { it.copy(toast = DiscoverySourcesToast.DeselectAllKeep(keepName)) }
    }

    fun undoLast() {
        val toast = state.value.toast as? DiscoverySourcesToast.Excluded ?: return
        val mediaType = state.value.mediaType
        discoveryPreferences.discoverySourceExcluded(mediaType).set(toast.undoCsv)
        DiscoveryUpdateJob.refreshNow(context, mediaType)
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
