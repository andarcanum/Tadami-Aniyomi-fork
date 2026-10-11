package eu.kanade.tachiyomi.data.discovery

import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle

data class DiscoveryMixQuotas(
    val similar: Int = 10,
    val taste: Int = 8,
    val fresh: Int = 6,
    val source: Int = 6,
) {
    val total: Int get() = similar + taste + fresh + source
}

/** runCatching, не глотающий отмену корутины: отмена = отмена, а не «пустой ряд». */
internal inline fun <T> runCatchingUnlessCancelled(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}

/**
 * Смешанный поток: round-robin по квотам сигналов (LIKE → TASTE → TREND → SOURCE),
 * внутри сигнала — порядок ряда (скор). Квота пустого сигнала перераспределяется
 * автоматически (round-robin просто пропускает пустой пул), остаток до [total]
 * добивается по убыванию [rrf]-скоров (ранговая fusion — шкалы сигналов несопоставимы),
 * а при rrf = null — по сырому скору (прежнее поведение).
 */
internal fun interleaveMix(
    rows: Map<DiscoveryRowType, List<DiscoveryRowItem>>,
    quotas: DiscoveryMixQuotas = DiscoveryMixQuotas(),
    total: Int = quotas.total,
    rrf: Map<String, Double>? = null,
): List<DiscoveryRowItem> {
    val quotaOf = mapOf(
        DiscoveryRowType.LIKE to quotas.similar,
        DiscoveryRowType.TASTE to quotas.taste,
        DiscoveryRowType.TREND to quotas.fresh,
        DiscoveryRowType.SOURCE to quotas.source,
    )
    val pools = DiscoveryRowType.entries.associateWith { type ->
        ArrayDeque(rows[type].orEmpty())
    }
    val out = mutableListOf<DiscoveryRowItem>()
    val seen = mutableSetOf<String>()
    val taken = mutableMapOf<DiscoveryRowType, Int>()
    var progressed = true
    while (out.size < total && progressed) {
        progressed = false
        for (type in DiscoveryRowType.entries) {
            if (out.size >= total) break
            if ((taken[type] ?: 0) >= (quotaOf[type] ?: 0)) continue
            val pool = pools[type] ?: continue
            var candidate: DiscoveryRowItem? = null
            while (pool.isNotEmpty()) {
                val next = pool.removeFirst()
                if (next.cleanTitle !in seen) {
                    candidate = next
                    break
                }
            }
            if (candidate == null) continue
            out += candidate
            seen += candidate.cleanTitle
            taken[type] = (taken[type] ?: 0) + 1
            progressed = true
        }
    }
    if (out.size < total) {
        pools.values.flatten()
            .filterNot { it.cleanTitle in seen }
            .distinctBy { it.cleanTitle }
            .sortedByDescending { rrf?.get(it.cleanTitle) ?: it.score }
            .take(total - out.size)
            .forEach { item ->
                out += item
                seen += item.cleanTitle
            }
    }
    return out
}

/** Стандартная константа Reciprocal Rank Fusion. */
internal const val RRF_K = 60

/**
 * RRF-скоры для fill-фазы микса: Σ 1/(k + rank) по всем рядам, где встречается
 * cleanTitle. Ранговая основа делает несопоставимые шкалы сигналов
 * (LIKE 0..100+, TASTE — сумма жанровых весов, TREND 0.0, SOURCE ~1.0) сравнимыми.
 */
internal fun rrfScores(
    rows: Map<DiscoveryRowType, List<DiscoveryRowItem>>,
    k: Int = RRF_K,
): Map<String, Double> {
    val out = mutableMapOf<String, Double>()
    rows.forEach { (_, items) ->
        items.forEachIndexed { rank, item ->
            out[item.cleanTitle] = (out[item.cleanTitle] ?: 0.0) + 1.0 / (k + rank)
        }
    }
    return out
}

/**
 * Мердж двух списков с min-max нормализацией каждого (паттерн TASTE-ряда:
 * trending-жанры и source-каталог имеют разные шкалы). Вырожденный список
 * (0–1 элемент или равные скоры) → нейтральные 0.5, а не вершина выдачи.
 */
