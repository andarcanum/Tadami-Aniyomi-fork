package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.data.suggestions.SuggestionCoordinator
import eu.kanade.tachiyomi.data.suggestions.SuggestionSeed
import eu.kanade.tachiyomi.data.suggestions.SuggestionTitleResolver
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import eu.kanade.tachiyomi.data.suggestions.util.bestMatchScoreFor
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle
import java.io.IOException

/** Кандидаты LIKE-сида: resolver дополняет сырой тайтл оригиналом из описания и нормализованными вариантами. */
internal fun likeSeedCandidateTitles(seed: DiscoverySeedInput): List<String> =
    (SuggestionTitleResolver.resolveCandidates(seed.title, seed.description) + seed.altTitles)
        .filter { it.isNotBlank() }
        .distinct()

/**
 * Ряд «Похоже на X»: переиспользует существующий [SuggestionCoordinator]
 * (AniList / MAL-Jikan / MangaUpdates / NovelUpdates) на каждый выбранный сид.
 */
class DiscoveryLikeRowBuilder(
    private val suggestionCoordinator: SuggestionCoordinator,
) : DiscoveryRowBuilder {

    override val rowType = DiscoveryRowType.LIKE

    override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
        val mediaType = when (context.mediaType) {
            DiscoveryMediaType.ANIME -> SuggestionMediaType.ANIME
            DiscoveryMediaType.MANGA -> SuggestionMediaType.MANGA
            DiscoveryMediaType.NOVEL -> SuggestionMediaType.NOVEL
        }
        var fullyFailedSeeds = 0
        val perSeed = context.seeds.map { seed ->
            val suggestionSeed = SuggestionSeed(
                mediaType = mediaType,
                primaryTitle = seed.title,
                candidateTitles = likeSeedCandidateTitles(seed),
                description = seed.description,
                author = seed.author,
                genres = seed.genres.ifEmpty { null },
            )
            val result = suggestionCoordinator.fetchSuggestions(
                suggestionSeed,
                limit = 25,
                releaseStatuses = context.releaseStatuses,
            )
            if (result.items.isEmpty() && result.attemptedSources > 0 &&
                result.failedSources == result.attemptedSources
            ) {
                fullyFailedSeeds++
            }
            result.items.map { item ->
                DiscoveryRowItem(
                    title = item.title,
                    cleanTitle = normalizeDiscoveryTitle(item.title),
                    coverUrl = item.thumbnailUrl,
                    // Локализованный текст «Похоже на „X“» композится на рендере из seedTitle.
                    reason = null,
                    seedTitle = seed.title,
                    provider = item.providerName,
                    score = item.bestMatchScoreFor(suggestionSeed).toDouble(),
                )
            }
        }
        val merged = mergeSeedResults(perSeed)
        if (merged.isEmpty() && context.seeds.isNotEmpty() && fullyFailedSeeds == context.seeds.size) {
            throw IOException("all suggestion providers failed for every seed")
        }
        return merged
    }
}

/**
 * Ряд «Тренды и сезон»: агрегатор трендов (Shikimori, MangaDex, Jikan, AniList)
 * и свежие обновления установленных источников.
 */
