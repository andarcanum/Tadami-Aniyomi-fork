package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySignalType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.repository.DiscoveryRepository
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
        val eventTimestamp = System.currentTimeMillis()
        val recorded = runCatchingUnlessCancelled {
            repository.recordSignal(
                mediaType = item.mediaType,
                cleanTitle = item.cleanTitle,
                title = item.title,
                signalType = signalType,
                genres = genres,
                provider = item.provider,
                sourceKey = sourceKey,
                timestamp = eventTimestamp,
            )
        }.isSuccess
        if (!recorded) return false
        if (signalType.excludesFromFeed) {
            recordConsumedAltKeys(
                repository = repository,
                mediaType = item.mediaType,
                primaryTitle = item.title,
                sourceKey = sourceKey,
                altMetaResolver = altMetaResolver,
            )
        }
        if (signalType == DiscoverySignalType.CONSUMED) {
            afterConsumed(repository, item.mediaType, item.cleanTitle)
        } else if (signalType == DiscoverySignalType.LIKE) {
            runCatching {
                val app = Injekt.get<android.app.Application>()
                DiscoveryUpdateJob.scheduleRefill(app, item.mediaType)
            }
            if (genres.isEmpty() && (item.sourceId ?: 0L) > 0L && !item.sourceUrl.isNullOrBlank()) {
                enrichmentScope.launch {
                    enrichLikeMetadata(
                        repository = repository,
                        item = item,
                        sourceKey = sourceKey,
                        expectedCreatedAt = eventTimestamp,
                        sourceDetailsResolver = sourceDetailsResolver,
                    )
                }
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
    ) {
        if ((item.sourceId ?: 0L) > 0L && !item.sourceUrl.isNullOrBlank()) {
            val sourceId = item.sourceId!!
            val sourceUrl = item.sourceUrl!!
            val enriched = runCatchingUnlessCancelled {
                sourceDetailsResolver(item.mediaType, sourceId, sourceUrl)
            }.getOrNull().orEmpty()
            if (enriched.isNotEmpty()) {
                runCatchingUnlessCancelled {
                    repository.enrichSignalMetadataIfCurrent(
                        mediaType = item.mediaType,
                        cleanTitle = item.cleanTitle,
                        expectedCreatedAt = expectedCreatedAt,
                        genres = enriched,
                        provider = item.provider,
                        sourceKey = sourceKey,
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
        altMetaResolver: suspend (String, DiscoveryMediaType) -> DiscoveryMeta? = { t, m ->
            runCatchingUnlessCancelled { sharedTrendingSource.fetchMeta(t, m) }.getOrNull()
        },
    ) {
        val cleanTitle = tachiyomi.domain.discovery.model.normalizeDiscoveryTitle(title)
        if (cleanTitle.isBlank()) return
        val sourceKey = resolveSourceKey(mediaType, sourceId)
        runCatchingUnlessCancelled {
            repository.recordSignal(
                mediaType = mediaType,
                cleanTitle = cleanTitle,
                title = title,
                signalType = DiscoverySignalType.CONSUMED,
                genres = emptyList(),
                provider = null,
                sourceKey = sourceKey,
            )
        }
        recordConsumedAltKeys(
            repository = repository,
            mediaType = mediaType,
            primaryTitle = title,
            sourceKey = sourceKey,
            altMetaResolver = altMetaResolver,
        )
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
        val meta = runCatchingUnlessCancelled { altMetaResolver(primaryTitle, mediaType) }.getOrNull() ?: return
        val altKey = consumedAltKey(primaryTitle, meta) ?: return
        runCatchingUnlessCancelled {
            repository.recordSignal(
                mediaType = mediaType,
                cleanTitle = altKey,
                title = meta.altTitle!!,
                signalType = DiscoverySignalType.CONSUMED,
                genres = emptyList(),
                provider = null,
                sourceKey = sourceKey,
            )
        }
    }

    /**
     * Пост-эффекты consumed: 48h-полка показа (тайтл не вернётся даже после сброса
     * вкус-лога) + дебаунс-дозаполнение ленты (10 мин REPLACE-очередь): прочитанное
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
    ): List<String> = runCatching {
        when (mediaType) {
            DiscoveryMediaType.MANGA -> {
                val source = Injekt.get<tachiyomi.domain.source.manga.service.MangaSourceManager>().get(sourceId)
                if (source != null && source !is StubMangaSource) {
                    val sManga = SManga.create().apply { url = sourceUrl }
                    val details = source.getMangaDetails(sManga)
                    parseGenres(details.genre)
                } else {
                    emptyList()
                }
            }
            DiscoveryMediaType.ANIME -> {
                val source = Injekt.get<tachiyomi.domain.source.anime.service.AnimeSourceManager>().get(sourceId)
                if (source != null && source !is StubAnimeSource) {
                    val sAnime = SAnime.create().apply { url = sourceUrl }
                    val details = source.getAnimeDetails(sAnime)
                    parseGenres(details.genre)
                } else {
                    emptyList()
                }
            }
            DiscoveryMediaType.NOVEL -> {
                val source = Injekt.get<tachiyomi.domain.source.novel.service.NovelSourceManager>().get(sourceId)
                if (source != null && source !is StubNovelSource) {
                    val sNovel = SNovel.create().apply { url = sourceUrl }
                    val details = source.getNovelDetails(sNovel)
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
