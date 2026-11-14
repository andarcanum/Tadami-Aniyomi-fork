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
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
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

data class DiscoveryBrowsePage(
    val items: List<DiscoveryRowItem>,
    val hasNextPage: Boolean? = null,
)

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

    /** Legacy catalogues cannot prove whether the raw source page has a continuation. */
    suspend fun popularPage(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        page: Int = 1,
        releaseStatuses: Set<DiscoveryReleaseStatus> = emptySet(),
        budget: DiscoveryVerificationBudget? = null,
    ): DiscoveryBrowsePage = DiscoveryBrowsePage(popular(mediaType, sourceId, page, releaseStatuses))

    /**
     * Compatibility path; production excludes generation keys before spending
     * status details on cards that cannot be shown.
     */
    suspend fun popularPage(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        page: Int = 1,
        releaseStatuses: Set<DiscoveryReleaseStatus> = emptySet(),
        budget: DiscoveryVerificationBudget? = null,
        excludedKeys: Set<String>,
    ): DiscoveryBrowsePage = popularPage(mediaType, sourceId, page, releaseStatuses, budget).let { result ->
        DiscoveryBrowsePage(
            items = result.items.filterNot { it.isExcludedBy(excludedKeys, mediaType) },
            hasNextPage = result.hasNextPage,
        )
    }

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

    /**
     * Genre query bounded by the per-source details allowance of one pass.
     *
     * The default keeps existing test doubles and legacy implementations
     * source-compatible; the app catalogue overrides it to spend [maxDetails]
     * in small batches across all cards of the fetched page.
     */
    suspend fun popularWithGenresAllocated(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        genres: List<String>,
        page: Int = 1,
        releaseStatuses: Set<DiscoveryReleaseStatus> = emptySet(),
        budget: DiscoveryVerificationBudget? = null,
        maxDetails: Int = 0,
    ): DiscoveryGenrePage = popularWithGenresVerified(
        mediaType = mediaType,
        sourceId = sourceId,
        genres = genres,
        page = page,
        releaseStatuses = releaseStatuses,
        budget = budget,
    )

    /** Compatibility path; production excludes generation keys before spending details. */
    suspend fun popularWithGenresAllocated(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        genres: List<String>,
        page: Int = 1,
        releaseStatuses: Set<DiscoveryReleaseStatus> = emptySet(),
        budget: DiscoveryVerificationBudget? = null,
        maxDetails: Int = 0,
        excludedKeys: Set<String>,
    ): DiscoveryGenrePage {
        val result = popularWithGenresAllocated(
            mediaType = mediaType,
            sourceId = sourceId,
            genres = genres,
            page = page,
            releaseStatuses = releaseStatuses,
            budget = budget,
            maxDetails = maxDetails,
        )
        return result.copy(
            items = result.items.filterNot { it.isExcludedBy(excludedKeys, mediaType) },
            pending = result.pending.filterNot { it.isExcludedBy(excludedKeys, mediaType) },
        )
    }

    /**
     * Hydrates cards already fetched from a source without asking for a page
     * again. Used to settle the previous page's pending cards when a richer
     * second wave gives the source more details allowance.
     */
    suspend fun hydrateGenres(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        items: List<DiscoveryRowItem>,
        budget: DiscoveryVerificationBudget? = null,
        maxDetails: Int = 0,
    ): DiscoveryGenrePage = DiscoveryGenrePage(
        items = items,
        verified = items.count { it.genres.isNotEmpty() },
        unattempted = items.count { it.genres.isEmpty() },
        metadataUnavailable = 0,
        transientFailures = 0,
        pending = items.filter { it.genres.isEmpty() && !it.sourceUrl.isNullOrBlank() },
        detailsUsed = 0,
    )

    /**
     * Ids that can actually answer a catalogue query for [mediaType].
     *
     * Selected discovery ids may be stubs or installed only for another media
     * type; those cannot yield cards and must not reserve a detail allowance.
     * The default trusts the caller, keeping legacy implementations working.
     */
    fun queryableSourceIds(mediaType: DiscoveryMediaType, sourceIds: List<Long>): List<Long> = sourceIds

    suspend fun latest(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        page: Int = 1,
        releaseStatuses: Set<DiscoveryReleaseStatus> = emptySet(),
    ): List<DiscoveryRowItem>

    suspend fun latestPage(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        page: Int = 1,
        releaseStatuses: Set<DiscoveryReleaseStatus> = emptySet(),
        budget: DiscoveryVerificationBudget? = null,
    ): DiscoveryBrowsePage = DiscoveryBrowsePage(latest(mediaType, sourceId, page, releaseStatuses))
}