class DiscoveryTrendRowBuilder(
    private val trending: DiscoveryTrendingSource,
    private val catalog: DiscoverySourceCatalog,
    private val seasonProvider: () -> TrendSeason,
    private val sortProvider: () -> TrendSort,
) : DiscoveryRowBuilder {

    override val rowType = DiscoveryRowType.TREND

    override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
        val effectiveSort = when (context.mediaType) {
            DiscoveryMediaType.ANIME -> sortProvider()
            // Для манги и новелл запрашиваем реальные тренды (TRENDING_DESC),
            // чтобы не дублировать статический all-time топ ряда TASTE
            DiscoveryMediaType.MANGA, DiscoveryMediaType.NOVEL -> TrendSort.TRENDING
        }
        // C1: приоритет — топ-взвешенный источник библиотеки, иначе lastUsed/fallback.
        val preferredSourceId = context.sourceIds.firstOrNull() ?: context.sourceId
        // B2: жанры из tag-blacklist не проходят в ряд (best-effort: без жанров — без фильтра).
        val expandedBlacklist = expandGenreSet(context.blacklistedTags.toList())

        val fromSource = if (context.mediaType == DiscoveryMediaType.NOVEL && preferredSourceId > 0) {
            runCatchingUnlessCancelled {
                catalog.latest(
                    context.mediaType,
                    preferredSourceId,
                    page = context.pageOffset,
                    releaseStatuses = context.releaseStatuses,
                )
            }.getOrNull().orEmpty().map { item ->
                item.copy(
                    reason = "source",
                    score = 0.0,
                )
            }
        } else {
            emptyList()
        }

        val fromTrending = runCatchingUnlessCancelled {
            trending.fetch(
                mediaType = context.mediaType,
                season = seasonProvider(),
                sort = effectiveSort,
                page = context.pageOffset,
                releaseStatuses = context.releaseStatuses,
            )
        }.getOrNull().orEmpty()
            // B2: жанры из tag-blacklist не проходят в ряд — эвристика GenreMatcher
            // режет любые языки/формы (экшен вырежет и «Action», и «aksiyon»).
            .filterNot { item -> matchesAnyGenre(item.genres, expandedBlacklist) }
            // V3: обязательные жанры — только тайтлы с ними (best-effort:
            // пустой результат → без фильтра, лента не голодает).
            .let { items -> applyRequiredGenres(items, context.requiredGenres) }
            .map { item ->
                DiscoveryRowItem(
                    title = item.title,
                    cleanTitle = item.cleanTitle,
                    altCleanTitles = listOfNotNull(
                        item.altTitle?.let {
                            normalizeDiscoveryTitle(it)
                        },
                    ).filter { it.isNotBlank() },
                    coverUrl = item.coverUrl,
                    // Payload "current"/"next"/"source"/null — шаблон строки выбирается на рендере.
                    reason = item.seasonLabel,
                    seedTitle = null,
                    provider = item.provider,
                    score = 0.0,
                    genres = item.genres,
                )
            }

        if (context.releaseStatuses.isNotEmpty()) {
            logcat {
                "[Discovery] trend row media=${context.mediaType} trending=${fromTrending.size} " +
                    "sourceLatest=${fromSource.size} statuses=${context.releaseStatuses}"
            }
        }

        // V3: приоритетные жанры — буст не применим к TREND-ряду: скор = 0.0
        // (тренды не ранжируются по жанрам), а reason хранит seasonLabel, не жанры.
        // Приоритет работает только в TASTE-ряду через boostedTasteScore.

        if (context.mediaType == DiscoveryMediaType.NOVEL) {
            val combinedNovels = (fromSource + fromTrending).distinctBy { it.cleanTitle }
            if (combinedNovels.isNotEmpty()) {
                return combinedNovels
            }
        } else {
            if (fromTrending.isNotEmpty()) {
                return fromTrending
            }

            // Фолбэк при недоступности трендов:
            // Запрашиваем «Свежее» (latest updates) из активного/установленного источника пользователя!
            if (preferredSourceId > 0) {
                val fallbackSource = runCatchingUnlessCancelled {
                    catalog.latest(
                        context.mediaType,
                        preferredSourceId,
                        page = context.pageOffset,
                        releaseStatuses = context.releaseStatuses,
                    )
                }.getOrNull().orEmpty().map { item ->
                    item.copy(
                        reason = "source",
                        score = 0.0,
                    )
                }
                if (fallbackSource.isNotEmpty()) {
                    return fallbackSource
                }
            }
        }

        return emptyList()
    }
}

/**
 * Ряд «Твой вкус»: жанровый профиль библиотеки/истории → тренды по жанрам
 * + жанровый фильтр каталога выбранного источника; скор = совпадение жанров.
 */
class DiscoveryTasteRowBuilder(
    private val trending: DiscoveryTrendingSource,
    private val catalog: DiscoverySourceCatalog,
    private val sortProvider: () -> TrendSort,
    // «Только плагины»: false — внешние жанровые провайдеры не опрашиваются,
    // ряд строится исключительно из каталога primary-источника.
    private val includeExternal: Boolean = true,
) : DiscoveryRowBuilder {

    override val rowType = DiscoveryRowType.TASTE

    override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
        val profile = context.tasteProfile
        if (profile.isEmpty()) return emptyList()
        // B2: заблэклиженные жанры выключаются из профиля ДО запросов — они не
        // должны попадать ни в genre-запросы, ни в скор/обоснования.
        val expandedBlacklist = expandGenreSet(context.blacklistedTags.toList())
        val activeProfile = profile.filterNot { (genre, _) -> matchesAnyGenre(listOf(genre), expandedBlacklist) }
        if (activeProfile.isEmpty()) return emptyList()
        val genreNames = activeProfile.take(6).map { it.first }
        // V3: у source-выдачи нет жанров на айтеме (клиентский фильтр невозможен),
        // поэтому обязательные жанры прокидываем в запрос серверно.
        val sourceGenreNames = if (context.requiredGenres.isNotEmpty()) {
            context.requiredGenres.toList()
        } else {
            genreNames
        }
        val trendingResult = if (includeExternal) {
            runCatchingUnlessCancelled {
                trending.fetchByGenres(
                    context.mediaType,
                    genreNames,
                    sortProvider(),
                    page = context.pageOffset,
                    releaseStatuses = context.releaseStatuses,
                )
            }
        } else {
            null
        }
        val sourceResult = if (context.sourceId > 0) {
            runCatchingUnlessCancelled {
                catalog.popularWithGenres(
                    context.mediaType,
                    context.sourceId,
                    sourceGenreNames,
                    page = context.pageOffset,
                    releaseStatuses = context.releaseStatuses,
                )
            }
        } else {
            null
        }
        val fromTrending = trendingResult?.getOrNull().orEmpty()
            // best-effort: провайдеры без жанров в выдаче (source-latest) не фильтруются
            .filterNot { item -> matchesAnyGenre(item.genres, expandedBlacklist) }
            // V3: обязательные жанры — best-effort (пустой результат → без фильтра).
            .let { items -> applyRequiredGenres(items, context.requiredGenres) }
            .map { item ->
                DiscoveryRowItem(
                    title = item.title,
                    cleanTitle = item.cleanTitle,
                    altCleanTitles = listOfNotNull(
                        item.altTitle?.let {
                            normalizeDiscoveryTitle(it)
                        },
                    ).filter { it.isNotBlank() },
                    coverUrl = item.coverUrl,
                    reason = matchedGenres(item.genres, activeProfile).joinToString(", "),
                    seedTitle = null,
                    provider = item.provider,
                    // V3: приоритетные жанры — буст скора (×1.5), не отсекают.
                    score = boostedTasteScore(item.genres, activeProfile, context.priorityGenres),
                    genres = item.genres,
                )
            }
        val fromSource = sourceResult?.getOrNull().orEmpty().map { item ->
            item.copy(
                reason = genreNames.take(2).joinToString(", "),
                score = 0.5 + item.score * 0.1,
            )
        }
        val combined = mergeNormalized(fromTrending, fromSource)
        val anyFailed = (trendingResult?.isFailure ?: false) || (sourceResult?.isFailure ?: false)
        if (combined.isEmpty() && anyFailed) {
            throw IOException("taste sources failed without results")
        }
        return filterGenreClustering(combined, maxPerGenre = 6).take(50)
    }
}

