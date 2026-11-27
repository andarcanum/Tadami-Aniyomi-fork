package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tachiyomi.domain.discovery.model.DiscoveryFactKind
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySignalType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.model.DiscoveryTasteMetadata
import tachiyomi.domain.discovery.model.DiscoveryTitleIdentity
import tachiyomi.domain.discovery.model.canonicalDiscoveryKey
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import tachiyomi.domain.discovery.service.DiscoverySourcePolicyProvider
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.manga.model.StubMangaSource
import tachiyomi.domain.source.novel.model.StubNovelSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private val defaultEnrichmentScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

/** Общий инстанс для кэшей меты (globalMetaCache статический, per-source кэши — общие). */
private val sharedTrendingSource: CompositeTrendingSource by lazy { CompositeTrendingSource() }

/** Альт-ключ consumed-сигнала: нормализованное альтернативное название тайтла. */
internal fun consumedAltKey(primaryTitle: String, meta: DiscoveryMeta?): String? {
    val alt = meta?.altTitle?.trim().orEmpty()
    if (alt.isEmpty()) return null
    val altKey = tachiyomi.domain.discovery.model.normalizeDiscoveryTitle(alt)
    val primaryKey = tachiyomi.domain.discovery.model.normalizeDiscoveryTitle(primaryTitle)
    return altKey.takeIf { it.isNotBlank() && it != primaryKey }
}

/**
 * Taste Learning Engine: wiring записи сигналов взаимодействия.
 * Жанры — best-effort снимок: TASTE-ряд хранит их в reason-CSV; для прочих
 * рядов жанрового вклада нет, но source-аффинити (по sourceId) считается.
 * Сеть в момент non-consumed сигналов не трогается; для consumed-сигналов
 * допустим best-effort резолв альтернативного названия через мету (fire-and-forget, throttled).
 * Диалог «Просмотренные» в настройках покажет альт-строку отдельной записью (undo по языковой форме).
 * Alt-строка с уже существующим LIKE/CLICK на этом ключе мержится в CONSUMED_LIKED обычной матрицей.
 */
object TasteSignalRecorder {

    private val lastSignalTimestamp = java.util.concurrent.atomic.AtomicLong(0L)

    internal fun nextMonotonicTimestamp(): Long {
        while (true) {
            val current = lastSignalTimestamp.get()
            val now = System.currentTimeMillis()
            val next = if (now > current) now else current + 1
            if (lastSignalTimestamp.compareAndSet(current, next)) {
                return next
            }
        }
    }

    /**
     * Жанры тайтла строго из достоверных метаданных: [item.genres] или cached meta по названию тайтла.
     * Explanation payload ([item.reason]) и сидовые метаданные ([item.seedTitle]) исключены,
     * чтобы не приписывать кандидату вымышленные жанры профиля/сида.
     */
    fun snapshotGenres(item: DiscoverySuggestion): List<String> {
        if (item.genres.isNotEmpty()) return item.genres
        val titleMeta = CompositeTrendingSource.getCachedMeta(item.title, item.mediaType)
        if (titleMeta != null && titleMeta.genres.isNotEmpty()) return titleMeta.genres
        return emptyList()
    }