internal fun mergeNormalized(
    a: List<DiscoveryRowItem>,
    b: List<DiscoveryRowItem>,
): List<DiscoveryRowItem> {
    fun normalized(list: List<DiscoveryRowItem>): List<Pair<DiscoveryRowItem, Double>> {
        if (list.size < 2) return list.map { it to 0.5 }
        val max = list.maxOf { it.score }
        val min = list.minOf { it.score }
        if (max == min) return list.map { it to 0.5 }
        return list.map { it to (it.score - min) / (max - min) }
    }
    return (normalized(a) + normalized(b))
        .sortedByDescending { it.second }
        .map { it.first }
}

/**
 * Комбинаторика пересечений: cleanTitle, рекомендованный k разными сидами,
 * получает score = maxScore * (1 + overlapMultiplier * (k - 1)); в reason-payload
 * (seedTitle) остаются до 2 сид-тайтлов. Из каждого сида берётся не больше
 * [perSeedCap] карточек.
 */
internal fun mergeSeedResults(
    perSeed: List<List<DiscoveryRowItem>>,
    perSeedCap: Int = 15,
    overlapMultiplier: Double = 0.5,
): List<DiscoveryRowItem> {
    class Acc(val template: DiscoveryRowItem) {
        var maxScore: Double = template.score
        val seeds = linkedSetOf<String>()
    }

    val byTitle = LinkedHashMap<String, Acc>()
    perSeed.forEach { seedItems ->
        seedItems.take(perSeedCap).forEach { item ->
            val seedTitle = item.seedTitle ?: return@forEach
            val acc = byTitle.getOrPut(item.cleanTitle) { Acc(item) }
            acc.maxScore = maxOf(acc.maxScore, item.score)
            acc.seeds += seedTitle
        }
    }
    return byTitle.values
        .map { acc ->
            val k = acc.seeds.size
            acc.template.copy(
                score = acc.maxScore * (1.0 + overlapMultiplier * (k - 1)),
                seedTitle = acc.seeds.take(2).joinToString(", "),
            )
        }
        .sortedByDescending { it.score }
}

/** Вклад дропнутого тайтла в каждый его жанр вместо положительного freshness. */
internal const val DROPPED_GENRE_PENALTY = 0.5

/** Категория жанра: фундаментальные вкусы живут годами, трендовые приедаются. */
internal fun genreDecayDays(genre: String): Double {
    val g = genre.trim().lowercase()
    return when (g) {
        in FUNDAMENTAL_GENRES -> 180.0
        in TRENDY_GENRES -> 20.0
        else -> 30.0
    }
}

private val FUNDAMENTAL_GENRES = setOf(
    "science fiction", "sci-fi", "фантастика",
    "psychological", "психологическое", "психология",
    "thriller", "триллер",
    "mystery", "детектив",
    "drama", "драма",
)

private val TRENDY_GENRES = setOf(
    "isekai",
    "исекай",
    "harem",
    "гарем",
    "slice of life",
    "повседневность",
)

/**
 * Жанровый профиль: топ-[topN] жанров библиотеки/истории за [windowDays] дней,
 * вес жанра = сумма freshness по тайтлам (freshness = 1 / (1 + возраст_в_днях /
 * [genreDecayDays]) — полураспад на каждый жанр свой).
 * Дропнутые тайтлы вычитают [DROPPED_GENRE_PENALTY] из каждого своего жанра;
 * жанры с неположительным итогом из профиля исключаются.
 */
