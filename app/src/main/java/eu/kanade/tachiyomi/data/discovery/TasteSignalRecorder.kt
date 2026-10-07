package eu.kanade.tachiyomi.data.discovery

import kotlinx.coroutines.flow.first
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySignalType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Taste Learning Engine: wiring записи сигналов взаимодействия.
 * Жанры — best-effort снимок: TASTE-ряд хранит их в reason-CSV; для прочих
 * рядов жанрового вклада нет, но source-аффинити (по sourceId) считается.
 * Сеть в момент сигнала не трогается — обогащение остаётся за генерацией ленты.
 */
object TasteSignalRecorder {

    /** Жанры тайтла из reason-CSV (TASTE), с фоллбэком на cached meta для прочих рядов. */
    fun snapshotGenres(item: DiscoverySuggestion): List<String> {
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
    ) {
        val sourceKey = resolveSourceKey(item.mediaType, item.sourceId)
        runCatching {
            repository.recordSignal(
                mediaType = item.mediaType,
                cleanTitle = item.cleanTitle,
                title = item.title,
                signalType = signalType,
                genres = snapshotGenres(item),
                provider = item.provider,
                sourceKey = sourceKey,
            )
        }
        if (signalType == DiscoverySignalType.CONSUMED) {
            afterConsumed(repository, item.mediaType, item.cleanTitle)
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
    ) {
        val cleanTitle = tachiyomi.domain.discovery.model.normalizeDiscoveryTitle(title)
        if (cleanTitle.isBlank()) return
        val sourceKey = resolveSourceKey(mediaType, sourceId)
        runCatching {
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
        afterConsumed(repository, mediaType, cleanTitle)
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
