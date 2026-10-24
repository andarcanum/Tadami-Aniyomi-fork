package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.suggestions.MultilingualQueryHelper
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException

/**
 * Ряд «Источник»: popular-витрина выбранного источника расширения
 * (catalogue API источника), опционально с жанровым фильтром (best-effort:
 * только если источник exposes группу жанровых чекбоксов).
 *
 * Жанровый фильтр считается эвристикой [GenreMatcher]: названия чекбоксов
 * источника любого языка/формы сопоставляются с запрошенными жанрами;
 * непонятые названия один раз переводятся через [GenreTranslationFallback].
 *
 * V4: статус выпуска ([DiscoveryReleaseStatus]) прокидывается тем же трактом —
 * эвристика [SourceStatusFilterMatcher] находит статус-фильтр источника (Select/
 * Group/TriState любого языка) и выставляет его в FilterList search-запроса.
 * Одиночный Select при 2+ выбранных статусах не выразим — инжект пропускается.
 * V4.1: в [latest] при выбранном статусе приоритет у search-выдачи со статус-фильтром
 * (getLatestUpdates фильтры не принимает, статус в списках обычно UNKNOWN); источник
 * не выражает статус — прежний пост-фильтр по status айтема (UNKNOWN не режем).
 */
interface DiscoverySourceCatalog {
    suspend fun popular(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        page: Int = 1,
        releaseStatuses: Set<DiscoveryReleaseStatus> = emptySet(),
    ): List<DiscoveryRowItem>

    suspend fun popularWithGenres(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        genres: List<String>,
        page: Int = 1,
        releaseStatuses: Set<DiscoveryReleaseStatus> = emptySet(),
    ): List<DiscoveryRowItem>

    /**
     * Genre query plus details-based verification of missing list genres.
     *
     * Default implementation reports only what the list already exposes; the
     * production catalog overrides it to hydrate under a shared budget.
     */
    suspend fun popularWithGenresVerified(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        genres: List<String>,
        page: Int = 1,
        releaseStatuses: Set<DiscoveryReleaseStatus> = emptySet(),
        budget: DiscoveryVerificationBudget? = null,
    ): DiscoveryGenrePage {
        val items = popularWithGenres(mediaType, sourceId, genres, page, releaseStatuses)
        return DiscoveryGenrePage(
            items = items,
            verified = items.count { it.genres.isNotEmpty() },
            unattempted = items.count { it.genres.isEmpty() },
            metadataUnavailable = 0,
            transientFailures = 0,
        )
    }

    suspend fun latest(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        page: Int = 1,
        releaseStatuses: Set<DiscoveryReleaseStatus> = emptySet(),
    ): List<DiscoveryRowItem>
}

class AppDiscoverySourceCatalog : DiscoverySourceCatalog {

    private val genreHydrator = DiscoveryGenreHydrator()

    /** M2: перевод непонятых чекбоксов жанров — ленивый, кэш внутри фолбэка. */
    private val genreFallback by lazy {
        GenreTranslationFallback(translate = { name -> MultilingualQueryHelper.translate(name) })
    }

    override suspend fun popular(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        page: Int,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
    ): List<DiscoveryRowItem> =
        fetch(mediaType, sourceId, genres = null, page = page, releaseStatuses = releaseStatuses)

    override suspend fun popularWithGenres(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        genres: List<String>,
        page: Int,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
    ): List<DiscoveryRowItem> =
        fetch(mediaType, sourceId, genres = genres, page = page, releaseStatuses = releaseStatuses)

    override suspend fun popularWithGenresVerified(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        genres: List<String>,
        page: Int,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
        budget: DiscoveryVerificationBudget?,
    ): DiscoveryGenrePage {
        val items = fetch(mediaType, sourceId, genres = genres, page = page, releaseStatuses = releaseStatuses)
        if (budget == null) {
            return DiscoveryGenrePage(
                items = items,
                verified = items.count { it.genres.isNotEmpty() },
                unattempted = items.count { it.genres.isEmpty() },
                metadataUnavailable = 0,
                transientFailures = 0,
            )
        }
        val byUrl = items.associateBy { it.sourceUrl }
        val hydrated = genreHydrator.hydrate(mediaType, sourceId, items, budget) { url ->
            val title = byUrl[url]?.title.orEmpty()
            val observed = when (mediaType) {
                DiscoveryMediaType.MANGA -> {
                    val source = Injekt.get<MangaSourceManager>().getOrStub(sourceId) as? CatalogueSource
                    val entry = SManga.create().apply {
                        this.url = url
                        this.title = title
                    }
                    source?.let { fetchDiscoveryMangaDetails(it, entry) }?.genre
                }
                DiscoveryMediaType.ANIME -> {
                    val source = Injekt.get<AnimeSourceManager>().getOrStub(sourceId) as? AnimeCatalogueSource
                    val entry = SAnime.create().apply {
                        this.url = url
                        this.title = title
                    }
                    source?.getAnimeDetails(entry)?.genre
                }
                DiscoveryMediaType.NOVEL -> {
                    val source = Injekt.get<NovelSourceManager>().getOrStub(sourceId) as? NovelCatalogueSource
                    val entry = SNovel.create().apply {
                        this.url = url
                        this.title = title
                    }
                    source?.getNovelDetails(entry)?.genre
                }
            }
            parseGenres(observed)
        }
        return DiscoveryGenrePage(
            items = hydrated.items,
            verified = hydrated.verified,
            unattempted = hydrated.unattempted,
            metadataUnavailable = hydrated.metadataUnavailable,
            transientFailures = hydrated.transientFailures,
        )
    }