internal fun buildTasteProfile(
    candidates: List<DiscoverySeedInput>,
    nowMs: Long = System.currentTimeMillis(),
    topN: Int = 6,
    windowDays: Long = 90,
): List<Pair<String, Double>> {
    val windowStart = nowMs - windowDays * DAY_MS
    val weights = mutableMapOf<String, Double>()
    candidates.forEach { candidate ->
        val interaction = (candidate.lastInteraction ?: candidate.dateAdded)
        if (interaction < windowStart) return@forEach
        val ageDays = ((nowMs - interaction) / DAY_MS).coerceAtLeast(0)
        candidate.genres.forEach { genre ->
            val freshness = 1.0 / (1.0 + ageDays / genreDecayDays(genre))
            val contribution = if (candidate.isDropped) -DROPPED_GENRE_PENALTY else freshness
            weights[genre] = (weights[genre] ?: 0.0) + contribution
        }
    }
    return weights.entries
        .filter { it.value > 0.0 }
        .sortedByDescending { it.value }
        .take(topN)
        .map { it.key to it.value }
}

/**
 * Lowercase-множество имён жанров плюс RU↔EN переводы (карта
 * [eu.kanade.tachiyomi.data.suggestions.MultilingualQueryHelper.getGenreTranslations]):
 * профиль вкуса строится из жанров библиотеки (часто русских), а теги внешних
 * провайдеров — английские.
 */
internal fun expandGenreSet(genres: List<String>): Set<String> {
    val out = mutableSetOf<String>()
    genres.forEach { genre ->
        val key = genre.trim().lowercase()
        if (key.isEmpty()) return@forEach
        out += key
        eu.kanade.tachiyomi.data.suggestions.MultilingualQueryHelper.getGenreTranslations(genre).forEach { variant ->
            val v = variant.trim().lowercase()
            if (v.isNotEmpty()) out += v
        }
    }
    return out
}

private fun genreVariants(genre: String): Set<String> = expandGenreSet(listOf(genre))

/** Веса профиля, развёрнутые по всем языковым вариантам каждого жанра. */
internal fun expandedTasteWeights(profile: List<Pair<String, Double>>): Map<String, Double> {
    val map = mutableMapOf<String, Double>()
    profile.forEach { (genre, weight) ->
        genreVariants(genre).forEach { variant ->
            map[variant] = maxOf(map[variant] ?: 0.0, weight)
        }
    }
    return map
}

/** Скор кандидата по профилю вкуса: сумма весов совпавших жанров с учётом RU↔EN переводов. */
internal fun tasteScore(itemGenres: List<String>, profile: List<Pair<String, Double>>): Double {
    val weights = expandedTasteWeights(profile)
    return itemGenres.mapTo(HashSet()) { it.trim().lowercase() }
        .sumOf { weights[it] ?: 0.0 }
}

/**
 * Жанры кандидата, совпавшие с профилем (топ-2 для обоснования).
 * Spelling — из профиля (язык библиотеки пользователя), порядок — по жанрам кандидата.
 */
internal fun matchedGenres(itemGenres: List<String>, profile: List<Pair<String, Double>>): List<String> {
    return itemGenres.mapNotNull { itemGenre ->
        val key = itemGenre.trim().lowercase()
        if (key.isEmpty()) return@mapNotNull null
        profile.firstOrNull { (genre, _) -> key in genreVariants(genre) }?.first
    }.distinct().take(2)
}

private const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * Кросс-рядовой дедуп по cleanTitle: приоритет = [DiscoveryRowType.ordinal]
 * (LIKE > TASTE > TREND > SOURCE). Упавший ряд сохраняет устаревший кэш, и тот же
 * тайтл может появиться в свежем ряду другого типа — оставляем копию приоритетного ряда.
 * Сортировка стабильна: порядок position внутри ряда сохраняется.
 */
internal fun dedupeCrossRow(
    items: List<tachiyomi.domain.discovery.model.DiscoverySuggestion>,
): List<tachiyomi.domain.discovery.model.DiscoverySuggestion> = items
    .sortedBy { it.rowType.ordinal }
    .distinctBy { it.cleanTitle }

/**
 * Матчит ли хоть один жанр айтема против набора целевых жанров (обычно уже
 * развёрнутых [expandGenreSet] вариантов). Эвристика [GenreMatcher] покрывает
 * любые языки и формы: блэклист «экшен» вырежет и «Action», и «aksiyon».
 */
internal fun matchesAnyGenre(itemGenres: List<String>, wantedVariants: Set<String>): Boolean =
    itemGenres.any { genre -> wantedVariants.any { wanted -> GenreMatcher.matches(wanted, genre) } }

