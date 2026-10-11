package eu.kanade.tachiyomi.data.discovery

import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoverySignal
import tachiyomi.domain.discovery.model.DiscoverySignalType
import kotlin.math.abs

// ==================== Taste Learning Engine: fold + merge ====================

/**
 * Fold сигнал-лога в выученный профиль вкуса: жанр → Σ weight·decay(age)
 * (полураспад — как у библиотечного профиля, трендовые жанры гаснут быстрее),
 * плюс аффинити источников (Σ weight по source_key). Пересечения: жанр из k
 * разных лайкнутых тайтлов получает множитель (1 + 0.5·(k−1)) — комбинаторика
 * как у mergeSeedResults. Вето-жанры [vetoGenres] не считаются (мягкий откат:
 * библиотечное влияние остаётся). Clamp [maxGenreWeight] — один жанр не задавливает.
 * Чистая функция — тестируется без БД/Compose.
 */
internal fun foldLearnedTasteProfile(
    signals: List<DiscoverySignal>,
    nowMs: Long = System.currentTimeMillis(),
    vetoGenres: Set<String> = emptySet(),
    maxGenreWeight: Double = 3.0,
): LearnedTasteProfile {
    if (signals.isEmpty()) return LearnedTasteProfile()

    val genreWeights = mutableMapOf<String, Double>()
    val genreTitleCount = mutableMapOf<String, Int>()
    val sourceAffinity = mutableMapOf<String, Double>()

    val vetoKeys = vetoGenres.mapNotNull { it.trim().lowercase().takeIf(String::isNotEmpty) }.toSet()

    signals.forEach { signal ->
        // Дрейф вкуса: вклад затухает по возрасту сигнала.
        val ageDays = ((nowMs - signal.createdAt) / DAY_MS).coerceAtLeast(0)
        val signalWeight = signal.signalType.weight

        signal.genres
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && it !in vetoKeys }
            .toSet()
            .forEach { genre ->
                val decayed = signalWeight / (1.0 + ageDays / genreDecayDays(genre))
                genreWeights[genre] = (genreWeights[genre] ?: 0.0) + decayed
                if (signalWeight > 0) genreTitleCount[genre] = (genreTitleCount[genre] ?: 0) + 1
            }

        val sourceKey = signal.sourceKey?.trim()?.takeIf(String::isNotEmpty)
        if (sourceKey != null) {
            sourceAffinity[sourceKey] = (sourceAffinity[sourceKey] ?: 0.0) + signalWeight
        }
    }

    val genres = genreWeights.entries
        .map { (genre, weight) ->
            // Пересечение: жанр из k разных тайтлов усиливается.
            val k = genreTitleCount[genre] ?: 1
            val boosted = weight * (1.0 + OVERLAP_MULTIPLIER * (k - 1))
            genre to boosted.coerceIn(-maxGenreWeight, maxGenreWeight)
        }
        .filter { it.second != 0.0 }
        .sortedByDescending { it.second }

    return LearnedTasteProfile(
        genres = genres,
        sourceAffinity = sourceAffinity.filterValues { it != 0.0 },
        // В ramp-up считаются только явные сигналы пользователя: CONSUMED (0.0)
        // и синтезированный SHOWN_IGNORED не разогревают blend без его участия.
        signalCount = signals.count {
            it.signalType.weight != 0.0 && it.signalType != DiscoverySignalType.SHOWN_IGNORED
        },
    )
}

/**
 * Слияние библиотечного и выученного профилей: вес жанра = max (не сумма —
 * иначе тайтл, добавленный из ленты, учтётся дважды: и как сигнал, и в библиотеке).
 * Выученное подмешивается плавно: blendFactor = min(1, signals / [rampUpSignals]) —
 * холодный старт не дёргает профиль единичными сигналами.
 */
internal fun mergeTasteProfiles(
    libraryProfile: List<Pair<String, Double>>,
    learned: LearnedTasteProfile,
    rampUpSignals: Int = 20,
): List<Pair<String, Double>> {
    if (learned.genres.isEmpty()) return libraryProfile
    val blend = (learned.signalCount.toDouble() / rampUpSignals).coerceAtMost(1.0)

    val merged = linkedMapOf<String, Double>()
    libraryProfile.forEach { (genre, weight) ->
        merged[genre.trim().lowercase()] = weight
    }
    learned.genres.forEach { (genre, learnedWeight) ->
        val key = genre.trim().lowercase()
        val scaled = learnedWeight * blend
        if (learnedWeight >= 0.0) {
            merged[key] = maxOf(merged[key] ?: 0.0, scaled)
        } else {
            merged[key] = ((merged[key] ?: 0.0) + scaled).coerceAtLeast(0.0)
        }
    }
    return merged.entries
        .filter { it.value > 0.0 }
        .sortedByDescending { it.value }
        .map { it.key to it.value }
}