/**
 * Ряд «Источник» (C1): микс ⅘ Latest + ⅕ Popular топ-N источников по весу
 * библиотеки (число тайтлов пользователя), round-robin интерлив; на ручном
 * рефреше порядок источников ротируется, а latest шагается страницей.
 * Единственный источник остаётся в силе — квота растягивается на весь ряд;
 * при скудном latest квота добирается popular'ом.
 */
class DiscoverySourceRowBuilder(
    private val catalog: DiscoverySourceCatalog,
    private val maxSources: Int = 3,
    private val rowCap: Int = 20,
) : DiscoveryRowBuilder {

    override val rowType = DiscoveryRowType.SOURCE

    override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
        val ids = context.sourceIds.filter { it > 0 }.take(maxSources)
            .ifEmpty { listOf(context.sourceId).filter { it > 0 } }
        if (ids.isEmpty()) return emptyList()
        val rotation = (context.pageOffset - 1).coerceAtLeast(0) % ids.size
        val ordered = ids.drop(rotation) + ids.take(rotation)
        val perSource = (rowCap + ordered.size - 1) / ordered.size
        // Popular-витрина первой страницы почти статична, latest-обновления ротируются
        // каждый час: основа ряда — Latest, Popular остаётся якорем качества (~20%).
        val latestQuota = perSource - perSource / 5
        var failures = 0
        val parts = ordered.map { id ->
            val latestResult = runCatchingUnlessCancelled {
                catalog.latest(
                    context.mediaType,
                    id,
                    page = context.pageOffset,
                    releaseStatuses = context.releaseStatuses,
                )
            }
            val latestItems = latestResult.getOrDefault(emptyList()).take(latestQuota)
            val latestTitles = latestItems.mapTo(HashSet()) { it.cleanTitle }
            val popularResult = runCatchingUnlessCancelled {
                catalog.popular(
                    context.mediaType,
                    id,
                    page = context.pageOffset,
                    releaseStatuses = context.releaseStatuses,
                )
            }
            // Источник считается провалившимся, только если popular упал И latest ничего
            // не дал — иначе ряд честно строится из реального контента.
            if (popularResult.isFailure && latestItems.isEmpty()) failures++
            val popularItems = popularResult.getOrDefault(emptyList())
                .filterNot { it.cleanTitle in latestTitles }
                .take(perSource - latestItems.size)
            latestItems + popularItems
        }
        // Все источники упали — ряд помечается failed (кэш не затирается,
        // баннер «часть подборок не обновилась» честный), как в LIKE/TASTE.
        if (failures == ordered.size) {
            throw IOException("all source catalog fetches failed")
        }
        return interleaveSourceParts(parts, rowCap)
    }
}

/** Round-robin по частям (источникам): топ каждого источника виден сразу. */
internal fun interleaveSourceParts(
    parts: List<List<DiscoveryRowItem>>,
    cap: Int = 20,
): List<DiscoveryRowItem> {
    val queues = parts.filter { it.isNotEmpty() }.map { ArrayDeque(it) }
    val out = mutableListOf<DiscoveryRowItem>()
    var progressed = true
    while (out.size < cap && progressed) {
        progressed = false
        for (queue in queues) {
            if (out.size >= cap) break
            if (queue.isNotEmpty()) {
                out += queue.removeFirst()
                progressed = true
            }
        }
    }
    return out
}