/**
 * V3: обязательные жанры — только тайтлы с хотя бы одним из них.
 * Best-effort: пустой результат → возвращаем исходный список (лента не голодает),
 * айтемы без жанров не режем.
 */
internal fun <T> applyRequiredGenres(
    items: List<T>,
    requiredGenres: Set<String>,
    itemGenres: (T) -> List<String>,
): List<T> {
    if (requiredGenres.isEmpty()) return items
    return items.filter { item ->
        val genres = itemGenres(item)
        genres.isEmpty() || genres.any { g -> requiredGenres.any { req -> GenreMatcher.matches(req, g) } }
    }
}

/** Перегрузка для [DiscoveryTrendingItem]: жанры — поле genres. */
internal fun applyRequiredGenres(
    items: List<DiscoveryTrendingItem>,
    requiredGenres: Set<String>,
): List<DiscoveryTrendingItem> = applyRequiredGenres(items, requiredGenres) { it.genres }

/** V3: TASTE-скорование с бустом приоритетных жанров (×1.5, не отсекает). */
internal fun boostedTasteScore(
    itemGenres: List<String>,
    profile: List<Pair<String, Double>>,
    priorityGenres: Set<String>,
): Double {
    val base = tasteScore(itemGenres, profile)
    if (priorityGenres.isEmpty() || itemGenres.isEmpty()) return base
    val hasPriority = itemGenres.any { g -> priorityGenres.any { pri -> GenreMatcher.matches(pri, g) } }
    return if (hasPriority) base * 1.5 else base
}

// ==================== V3: CSV с raw-префиксом для жанровых фильтров ====================

/**
 * V3: парсинг CSV жанрового фильтра формата `canonical|canonical|raw:«строка»`.
 * Возвращает (canonical keys, raw strings) — обе части матчятся через GenreMatcher
 * (canonical — через онтологию, raw — напрямую).
 */
internal fun parseGenreFilterCsv(csv: String?): Pair<Set<String>, List<String>> {
    if (csv.isNullOrBlank()) return emptySet<String>() to emptyList<String>()
    val canonical = mutableSetOf<String>()
    val raw = mutableListOf<String>()
    csv.splitToSequence(",")
        .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        .forEach { entry ->
            if (entry.startsWith(RAW_GENRE_PREFIX)) {
                entry.removePrefix(RAW_GENRE_PREFIX).takeIf(String::isNotBlank)?.let(raw::add)
            } else {
                canonical.add(entry)
            }
        }
    return canonical to raw
}

/**
 * V3: сериализация CSV жанрового фильтра. Raw-жанры получают префикс `raw:`
 * чтобы отличаться от canonical ключей при следующем чтении.
 */
internal fun serializeGenreFilterCsv(canonical: Set<String>, raw: List<String>): String =
    (canonical + raw.map { RAW_GENRE_PREFIX + it }).filter { it.isNotBlank() }.joinToString(",")

/**
 * V3: резолв введённого пользователем жанра: канонический → canonical key;
 * незнакомый → raw string «как есть».
 */
internal fun resolveUserGenreInput(input: String): Pair<String, Boolean> {
    // Запятая — разделитель CSV-хранилища: в жанре она превратится в пробел,
    // иначе raw-строка распадётся на два битых элемента при следующем чтении.
    val trimmed = input.trim().replace(',', ' ').replace('\n', ' ').trim()
    if (trimmed.isEmpty()) return "" to false
    val canonical = GenreMatcher.resolve(trimmed).firstOrNull()
    return if (canonical != null) canonical to true else trimmed to false
}

private const val RAW_GENRE_PREFIX = "raw:"

/**
 * Read-time фильтр tag-blacklist (B2): у TASTE-ряда жанры профиля сохранены в
 * reason-CSV — они фильтруются мгновенно, без регенерации. У TREND/LIKE/SOURCE
 * жанры не персистятся: для них блэклист действует со следующего обновления ряда.
 * [expandedBlacklist] — уже развёрнутый [expandGenreSet] (RU↔EN варианты),
 * сравнение — через эвристику [GenreMatcher].
 */