    override suspend fun latest(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        page: Int,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
    ): List<DiscoveryRowItem> = try {
        // V4.1: при выбранном статусе latest-лента источника не выражает фильтр
        // (getLatestUpdates не принимает FilterList, а статус в списках обычно UNKNOWN).
        // Если источник умеет статус своим фильтром — ряд строится из search-выдачи
        // с этим фильтром; иначе — прежний best-effort пост-фильтр по status айтема.
        if (releaseStatuses.isNotEmpty()) {
            statusSearch(mediaType, sourceId, page, releaseStatuses)?.let { return it }
        }
        when (mediaType) {
            DiscoveryMediaType.MANGA -> {
                val source = Injekt.get<MangaSourceManager>().getOrStub(sourceId) as? CatalogueSource
                    ?: return emptyList()
                val pageData = if (source.supportsLatest) {
                    runCatching { source.getLatestUpdates(page) }.getOrNull()?.takeIf { it.mangas.isNotEmpty() }
                        ?: if (page > 1) {
                            runCatching { source.getLatestUpdates(1) }.getOrNull()?.takeIf { it.mangas.isNotEmpty() }
                        } else {
                            null
                        }
                        ?: runCatching { source.getPopularManga(page) }.getOrNull()?.takeIf { it.mangas.isNotEmpty() }
                        ?: if (page > 1) runCatching { source.getPopularManga(1) }.getOrNull() else null
                } else {
                    runCatching { source.getPopularManga(page) }.getOrNull()?.takeIf { it.mangas.isNotEmpty() }
                        ?: if (page > 1) runCatching { source.getPopularManga(1) }.getOrNull() else null
                } ?: return emptyList()
                val keptMangas = pageData.mangas
                    .filter { SourceStatusFilterMatcher.entryPasses(it.status, releaseStatuses) }
                if (releaseStatuses.isNotEmpty()) {
                    logcat {
                        "[DiscoverySourceCatalog] latest postfilter source=$sourceId " +
                            "kept=${keptMangas.size}/${pageData.mangas.size} statuses=$releaseStatuses"
                    }
                }
                keptMangas.mapIndexed { idx, m ->
                    rowItem(m.title, m.thumbnail_url, source.name, idx, sourceId, m.url, parseGenres(m.genre))
                }
            }
            DiscoveryMediaType.ANIME -> {
                val source = Injekt.get<AnimeSourceManager>().getOrStub(sourceId) as? AnimeCatalogueSource
                    ?: return emptyList()
                val pageData = if (source.supportsLatest) {
                    runCatching { source.getLatestUpdates(page) }.getOrNull()?.takeIf { it.animes.isNotEmpty() }
                        ?: if (page > 1) {
                            runCatching { source.getLatestUpdates(1) }.getOrNull()?.takeIf { it.animes.isNotEmpty() }
                        } else {
                            null
                        }
                        ?: runCatching { source.getPopularAnime(page) }.getOrNull()?.takeIf { it.animes.isNotEmpty() }
                        ?: if (page > 1) runCatching { source.getPopularAnime(1) }.getOrNull() else null
                } else {
                    runCatching { source.getPopularAnime(page) }.getOrNull()?.takeIf { it.animes.isNotEmpty() }
                        ?: if (page > 1) runCatching { source.getPopularAnime(1) }.getOrNull() else null
                } ?: return emptyList()
                val keptAnimes = pageData.animes
                    .filter { SourceStatusFilterMatcher.entryPasses(it.status, releaseStatuses) }
                if (releaseStatuses.isNotEmpty()) {
                    logcat {
                        "[DiscoverySourceCatalog] latest postfilter source=$sourceId " +
                            "kept=${keptAnimes.size}/${pageData.animes.size} statuses=$releaseStatuses"
                    }
                }
                keptAnimes.mapIndexed { idx, a ->
                    rowItem(a.title, a.thumbnail_url, source.name, idx, sourceId, a.url, parseGenres(a.genre))
                }
            }
            DiscoveryMediaType.NOVEL -> {
                val source = Injekt.get<NovelSourceManager>().getOrStub(sourceId) as? NovelCatalogueSource
                    ?: return emptyList()
                val pageData = if (source.supportsLatest) {
                    runCatching { source.getLatestUpdates(page) }.getOrNull()?.takeIf { it.novels.isNotEmpty() }
                        ?: if (page > 1) {
                            runCatching { source.getLatestUpdates(1) }.getOrNull()?.takeIf { it.novels.isNotEmpty() }
                        } else {
                            null
                        }
                        ?: runCatching { source.getPopularNovels(page) }.getOrNull()?.takeIf { it.novels.isNotEmpty() }
                        ?: if (page > 1) runCatching { source.getPopularNovels(1) }.getOrNull() else null
                } else {
                    runCatching { source.getPopularNovels(page) }.getOrNull()?.takeIf { it.novels.isNotEmpty() }
                        ?: if (page > 1) runCatching { source.getPopularNovels(1) }.getOrNull() else null
                } ?: return emptyList()
                val keptNovels = pageData.novels
                    .filter { SourceStatusFilterMatcher.entryPasses(it.status, releaseStatuses) }
                if (releaseStatuses.isNotEmpty()) {
                    logcat {
                        "[DiscoverySourceCatalog] latest postfilter source=$sourceId " +
                            "kept=${keptNovels.size}/${pageData.novels.size} statuses=$releaseStatuses"
                    }
                }
                keptNovels.mapIndexed { idx, n ->
                    rowItem(n.title, n.thumbnail_url, source.name, idx, sourceId, n.url, parseGenres(n.genre))
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: OutOfMemoryError) {
        // Тяжёлый источник (напр. Hitomi с in-memory индексом всех галерей) исчерпал heap:
        // Error не ловится catch(Exception) и без этого-guard убивает весь процесс.
        // Ряд выживает без источника, буферы становятся мусором после выхода из catch.
        logcat { "[DiscoverySourceCatalog] latest OOM source=$sourceId" }
        emptyList()
    } catch (e: Exception) {
        logcat { "[DiscoverySourceCatalog] latest FAILED source=$sourceId: ${e.message}" }
        emptyList()
    }

    /**
     * V4.1: search-выдача источника с выставленным статус-фильтром; null — источник
     * не выражает выбранные статусы своим фильтром (одиночный Select при 2+ статусах,
     * отсутствие фильтра) — вызывающий возвращается к пост-фильтру latest.
     */
    private suspend fun statusSearch(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        page: Int,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
    ): List<DiscoveryRowItem>? = try {
        when (mediaType) {
            DiscoveryMediaType.MANGA -> {
                val source = Injekt.get<MangaSourceManager>().getOrStub(sourceId) as? CatalogueSource
                    ?: return null
                val filters = source.getFilterList()
                if (!applyMangaStatusFilter(filters, releaseStatuses)) return null
                logcat { "[DiscoverySourceCatalog] statusSearch hit source=$sourceId statuses=$releaseStatuses" }
                val pageData = runCatching { source.getSearchManga(page, "", filters) }
                    .getOrNull()?.takeIf { it.mangas.isNotEmpty() }
                    ?: if (page > 1) {
                        runCatching { source.getSearchManga(1, "", filters) }.getOrNull()
                    } else {
                        null
                    }
                val mangas = pageData?.mangas
                    ?.filter { SourceStatusFilterMatcher.entryPasses(it.status, releaseStatuses) }
                    ?: return null
                mangas.mapIndexed { idx, m ->
                    rowItem(m.title, m.thumbnail_url, source.name, idx, sourceId, m.url, parseGenres(m.genre))
                }
            }
            DiscoveryMediaType.ANIME -> {
                val source = Injekt.get<AnimeSourceManager>().getOrStub(sourceId) as? AnimeCatalogueSource
                    ?: return null
                val filters = source.getFilterList()
                if (!applyAnimeStatusFilter(filters, releaseStatuses)) return null
                logcat { "[DiscoverySourceCatalog] statusSearch hit source=$sourceId statuses=$releaseStatuses" }
                val pageData = runCatching { source.getSearchAnime(page, "", filters) }
                    .getOrNull()?.takeIf { it.animes.isNotEmpty() }
                    ?: if (page > 1) {
                        runCatching { source.getSearchAnime(1, "", filters) }.getOrNull()
                    } else {
                        null
                    }
                val animes = pageData?.animes
                    ?.filter { SourceStatusFilterMatcher.entryPasses(it.status, releaseStatuses) }
                    ?: return null
                animes.mapIndexed { idx, a ->
                    rowItem(a.title, a.thumbnail_url, source.name, idx, sourceId, a.url, parseGenres(a.genre))
                }
            }
            DiscoveryMediaType.NOVEL -> {
                val source = Injekt.get<NovelSourceManager>().getOrStub(sourceId) as? NovelCatalogueSource
                    ?: return null
                val filters = source.getFilterList()
                if (!applyNovelStatusFilter(filters, releaseStatuses)) return null
                logcat { "[DiscoverySourceCatalog] statusSearch hit source=$sourceId statuses=$releaseStatuses" }
                val pageData = runCatching { source.getSearchNovels(page, "", filters) }
                    .getOrNull()?.takeIf { it.novels.isNotEmpty() }
                    ?: if (page > 1) {
                        runCatching { source.getSearchNovels(1, "", filters) }.getOrNull()
                    } else {
                        null
                    }
                val novels = pageData?.novels
                    ?.filter { SourceStatusFilterMatcher.entryPasses(it.status, releaseStatuses) }
                    ?: return null
                novels.mapIndexed { idx, n ->
                    rowItem(n.title, n.thumbnail_url, source.name, idx, sourceId, n.url, parseGenres(n.genre))
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logcat { "[DiscoverySourceCatalog] statusSearch FAILED source=$sourceId: ${e.message}" }
        null
    }

    private suspend fun fetch(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        genres: List<String>?,
        page: Int = 1,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
    ): List<DiscoveryRowItem> = try {
        when (mediaType) {
            DiscoveryMediaType.MANGA -> {
                val source = Injekt.get<MangaSourceManager>().getOrStub(sourceId) as? CatalogueSource
                    ?: return emptyList()
                var filters = if (genres != null) withMangaGenreFilters(source.getFilterList(), genres) else null
                // Unsupported query filters fall back to bounded browse + observed-details verification.
                // V4: статус выпуска — в тот же FilterList поверх жанров; не применим
                // (нет фильтра / одиночный Select при 2+ статусах) — best-effort без него.
                if (releaseStatuses.isNotEmpty()) {
                    val candidate = filters ?: source.getFilterList()
                    val applied = applyMangaStatusFilter(candidate, releaseStatuses)
                    if (applied) filters = candidate
                    logcat {
                        "[DiscoverySourceCatalog] search statusApplied=$applied " +
                            "source=$sourceId statuses=$releaseStatuses"
                    }
                }
                val pageData = if (filters == null) {
                    runCatching {
                        source.getPopularManga(page)
                    }.getForDiscoveryGenreQuery(genres)?.takeIf { it.mangas.isNotEmpty() }
                        ?: if (page >
                            1
                        ) {
                            runCatching { source.getPopularManga(1) }.getForDiscoveryGenreQuery(genres)
                        } else {
                            null
                        }
                } else {
                    runCatching { source.getSearchManga(page, "", filters) }
                        .getForDiscoveryGenreQuery(genres)?.takeIf { it.mangas.isNotEmpty() }
                        ?: if (page >
                            1
                        ) {
                            runCatching { source.getSearchManga(1, "", filters) }.getForDiscoveryGenreQuery(genres)
                        } else {
                            null
                        }
                } ?: return emptyList()

                val keptMangas = enrichMangasWithStatus(source, pageData.mangas, releaseStatuses)
                    .filter { SourceStatusFilterMatcher.entryPasses(it.status, releaseStatuses) }
                if (releaseStatuses.isNotEmpty()) {
                    logcat {
                        "[DiscoverySourceCatalog] popular postfilter source=$sourceId " +
                            "kept=${keptMangas.size}/${pageData.mangas.size} statuses=$releaseStatuses"
                    }
                }
                keptMangas.mapIndexed { idx, m ->
                    rowItem(m.title, m.thumbnail_url, source.name, idx, sourceId, m.url, parseGenres(m.genre))
                }
            }
            DiscoveryMediaType.ANIME -> {
                val source = Injekt.get<AnimeSourceManager>().getOrStub(sourceId) as? AnimeCatalogueSource
                    ?: return emptyList()
                var filters = if (genres != null) withAnimeGenreFilters(source.getFilterList(), genres) else null
                if (releaseStatuses.isNotEmpty()) {
                    val candidate = filters ?: source.getFilterList()
                    val applied = applyAnimeStatusFilter(candidate, releaseStatuses)
                    if (applied) filters = candidate
                    logcat {
                        "[DiscoverySourceCatalog] search statusApplied=$applied " +
                            "source=$sourceId statuses=$releaseStatuses"
                    }
                }
                val pageData = if (filters == null) {
                    runCatching {
                        source.getPopularAnime(page)
                    }.getForDiscoveryGenreQuery(genres)?.takeIf { it.animes.isNotEmpty() }
                        ?: if (page >
                            1
                        ) {
                            runCatching { source.getPopularAnime(1) }.getForDiscoveryGenreQuery(genres)
                        } else {
                            null
                        }
                } else {
                    runCatching { source.getSearchAnime(page, "", filters) }
                        .getForDiscoveryGenreQuery(genres)?.takeIf { it.animes.isNotEmpty() }
                        ?: if (page >
                            1
                        ) {
                            runCatching { source.getSearchAnime(1, "", filters) }.getForDiscoveryGenreQuery(genres)
                        } else {
                            null
                        }
                } ?: return emptyList()

                val keptAnimes = enrichAnimesWithStatus(source, pageData.animes, releaseStatuses)
                    .filter { SourceStatusFilterMatcher.entryPasses(it.status, releaseStatuses) }
                if (releaseStatuses.isNotEmpty()) {
                    logcat {
                        "[DiscoverySourceCatalog] popular postfilter source=$sourceId " +
                            "kept=${keptAnimes.size}/${pageData.animes.size} statuses=$releaseStatuses"
                    }
                }
                keptAnimes.mapIndexed { idx, a ->
                    rowItem(a.title, a.thumbnail_url, source.name, idx, sourceId, a.url, parseGenres(a.genre))
                }
            }
            DiscoveryMediaType.NOVEL -> {
                val source = Injekt.get<NovelSourceManager>().getOrStub(sourceId) as? NovelCatalogueSource
                    ?: return emptyList()
                var filters = if (genres != null) withNovelGenreFilters(source.getFilterList(), genres) else null
                if (releaseStatuses.isNotEmpty()) {
                    val candidate = filters ?: source.getFilterList()
                    val applied = applyNovelStatusFilter(candidate, releaseStatuses)
                    if (applied) filters = candidate
                    logcat {
                        "[DiscoverySourceCatalog] search statusApplied=$applied " +
                            "source=$sourceId statuses=$releaseStatuses"
                    }
                }
                val pageData = if (filters == null) {
                    runCatching {
                        source.getPopularNovels(page)
                    }.getForDiscoveryGenreQuery(genres)?.takeIf { it.novels.isNotEmpty() }
                        ?: if (page >
                            1
                        ) {
                            runCatching { source.getPopularNovels(1) }.getForDiscoveryGenreQuery(genres)
                        } else {
                            null
                        }
                } else {
                    runCatching { source.getSearchNovels(page, "", filters) }
                        .getForDiscoveryGenreQuery(genres)?.takeIf { it.novels.isNotEmpty() }
                        ?: if (page >
                            1
                        ) {
                            runCatching { source.getSearchNovels(1, "", filters) }.getForDiscoveryGenreQuery(genres)
                        } else {
                            null
                        }
                } ?: return emptyList()

                val keptNovels = enrichNovelsWithStatus(source, pageData.novels, releaseStatuses)
                    .filter { SourceStatusFilterMatcher.entryPasses(it.status, releaseStatuses) }
                if (releaseStatuses.isNotEmpty()) {
                    logcat {
                        "[DiscoverySourceCatalog] popular postfilter source=$sourceId " +
                            "kept=${keptNovels.size}/${pageData.novels.size} statuses=$releaseStatuses"
                    }
                }
                keptNovels.mapIndexed { idx, n ->
                    rowItem(n.title, n.thumbnail_url, source.name, idx, sourceId, n.url, parseGenres(n.genre))
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: OutOfMemoryError) {
        // Не даём Error убить генератор ленты: ряд честно помечается failed через IOException.
        logcat { "[DiscoverySourceCatalog] OOM source=$sourceId" }
        throw IOException("source $sourceId OOM", e)
    } catch (e: Exception) {
        // Пробрасываем: ряд SOURCE помечается failed, кэш не затирается.
        logcat { "[DiscoverySourceCatalog] FAILED source=$sourceId: ${e.message}" }
        throw e
    }

    private fun parseGenres(genre: String?): List<String> =
        genre.orEmpty().splitToSequence(",").mapNotNull { it.trim().takeIf(String::isNotEmpty) }.toList()

    private fun rowItem(
        title: String,
        thumbnailUrl: String?,
        sourceName: String,
        index: Int,
        sourceId: Long,
        sourceUrl: String?,
        genres: List<String> = emptyList(),
    ) = DiscoveryRowItem(
        title = title,
        cleanTitle = normalizeDiscoveryTitle(title),
        coverUrl = thumbnailUrl,
        reason = null,
        seedTitle = null,
        provider = sourceName,
        score = 1.0 - index * 0.01,
        sourceId = sourceId,
        sourceUrl = sourceUrl,
        genres = genres,
    )

    internal suspend fun withMangaGenreFilters(filters: FilterList, genres: List<String>): FilterList? {
        val groups = filters.filterIsInstance<Filter.Group<*>>().filter { isGenreGroupName(it.name) }
        if (groups.isEmpty()) return null
        // LibGroup has distinct GenreList and TagList; keep the original plugin instances and IDs.
        val options = groups.flatMap { it.state }
        val boxes = options.filterIsInstance<Filter.CheckBox>()
        val tris = options.filterIsInstance<Filter.TriState>()
        val allOptions = (boxes.map { it.name } + tris.map { it.name }).distinct()
        // Эвристика жанров: чекбоксы и тристейты любого языка матчатся с запросом; непонятые
        // названия источник-специфичной экзотики переводятся один раз (M2).
        val matched = genreFallback.selectSourceGenres(genres, allOptions)
        val selectedNames = HashSet<String>()
        for (option in options) {
            when (option) {
                is Filter.CheckBox -> {
                    if (option.name in matched && selectedNames.add(GenreMatcher.normalize(option.name))) {
                        option.state = true
                    }
                }
                is Filter.TriState -> {
                    if (option.name in matched && selectedNames.add(GenreMatcher.normalize(option.name))) {
                        option.state = Filter.TriState.STATE_INCLUDE
                    }
                }
            }
        }
        return if (matched.isNotEmpty()) filters else null
    }

    private suspend fun withAnimeGenreFilters(filters: AnimeFilterList, genres: List<String>): AnimeFilterList? {
        val group = filters.filterIsInstance<AnimeFilter.Group<*>>()
            .firstOrNull { isGenreGroupName(it.name) }
            ?: return null
        val boxes = group.state.filterIsInstance<AnimeFilter.CheckBox>()
        val tris = group.state.filterIsInstance<AnimeFilter.TriState>()
        val allOptions = (boxes.map { it.name } + tris.map { it.name }).distinct()
        val matched = genreFallback.selectSourceGenres(genres, allOptions)
        boxes.filter { it.name in matched }.forEach { it.state = true }
        tris.filter { it.name in matched }.forEach { it.state = AnimeFilter.TriState.STATE_INCLUDE }
        return if (matched.isNotEmpty()) filters else null
    }

    private suspend fun withNovelGenreFilters(filters: NovelFilterList, genres: List<String>): NovelFilterList? {
        val group = filters.filterIsInstance<NovelFilter.Group<*>>()
            .firstOrNull { isGenreGroupName(it.name) }
            ?: return null
        val boxes = group.state.filterIsInstance<NovelFilter.CheckBox>()
        val tris = group.state.filterIsInstance<NovelFilter.TriState>()
        val allOptions = (boxes.map { it.name } + tris.map { it.name }).distinct()
        val matched = genreFallback.selectSourceGenres(genres, allOptions)
        boxes.filter { it.name in matched }.forEach { it.state = true }
        tris.filter { it.name in matched }.forEach { it.state = NovelFilter.TriState.STATE_INCLUDE }
        return if (matched.isNotEmpty()) filters else null
    }

    /**
     * V4: выставить статус-фильтр источника под выбранные статусы выпуска.
     * Select — одиночный (применим только при одном статусе), Group — чекбоксы/
     * tri-state (любое число), топ-левел TriState/CheckBox — по имени опции.
     * true = фильтр выставлен, запрос пойдёт через search с этим FilterList.
     *
     * internal: юнит-тест прогоняет РЕАЛЬНЫЕ формы фильтров расширений
     * (weebcentral/asurascans/comick/readmanga/mangalib…) — см. RealSourcesStatusFilterSimulationTest.
     */
    internal fun applyMangaStatusFilter(filters: FilterList, selected: Set<DiscoveryReleaseStatus>): Boolean {
        val select = filters.filterIsInstance<Filter.Select<*>>()
            .firstOrNull { SourceStatusFilterMatcher.isStatusFilterName(it.name) }
        if (select != null) {
            val idx = SourceStatusFilterMatcher.matchSelectIndex(select.values.map(::optionName), selected)
                ?: return false
            select.state = idx
            return true
        }
        val group = filters.filterIsInstance<Filter.Group<*>>()
            .firstOrNull { SourceStatusFilterMatcher.isStatusFilterName(it.name) }
        if (group != null) {
            val boxes = group.state.filterIsInstance<Filter.CheckBox>()
            val matchedBoxes = SourceStatusFilterMatcher.matchOptionNames(boxes.map { it.name }, selected)
            if (boxes.isNotEmpty() && matchedBoxes.isNotEmpty()) {
                boxes.filter { it.name in matchedBoxes }.forEach { it.state = true }
                return true
            }
            val tris = group.state.filterIsInstance<Filter.TriState>()
            val matchedTris = SourceStatusFilterMatcher.matchOptionNames(tris.map { it.name }, selected)
            if (tris.isNotEmpty() && matchedTris.isNotEmpty()) {
                tris.filter { it.name in matchedTris }.forEach { it.state = Filter.TriState.STATE_INCLUDE }
                return true
            }
            return false
        }
        val loneTri = filters.filterIsInstance<Filter.TriState>()
            .firstOrNull { SourceStatusFilterMatcher.statusOfOption(it.name) in selected }
        if (loneTri != null) {
            loneTri.state = Filter.TriState.STATE_INCLUDE
            return true
        }
        val loneBox = filters.filterIsInstance<Filter.CheckBox>()
            .firstOrNull { SourceStatusFilterMatcher.statusOfOption(it.name) in selected }
        if (loneBox != null) {
            loneBox.state = true
            return true
        }
        return false
    }

    private fun applyAnimeStatusFilter(
        filters: AnimeFilterList,
        selected: Set<DiscoveryReleaseStatus>,
    ): Boolean {
        val select = filters.filterIsInstance<AnimeFilter.Select<*>>()
            .firstOrNull { SourceStatusFilterMatcher.isStatusFilterName(it.name) }
        if (select != null) {
            val idx = SourceStatusFilterMatcher.matchSelectIndex(select.values.map(::optionName), selected)
                ?: return false
            select.state = idx
            return true
        }
        val group = filters.filterIsInstance<AnimeFilter.Group<*>>()
            .firstOrNull { SourceStatusFilterMatcher.isStatusFilterName(it.name) }
        if (group != null) {
            val boxes = group.state.filterIsInstance<AnimeFilter.CheckBox>()
            val matchedBoxes = SourceStatusFilterMatcher.matchOptionNames(boxes.map { it.name }, selected)
            if (boxes.isNotEmpty() && matchedBoxes.isNotEmpty()) {
                boxes.filter { it.name in matchedBoxes }.forEach { it.state = true }
                return true
            }
            val tris = group.state.filterIsInstance<AnimeFilter.TriState>()
            val matchedTris = SourceStatusFilterMatcher.matchOptionNames(tris.map { it.name }, selected)
            if (tris.isNotEmpty() && matchedTris.isNotEmpty()) {
                tris.filter { it.name in matchedTris }.forEach { it.state = AnimeFilter.TriState.STATE_INCLUDE }
                return true
            }
            return false
        }
        val loneTri = filters.filterIsInstance<AnimeFilter.TriState>()
            .firstOrNull { SourceStatusFilterMatcher.statusOfOption(it.name) in selected }
        if (loneTri != null) {
            loneTri.state = AnimeFilter.TriState.STATE_INCLUDE
            return true
        }
        val loneBox = filters.filterIsInstance<AnimeFilter.CheckBox>()
            .firstOrNull { SourceStatusFilterMatcher.statusOfOption(it.name) in selected }
        if (loneBox != null) {
            loneBox.state = true
            return true
        }
        return false
    }

    private fun applyNovelStatusFilter(
        filters: NovelFilterList,
        selected: Set<DiscoveryReleaseStatus>,
    ): Boolean {
        val select = filters.filterIsInstance<NovelFilter.Select<*>>()
            .firstOrNull { SourceStatusFilterMatcher.isStatusFilterName(it.name) }
        if (select != null) {
            val idx = SourceStatusFilterMatcher.matchSelectIndex(select.values.map(::optionName), selected)
                ?: return false
            select.state = idx
            return true
        }
        val group = filters.filterIsInstance<NovelFilter.Group<*>>()
            .firstOrNull { SourceStatusFilterMatcher.isStatusFilterName(it.name) }
        if (group != null) {
            val boxes = group.state.filterIsInstance<NovelFilter.CheckBox>()
            val matchedBoxes = SourceStatusFilterMatcher.matchOptionNames(boxes.map { it.name }, selected)
            if (boxes.isNotEmpty() && matchedBoxes.isNotEmpty()) {
                boxes.filter { it.name in matchedBoxes }.forEach { it.state = true }
                return true
            }
            val tris = group.state.filterIsInstance<NovelFilter.TriState>()
            val matchedTris = SourceStatusFilterMatcher.matchOptionNames(tris.map { it.name }, selected)
            if (tris.isNotEmpty() && matchedTris.isNotEmpty()) {
                tris.filter { it.name in matchedTris }.forEach { it.state = NovelFilter.TriState.STATE_INCLUDE }
                return true
            }
            return false
        }
        val loneTri = filters.filterIsInstance<NovelFilter.TriState>()
            .firstOrNull { SourceStatusFilterMatcher.statusOfOption(it.name) in selected }
        if (loneTri != null) {
            loneTri.state = NovelFilter.TriState.STATE_INCLUDE
            return true
        }
        val loneBox = filters.filterIsInstance<NovelFilter.CheckBox>()
            .firstOrNull { SourceStatusFilterMatcher.statusOfOption(it.name) in selected }
        if (loneBox != null) {
            loneBox.state = true
            return true
        }
        return false
    }

    /** Имя опции Select: строка, пара «имя→query» или toString произвольного значения. */
    private fun optionName(value: Any?): String = when (value) {
        is String -> value
        is Pair<*, *> -> value.first?.toString().orEmpty()
        else -> value?.toString().orEmpty()
    }

    /**
     * Обогащение статусом для источников без статус-фильтра (MangaKakalot, FlameComics):
     * при активном выборе статуса айтемы с UNKNOWN-статусом дозапрашиваются деталями
     * (getMangaDetails/getAnimeDetails/getNovelDetails), где статус есть на странице.
     * Ограничение [DETAILS_ENRICH_CAP] — витрина не делается тяжелой; остальные
     * UNKNOWN проходят как раньше (best-effort, лента не голодает).
     */
    private suspend fun enrichMangasWithStatus(
        source: CatalogueSource,
        mangas: List<SManga>,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
    ): List<SManga> {
        if (releaseStatuses.isEmpty()) return mangas
        val (unknown, known) = mangas.partition { SourceStatusFilterMatcher.fromEntryStatus(it.status) == null }
        if (known.isNotEmpty() || unknown.isEmpty()) return mangas
        // Все без статуса — иначе известные уже отфильтрованы корректно, детали не нужны.
        val enriched = unknown.take(DETAILS_ENRICH_CAP).map { manga ->
            runCatchingUnlessCancelled {
                fetchDiscoveryMangaDetails(source, manga)
            }.getOrNull() ?: manga
        }
        return known + enriched + unknown.drop(DETAILS_ENRICH_CAP)
    }

    private suspend fun enrichAnimesWithStatus(
        source: AnimeCatalogueSource,
        animes: List<SAnime>,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
    ): List<SAnime> {
        if (releaseStatuses.isEmpty()) return animes
        val (unknown, known) = animes.partition { SourceStatusFilterMatcher.fromEntryStatus(it.status) == null }
        if (known.isNotEmpty() || unknown.isEmpty()) return animes
        val enriched = unknown.take(DETAILS_ENRICH_CAP).map { anime ->
            runCatching {
                source.getAnimeDetails(anime)
            }.getOrNull() ?: anime
        }
        return known + enriched + unknown.drop(DETAILS_ENRICH_CAP)
    }

    private suspend fun enrichNovelsWithStatus(
        source: NovelCatalogueSource,
        novels: List<SNovel>,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
    ): List<SNovel> {
        if (releaseStatuses.isEmpty()) return novels
        val (unknown, known) = novels.partition { SourceStatusFilterMatcher.fromEntryStatus(it.status) == null }
        if (known.isNotEmpty() || unknown.isEmpty()) return novels
        val enriched = unknown.take(DETAILS_ENRICH_CAP).map { novel ->
            runCatching {
                source.getNovelDetails(novel)
            }.getOrNull() ?: novel
        }
        return known + enriched + unknown.drop(DETAILS_ENRICH_CAP)
    }

    private companion object {
        const val DETAILS_ENRICH_CAP = 8
    }
}

internal val GENRE_GROUP_ALIASES = listOf(
    "genre", "жанр", "tag", "тег", "categor", "категор", "género", "genero", "ジャンル",
)

internal fun isGenreGroupName(name: String): Boolean =
    GENRE_GROUP_ALIASES.any { name.contains(it, ignoreCase = true) }
