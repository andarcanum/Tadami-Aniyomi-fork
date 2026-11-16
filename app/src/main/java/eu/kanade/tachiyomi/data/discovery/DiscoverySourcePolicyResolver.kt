package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryExternalProvider
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySourceInventory
import tachiyomi.domain.discovery.model.DiscoverySourcePlugin
import tachiyomi.domain.discovery.model.DiscoverySourcePolicy
import tachiyomi.domain.discovery.service.DiscoverySourcePolicyProvider
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** App-only SDK identity authority. Domain policies never carry runtime SDK objects. */
interface DiscoverySdkBindingAuthority {
    fun isCurrentSourceBinding(
        media: DiscoveryMediaType,
        sourceId: Long,
        source: Any,
        capturedRevision: String? = null,
    ): Boolean
}

/** The getter reads an already loaded atomic inventory; it must not initialize a manager or perform IO. */
class DiscoverySourceInventorySource(
    val current: () -> DiscoverySourceInventory,
    val changes: Flow<DiscoverySourceInventory>,
    val matchesSource: (Long, Any, Set<String>, DiscoverySourceInventory) -> Boolean = { _, _, _, _ -> false },
)

class DiscoverySourcePolicyResolver(
    private val preferences: DiscoveryPreferences = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val inventorySourceLoader: suspend (DiscoveryMediaType) -> DiscoverySourceInventorySource =
        ::loadAppDiscoveryInventorySource,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clock: () -> Long = System::currentTimeMillis,
) : DiscoverySourcePolicyProvider, DiscoverySdkBindingAuthority {
    private data class RetrievalWeights(
        val library: Map<Long, Int> = emptyMap(),
        val affinity: Map<String, Double> = emptyMap(),
        val appLanguage: String = Locale.getDefault().language.lowercase(Locale.ROOT),
        val capturedAt: Long = 0L,
    )

    private data class CachedPolicy(
        val revision: String,
        val weights: RetrievalWeights,
        val failureHistory: String,
        val policy: DiscoverySourcePolicy,
    )

    private data class RankedPlugin(val representative: Long, val weight: Double)

    private val inventories = ConcurrentHashMap<DiscoveryMediaType, DiscoverySourceInventorySource>()
    private val loaderFailures = DiscoveryMediaType.entries.associateWith { MutableStateFlow<Throwable?>(null) }
    private val inventoryChanges = DiscoveryMediaType.entries.associateWith { MutableStateFlow(0L) }
    private val retrievalChanges = DiscoveryMediaType.entries.associateWith { MutableStateFlow(RetrievalWeights()) }
    private val cached = ConcurrentHashMap<DiscoveryMediaType, CachedPolicy>()

    private val loaderJobs = mutableMapOf<DiscoveryMediaType, Job>()

    init {
        DiscoveryMediaType.entries.forEach(::ensureLoader)
    }

    fun lastInventoryFailure(mediaType: DiscoveryMediaType): Throwable? = loaderFailures.getValue(mediaType).value

    fun observeInventoryFailure(mediaType: DiscoveryMediaType): Flow<Throwable?> = loaderFailures.getValue(mediaType)

    fun retryInventoryLoad(mediaType: DiscoveryMediaType) {
        ensureLoader(mediaType)
    }

    private fun ensureLoader(mediaType: DiscoveryMediaType) {
        val job = synchronized(loaderJobs) {
            val existing = loaderJobs[mediaType]
            if (existing != null && !existing.isCompleted) return
            if (inventories[mediaType]?.current?.invoke()?.complete == true) return
            loaderFailures.getValue(mediaType).value = null
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val source = inventorySourceLoader(mediaType)
                    inventories[mediaType] = source
                    loaderFailures.getValue(mediaType).value = null
                    inventoryChanges.getValue(mediaType).value++
                    source.changes.collect { inventoryChanges.getValue(mediaType).value++ }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    loaderFailures.getValue(mediaType).value = error
                    inventoryChanges.getValue(mediaType).value++
                    logcat { "[DiscoverySourcePolicy] inventory unavailable media=$mediaType: " + error.message }
                }
            }.also { loaderJobs[mediaType] = it }
        }
        job.start()
    }

    /** Runtime binding authority stays in app: raw owner keys and current SDK references, never provider display names. */
    override fun isCurrentSourceBinding(
        media: DiscoveryMediaType,
        sourceId: Long,
        source: Any,
        capturedRevision: String?,
    ): Boolean =
        preferences.withSourcePreferencesLock(media) {
            val holder = inventories[media] ?: return@withSourcePreferencesLock false
            val inventory = holder.current()
            val policy = current(media)
            if (capturedRevision != null && capturedRevision != policy.revision) return@withSourcePreferencesLock false
            val revision = sourceRevision(
                media,
                inventory,
                preferences.discoverySourceMode(media).get(),
                parseKeyCsv(preferences.discoverySourceExcluded(media).get()),
                parseKeyCsv(preferences.discoverySourceKnown(media).get()),
                externalProviders(media),
            )
            policy.revision == revision && policy.allowsNative(sourceId) &&
                holder.matchesSource(sourceId, source, policy.allowedPluginKeys, inventory)
        }

    /** Generation supplies its already loaded library/signals; no database read belongs in current(). */
    fun updateRetrievalWeights(
        mediaType: DiscoveryMediaType,
        libraryWeights: Map<Long, Int>,
        sourceAffinity: Map<String, Double>,
        appLanguage: String = Locale.getDefault().language.lowercase(Locale.ROOT),
        capturedAt: Long = clock(),
    ) {
        retrievalChanges.getValue(mediaType).value = RetrievalWeights(
            library = libraryWeights.toMap(),
            affinity = sourceAffinity.toMap(),
            appLanguage = appLanguage,
            capturedAt = capturedAt,
        )
    }

    override fun current(mediaType: DiscoveryMediaType): DiscoverySourcePolicy =
        preferences.withSourcePreferencesLock(mediaType) {
            val inventory = inventories[mediaType]?.current?.invoke() ?: DiscoverySourceInventory.Unavailable
            val mode = preferences.discoverySourceMode(mediaType).get()
            val excluded = parseKeyCsv(preferences.discoverySourceExcluded(mediaType).get())
            val known = parseKeyCsv(preferences.discoverySourceKnown(mediaType).get())
            val externals = externalProviders(mediaType)
            val revision = sourceRevision(mediaType, inventory, mode, excluded, known, externals)
            val weights = retrievalChanges.getValue(mediaType).value
            val failureHistory = preferences.sourceFailureHistory(mediaType).get()
            cached[mediaType]?.takeIf {
                it.revision == revision && it.weights == weights && it.failureHistory == failureHistory
            }?.let { return@withSourcePreferencesLock it.policy }

            val plugins = inventory.plugins.groupBy { it.key }.map { (key, variants) ->
                DiscoverySourcePlugin(
                    key = key,
                    sourceIds = variants.flatMap { it.sourceIds },
                    langs = variants.flatMap { it.langs.entries }.associate { it.key to it.value },
                )
            }
            val members = plugins.associate { it.key to it.sourceIds }
            val allowedKeys = if (inventory.complete) {
                members.keys.filterTo(LinkedHashSet()) { key ->
                    key !in excluded && (mode != "manual" || known.isEmpty() || key in known)
                }
            } else {
                emptySet()
            }
            val ownership = DiscoverySourcePolicy(
                mediaType = mediaType,
                revision = revision,
                inventoryComplete = inventory.complete,
                allowedPluginKeys = allowedKeys,
                pluginMembers = members,
                allowedExternalProviders = externals,
            )
            val history = parseSourceFailureHistory(failureHistory)
            val now = if (cached[mediaType]?.failureHistory != failureHistory || weights.capturedAt <= 0L) {
                clock()
            } else {
                weights.capturedAt
            }
            val ranked = plugins.filter { it.key in allowedKeys }.mapNotNull { plugin ->
                val ids = plugin.sourceIds.filterTo(LinkedHashSet()) { ownership.allowsNative(it) }
                val representative = selectPluginRepresentative(ids, weights.library, plugin.langs, weights.appLanguage)
                    ?: return@mapNotNull null
                val libraryWeight = if (mode == "manual") 1 else plugin.sourceIds.sumOf { weights.library[it] ?: 0 }
                val affinity = applySourceAffinity(
                    libraryWeight,
                    weights.affinity[plugin.key] ?: 0.0,
                    isManualMode =
                    mode == "manual",
                )
                RankedPlugin(representative, affinity * calculateSourceReliabilityFactor(representative, history, now))
            }.sortedWith(compareByDescending<RankedPlugin> { it.weight }.thenBy { it.representative })
            val representatives = if (mode == "manual") ranked else ranked.take(3)
            val policy = DiscoverySourcePolicy(
                mediaType = mediaType,
                revision = revision,
                inventoryComplete = inventory.complete,
                allowedPluginKeys = allowedKeys,
                pluginMembers = members,
                representativeSourceIds = representatives.map { it.representative },
                sourceWeights = ranked.associate { it.representative to it.weight },
                allowedExternalProviders = externals,
            )
            cached[mediaType] = CachedPolicy(revision, weights, failureHistory, policy)
            policy
        }

    override fun observe(mediaType: DiscoveryMediaType): Flow<DiscoverySourcePolicy> =
        observeRaw(mediaType, rearmLoader = true)

    private fun observeRaw(mediaType: DiscoveryMediaType, rearmLoader: Boolean): Flow<DiscoverySourcePolicy> {
        val updates = listOf(
            inventoryChanges.getValue(mediaType).map { Unit },
            retrievalChanges.getValue(mediaType).map { Unit },
            preferences.discoverySourceMode(mediaType).changes().map { Unit },
            preferences.discoverySourceExcluded(mediaType).changes().map { Unit },
            preferences.discoverySourceKnown(mediaType).changes().map { Unit },
            preferences.externalProvidersEnabled().changes().map { Unit },
            preferences.sourceFailureHistory(mediaType).changes().map { Unit },
            sourcePreferences.suggestionsUseShikimori().changes().map { Unit },
            sourcePreferences.suggestionsUseMangaUpdatesNovel().changes().map { Unit },
            sourcePreferences.suggestionsUseNovelUpdates().changes().map { Unit },
        )
        return merge(*updates.toTypedArray())
            .onStart {
                if (rearmLoader) ensureLoader(mediaType)
                emit(Unit)
            }
            .map { current(mediaType) }
            .distinctUntilChanged()
    }

    override suspend fun awaitComplete(mediaType: DiscoveryMediaType): DiscoverySourcePolicy {
        ensureLoader(mediaType)
        val (complete, failure) = combine(
            observeRaw(mediaType, rearmLoader = false),
            loaderFailures.getValue(mediaType),
        ) { policy, error ->
            policy to error
        }.first { (policy, error) ->
            policy.inventoryComplete || error != null
        }
        if (!complete.inventoryComplete) {
            throw failure ?: IllegalStateException("Discovery inventory unavailable for $mediaType")
        }
        reconcileDiscoverySourcePreferences(preferences, mediaType, complete.pluginMembers.keys)
        return current(mediaType)
    }

    private fun externalProviders(media: DiscoveryMediaType): Map<DiscoveryRowType, Set<DiscoveryExternalProvider>> {
        if (!preferences.externalProvidersEnabled().get()) return emptyMap()
        val all = DiscoveryExternalProvider.entries.toSet()
        val similar = all - buildSet {
            if (!sourcePreferences.suggestionsUseShikimori().get()) add(DiscoveryExternalProvider.SHIKIMORI)
            if (media != DiscoveryMediaType.ANIME &&
                !sourcePreferences.suggestionsUseMangaUpdatesNovel().get()
            ) {
                add(DiscoveryExternalProvider.MANGA_UPDATES)
            }
            if (media == DiscoveryMediaType.NOVEL &&
                !sourcePreferences.suggestionsUseNovelUpdates().get()
            ) {
                add(DiscoveryExternalProvider.NOVEL_UPDATES)
            }
        }
        return mapOf(
            DiscoveryRowType.LIKE to similar,
            DiscoveryRowType.TASTE to all,
            DiscoveryRowType.TREND to all,
        )
    }
}