internal fun isBlacklisted(
    item: tachiyomi.domain.discovery.model.DiscoverySuggestion,
    expandedBlacklist: Set<String>,
): Boolean {
    if (expandedBlacklist.isEmpty()) return false
    if (item.genres.isNotEmpty()) return matchesAnyGenre(item.genres, expandedBlacklist)
    if (item.rowType != tachiyomi.domain.discovery.model.DiscoveryRowType.TASTE) return false
    val reason = item.reason ?: return false
    return reason.splitToSequence(",").any { tag -> matchesAnyGenre(listOf(tag), expandedBlacklist) }
}

/**
 * Извлекает ключ серии / франшизы для предотвращения засилья однотипных тайтлов.
 * Выделяет общую основу для тайтлов с сезонами, частями, римскими цифрами,
 * оговорками (OVA, Movie) и подзаголовками через двоеточие.
 */
internal fun extractSeriesKey(rawTitle: String, cleanTitle: String): String {
    // 1. Проверяем префикс до двоеточия (если есть подзаголовок, например "Solo Leveling: Ragnarok" или "Naruto: Shippuden")
    if (rawTitle.contains(": ")) {
        val prefix = rawTitle.substringBefore(": ").trim()
        val cleanPrefix = normalizeDiscoveryTitle(prefix)
        if (cleanPrefix.length >= 3) {
            return cleanPrefix
        }
    }

    var s = cleanTitle.lowercase().trim()
    // 2. Срезаем типичные суффиксы сезонов, куров, частей и т.д. на английском и русском
    s = s.replace(Regex("\\b(the\\s+)?final\\s+season\\b.*", RegexOption.IGNORE_CASE), "")
    s = s.replace(Regex("\\b(season|cour|part|act|vol|volume|s)\\s*\\d+\\b.*", RegexOption.IGNORE_CASE), "")
    s = s.replace(Regex("\\b\\d+(st|nd|rd|th)\\s+season\\b.*", RegexOption.IGNORE_CASE), "")
    s = s.replace(Regex("\\b(сезон|часть|фильм|том)\\s*\\d+\\b.*", RegexOption.IGNORE_CASE), "")
    s = s.replace(Regex("\\b\\d+\\s*(сезон|сезона|сезоны|часть|фильм|том)\\b.*", RegexOption.IGNORE_CASE), "")
    s = s.replace(Regex("\\b(финал|фильм|ова|спешл)\\b.*", RegexOption.IGNORE_CASE), "")
    s = s.replace(Regex("\\b(ova|oad|movie|specials?)\\b.*", RegexOption.IGNORE_CASE), "")
    s = s.replace(Regex("\\b(ii|iii|iv|v|vi|vii|viii|ix|x)\\s*$", RegexOption.IGNORE_CASE), "")
    s = s.trim()

    return if (s.length >= 3) s else cleanTitle
}

/**
 * Обобщённая функция ограничения кластеризации серии/франшизы:
 * пропускает не более [maxPerSeries] карточек одной серии в начало, а остальные
 * сдвигает в конец (overflow), предотвращая переполнение подборки однотипными сиквелами.
 */
internal fun <T> filterFranchiseClusteringGeneric(
    items: List<T>,
    titleExtractor: (T) -> String,
    cleanTitleExtractor: (T) -> String,
    maxPerSeries: Int = 1,
): List<T> {
    if (items.size <= 1) return items
    val result = mutableListOf<T>()
    val overflow = mutableListOf<T>()
    val seriesCount = mutableMapOf<String, Int>()

    for (item in items) {
        val key = extractSeriesKey(titleExtractor(item), cleanTitleExtractor(item))
        val count = seriesCount.getOrDefault(key, 0)
        if (count < maxPerSeries) {
            result.add(item)
            seriesCount[key] = count + 1
        } else {
            overflow.add(item)
        }
    }
    return result + overflow
}

/**
 * Ограничивает кластеризацию одной франшизы/серии для элементов рядов [DiscoveryRowItem].
 */