class AppDiscoverySourceCatalog(
    private val mangaSourceManagerProvider: () -> MangaSourceManager = { Injekt.get<MangaSourceManager>() },
) : DiscoverySourceCatalog {

    private val genreHydrator = DiscoveryGenreHydrator()

    /** M2: перевод непонятых чекбоксов жанров — ленивый, кэш внутри фолбэка. */
    private val genreFallback by lazy {
        GenreTranslationFallback(
            translate = { name -> MultilingualQueryHelper.translate(name) },
            synonyms = { name -> MultilingualQueryHelper.getGenreTranslations(name) },
        )
    }

    override suspend fun popular(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        page: Int,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
    ): List<DiscoveryRowItem> =
        fetch(mediaType, sourceId, genres = null, page = page, releaseStatuses = releaseStatuses)

    override suspend fun popularPage(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        page: Int,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
        budget: DiscoveryVerificationBudget?,
    ): DiscoveryBrowsePage = popularPage(
        mediaType,
        sourceId,
        page,
        releaseStatuses,
        budget,
        excludedKeys = emptySet(),
    )

    override suspend fun popularPage(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        page: Int,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
        budget: DiscoveryVerificationBudget?,
        excludedKeys: Set<String>,
    ): DiscoveryBrowsePage = fetchPage(
        mediaType,
        sourceId,
        genres = null,
        page = page,
        releaseStatuses = releaseStatuses,
        budget = budget,
        excludedKeys = excludedKeys,
        strictPage = true,
    ).let { DiscoveryBrowsePage(it.items, it.hasNextPage) }

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
    ): DiscoveryGenrePage = popularWithGenresAllocated(
        mediaType = mediaType,
        sourceId = sourceId,
        genres = genres,
        page = page,
        releaseStatuses = releaseStatuses,
        budget = budget,
        maxDetails = budget?.remainingDetails ?: 0,
    )

    override suspend fun popularWithGenresAllocated(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        genres: List<String>,
        page: Int,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
        budget: DiscoveryVerificationBudget?,
        maxDetails: Int,
    ): DiscoveryGenrePage = popularWithGenresAllocated(
        mediaType = mediaType,
        sourceId = sourceId,
        genres = genres,
        page = page,
        releaseStatuses = releaseStatuses,
        budget = budget,
        maxDetails = maxDetails,
        excludedKeys = emptySet(),
    )

    override suspend fun popularWithGenresAllocated(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        genres: List<String>,
        page: Int,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
        budget: DiscoveryVerificationBudget?,
        maxDetails: Int,
        excludedKeys: Set<String>,
    ): DiscoveryGenrePage {
        val fetched = fetchPage(
            mediaType,
            sourceId,
            genres = genres,
            page = page,
            releaseStatuses = releaseStatuses,
            budget = budget,
            maxDetails = maxDetails,
            excludedKeys = excludedKeys,
            strictPage = true,
        )
        val queryKey = discoveryGenreQueryKey(mediaType, sourceId, genres, releaseStatuses)
        val items = fetched.items.filterNot { it.isExcludedBy(excludedKeys, mediaType) }
        logcat {
            "[DiscoverySourceCatalog] genreQuery source=$sourceId page=$page requested=$genres " +
                "filterApplied=${fetched.genreFilterApplied}"
        }
        if (budget == null) {
            return DiscoveryGenrePage(
                items = items,
                verified = items.count { it.genres.isNotEmpty() },
                unattempted = items.count { it.genres.isEmpty() },
                metadataUnavailable = 0,
                transientFailures = 0,
                hasNextPage = fetched.hasNextPage,
                pending = items.filter { it.genres.isEmpty() && !it.sourceUrl.isNullOrBlank() },
                detailsUsed = 0,
                page = page,
                queryKey = queryKey,
                genreFilterApplied = fetched.genreFilterApplied,
                genreSearchApplied = fetched.genreSearchApplied,
            )
        }
        val settled = hydrateItems(
            mediaType,
            sourceId,
            items,
            budget,
            maxDetails = (maxDetails - fetched.statusDetailsUsed).coerceAtLeast(0),
        )
        return settled.copy(
            hasNextPage = fetched.hasNextPage,
            detailsUsed = fetched.statusDetailsUsed + settled.detailsUsed,
            page = page,
            queryKey = queryKey,
            genreFilterApplied = fetched.genreFilterApplied,
            genreSearchApplied = fetched.genreSearchApplied,
        )
    }

    override suspend fun hydrateGenres(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        items: List<DiscoveryRowItem>,
        budget: DiscoveryVerificationBudget?,
        maxDetails: Int,
    ): DiscoveryGenrePage {
        if (budget == null) {
            return DiscoveryGenrePage(
                items = items,
                verified = items.count { it.genres.isNotEmpty() },
                unattempted = items.count { it.genres.isEmpty() },
                metadataUnavailable = 0,
                transientFailures = 0,
                pending = items.filter { it.genres.isEmpty() && !it.sourceUrl.isNullOrBlank() },
                detailsUsed = 0,
            )
        }
        return hydrateItems(mediaType, sourceId, items, budget, maxDetails)
    }

    private suspend fun hydrateItems(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        items: List<DiscoveryRowItem>,
        budget: DiscoveryVerificationBudget,
        maxDetails: Int,
    ): DiscoveryGenrePage {
        val byUrl = items.associateBy { it.sourceUrl }
        return hydrateDiscoveryBatchUntilSettled(items, maxDetails) { batch, allowance ->
            genreHydrator.hydrate(mediaType, sourceId, batch, budget, maxDetails = allowance) { url ->
                fetchItemGenres(mediaType, sourceId, url, byUrl[url]?.title.orEmpty())
            }
        }
    }

    private suspend fun fetchItemGenres(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        url: String,
        title: String,
    ): List<String> {
        val observed = when (mediaType) {
            DiscoveryMediaType.MANGA -> {
                val source = mangaSourceManagerProvider().getOrStub(sourceId) as? CatalogueSource
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
        return parseGenres(observed)
    }

    override fun queryableSourceIds(mediaType: DiscoveryMediaType, sourceIds: List<Long>): List<Long> =
        sourceIds.filter { id ->
            when (mediaType) {
                DiscoveryMediaType.MANGA -> mangaSourceManagerProvider().getOrStub(id) is CatalogueSource
                DiscoveryMediaType.ANIME -> Injekt.get<AnimeSourceManager>().getOrStub(id) is AnimeCatalogueSource
                DiscoveryMediaType.NOVEL -> Injekt.get<NovelSourceManager>().getOrStub(id) is NovelCatalogueSource
            }
        }

    override suspend fun latestPage(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        page: Int,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
        budget: DiscoveryVerificationBudget?,
    ): DiscoveryBrowsePage = fetchPage(
        mediaType,
        sourceId,
        genres = null,
        page = page,
        releaseStatuses = releaseStatuses,
        budget = budget,
        // Latest keeps its existing list-only status postfilter.
        maxDetails = 0,
        strictPage = true,
        latest = true,
    ).let { DiscoveryBrowsePage(it.items, it.hasNextPage) }

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
                val source = mangaSourceManagerProvider().getOrStub(sourceId) as? CatalogueSource
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
                val source = mangaSourceManagerProvider().getOrStub(sourceId) as? CatalogueSource
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

    private data class FetchedPage(
        val items: List<DiscoveryRowItem>,
        val hasNextPage: Boolean,
        val statusDetailsUsed: Int = 0,
        /** Null for a plain browse; true when the source accepted the genre filter. */
        val genreFilterApplied: Boolean? = null,
        val genreSearchApplied: Boolean = false,
    )

    private data class StatusEnrichment<T>(
        val items: List<T>,
        val detailsUsed: Int = 0,
    )

    private suspend fun fetch(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        genres: List<String>?,
        page: Int = 1,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
    ): List<DiscoveryRowItem> = fetchPage(mediaType, sourceId, genres, page, releaseStatuses).items

    /**
     * Sources may expose genres only as free-text tags or not at all. When no
     * checkbox filter matched, search the genre name instead of browsing.
     */
    private suspend fun <F, P> searchByGenreQuery(
        sourceId: Long,
        genres: List<String>?,
        lang: String,
        page: Int,
        filters: F,
        search: suspend (String, F) -> P?,
        hasItems: (P) -> Boolean,
    ): P? {
        if (genres == null) return null
        val query = discoveryGenreSearchQuery(genres, lang)
        if (query.isBlank()) return null
        val result = runCatchingUnlessCancelled { search(query, filters) }
        val err = result.exceptionOrNull()?.let { " err=${it.javaClass.simpleName}:${it.message}" }.orEmpty()
        val outcome = result.getOrNull()?.let { page -> if (hasItems(page)) "hit" else "empty" } ?: "failed"
        logcat {
            "[DiscoverySourceCatalog] genreSearchFallback source=$sourceId query=$query " +
                "page=$page result=$outcome$err"
        }
        return result.getOrNull()?.takeIf(hasItems)
    }

    private suspend fun fetchPage(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        genres: List<String>?,
        page: Int = 1,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
        budget: DiscoveryVerificationBudget? = null,
        maxDetails: Int = DETAILS_ENRICH_CAP,
        excludedKeys: Set<String> = emptySet(),
        strictPage: Boolean = false,
        latest: Boolean = false,
    ): FetchedPage = try {
        when (mediaType) {
            DiscoveryMediaType.MANGA -> {
                val source = mangaSourceManagerProvider().getOrStub(sourceId) as? CatalogueSource
                    ?: if (strictPage) {
                        throw IOException("source $sourceId is not a catalogue source")
                    } else {
                        return FetchedPage(emptyList(), hasNextPage = false)
                    }
                var filters = if (genres !=
                    null
                ) {
                    withMangaGenreFilters(source.getFilterList(), genres, source.lang)
                } else {
                    null
                }
                val genreFilterApplied = genres != null && filters != null
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
                val genreSearch = if (!genreFilterApplied) {
                    searchByGenreQuery(
                        sourceId = sourceId,
                        genres = genres,
                        lang = source.lang,
                        page = page,
                        filters = filters ?: runCatching { source.getFilterList() }.getOrElse { FilterList() },
                        search = { query, searchFilters -> source.getSearchManga(page, query, searchFilters) },
                        hasItems = { it.mangas.isNotEmpty() },
                    )
                } else {
                    null
                }
                val rawPage = when {
                    genreSearch != null -> genreSearch
                    filters != null -> runCatching { source.getSearchManga(page, "", filters) }
                        .let { if (strictPage) it.getOrThrow() else it.getForDiscoveryGenreQuery(genres) }
                    else -> runCatching {
                        if (latest && source.supportsLatest) {
                            source.getLatestUpdates(page)
                        } else {
                            source.getPopularManga(page)
                        }
                    }.let { if (strictPage) it.getOrThrow() else it.getForDiscoveryGenreQuery(genres) }
                }
                val nonEmptyPage = rawPage?.takeIf { it.mangas.isNotEmpty() }
                // Genre pagination is a continuation: falling back to page 1 would
                // re-serve the same cards and hide the real end of the query.
                val usedFallback = !strictPage && nonEmptyPage == null && genres == null && page > 1
                val pageData = nonEmptyPage ?: if (usedFallback) {
                    if (filters == null) {
                        runCatching { source.getPopularManga(1) }.getOrNull()
                    } else {
                        runCatching { source.getSearchManga(1, "", filters) }.getOrNull()
                    }
                } else {
                    rawPage
                } ?: return FetchedPage(emptyList(), hasNextPage = false)

                val mangas = if (excludedKeys.isEmpty()) {
                    pageData.mangas
                } else {
                    pageData.mangas.filterNot { manga ->
                        rowItem(manga.title, manga.thumbnail_url, source.name, 0, sourceId, manga.url)
                            .isExcludedBy(excludedKeys, mediaType)
                    }
                }
                val statusEnrichment = enrichMangasWithStatus(
                    source,
                    mangas,
                    releaseStatuses,
                    budget,
                    maxDetails,
                )
                val keptMangas = statusEnrichment.items
                    .filter { SourceStatusFilterMatcher.entryPasses(it.status, releaseStatuses) }
                if (releaseStatuses.isNotEmpty()) {
                    logcat {
                        "[DiscoverySourceCatalog] popular postfilter source=$sourceId " +
                            "kept=${keptMangas.size}/${pageData.mangas.size} statuses=$releaseStatuses"
                    }
                }
                FetchedPage(
                    items = keptMangas.mapIndexed { idx, m ->
                        rowItem(m.title, m.thumbnail_url, source.name, idx, sourceId, m.url, parseGenres(m.genre))
                    },
                    hasNextPage = pageData.hasNextPage,
                    statusDetailsUsed = statusEnrichment.detailsUsed,
                    genreFilterApplied = genreFilterApplied,
                    genreSearchApplied = genreSearch != null,
                )
            }
            DiscoveryMediaType.ANIME -> {
                val source = Injekt.get<AnimeSourceManager>().getOrStub(sourceId) as? AnimeCatalogueSource
                    ?: if (strictPage) {
                        throw IOException("source $sourceId is not a catalogue source")
                    } else {
                        return FetchedPage(emptyList(), hasNextPage = false)
                    }
                var filters = if (genres !=
                    null
                ) {
                    withAnimeGenreFilters(source.getFilterList(), genres, source.lang)
                } else {
                    null
                }
                val genreFilterApplied = genres != null && filters != null
                if (releaseStatuses.isNotEmpty()) {
                    val candidate = filters ?: source.getFilterList()
                    val applied = applyAnimeStatusFilter(candidate, releaseStatuses)
                    if (applied) filters = candidate
                    logcat {
                        "[DiscoverySourceCatalog] search statusApplied=$applied " +
                            "source=$sourceId statuses=$releaseStatuses"
                    }
                }
                val genreSearch = if (!genreFilterApplied) {
                    searchByGenreQuery(
                        sourceId = sourceId,
                        genres = genres,
                        lang = source.lang,
                        page = page,
                        filters = filters ?: runCatching { source.getFilterList() }.getOrElse { AnimeFilterList() },
                        search = { query, searchFilters -> source.getSearchAnime(page, query, searchFilters) },
                        hasItems = { it.animes.isNotEmpty() },
                    )
                } else {
                    null
                }
                val rawPage = when {
                    genreSearch != null -> genreSearch
                    filters != null -> runCatching { source.getSearchAnime(page, "", filters) }
                        .let { if (strictPage) it.getOrThrow() else it.getForDiscoveryGenreQuery(genres) }
                    else -> runCatching {
                        if (latest && source.supportsLatest) {
                            source.getLatestUpdates(page)
                        } else {
                            source.getPopularAnime(page)
                        }
                    }.let { if (strictPage) it.getOrThrow() else it.getForDiscoveryGenreQuery(genres) }
                }
                val nonEmptyPage = rawPage?.takeIf { it.animes.isNotEmpty() }
                val usedFallback = !strictPage && nonEmptyPage == null && genres == null && page > 1
                val pageData = nonEmptyPage ?: if (usedFallback) {
                    if (filters == null) {
                        runCatching { source.getPopularAnime(1) }.getOrNull()
                    } else {
                        runCatching { source.getSearchAnime(1, "", filters) }.getOrNull()
                    }
                } else {
                    rawPage
                } ?: return FetchedPage(emptyList(), hasNextPage = false)

                val animes = if (excludedKeys.isEmpty()) {
                    pageData.animes
                } else {
                    pageData.animes.filterNot { anime ->
                        rowItem(anime.title, anime.thumbnail_url, source.name, 0, sourceId, anime.url)
                            .isExcludedBy(excludedKeys, mediaType)
                    }
                }
                val statusEnrichment = enrichAnimesWithStatus(
                    source,
                    animes,
                    releaseStatuses,
                    budget,
                    maxDetails,
                )
                val keptAnimes = statusEnrichment.items
                    .filter { SourceStatusFilterMatcher.entryPasses(it.status, releaseStatuses) }
                if (releaseStatuses.isNotEmpty()) {
                    logcat {
                        "[DiscoverySourceCatalog] popular postfilter source=$sourceId " +
                            "kept=${keptAnimes.size}/${pageData.animes.size} statuses=$releaseStatuses"
                    }
                }
                FetchedPage(
                    items = keptAnimes.mapIndexed { idx, a ->
                        rowItem(a.title, a.thumbnail_url, source.name, idx, sourceId, a.url, parseGenres(a.genre))
                    },
                    hasNextPage = pageData.hasNextPage,
                    statusDetailsUsed = statusEnrichment.detailsUsed,
                    genreFilterApplied = genreFilterApplied,
                    genreSearchApplied = genreSearch != null,
                )
            }
            DiscoveryMediaType.NOVEL -> {
                val source = Injekt.get<NovelSourceManager>().getOrStub(sourceId) as? NovelCatalogueSource
                    ?: if (strictPage) {
                        throw IOException("source $sourceId is not a catalogue source")
                    } else {
                        return FetchedPage(emptyList(), hasNextPage = false)
                    }
                var filters = if (genres !=
                    null
                ) {
                    withNovelGenreFilters(source.getFilterList(), genres, source.lang)
                } else {
                    null
                }
                val genreFilterApplied = genres != null && filters != null
                if (releaseStatuses.isNotEmpty()) {
                    val candidate = filters ?: source.getFilterList()
                    val applied = applyNovelStatusFilter(candidate, releaseStatuses)
                    if (applied) filters = candidate
                    logcat {
                        "[DiscoverySourceCatalog] search statusApplied=$applied " +
                            "source=$sourceId statuses=$releaseStatuses"
                    }
                }
                val genreSearch = if (!genreFilterApplied) {
                    searchByGenreQuery(
                        sourceId = sourceId,
                        genres = genres,
                        lang = source.lang,
                        page = page,
                        filters = filters ?: runCatching { source.getFilterList() }.getOrElse { NovelFilterList() },
                        search = { query, searchFilters -> source.getSearchNovels(page, query, searchFilters) },
                        hasItems = { it.novels.isNotEmpty() },
                    )
                } else {
                    null
                }
                val rawPage = when {
                    genreSearch != null -> genreSearch
                    filters != null -> runCatching { source.getSearchNovels(page, "", filters) }
                        .let { if (strictPage) it.getOrThrow() else it.getForDiscoveryGenreQuery(genres) }
                    else -> runCatching {
                        if (latest && source.supportsLatest) {
                            source.getLatestUpdates(page)
                        } else {
                            source.getPopularNovels(page)
                        }
                    }.let { if (strictPage) it.getOrThrow() else it.getForDiscoveryGenreQuery(genres) }
                }
                val nonEmptyPage = rawPage?.takeIf { it.novels.isNotEmpty() }
                val usedFallback = !strictPage && nonEmptyPage == null && genres == null && page > 1
                val pageData = nonEmptyPage ?: if (usedFallback) {
                    if (filters == null) {
                        runCatching { source.getPopularNovels(1) }.getOrNull()
                    } else {
                        runCatching { source.getSearchNovels(1, "", filters) }.getOrNull()
                    }
                } else {
                    rawPage
                } ?: return FetchedPage(emptyList(), hasNextPage = false)

                val novels = if (excludedKeys.isEmpty()) {
                    pageData.novels
                } else {
                    pageData.novels.filterNot { novel ->
                        rowItem(novel.title, novel.thumbnail_url, source.name, 0, sourceId, novel.url)
                            .isExcludedBy(excludedKeys, mediaType)
                    }
                }
                val statusEnrichment = enrichNovelsWithStatus(
                    source,
                    novels,
                    releaseStatuses,
                    budget,
                    maxDetails,
                )
                val keptNovels = statusEnrichment.items
                    .filter { SourceStatusFilterMatcher.entryPasses(it.status, releaseStatuses) }
                if (releaseStatuses.isNotEmpty()) {
                    logcat {
                        "[DiscoverySourceCatalog] popular postfilter source=$sourceId " +
                            "kept=${keptNovels.size}/${pageData.novels.size} statuses=$releaseStatuses"
                    }
                }
                FetchedPage(
                    items = keptNovels.mapIndexed { idx, n ->
                        rowItem(n.title, n.thumbnail_url, source.name, idx, sourceId, n.url, parseGenres(n.genre))
                    },
                    hasNextPage = pageData.hasNextPage,
                    statusDetailsUsed = statusEnrichment.detailsUsed,
                    genreFilterApplied = genreFilterApplied,
                    genreSearchApplied = genreSearch != null,
                )
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

    internal suspend fun withMangaGenreFilters(
        filters: FilterList,
        genres: List<String>,
        lang: String = "en",
    ): FilterList? {
        val groups = filters.filterIsInstance<Filter.Group<*>>().filter { isGenreGroupName(it.name) }
        val topBoxes = filters.filterIsInstance<Filter.CheckBox>()
        val topTris = filters.filterIsInstance<Filter.TriState>()
        if (groups.isNotEmpty() || topBoxes.isNotEmpty() || topTris.isNotEmpty()) {
            // LibGroup has distinct GenreList and TagList; keep the original plugin instances and IDs.
            val options = groups.flatMap { it.state } + topBoxes + topTris
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
            if (matched.isNotEmpty()) return filters
        }
        val selects = filters.filterIsInstance<Filter.Select<*>>().filter { isGenreGroupName(it.name) }
        for (select in selects) {
            val names = select.values.map(::optionName)
            val matched = genreFallback.selectSourceGenres(genres, names)
            val targetName = matched.firstOrNull() ?: continue
            val idx = names.indexOfFirst { it == targetName }
            if (idx >= 0) {
                select.state = idx
                return filters
            }
        }
        val textFilter = filters.filterIsInstance<Filter.Text>().firstOrNull { isGenreGroupName(it.name) }
            ?: groups.flatMap { it.state }.filterIsInstance<Filter.Text>().firstOrNull()
        if (textFilter != null) {
            val query = discoveryGenreSearchQuery(genres, lang)
            if (query.isNotBlank()) {
                textFilter.state = query
                return filters
            }
        }
        return null
    }

    private suspend fun withAnimeGenreFilters(
        filters: AnimeFilterList,
        genres: List<String>,
        lang: String = "en",
    ): AnimeFilterList? {
        val groups = filters.filterIsInstance<AnimeFilter.Group<*>>().filter { isGenreGroupName(it.name) }
        val topBoxes = filters.filterIsInstance<AnimeFilter.CheckBox>()
        val topTris = filters.filterIsInstance<AnimeFilter.TriState>()
        if (groups.isNotEmpty() || topBoxes.isNotEmpty() || topTris.isNotEmpty()) {
            val options = groups.flatMap { it.state } + topBoxes + topTris
            val boxes = options.filterIsInstance<AnimeFilter.CheckBox>()
            val tris = options.filterIsInstance<AnimeFilter.TriState>()
            val allOptions = (boxes.map { it.name } + tris.map { it.name }).distinct()
            val matched = genreFallback.selectSourceGenres(genres, allOptions)
            boxes.filter { it.name in matched }.forEach { it.state = true }
            tris.filter { it.name in matched }.forEach { it.state = AnimeFilter.TriState.STATE_INCLUDE }
            if (matched.isNotEmpty()) return filters
        }
        val selects = filters.filterIsInstance<AnimeFilter.Select<*>>().filter { isGenreGroupName(it.name) }
        for (select in selects) {
            val names = select.values.map(::optionName)
            val matched = genreFallback.selectSourceGenres(genres, names)
            val targetName = matched.firstOrNull() ?: continue
            val idx = names.indexOfFirst { it == targetName }
            if (idx >= 0) {
                select.state = idx
                return filters
            }
        }
        val textFilter = filters.filterIsInstance<AnimeFilter.Text>().firstOrNull { isGenreGroupName(it.name) }
            ?: groups.flatMap { it.state }.filterIsInstance<AnimeFilter.Text>().firstOrNull()
        if (textFilter != null) {
            val query = discoveryGenreSearchQuery(genres, lang)
            if (query.isNotBlank()) {
                textFilter.state = query
                return filters
            }
        }
        return null
    }

    private suspend fun withNovelGenreFilters(
        filters: NovelFilterList,
        genres: List<String>,
        lang: String = "en",
    ): NovelFilterList? {
        val groups = filters.filterIsInstance<NovelFilter.Group<*>>().filter { isGenreGroupName(it.name) }
        val topBoxes = filters.filterIsInstance<NovelFilter.CheckBox>()
        val topTris = filters.filterIsInstance<NovelFilter.TriState>()
        if (groups.isNotEmpty() || topBoxes.isNotEmpty() || topTris.isNotEmpty()) {
            val options = groups.flatMap { it.state } + topBoxes + topTris
            val boxes = options.filterIsInstance<NovelFilter.CheckBox>()
            val tris = options.filterIsInstance<NovelFilter.TriState>()
            val allOptions = (boxes.map { it.name } + tris.map { it.name }).distinct()
            val matched = genreFallback.selectSourceGenres(genres, allOptions)
            boxes.filter { it.name in matched }.forEach { it.state = true }
            tris.filter { it.name in matched }.forEach { it.state = NovelFilter.TriState.STATE_INCLUDE }
            if (matched.isNotEmpty()) return filters
        }
        val selects = filters.filterIsInstance<NovelFilter.Select<*>>().filter { isGenreGroupName(it.name) }
        for (select in selects) {
            val names = select.values.map(::optionName)
            val matched = genreFallback.selectSourceGenres(genres, names)
            val targetName = matched.firstOrNull() ?: continue
            val idx = names.indexOfFirst { it == targetName }
            if (idx >= 0) {
                select.state = idx
                return filters
            }
        }
        val textFilter = filters.filterIsInstance<NovelFilter.Text>().firstOrNull { isGenreGroupName(it.name) }
            ?: groups.flatMap { it.state }.filterIsInstance<NovelFilter.Text>().firstOrNull()
        if (textFilter != null) {
            val query = discoveryGenreSearchQuery(genres, lang)
            if (query.isNotBlank()) {
                textFilter.state = query
                return filters
            }
        }
        return null
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
        budget: DiscoveryVerificationBudget?,
        maxDetails: Int,
    ): StatusEnrichment<SManga> = enrichItemsWithStatus(
        mangas,
        releaseStatuses,
        budget,
        maxDetails,
        status = { it.status },
    ) { fetchDiscoveryMangaDetails(source, it) }

    private suspend fun enrichAnimesWithStatus(
        source: AnimeCatalogueSource,
        animes: List<SAnime>,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
        budget: DiscoveryVerificationBudget?,
        maxDetails: Int,
    ): StatusEnrichment<SAnime> = enrichItemsWithStatus(
        animes,
        releaseStatuses,
        budget,
        maxDetails,
        status = { it.status },
    ) { source.getAnimeDetails(it) }

    private suspend fun enrichNovelsWithStatus(
        source: NovelCatalogueSource,
        novels: List<SNovel>,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
        budget: DiscoveryVerificationBudget?,
        maxDetails: Int,
    ): StatusEnrichment<SNovel> = enrichItemsWithStatus(
        novels,
        releaseStatuses,
        budget,
        maxDetails,
        status = { it.status },
    ) { source.getNovelDetails(it) }

    private suspend fun <T> enrichItemsWithStatus(
        items: List<T>,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
        budget: DiscoveryVerificationBudget?,
        maxDetails: Int,
        status: (T) -> Int,
        fetchDetails: suspend (T) -> T,
    ): StatusEnrichment<T> {
        if (releaseStatuses.isEmpty()) return StatusEnrichment(items)
        val (unknown, known) = items.partition { SourceStatusFilterMatcher.fromEntryStatus(status(it)) == null }
        if (unknown.isEmpty()) return StatusEnrichment(items)
        // Все без статуса — иначе известные уже отфильтрованы корректно, детали не нужны.
        val limit = minOf(DETAILS_ENRICH_CAP, maxDetails.coerceAtLeast(0))
        var charged = 0
        val enriched = unknown.take(limit).map { item ->
            runCatchingUnlessCancelled {
                if (budget == null) {
                    fetchDetails(item)
                } else {
                    budget.detailPermits.withPermit {
                        val remaining = budget.remainingMs()
                        if (remaining <= 0L || !budget.tryAcquireDetail()) return@withPermit null
                        charged++
                        withTimeoutOrNull(remaining) { fetchDetails(item) }
                    }
                }
            }.getOrNull() ?: item
        }
        return StatusEnrichment(known + enriched + unknown.drop(limit), detailsUsed = charged)
    }

    private companion object {
        const val DETAILS_ENRICH_CAP = 24
    }
}

internal val GENRE_GROUP_ALIASES = listOf(
    "genre", "жанр", "tag", "тег", "categor", "категор", "género", "genero", "ジャンル",
    "theme", "тем", "метк", "demograph", "демограф", "format", "формат",
)

internal fun isGenreGroupName(name: String): Boolean =
    GENRE_GROUP_ALIASES.any { name.contains(it, ignoreCase = true) }

/**
 * Text query for sources that cannot express a genre as a checkbox filter.
 * English/all sources search the canonical genre, Russian sources search the
 * localized synonym that their catalogue exposes.
 */
internal fun discoveryGenreSearchQuery(genres: List<String>, lang: String): String {
    val chosen = if (lang.startsWith("ru", ignoreCase = true)) {
        genres.map { genre -> MultilingualQueryHelper.getGenreTranslations(genre).firstOrNull() ?: genre }
    } else {
        genres
    }
    return chosen.joinToString(" ").trim()
}

/**
 * Identity of a source continuation: changing genres or statuses starts a new
 * query sequence, so a page index from the previous sequence must not be reused.
 */
internal fun discoveryGenreQueryKey(
    media: DiscoveryMediaType,
    sourceId: Long,
    genres: List<String>,
    releaseStatuses: Set<DiscoveryReleaseStatus>,
): String = buildString {
    append(media.name).append('|').append(sourceId).append('|')
    append(genres.map { it.lowercase() }.sorted().joinToString(",")).append('|')
    append(releaseStatuses.map { it.name }.sorted().joinToString(","))
}