    suspend fun record(
        repository: DiscoveryRepository,
        item: DiscoverySuggestion,
        signalType: DiscoverySignalType,
        enrichmentScope: CoroutineScope = defaultEnrichmentScope,
        altMetaResolver: suspend (String, DiscoveryMediaType) -> DiscoveryMeta? = { t, m ->
            runCatchingUnlessCancelled { sharedTrendingSource.fetchMeta(t, m) }.getOrNull()
        },
        sourceDetailsResolver: suspend (
            DiscoveryMediaType,
            Long,
            String,
        ) -> List<String> = { mediaType, sourceId, sourceUrl ->
            fetchSourceGenres(mediaType, sourceId, sourceUrl)
        },
        localEntryResolver: suspend (
            DiscoveryMediaType,
            Long,
            String,
        ) -> List<String> = { mediaType, sourceId, sourceUrl ->
            fetchLocalGenres(mediaType, sourceId, sourceUrl)
        },
        sourcePolicyProvider: DiscoverySourcePolicyProvider? = null,
        altKeyEnrichmentScope: CoroutineScope? = null,
    ): Boolean {
        val sourceKey = resolveSourceKey(item.mediaType, item.sourceId)
        var genres = snapshotGenres(item)
        if (genres.isEmpty() && (item.sourceId ?: 0L) > 0L && !item.sourceUrl.isNullOrBlank()) {
            val local = runCatchingUnlessCancelled {
                localEntryResolver(item.mediaType, item.sourceId!!, item.sourceUrl!!)
            }.getOrNull().orEmpty()
            if (local.isNotEmpty()) {
                genres = local
            }
        }
        val eventTimestamp = nextMonotonicTimestamp()
        val identity = DiscoveryTitleIdentity(
            mediaType = item.mediaType,
            identityKey = item.identityKey.orEmpty(),
            title = item.title,
            primaryCleanTitle = item.cleanTitle,
            sourceId = item.sourceId,
            sourceUrl = item.sourceUrl,
            trustedAliases = item.trustedAliases,
            metadataChangedAt = eventTimestamp,
        )
        val metadata = DiscoveryTasteMetadata(
            title = item.title,
            primaryCleanTitle = item.cleanTitle,
            genres = genres,
            provider = item.provider,
            sourceKey = sourceKey,
            sourceId = item.sourceId,
            sourceUrl = item.sourceUrl,
            trustedAliases = item.trustedAliases,
            changedAt = eventTimestamp,
        )
        val positive = signalType == DiscoverySignalType.CLICK ||
            signalType == DiscoverySignalType.LIKE ||
            signalType == DiscoverySignalType.ADD
        val recordedTimestamp = if (positive) {
            runCatchingUnlessCancelled {
                repository.recordTaste(identity, signalType, metadata)?.positiveCreatedAt
            }.getOrNull() ?: return false
        } else if (signalType == DiscoverySignalType.HIDE) {
            runCatchingUnlessCancelled {
                repository.recordNegativeTaste(identity, metadata)
            }.getOrNull() ?: return false
        } else if (signalType == DiscoverySignalType.CONSUMED) {
            runCatchingUnlessCancelled {
                repository.recordConsumedTaste(identity, metadata)
            }.getOrNull() ?: return false
        } else {
            runCatchingUnlessCancelled {
                repository.recordSignalWithReceipt(
                    mediaType = item.mediaType,
                    cleanTitle = item.cleanTitle,
                    title = item.title,
                    signalType = signalType,
                    genres = genres,
                    provider = item.provider,
                    sourceKey = sourceKey,
                    timestamp = eventTimestamp,
                )
            }.getOrNull() ?: return false
        }
        if (signalType.excludesFromFeed) {
            if (altKeyEnrichmentScope != null) {
                altKeyEnrichmentScope.launch {
                    recordConsumedAltKeys(
                        repository = repository,
                        mediaType = item.mediaType,
                        primaryTitle = item.title,
                        sourceKey = sourceKey,
                        altMetaResolver = altMetaResolver,
                    )
                }
            } else {
                recordConsumedAltKeys(
                    repository = repository,
                    mediaType = item.mediaType,
                    primaryTitle = item.title,
                    sourceKey = sourceKey,
                    altMetaResolver = altMetaResolver,
                )
            }
        }
        if (signalType == DiscoverySignalType.CONSUMED) {
            afterConsumed(repository, item.mediaType, item.cleanTitle)
        } else if (
            (signalType == DiscoverySignalType.LIKE || signalType == DiscoverySignalType.ADD) &&
            genres.isEmpty() &&
            (item.sourceId ?: 0L) > 0L &&
            !item.sourceUrl.isNullOrBlank()
        ) {
            enrichmentScope.launch {
                enrichLikeMetadata(
                    repository = repository,
                    item = item,
                    sourceKey = sourceKey,
                    expectedCreatedAt = recordedTimestamp,
                    sourceDetailsResolver = sourceDetailsResolver,
                    sourcePolicyProvider = sourcePolicyProvider,
                )
            }
        }
        return true
    }

