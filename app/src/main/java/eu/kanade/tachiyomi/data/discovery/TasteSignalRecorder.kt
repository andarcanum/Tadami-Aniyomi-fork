package eu.kanade.tachiyomi.data.discovery

import kotlinx.coroutines.flow.first
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySignalType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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

    /** Жанры тайтла из genres (все ряды) с фоллбэком на reason-CSV (TASTE) и cached meta. */
    fun snapshotGenres(item: DiscoverySuggestion): List<String> {
        if (item.genres.isNotEmpty()) return item.genres
        val fromReason = if (item.rowType == DiscoveryRowType.TASTE) {
            item.reason
                ?.splitToSequence(",")
                ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                ?.toList()
                .orEmpty()
        } else {
            emptyList()
        }
        if (fromReason.isNotEmpty()) return fromReason
        val titleMeta = CompositeTrendingSource.getCachedMeta(item.title, item.mediaType)
        if (titleMeta != null && titleMeta.genres.isNotEmpty()) return titleMeta.genres
        val seed = item.seedTitle
        if (!seed.isNullOrBlank()) {
            val seedMeta = CompositeTrendingSource.getCachedMeta(seed, item.mediaType)
            if (seedMeta != null && seedMeta.genres.isNotEmpty()) return seedMeta.genres
        }
        return emptyList()
    }

    suspend fun record(
        repository: DiscoveryRepository,
        item: DiscoverySuggestion,
        signalType: DiscoverySignalType,
        altMetaResolver: suspend (String, DiscoveryMediaType) -> DiscoveryMeta? = { t, m ->
            runCatchingUnlessCancelled { sharedTrendingSource.fetchMeta(t, m) }.getOrNull()
        },
    ): Boolean {
        val sourceKey = resolveSourceKey(item.mediaType, item.sourceId)
        val recorded = runCatchingUnlessCancelled {
            repository.recordSignal(
                mediaType = item.mediaType,
                cleanTitle = item.cleanTitle,
                title = item.title,
                signalType = signalType,
                genres = snapshotGenres(item),
                provider = item.provider,
                sourceKey = sourceKey,
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
        }
        return true
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
        runCatching { repository.markShown(mediaType, listOf(cleanTitle)) }
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
}