internal fun filterFranchiseClustering(
    items: List<DiscoveryRowItem>,
    maxPerSeries: Int = 1,
): List<DiscoveryRowItem> = filterFranchiseClusteringGeneric(
    items = items,
    titleExtractor = { it.title },
    cleanTitleExtractor = { it.cleanTitle },
    maxPerSeries = maxPerSeries,
)

/**
 * Ограничивает кластеризацию одного доминирующего жанра в выдаче ряда «Твой вкус»:
 * предотвращает монополизацию выдачи одним жанром, перемещая избыточные тайтлы
 * того же первичного жанра сверх [maxPerGenre] в хвост списка.
 */
internal fun filterGenreClustering(
    items: List<DiscoveryRowItem>,
    maxPerGenre: Int = 6,
): List<DiscoveryRowItem> {
    if (items.size <= 1) return items
    val result = mutableListOf<DiscoveryRowItem>()
    val overflow = mutableListOf<DiscoveryRowItem>()
    val genreCounts = mutableMapOf<String, Int>()
    for (item in items) {
        val primaryGenre = item.reason?.substringBefore(",")?.trim()?.lowercase().orEmpty()
        val count = genreCounts.getOrDefault(primaryGenre, 0)
        if (primaryGenre.isEmpty() || count < maxPerGenre) {
            result.add(item)
            if (primaryGenre.isNotEmpty()) genreCounts[primaryGenre] = count + 1
        } else {
            overflow.add(item)
        }
    }
    return result + overflow
}

/**
 * Балансировка разнообразия плагинов/источников:
 * Плагины с более сильными сигналами могут выдавать больше тайтлов,
 * но если доступны другие плагины/источники, минимум [minOtherCount] (дефолт 3)
 * тайтлов строго сохраняются за другими плагинами/источниками для разнообразия.
 */
internal fun <T> balancePluginDiversity(
    items: List<T>,
    limit: Int,
    sourceKeyExtractor: (T) -> String,
    minOtherCount: Int = 3,
    maxDominantShare: Double = 0.30,
): List<T> {
    if (items.size <= 1 || limit <= 0) return items.take(limit)

    val sourceCounts = mutableMapOf<String, Int>()
    items.forEach { item ->
        val key = sourceKeyExtractor(item)
        if (key.isNotEmpty()) {
            sourceCounts[key] = (sourceCounts[key] ?: 0) + 1
        }
    }

    val dominantSource = sourceCounts.maxByOrNull { it.value }?.key ?: return items.take(limit)
    val otherCandidates = items.filter { sourceKeyExtractor(it) != dominantSource }
    if (otherCandidates.isEmpty()) return items.take(limit)

    // При N >= 4 доминирующий источник ограничивается потолком maxDominantShare (дефолт 30%).
    // При N < 4 потолок не применяется (дефолтное поведение: minOtherCount).
    val dominantCap = if (sourceCounts.size >= 4) {
        maxOf(1, kotlin.math.ceil(limit * maxDominantShare).toInt())
    } else {
        limit - minOf(minOtherCount, otherCandidates.size)
    }
    val targetOtherCount = minOf(otherCandidates.size, limit - dominantCap)
        .coerceAtLeast(minOf(minOtherCount, otherCandidates.size))
        .coerceAtMost(maxOf(0, limit - 1))
    val maxDominant = limit - targetOtherCount

    val result = mutableListOf<T>()
    var dominantCount = 0
    val others = ArrayDeque(otherCandidates)
    val seen = mutableSetOf<T>()

    for (item in items) {
        if (result.size >= limit) break
        val key = sourceKeyExtractor(item)
        if (key == dominantSource) {
            if (dominantCount < maxDominant) {
                result.add(item)
                seen.add(item)
                dominantCount++
            }
        } else {
            result.add(item)
            seen.add(item)
        }
    }

    val currentOtherCount = result.count { sourceKeyExtractor(it) != dominantSource }
    if (currentOtherCount < targetOtherCount) {
        while (result.size < limit && others.isNotEmpty()) {
            val other = others.removeFirst()
            if (other !in seen) {
                result.add(other)
                seen.add(other)
            }
        }
    }

    return result.take(limit)
}