    internal suspend fun enrichLikeMetadata(
        repository: DiscoveryRepository,
        item: DiscoverySuggestion,
        sourceKey: String?,
        expectedCreatedAt: Long,
        sourceDetailsResolver: suspend (
            DiscoveryMediaType,
            Long,
            String,
        ) -> List<String> = { mediaType, sourceId, sourceUrl ->
            fetchSourceGenres(mediaType, sourceId, sourceUrl)
        },
        sourcePolicyProvider: DiscoverySourcePolicyProvider? = null,
    ) {
        val policies = runCatchingUnlessCancelled {
            sourcePolicyProvider ?: Injekt.get<DiscoverySourcePolicyProvider>()
        }.getOrNull() ?: return
        if (!policies.current(item.mediaType).allows(item)) return
        if ((item.sourceId ?: 0L) > 0L && !item.sourceUrl.isNullOrBlank()) {
            val sourceId = item.sourceId!!
            val sourceUrl = item.sourceUrl!!
            val enriched = runCatchingUnlessCancelled {
                sourceDetailsResolver(item.mediaType, sourceId, sourceUrl)
            }.getOrNull().orEmpty()
            if (enriched.isNotEmpty()) {
                val identityKey = item.identityKey ?: canonicalDiscoveryKey(
                    item.mediaType,
                    item.sourceId,
                    item.sourceUrl,
                    item.cleanTitle,
                )
                runCatchingUnlessCancelled {
                    repository.enrichTasteMetadata(
                        media = item.mediaType,
                        identityKey = identityKey,
                        expectedPositiveAt = expectedCreatedAt,
                        metadata = DiscoveryTasteMetadata(
                            title = item.title,
                            primaryCleanTitle = item.cleanTitle,
                            genres = enriched,
                            provider = item.provider,
                            sourceKey = sourceKey,
                            sourceId = item.sourceId,
                            sourceUrl = item.sourceUrl,
                            trustedAliases = item.trustedAliases,
                            changedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }
    }

    /**
     * «Просмотрено/прочитано» для тайтла из библиотеки/чтения: нейтральное
     * исключение из ленты (вес 0, вкусовой профиль не трогает). Вызывается
     * открытием читалки/плеера — работает и в инкогнито (читалка открывается
     * всегда, история может не писаться). Жанры — из записи тайтла, если
     * совпадение в сигнал-логе уже есть (best-effort), иначе пусто.
     */
    suspend fun recordConsumed(
        repository: DiscoveryRepository,
        mediaType: DiscoveryMediaType,
        title: String,
        sourceId: Long?,
        enrichmentScope: CoroutineScope? = null,
        altMetaResolver: suspend (String, DiscoveryMediaType) -> DiscoveryMeta? = { t, m ->
            runCatchingUnlessCancelled { sharedTrendingSource.fetchMeta(t, m) }.getOrNull()
        },
    ) {
        val cleanTitle = tachiyomi.domain.discovery.model.normalizeDiscoveryTitle(title)
        if (cleanTitle.isBlank()) return
        val sourceKey = resolveSourceKey(mediaType, sourceId)
        val identityKey = canonicalDiscoveryKey(mediaType, sourceId, null, cleanTitle)
        val eventTimestamp = nextMonotonicTimestamp()
        val identity = DiscoveryTitleIdentity(
            mediaType = mediaType,
            identityKey = identityKey,
            title = title,
            primaryCleanTitle = cleanTitle,
            sourceId = sourceId,
            metadataChangedAt = eventTimestamp,
        )
        val metadata = DiscoveryTasteMetadata(
            title = title,
            primaryCleanTitle = cleanTitle,
            genres = emptyList(),
            provider = null,
            sourceKey = sourceKey,
            sourceId = sourceId,
            changedAt = eventTimestamp,
        )
        runCatchingUnlessCancelled {
            repository.recordConsumedTaste(identity, metadata)
        }
        runCatchingUnlessCancelled {
            repository.recordFact(
                identity,
                DiscoveryFactKind.STARTED,
            )
        }
        if (enrichmentScope != null) {
            enrichmentScope.launch {
                recordConsumedAltKeys(
                    repository = repository,
                    mediaType = mediaType,
                    primaryTitle = title,
                    sourceKey = sourceKey,
                    altMetaResolver = altMetaResolver,
                )
            }
        } else {
            recordConsumedAltKeys(
                repository = repository,
                mediaType = mediaType,
                primaryTitle = title,
                sourceKey = sourceKey,
                altMetaResolver = altMetaResolver,
            )
        }
        afterConsumed(repository, mediaType, cleanTitle)
    }

    /**
     * Мультиключевой consumed: тот же тайтл от другого провайдера называется иначе
     * (ромадзи/EN/RU) — single-ключ не совпадает и тайтл рекомендуется повторно.
     * Best-effort резолв альтернативного названия через мету (кэш → сеть), сигнал
     * пишется и под альтернативным именем. Ошибка резолва молча пропускается.
     */
    private suspend fun recordConsumedAltKeys(
        repository: DiscoveryRepository,
        mediaType: DiscoveryMediaType,
        primaryTitle: String,
        sourceKey: String?,
        altMetaResolver: suspend (String, DiscoveryMediaType) -> DiscoveryMeta?,
    ) {
        val externalAllowed = runCatching {
            Injekt.get<eu.kanade.domain.discovery.service.DiscoveryPreferences>().externalProvidersEnabled().get()
        }.getOrDefault(true)
        if (!externalAllowed) return
        val meta = runCatchingUnlessCancelled { altMetaResolver(primaryTitle, mediaType) }.getOrNull() ?: return
        val altKey = consumedAltKey(primaryTitle, meta) ?: return
        val altTitle = meta.altTitle ?: return
        val altIdentityKey = canonicalDiscoveryKey(mediaType, null, null, altKey)
        val eventTimestamp = nextMonotonicTimestamp()
        runCatchingUnlessCancelled {
            repository.recordConsumedTaste(
                identity = DiscoveryTitleIdentity(
                    mediaType = mediaType,
                    identityKey = altIdentityKey,
                    title = altTitle,
                    primaryCleanTitle = altKey,
                    metadataChangedAt = eventTimestamp,
                ),
                metadata = DiscoveryTasteMetadata(
                    title = altTitle,
                    primaryCleanTitle = altKey,
                    genres = emptyList(),
                    provider = null,
                    sourceKey = sourceKey,
                    changedAt = eventTimestamp,
                ),
            )
        }
    }

    /**
     * Пост-эффекты consumed: 48h-полка показа (тайтл не вернётся даже после сброса
     * вкус-лога) + дебаунс-дозаполнение ленты (60 с REPLACE-очередь): прочитанное
     * заменяется свежим тайтлом из тех же плагинов, дыры в ряде не остаётся.
     */
    private suspend fun afterConsumed(
        repository: DiscoveryRepository,
        mediaType: DiscoveryMediaType,
        cleanTitle: String,
    ) {
        runCatching { repository.suppressRecentlyConsumed(mediaType, listOf(cleanTitle)) }
        runCatching {
            val app = Injekt.get<android.app.Application>()
            DiscoveryUpdateJob.scheduleConsumedRefill(app, mediaType)
        }
    }

    /**
     * Ключ плагина для source-аффинити — та же семантика, что у участия плагинов
     * в [DiscoveryRunner.installedPluginsProvider]: pkgName расширения (anime/manga)
     * или pluginId новелл-плагина. Источник без плагина (OmniSource, локальный)
     * ключа не имеет — аффинити для него не считается.
     */
    suspend fun resolveSourceKey(mediaType: DiscoveryMediaType, sourceId: Long?): String? {
        if (sourceId == null || sourceId <= 0L) return null
        return runCatching {
            when (mediaType) {
                DiscoveryMediaType.ANIME -> {
                    val manager = Injekt.get<tachiyomi.domain.source.anime.service.AnimeSourceManager>()
                    val catalogueIds = manager.getCatalogueSources().mapTo(HashSet()) { it.id }
                    if (sourceId !in catalogueIds) return@runCatching null
                    val extensions = Injekt
                        .get<eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager>()
                        .installedExtensionsFlow.first()
                    extensions.firstOrNull { ext -> sourceId in ext.sources.map { it.id } }?.pkgName
                }
                DiscoveryMediaType.MANGA -> {
                    val manager = Injekt.get<tachiyomi.domain.source.manga.service.MangaSourceManager>()
                    val catalogueIds = manager.getCatalogueSources().mapTo(HashSet()) { it.id }
                    if (sourceId !in catalogueIds) return@runCatching null
                    val extensions = Injekt
                        .get<eu.kanade.tachiyomi.extension.manga.MangaExtensionManager>()
                        .installedExtensionsFlow.first()
                    extensions.firstOrNull { ext -> sourceId in ext.sources.map { it.id } }?.pkgName
                }
                DiscoveryMediaType.NOVEL -> {
                    val manager = Injekt.get<tachiyomi.domain.source.novel.service.NovelSourceManager>()
                    val catalogueIds = manager.getCatalogueSources().mapTo(HashSet()) { it.id }
                    if (sourceId !in catalogueIds) return@runCatching null
                    Injekt.get<eu.kanade.tachiyomi.extension.novel.NovelExtensionManager>()
                        .getPluginId(sourceId)
                }
            }
        }.getOrNull()
    }

    internal suspend fun fetchSourceGenres(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        sourceUrl: String,
    ): List<String> = runCatchingUnlessCancelled {
        val context = currentCoroutineContext()
        context.ensureActive()
        val policies = Injekt.get<DiscoverySourcePolicyProvider>()
        val authority = policies as? DiscoverySdkBindingAuthority ?: return@runCatchingUnlessCancelled emptyList()
        val revision = policies.current(mediaType).revision
        fun guard(source: Any) {
            context.ensureActive()
            if (!policies.current(mediaType).allowsNative(sourceId) ||
                !authority.isCurrentSourceBinding(mediaType, sourceId, source, revision)
            ) {
                throw kotlinx.coroutines.CancellationException("Discovery source binding changed")
            }
        }
        when (mediaType) {
            DiscoveryMediaType.MANGA -> {
                val source = Injekt.get<tachiyomi.domain.source.manga.service.MangaSourceManager>().get(sourceId)
                if (source != null && source !is StubMangaSource) {
                    guard(source)
                    val entry = SManga.create().apply { url = sourceUrl }
                    val details = fetchDiscoveryMangaDetails(source, entry) { guard(source) }
                    guard(source)
                    parseGenres(details.genre)
                } else {
                    emptyList()
                }
            }
            DiscoveryMediaType.ANIME -> {
                val source = Injekt.get<tachiyomi.domain.source.anime.service.AnimeSourceManager>().get(sourceId)
                if (source != null && source !is StubAnimeSource) {
                    guard(source)
                    val details = source.getAnimeDetails(SAnime.create().apply { url = sourceUrl })
                    guard(source)
                    parseGenres(details.genre)
                } else {
                    emptyList()
                }
            }
            DiscoveryMediaType.NOVEL -> {
                val source = Injekt.get<tachiyomi.domain.source.novel.service.NovelSourceManager>().get(sourceId)
                if (source != null && source !is StubNovelSource) {
                    guard(source)
                    val details = source.getNovelDetails(SNovel.create().apply { url = sourceUrl })
                    guard(source)
                    parseGenres(details.genre)
                } else {
                    emptyList()
                }
            }
        }
    }.getOrDefault(emptyList())

    internal suspend fun fetchLocalGenres(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        sourceUrl: String,
    ): List<String> = runCatching {
        when (mediaType) {
            DiscoveryMediaType.MANGA -> {
                val getManga = Injekt.get<tachiyomi.domain.entries.manga.interactor.GetMangaByUrlAndSourceId>()
                getManga.await(sourceUrl, sourceId)?.genre.orEmpty()
            }
            DiscoveryMediaType.ANIME -> {
                val getAnime = Injekt.get<tachiyomi.domain.entries.anime.interactor.GetAnimeByUrlAndSourceId>()
                getAnime.await(sourceUrl, sourceId)?.genre.orEmpty()
            }
            DiscoveryMediaType.NOVEL -> {
                val getNovel = Injekt.get<tachiyomi.domain.entries.novel.interactor.GetNovelByUrlAndSourceId>()
                getNovel.await(sourceUrl, sourceId)?.genre.orEmpty()
            }
        }
    }.getOrDefault(emptyList())

    internal fun parseGenres(genre: String?): List<String> =
        genre.orEmpty().splitToSequence(",").mapNotNull { it.trim().takeIf(String::isNotEmpty) }.toList()
}