private fun sourceRevision(
    mediaType: DiscoveryMediaType,
    inventory: DiscoverySourceInventory,
    mode: String,
    excluded: Set<String>,
    known: Set<String>,
    externals: Map<DiscoveryRowType, Set<DiscoveryExternalProvider>>,
): String = buildString {
    fun value(text: String) {
        append(text.length).append(':').append(text)
    }
    value("discovery-source-v1")
    value(mediaType.key)
    value(inventory.complete.toString())
    value(mode)
    excluded.sorted().forEach {
        value("excluded")
        value(it)
    }
    known.sorted().forEach {
        value("known")
        value(it)
    }
    inventory.plugins.sortedWith(compareBy<DiscoverySourcePlugin> { it.key }.thenBy { it.version }).forEach { plugin ->
        value("plugin")
        value(plugin.key)
        value(plugin.version)
        plugin.sourceIds.sorted().forEach { id ->
            value(id.toString())
            value(plugin.langs[id].orEmpty())
        }
    }
    externals.toSortedMap(compareBy { it.key }).forEach { (row, providers) ->
        value(row.key)
        providers.sortedBy { it.name }.forEach { value(it.name) }
    }
}

private suspend fun loadAppDiscoveryInventorySource(mediaType: DiscoveryMediaType): DiscoverySourceInventorySource =
    when (mediaType) {
        DiscoveryMediaType.MANGA -> {
            val manager = Injekt.get<MangaExtensionManager>()
            val sources = Injekt.get<MangaSourceManager>()
            bindInventoryWithSourceManager(
                extensionInventory = manager::discoverySourceInventory,
                extensionChanges = manager.discoverySourceInventoryChanges,
                matchesExtension = manager::isDiscoverySourceBinding,
                sourceInitialized = sources.isInitialized,
                getSource = sources::get,
            )
        }
        DiscoveryMediaType.ANIME -> {
            val manager = Injekt.get<AnimeExtensionManager>()
            val sources = Injekt.get<AnimeSourceManager>()
            bindInventoryWithSourceManager(
                extensionInventory = manager::discoverySourceInventory,
                extensionChanges = manager.discoverySourceInventoryChanges,
                matchesExtension = manager::isDiscoverySourceBinding,
                sourceInitialized = sources.isInitialized,
                getSource = sources::get,
            )
        }
        DiscoveryMediaType.NOVEL -> {
            val manager = Injekt.get<NovelExtensionManager>()
            val sources = Injekt.get<NovelSourceManager>()
            bindInventoryWithSourceManager(
                extensionInventory = manager::discoverySourceInventory,
                extensionChanges = manager.discoverySourceInventoryChanges,
                matchesExtension = manager::isDiscoverySourceBinding,
                sourceInitialized = sources.isInitialized,
                getSource = sources::get,
            )
        }
    }

internal fun bindInventoryWithSourceManager(
    extensionInventory: () -> DiscoverySourceInventory,
    extensionChanges: Flow<DiscoverySourceInventory>,
    matchesExtension: (Long, Any, Set<String>, DiscoverySourceInventory) -> Boolean,
    sourceInitialized: StateFlow<Boolean>,
    getSource: (Long) -> Any?,
): DiscoverySourceInventorySource {
    fun currentInventory(): DiscoverySourceInventory {
        val raw = extensionInventory()
        return if (raw.complete && sourceInitialized.value) {
            raw
        } else {
            DiscoverySourceInventory(complete = false, plugins = raw.plugins)
        }
    }
    return DiscoverySourceInventorySource(
        current = ::currentInventory,
        changes = merge(extensionChanges, sourceInitialized).map { currentInventory() },
        matchesSource = { sourceId, source, allowedOwners, expected ->
            getSource(sourceId) === source &&
                matchesExtension(sourceId, source, allowedOwners, expected)
        },
    )
}