private fun normalizeWeight(raw: Double?): Double {
    if (raw == null || raw.isNaN() || raw <= 0.0) return 0.0
    if (raw.isInfinite()) return 1_000_000.0
    return raw
}

/**
 * Распределяет целое число слотов [totalSlots] между источниками [sourceIds]
 * с учётом их эффективных весов [effectiveWeights].
 *
 * Если слотов достаточно (totalSlots >= sourceIds.size): каждый получает минимум 1 слот,
 * а остаток делится пропорционально положительным весам методом Largest Remainder (Гамильтона).
 * При равенстве остатков приоритет отдаётся исходному порядку.
 * Если источников больше, чем слотов: выбираются источники с наибольшим весом (tie-break — исходный порядок).
 * При неположительном totalSlots или пустом списке источников возвращается пустая карта.
 */
internal fun allocateSourceSlots(
    sourceIds: List<Long>,
    effectiveWeights: Map<Long, Double>,
    totalSlots: Int,
    maxDominantShare: Double = 0.30,
): Map<Long, Int> {
    if (totalSlots <= 0 || sourceIds.isEmpty()) return emptyMap()

    if (sourceIds.size > totalSlots) {
        val indexed = sourceIds.mapIndexed { index, id ->
            Triple(id, normalizeWeight(effectiveWeights[id]), index)
        }
        val sorted = indexed.sortedWith(
            compareByDescending<Triple<Long, Double, Int>> { it.second }
                .thenBy { it.third },
        )
        val selected = sorted.take(totalSlots)
        return selected.sortedBy { it.third }.associate { it.first to 1 }
    }

    val allocation = LinkedHashMap<Long, Int>(sourceIds.size)
    sourceIds.forEach { allocation[it] = 1 }
    val remainingSlots = totalSlots - sourceIds.size
    if (remainingSlots == 0) return allocation

    // При N >= 4 источниках ни один источник не может получить больше maxDominantShare от totalSlots
    val slotCap = if (sourceIds.size >= 4) {
        maxOf(1, (totalSlots * maxDominantShare).toInt())
    } else {
        totalSlots
    }

    val activeIds = sourceIds.toMutableList()
    var slotsLeft = remainingSlots

    while (slotsLeft > 0 && activeIds.isNotEmpty()) {
        val weights = activeIds.map { id -> id to normalizeWeight(effectiveWeights[id]) }
        val totalWeight = weights.sumOf { it.second }

        val effectiveShares = if (totalWeight > 0.0) {
            weights.map { (id, w) -> id to (w / totalWeight) }
        } else {
            val equalShare = 1.0 / activeIds.size
            weights.map { (id, _) -> id to equalShare }
        }

        val exactQuotas = effectiveShares.map { (id, share) ->
            val exact = slotsLeft * share
            val floor = exact.toInt()
            val remainder = exact - floor
            id to (floor to remainder)
        }

        var hitCap = false
        for ((id, pair) in exactQuotas) {
            val cur = allocation[id] ?: 1
            val next = cur + pair.first
            if (next >= slotCap && cur < slotCap) {
                allocation[id] = slotCap
                activeIds.remove(id)
                hitCap = true
            } else if (next < slotCap) {
                allocation[id] = next
            }
        }

        if (hitCap) {
            slotsLeft = totalSlots - allocation.values.sum()
            continue
        }

        var leftover = slotsLeft - exactQuotas.sumOf { it.second.first }
        val indexedRemainders = exactQuotas.mapIndexed { index, (id, pair) ->
            Triple(id, pair.second, index)
        }.sortedWith(
            compareByDescending<Triple<Long, Double, Int>> { it.second }
                .thenBy { it.third },
        )

        for (item in indexedRemainders) {
            if (leftover <= 0) break
            val cur = allocation[item.first] ?: 1
            if (cur < slotCap) {
                allocation[item.first] = cur + 1
                leftover--
            }
        }
        break
    }

    return allocation
}