/**
 * Аффинити источника к весу участия плагина: вес библиотеки · (1 + [boostCap]·tanh(
 * affinity / [affinityScale])) — плавный буст без переворота порядка для
 * малых аффинити; клампится в [0.4..2.0]x.
 */
internal fun applySourceAffinity(
    libraryWeight: Int,
    affinity: Double,
    affinityScale: Double = 4.0,
    boostCap: Double = 0.6,
    isManualMode: Boolean = false,
): Double {
    if (isManualMode) {
        val positiveAffinity = affinity.coerceAtLeast(0.0)
        return 1.0 + 3.5 * kotlin.math.tanh(positiveAffinity / affinityScale)
    }
    val boost = 1.0 + boostCap * kotlin.math.tanh(affinity / affinityScale)
    val effectiveBase = libraryWeight.coerceAtLeast(1)
    return effectiveBase * boost.coerceIn(0.4, 2.0)
}

/** Выученный профиль: жанры с весами, аффинити источников, счётчик сигналов. */
internal data class LearnedTasteProfile(
    val genres: List<Pair<String, Double>> = emptyList(),
    val sourceAffinity: Map<String, Double> = emptyMap(),
    val signalCount: Int = 0,
)

/** Категория веса для отображения на экране профиля (чипы). */
internal fun learnedGenreTier(weight: Double): Int = when {
    weight >= 2.0 -> 3
    weight >= 1.0 -> 2
    weight > 0.0 -> 1
    else -> 0
}

/**
 * Синтез неявного негатива «показано, но не кликнуто»:
 * тайтл из тизера Home показан [SHOWN_IGNORED_MIN_SHOWS]+ раз за 48ч
 * и не имеет ЯВНОГО сигнала → жанры этого тайтла получают мягкий минус
 * [DiscoverySignalType.SHOWN_IGNORED.weight] (-0.15). Синтезированные сигналы
 * НЕ пишутся в БД — пересчитываются на каждый прогон из счётчика показов.
 */
internal fun synthesizeImplicitNegatives(
    shownWithCount: List<Triple<String, Long, Int>>,
    explicitSignals: List<DiscoverySignal>,
    currentSuggestions: List<tachiyomi.domain.discovery.model.DiscoverySuggestion>,
    mediaType: DiscoveryMediaType,
    profileGenres: List<String> = emptyList(),
): List<DiscoverySignal> {
    val explicitTitles = explicitSignals.mapTo(HashSet()) { it.cleanTitle }
    // cleanTitle → жанры из reason-CSV текущего кэша (TASTE-ряд; прочие — без жанров).
    val genresByTitle = currentSuggestions
        .filter { it.rowType == tachiyomi.domain.discovery.model.DiscoveryRowType.TASTE }
        .associate { item ->
            item.cleanTitle to (
                item.reason
                    ?.splitToSequence(",")
                    ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                    ?.toList()
                    .orEmpty()
                )
        }
    return shownWithCount
        .filter { (cleanTitle, _, count) ->
            count >= SHOWN_IGNORED_MIN_SHOWS && cleanTitle !in explicitTitles
        }
        .mapNotNull { (cleanTitle, _, _) ->
            val genres = genresByTitle[cleanTitle].orEmpty()
                .filterNot { g -> profileGenres.any { p -> GenreMatcher.matches(p, g) } }
            if (genres.isEmpty()) return@mapNotNull null
            DiscoverySignal(
                mediaType = mediaType,
                cleanTitle = cleanTitle,
                title = cleanTitle,
                signalType = DiscoverySignalType.SHOWN_IGNORED,
                genres = genres,
                provider = null,
                sourceKey = null,
                createdAt = System.currentTimeMillis(),
            )
        }
}

private const val SHOWN_IGNORED_MIN_SHOWS = 3

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val OVERLAP_MULTIPLIER = 0.5
