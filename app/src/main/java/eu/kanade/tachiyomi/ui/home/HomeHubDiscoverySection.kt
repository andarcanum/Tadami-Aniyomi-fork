package eu.kanade.tachiyomi.ui.home

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.LabelOff
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.ui.model.HomeHeroMode
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.AuroraCoverPlaceholderVariant
import eu.kanade.presentation.components.AuroraSheetWindowFx
import eu.kanade.presentation.components.buildAuroraCoverImageRequest
import eu.kanade.presentation.components.rememberCoverReloadTick
import eu.kanade.presentation.components.rememberThemeAwareCoverErrorPainter
import eu.kanade.presentation.components.shouldAnimateAuroraBackground
import eu.kanade.presentation.entries.components.aurora.AuroraGlassCtaSurface
import eu.kanade.presentation.entries.components.aurora.AuroraHeroCtaMode
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterColorFilter
import eu.kanade.presentation.theme.AuroraSurfaceLevel
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import eu.kanade.presentation.theme.resolveAuroraSurfaceColor
import eu.kanade.presentation.util.rememberSupportsBlurBehind
import eu.kanade.tachiyomi.data.discovery.DiscoveryRowItem
import eu.kanade.tachiyomi.data.discovery.balancePluginDiversity
import eu.kanade.tachiyomi.data.discovery.extractSeriesKey
import eu.kanade.tachiyomi.data.discovery.filterFranchiseClustering
import eu.kanade.tachiyomi.data.discovery.filterFranchiseClusteringGeneric
import eu.kanade.tachiyomi.data.discovery.interleaveMix
import eu.kanade.tachiyomi.data.discovery.rrfScores
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionReason
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import eu.kanade.tachiyomi.ui.discovery.discoveryCoverData
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

// ============================ Чистые функции (тестируются) ============================

/** Тизер = топ-N смешанного потока (интерлив квот сигналов), а не только LIKE-ряд. */
internal fun composeTeaserItems(
    items: List<DiscoverySuggestion>,
    limit: Int,
    offset: Int = 0,
): List<HomeHubDiscoveryItem> {
    val capped = limit.coerceIn(3, 20)
    val rows = items.groupBy { it.rowType }
        .mapValues { (_, row) ->
            row.map { s ->
                DiscoveryRowItem(
                    title = s.title,
                    cleanTitle = s.cleanTitle,
                    coverUrl = s.coverUrl,
                    reason = s.reason,
                    seedTitle = s.seedTitle,
                    provider = s.provider,
                    score = s.score,
                    sourceId = s.sourceId,
                    sourceUrl = s.sourceUrl,
                )
            }
        }
    val fullMix = interleaveMix(rows, total = items.size, rrf = rrfScores(rows))
    val clustered = filterFranchiseClustering(fullMix, maxPerSeries = if (capped >= 15) 2 else 1)
    val safeOffset = if (clustered.isNotEmpty()) offset % clustered.size else 0
    val rotated = if (safeOffset <= 0) {
        clustered
    } else {
        clustered.drop(safeOffset) + clustered.take(safeOffset)
    }
    val finalSelection = balancePluginDiversity(
        items = rotated,
        limit = capped,
        sourceKeyExtractor = { it.provider.ifBlank { it.sourceId?.toString().orEmpty() } },
        minOtherCount = 3,
    )
    return finalSelection.mapNotNull { row ->
        items.firstOrNull { it.cleanTitle == row.cleanTitle }?.toHomeHubDiscoveryItem()
    }
}

/**
 * Локальная замена карточки Home-тизера при лайке:
 * 1. Ищет лайкнутый тайтл в [currentTeaser] по [likedCleanTitle].
 * 2. Если найден, ищет в [persistedPool] первого кандидата, отсутствующего в [currentTeaser]
 *    и не помеченного как dismissed ([dismissedTitles]).
 * 3. Если кандидат найден, ставит его ровно на место лайкнутой карточки.
 * 4. Если кандидата нет, лайкнутая карточка удаляется, список сжимается.
 * 5. Сеть не ждёт, persistent pool не мутирует, markShown/CONSUMED не трогает.
 */
internal fun patchAfterLocalLike(
    currentTeaser: List<HomeHubDiscoveryItem>,
    persistedPool: List<HomeHubDiscoveryItem>,
    likedCleanTitle: String,
    targetCount: Int = currentTeaser.size,
    dismissedTitles: Set<String> = emptySet(),
): List<HomeHubDiscoveryItem> {
    val index = currentTeaser.indexOfFirst { it.cleanTitle == likedCleanTitle }
    if (index < 0) return currentTeaser

    val existingTitles = currentTeaser.mapTo(HashSet()) { it.cleanTitle }
    val excluded = existingTitles + dismissedTitles + likedCleanTitle

    val replacement = persistedPool.firstOrNull { it.cleanTitle !in excluded }

    val mutable = currentTeaser.toMutableList()
    if (replacement != null && mutable.size <= targetCount) {
        mutable[index] = replacement
    } else {
        mutable.removeAt(index)
    }
    return mutable
}

/**
 * Локальное восстановление карточки Home-тизера при отмене скрытия (Undo):
 * 1. Если карточка уже присутствует в [current], возвращает список без изменений.
 * 2. Если размер списка >= [targetCount], заменяет последнюю карточку на восстановленную.
 * 3. Если меньше, добавляет восстановленную карточку в начало.
 */
internal fun restoreTeaserItem(
    current: List<HomeHubDiscoveryItem>,
    restored: HomeHubDiscoveryItem,
    targetCount: Int = current.size,
): List<HomeHubDiscoveryItem> {
    if (current.any { it.cleanTitle == restored.cleanTitle }) return current
    val mutable = current.toMutableList()
    if (mutable.size >= targetCount && mutable.isNotEmpty()) {
        mutable[mutable.lastIndex] = restored
    } else {
        mutable.add(0, restored)
    }
    return mutable
}

/**
 * Выбирает элементы тизера с соблюдением 48-часовой уникальности и минимизацией
 * пересечения с карточками, видимыми прямо сейчас:
 * 1. Исключает тайтлы из [shownTitles] (показанные за последние 48 ч).
 * 2. Если задан [currentTitles], в первую очередь отбирает тайтлы, которых нет на экране.
 * 3. Если свежих тайтлов >= count, формирует сбалансированный тизер из свежих с ротацией по [offset].
 * 4. Если свежих тайтлов < count, добирает недостающие из ранее показанных в порядке
 *    [shownCutoffMap] (наименее недавно показанные первыми, с приоритетом не видимых сейчас).
 * 5. Если весь пул меньше или равен count, циклически ротирует порядок отображения по [offset].
 */
internal fun selectFreshTeaserItems(
    pool: List<DiscoverySuggestion>,
    shownTitles: Set<String>,
    count: Int,
    offset: Int = 0,
    shownCutoffMap: Map<String, Long> = emptyMap(),
    currentTitles: Set<String> = emptySet(),
): List<HomeHubDiscoveryItem> {
    val capped = count.coerceIn(3, 20)
    if (pool.isEmpty()) return emptyList()

    fun <T> rotateList(list: List<T>, off: Int): List<T> {
        if (list.size <= 1) return list
        val safe = (off % list.size).let { if (it < 0) it + list.size else it }
        return if (safe > 0) list.drop(safe) + list.take(safe) else list
    }

    val freshPool = pool.filterNot { it.cleanTitle in shownTitles }
    val shownPool = pool.filter { it.cleanTitle in shownTitles }

    // Сначала пробуем кандидатов, которых нет на экране
    val freshNotCurrent = if (currentTitles.isNotEmpty()) {
        freshPool.filterNot { it.cleanTitle in currentTitles }
    } else {
        freshPool
    }
    val freshOnScreen = if (currentTitles.isNotEmpty()) {
        freshPool.filter { it.cleanTitle in currentTitles }
    } else {
        emptyList()
    }

    val shownNotCurrent = shownPool.filterNot { it.cleanTitle in currentTitles }
        .sortedBy { shownCutoffMap[it.cleanTitle] ?: 0L }
    val shownOnScreen = shownPool.filter { it.cleanTitle in currentTitles }
        .sortedBy { shownCutoffMap[it.cleanTitle] ?: 0L }

    val rawSelection: List<HomeHubDiscoveryItem> = when {
        freshNotCurrent.size >= capped -> {
            composeTeaserItems(rotateList(freshNotCurrent, offset), capped, offset = 0)
        }
        freshPool.size >= capped -> {
            val chosen = rotateList(freshNotCurrent, offset).map { it.toHomeHubDiscoveryItem() }
            val remainingFresh = rotateList(freshOnScreen, offset).map { it.toHomeHubDiscoveryItem() }
            (chosen + remainingFresh).take(capped)
        }
        freshPool.isNotEmpty() -> {
            val freshCandidates = (rotateList(freshNotCurrent, offset) + rotateList(freshOnScreen, offset))
                .distinctBy { it.cleanTitle }
                .map { it.toHomeHubDiscoveryItem() }
            val needed = capped - freshCandidates.size

            val rotatedShownNotCurrent = rotateList(shownNotCurrent, offset)
            val rotatedShownOnScreen = rotateList(shownOnScreen, offset)
            val combined = rotatedShownNotCurrent + rotatedShownOnScreen
            val backfillPool = combined.take(needed).map { it.toHomeHubDiscoveryItem() }
            (freshCandidates + backfillPool).take(capped)
        }
        else -> {
            val rotatedShownNotCurrent = rotateList(shownNotCurrent, offset)
            val rotatedShownOnScreen = rotateList(shownOnScreen, offset)
            val combined = rotatedShownNotCurrent + rotatedShownOnScreen
            combined.take(capped).map { it.toHomeHubDiscoveryItem() }
        }
    }

    val maxPerSeries = if (capped >= 15) 2 else 1
    val clustered = filterFranchiseClusteringGeneric(
        items = rawSelection,
        titleExtractor = { it.title },
        cleanTitleExtractor = { it.cleanTitle },
        maxPerSeries = maxPerSeries,
    ).toMutableList()

    if (clustered.size < capped) {
        val selectedCleanTitles = clustered.mapTo(HashSet()) { it.cleanTitle }
        val seriesCounts = mutableMapOf<String, Int>()
        for (item in clustered) {
            val key = extractSeriesKey(item.title, item.cleanTitle)
            seriesCounts[key] = (seriesCounts[key] ?: 0) + 1
        }

        val allRemaining = (
            rotateList(freshNotCurrent, offset) +
                rotateList(freshOnScreen, offset) +
                rotateList(shownNotCurrent, offset) +
                rotateList(shownOnScreen, offset)
            ).distinctBy { it.cleanTitle }
            .filterNot { it.cleanTitle in selectedCleanTitles }

        for (item in allRemaining) {
            if (clustered.size >= capped) break
            val key = extractSeriesKey(item.title, item.cleanTitle)
            if ((seriesCounts[key] ?: 0) < maxPerSeries) {
                clustered.add(item.toHomeHubDiscoveryItem())
                selectedCleanTitles.add(item.cleanTitle)
                seriesCounts[key] = (seriesCounts[key] ?: 0) + 1
            }
        }

        for (item in allRemaining) {
            if (clustered.size >= capped) break
            if (item.cleanTitle !in selectedCleanTitles) {
                clustered.add(item.toHomeHubDiscoveryItem())
                selectedCleanTitles.add(item.cleanTitle)
            }
        }
    }

    return clustered
}

/**
 * Показывать ли уведомление «подборка ограничена» после ручного обновления тизера.
 * Сигнал нужен, когда обновление почти не меняет ленту: пул не больше окна тизера
 * (ротация лишь переставляет те же карточки) или новое окно пересекается с текущим.
 * Модель зовёт её уже после проверки `notifyIfLimited && currentTitles.isNotEmpty()`.
 */
internal fun shouldNotifyDiscoveryPoolLimited(
    teaser: List<HomeHubDiscoveryItem>,
    currentTitles: Set<String>,
    distinctPoolSize: Int,
    count: Int,
): Boolean = distinctPoolSize <= count || teaser.any { it.cleanTitle in currentTitles }

/**
 * Точечное обновление тизера ленты при скрытии или отметке «Просмотрено»:
 * оставляет все валидные карточки на их прежних позициях, заменяя только выпавшую(ие)
 * карточку свежими кандидатами из пула (без перезагрузки всей ленты).
 */
internal fun patchTeaserItems(
    currentItems: List<HomeHubDiscoveryItem>,
    pool: List<DiscoverySuggestion>,
    validCleanTitles: Set<String>,
    targetCount: Int,
    shownTitles: Set<String> = emptySet(),
    shownCutoffMap: Map<String, Long> = emptyMap(),
    dismissedTitles: Set<String> = emptySet(),
): List<HomeHubDiscoveryItem> {
    val capped = targetCount.coerceIn(3, 20)
    if (pool.isEmpty()) return emptyList()
    if (currentItems.isEmpty()) {
        return selectFreshTeaserItems(
            pool = pool.filterNot { it.cleanTitle in dismissedTitles },
            shownTitles = shownTitles,
            count = capped,
            shownCutoffMap = shownCutoffMap,
        )
    }

    val usedCleanTitles = currentItems
        .filter { it.cleanTitle in validCleanTitles && it.cleanTitle !in dismissedTitles }
        .mapTo(HashSet()) { it.cleanTitle }
    usedCleanTitles.addAll(dismissedTitles)

    // Кандидаты на замену из доступного валидного пула
    val validCandidates = pool.filter {
        it.cleanTitle in validCleanTitles &&
            it.cleanTitle !in usedCleanTitles &&
            it.cleanTitle !in dismissedTitles
    }
    val freshCandidates = ArrayDeque(validCandidates.filterNot { it.cleanTitle in shownTitles })
    val backfillCandidates = ArrayDeque(
        validCandidates.filter { it.cleanTitle in shownTitles }
            .sortedBy { shownCutoffMap[it.cleanTitle] ?: 0L },
    )

    val currentSeriesKeys = mutableMapOf<String, Int>()
    for (item in currentItems) {
        if (item.cleanTitle in validCleanTitles && item.cleanTitle !in dismissedTitles) {
            val key = extractSeriesKey(item.title, item.cleanTitle)
            currentSeriesKeys[key] = (currentSeriesKeys[key] ?: 0) + 1
        }
    }
    val maxPerSeries = if (capped >= 15) 2 else 1

    fun nextReplacement(): DiscoverySuggestion? {
        val candidate = freshCandidates.firstOrNull {
            val key = extractSeriesKey(it.title, it.cleanTitle)
            (currentSeriesKeys[key] ?: 0) < maxPerSeries
        }?.also {
            freshCandidates.remove(it)
        } ?: backfillCandidates.firstOrNull {
            val key = extractSeriesKey(it.title, it.cleanTitle)
            (currentSeriesKeys[key] ?: 0) < maxPerSeries
        }?.also {
            backfillCandidates.remove(it)
        } ?: if (freshCandidates.isNotEmpty()) {
            freshCandidates.removeFirst()
        } else if (backfillCandidates.isNotEmpty()) {
            backfillCandidates.removeFirst()
        } else {
            null
        }

        if (candidate != null) {
            val key = extractSeriesKey(candidate.title, candidate.cleanTitle)
            currentSeriesKeys[key] = (currentSeriesKeys[key] ?: 0) + 1
        }
        return candidate
    }

    val poolMap = pool.associateBy { it.cleanTitle }
    val result = mutableListOf<HomeHubDiscoveryItem>()
    for (item in currentItems) {
        if (result.size >= capped) break
        if (item.cleanTitle in validCleanTitles && item.cleanTitle !in dismissedTitles) {
            val fresh = poolMap[item.cleanTitle]?.toHomeHubDiscoveryItem(shownCutoffMap[item.cleanTitle]) ?: item
            result.add(fresh)
        } else {
            val replacement = nextReplacement()
            if (replacement != null) {
                result.add(replacement.toHomeHubDiscoveryItem(shownCutoffMap[replacement.cleanTitle]))
                usedCleanTitles.add(replacement.cleanTitle)
            }
        }
    }

    while (result.size < capped) {
        val replacement = nextReplacement() ?: break
        result.add(replacement.toHomeHubDiscoveryItem(shownCutoffMap[replacement.cleanTitle]))
        usedCleanTitles.add(replacement.cleanTitle)
    }

    return result
}

/** Секция видна всегда при включённом discovery: при пустой ленте рендерит карточку-вход на полный экран. */
internal fun shouldShowForYouSection(enabled: Boolean): Boolean = enabled

/**
 * B2: первый тег для «скрыть всё с тегом X» — только TASTE reason-CSV
 * (жанры прочих рядов не персистятся; у них пункт меню disabled).
 */
internal fun firstBlacklistTag(
    genres: List<String>,
    rowType: DiscoveryRowType,
    reasonPayload: String?,
): String? = genres.firstOrNull { it.isNotBlank() }
    ?: when (rowType) {
        DiscoveryRowType.TASTE -> reasonPayload?.splitToSequence(",")
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() }
        else -> null
    }

/** Сколько карточек текущего тизера скроется при блэклисте [tag] (для undo-snackbar). */
internal fun countAffectedTeasers(items: List<HomeHubDiscoveryItem>, tag: String): Int {
    val policy = eu.kanade.tachiyomi.data.discovery.DiscoveryCandidatePolicy(blacklistedTags = setOf(tag))
    return items.count { !policy.isAllowed(it.toDiscoverySuggestion()) }
}

/**
 * Локализованная подпись-обоснование. Шаблоны строк передаются параметрами,
 * чтобы функция оставалась чистой (тестируемой без Compose).
 */
internal fun discoveryReasonText(
    item: HomeHubDiscoveryItem,
    similarTemplate: String,
    trendTemplate: String,
    nextSeasonTemplate: String,
): String? = when (item.rowType) {
    DiscoveryRowType.LIKE -> item.seedTitle?.let { similarTemplate.replace("%1\$s", it) }
    DiscoveryRowType.TREND -> when (item.reasonPayload) {
        "next" -> nextSeasonTemplate
        // Тайтлы из каталога источника (novel-first path и fallback) — честно подписываем источником.
        "source" -> item.provider
        else -> trendTemplate
    }
    DiscoveryRowType.TASTE -> item.reasonPayload?.takeIf { it.isNotBlank() }
    DiscoveryRowType.SOURCE -> item.provider
}

/**
 * Режим hero с деградацией: Collage/Hybrid требуют включённый discovery с непустой лентой.
 * Auto (дефолт) = кинематографичный Stage при работающем «Для тебя», иначе Continue.
 */
internal fun resolveHeroPresentation(
    prefMode: HomeHeroMode,
    discoveryEnabled: Boolean,
    discoveryCount: Int,
): HomeHeroMode = when (prefMode) {
    HomeHeroMode.Auto ->
        if (discoveryEnabled && discoveryCount >= 3) HomeHeroMode.Stage else HomeHeroMode.Continue
    HomeHeroMode.Continue -> HomeHeroMode.Continue
    HomeHeroMode.Collage ->
        if (discoveryEnabled && discoveryCount >= 3) HomeHeroMode.Collage else HomeHeroMode.Continue
    HomeHeroMode.Hybrid ->
        if (discoveryEnabled && discoveryCount > 0) HomeHeroMode.Hybrid else HomeHeroMode.Continue
    HomeHeroMode.Stage ->
        if (discoveryEnabled && discoveryCount >= 3) HomeHeroMode.Stage else HomeHeroMode.Continue
}

internal fun DiscoverySuggestion.toHomeHubDiscoveryItem(shownAt: Long? = null) = HomeHubDiscoveryItem(
    title = title,
    cleanTitle = cleanTitle,
    coverUrl = coverUrl,
    seedTitle = seedTitle,
    reasonPayload = reason,
    provider = provider,
    rowType = rowType,
    mediaType = mediaType,
    sourceId = sourceId,
    sourceUrl = sourceUrl,
    shownAt = shownAt,
    genres = genres,
)

internal fun HomeHubDiscoveryItem.toSuggestionItem(): SuggestionItem = SuggestionItem(
    title = title,
    searchQueries = listOf(title),
    thumbnailUrl = coverUrl,
    providerName = provider,
    providerUrl = "",
    providerId = null,
    mediaType = when (mediaType) {
        DiscoveryMediaType.ANIME -> SuggestionMediaType.ANIME
        DiscoveryMediaType.MANGA -> SuggestionMediaType.MANGA
        DiscoveryMediaType.NOVEL -> SuggestionMediaType.NOVEL
    },
    reason = when (provider.lowercase()) {
        "anilist" -> SuggestionReason.EXTERNAL_ANILIST
        "myanimelist", "mal" -> SuggestionReason.EXTERNAL_MAL
        "mangaupdates" -> SuggestionReason.EXTERNAL_MU
        "novelupdates" -> SuggestionReason.EXTERNAL_NU
        "shikimori" -> SuggestionReason.EXTERNAL_SHIKIMORI
        else -> SuggestionReason.SEARCH_TITLE
    },
)

internal fun HomeHubDiscoveryItem.toDiscoverySuggestion(): DiscoverySuggestion = DiscoverySuggestion(
    id = 0L,
    mediaType = mediaType,
    rowType = rowType,
    title = title,
    cleanTitle = cleanTitle,
    coverUrl = coverUrl,
    reason = reasonPayload,
    seedTitle = seedTitle,
    provider = provider,
    score = 1.0,
    position = 0L,
    createdAt = 0L,
    sourceId = sourceId,
    sourceUrl = sourceUrl,
    genres = genres,
)

// ============================ UI ============================

/** Маппинг секции Home Hub в медиатип discovery (для резолва источника обложки). */
internal fun HomeHubSection.toDiscoveryMediaType(): DiscoveryMediaType = when (this) {
    HomeHubSection.Anime -> DiscoveryMediaType.ANIME
    HomeHubSection.Manga -> DiscoveryMediaType.MANGA
    HomeHubSection.Novel -> DiscoveryMediaType.NOVEL
}

internal data class HybridDiscoveryStripLayoutSpec(
    val cardWidth: Int,
    val sectionHorizontalPadding: Int,
    val rowSpacing: Int,
)

internal fun resolveHybridDiscoveryStripLayoutSpec(deviceClass: AuroraDeviceClass): HybridDiscoveryStripLayoutSpec {
    return when (deviceClass) {
        AuroraDeviceClass.Phone -> HybridDiscoveryStripLayoutSpec(
            cardWidth = 128,
            sectionHorizontalPadding = 24,
            rowSpacing = 14,
        )
        AuroraDeviceClass.TabletCompact -> HybridDiscoveryStripLayoutSpec(
            cardWidth = 152,
            sectionHorizontalPadding = 28,
            rowSpacing = 16,
        )
        AuroraDeviceClass.TabletExpanded -> HybridDiscoveryStripLayoutSpec(
            cardWidth = 176,
            sectionHorizontalPadding = 32,
            rowSpacing = 18,
        )
    }
}

/** Карточка discovery: гармонизирована с HomeHubRecentPosterCard (постер 0.9, скругление 16dp/18dp, текст под постером). */
@Composable
internal fun DiscoveryPosterCard(
    title: String,
    coverUrl: String?,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    deviceClass: AuroraDeviceClass = AuroraDeviceClass.Phone,
    coverMediaType: DiscoveryMediaType? = null,
    coverProvider: String? = null,
    onLongClick: (() -> Unit)? = null,
    // Бейдж «откроется напрямую»: микро-молния в углу постера, читается до тапа.
    showsDirectOpenBadge: Boolean = false,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val posterSpec = remember(deviceClass) {
        resolveHomeHubRecentPosterCardSpec(deviceClass)
    }
    val surfaceSpec = remember(colors.isDark) {
        resolveHomeHubRecentPosterSurfaceSpec(colors.isDark)
    }
    val cardShape = RoundedCornerShape(18.dp)
    val posterShape = RoundedCornerShape(16.dp)
    val fallbackPainter = rememberThemeAwareCoverErrorPainter(
        variant = AuroraCoverPlaceholderVariant.Portrait,
    )
    val isLightTheme = !colors.isDark && !colors.isEInk
    val outerSurface = if (colors.isDark) {
        colors.glass.copy(alpha = surfaceSpec.containerAlpha)
    } else if (colors.isEInk) {
        resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Glass)
    } else {
        Color.Transparent
    }
    val posterSurface = if (colors.isDark) {
        colors.cardBackground.copy(alpha = surfaceSpec.posterAlpha)
    } else {
        resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Subtle)
    }

    val cardContent: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(6.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(posterSpec.posterAspectRatio)
                    .clip(posterShape)
                    .background(posterSurface)
                    .then(
                        if (colors.isDark || colors.isEInk) {
                            Modifier.border(
                                width = 1.dp,
                                color = if (colors.isDark) {
                                    Color.White.copy(alpha = 0.06f)
                                } else {
                                    Color.Black.copy(alpha = 0.04f)
                                },
                                shape = posterShape,
                            )
                        } else {
                            Modifier
                        },
                    ),
            ) {
                val posterContext = LocalContext.current
                val posterCoverReloadTick = rememberCoverReloadTick()
                val posterCoverRequest = remember(
                    posterContext,
                    coverUrl,
                    coverMediaType,
                    coverProvider,
                    posterCoverReloadTick,
                ) {
                    buildAuroraCoverImageRequest(
                        posterContext,
                        discoveryCoverData(coverMediaType, coverProvider, coverUrl),
                    )
                }
                AsyncImage(
                    model = posterCoverRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = rememberAuroraPosterColorFilter(),
                    modifier = Modifier.fillMaxSize(),
                    error = fallbackPainter,
                    fallback = fallbackPainter,
                )
                if (showsDirectOpenBadge) {
                    DirectOpenBadge(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                }
            }
            Spacer(Modifier.height(posterSpec.textTopSpacingDp.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = posterSpec.textBlockMinHeightDp.dp)
                    .padding(horizontal = posterSpec.textHorizontalPaddingDp.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = posterSpec.titleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = colors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(),
            onClick = {
                appHaptics.tap()
                onClick()
            },
            onLongClick = {
                appHaptics.tap()
                onLongClick()
            },
        )
    } else {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(),
            onClick = {
                appHaptics.tap()
                onClick()
            },
        )
    }

    if (isLightTheme) {
        Box(
            modifier = modifier
                .drawBehind {
                    val radius = 18.dp.toPx()
                    val cornerRadius = CornerRadius(radius, radius)
                    val neutralOffsetY = 3.dp.toPx()
                    val warmOffsetY = 5.dp.toPx()
                    val neutralInset = 1.dp.toPx()
                    val warmInset = 3.dp.toPx()

                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.035f),
                        topLeft = Offset(x = neutralInset, y = neutralOffsetY),
                        size = Size(width = size.width - neutralInset * 2, height = size.height),
                        cornerRadius = cornerRadius,
                    )
                    drawRoundRect(
                        color = Color(0xFF6B4E28).copy(alpha = 0.04f),
                        topLeft = Offset(x = warmInset, y = warmOffsetY),
                        size = Size(width = size.width - warmInset * 2, height = size.height),
                        cornerRadius = cornerRadius,
                    )
                }
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.78f),
                            Color.White.copy(alpha = 0.68f),
                            Color.White.copy(alpha = 0.60f),
                        ),
                    ),
                    shape = cardShape,
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.75f),
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.12f),
                        ),
                    ),
                    shape = cardShape,
                )
                .clip(cardShape)
                .then(clickModifier),
        ) {
            cardContent()
        }
    } else {
        Box(
            modifier = modifier
                .clip(cardShape)
                .background(outerSurface)
                .then(
                    if (colors.isDark || colors.isEInk) {
                        Modifier.border(
                            width = 1.dp,
                            color = if (colors.isDark) {
                                Color.White.copy(alpha = 0.06f)
                            } else {
                                Color.Black.copy(alpha = 0.05f)
                            },
                            shape = cardShape,
                        )
                    } else {
                        Modifier
                    },
                )
                .then(clickModifier),
        ) {
            cardContent()
        }
    }
}

/**
 * Микро-бейдж «откроется напрямую» (молния) на привязанных карточках: ожидание
 * читается до тапа. Полупрозрачная подложка + тонкий бордер — как плавающие
 * иконки Stage, читается на светлых и тёмных обложках; в e-ink — контурно.
 */
@Composable
internal fun DirectOpenBadge(modifier: Modifier = Modifier) {
    val colors = AuroraTheme.colors
    val badgeShape = CircleShape
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(badgeShape)
            .background(
                if (colors.isEInk) {
                    Color.White.copy(alpha = 0.85f)
                } else {
                    Color.Black.copy(alpha = 0.45f)
                },
            )
            .border(
                width = 1.dp,
                color = if (colors.isEInk) colors.divider else Color.White.copy(alpha = 0.35f),
                shape = badgeShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Bolt,
            contentDescription = stringResource(AYMR.strings.for_you_direct_open_badge),
            tint = if (colors.isEInk) Color.Black else colors.accent,
            modifier = Modifier.size(11.dp),
        )
    }
}

@Composable
private fun discoveryReasonOrNull(item: HomeHubDiscoveryItem): String? {
    val discoveryPreferences = remember { Injekt.get<DiscoveryPreferences>() }
    val showReasons by discoveryPreferences.showReasons().collectAsStateWithLifecycle()
    if (!showReasons) return null
    val similarTemplate = stringResource(AYMR.strings.for_you_reason_similar)
    val trendTemplate = stringResource(AYMR.strings.for_you_reason_trending)
    val nextTemplate = stringResource(AYMR.strings.for_you_reason_season_next)
    return discoveryReasonText(item, similarTemplate, trendTemplate, nextTemplate)
}

/** Круглая кнопка обновления ленты (общая для заголовка ForYouSection и Hybrid-полосы). */
@Composable
private fun DiscoveryRefreshIconButton(isRefreshing: Boolean, onClick: () -> Unit) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val rotationAnim = rememberInfiniteTransition(label = "discovery_refresh_rot")
    val rotationAngle by if (isRefreshing) {
        rotationAnim.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "refresh_angle",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val refreshRimBrush = remember(colors) {
        if (colors.isEInk) {
            SolidColor(colors.divider)
        } else {
            Brush.verticalGradient(
                listOf(
                    if (colors.isDark) {
                        Color.White.copy(
                            alpha = 0.18f,
                        )
                    } else {
                        Color.White.copy(alpha = 0.50f)
                    },
                    Color.Transparent,
                ),
            )
        }
    }
    val refreshTintBrush = remember(colors) {
        if (colors.isEInk) {
            SolidColor(Color.Transparent)
        } else {
            Brush.verticalGradient(
                listOf(
                    if (colors.isDark) {
                        Color.White.copy(
                            alpha = 0.08f,
                        )
                    } else {
                        Color.White.copy(alpha = 0.20f)
                    },
                    Color.Transparent,
                ),
            )
        }
    }

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.Transparent)
            .border(
                1.dp,
                refreshRimBrush,
                CircleShape,
            )
            .background(
                brush = refreshTintBrush,
                shape = CircleShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 16.dp),
                onClick = {
                    appHaptics.tap()
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Refresh,
            contentDescription = stringResource(AYMR.strings.for_you_refresh),
            tint = colors.textPrimary,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { rotationZ = rotationAngle },
        )
    }
}

/** Тизер-секция «Для тебя» на Home Hub: заголовок + «Ещё» + горизонтальный рельс карточек. */
@Composable
internal fun ForYouSection(
    items: List<HomeHubDiscoveryItem>,
    coverMediaType: DiscoveryMediaType,
    onMoreClick: () -> Unit,
    onItemClick: (HomeHubDiscoveryItem) -> Unit,
    onLongClick: ((HomeHubDiscoveryItem) -> Unit)? = null,
    isRefreshing: Boolean = false,
    onRefreshClick: (() -> Unit)? = null,
    tracker: HomeDiscoveryExposureTracker? = null,
    isSectionActive: Boolean = true,
    isResumed: Boolean = true,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    if (items.isEmpty()) {
        EmptyForYouCard(onMoreClick = onMoreClick)
        return
    }
    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val contentMaxWidthDp = auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp
    val sectionHorizontalPadding = when (auroraAdaptiveSpec.deviceClass) {
        AuroraDeviceClass.Phone -> 24.dp
        AuroraDeviceClass.TabletCompact -> 28.dp
        AuroraDeviceClass.TabletExpanded -> 32.dp
    }
    val cardWidth = when (auroraAdaptiveSpec.deviceClass) {
        AuroraDeviceClass.Phone -> 128.dp
        AuroraDeviceClass.TabletCompact -> 152.dp
        AuroraDeviceClass.TabletExpanded -> 176.dp
    }
    val rowSpacing = when (auroraAdaptiveSpec.deviceClass) {
        AuroraDeviceClass.Phone -> 14.dp
        AuroraDeviceClass.TabletCompact -> 16.dp
        AuroraDeviceClass.TabletExpanded -> 18.dp
    }

    Column(modifier = Modifier.padding(top = 32.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .auroraCenteredMaxWidth(contentMaxWidthDp)
                .padding(horizontal = sectionHorizontalPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(AYMR.strings.aurora_for_you),
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                if (onRefreshClick != null) {
                    Spacer(Modifier.width(10.dp))
                    DiscoveryRefreshIconButton(isRefreshing = isRefreshing, onClick = onRefreshClick)
                }
            }
            Text(
                stringResource(AYMR.strings.aurora_more),
                color = colors.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    appHaptics.tap()
                    onMoreClick()
                },
            )
        }
        Spacer(Modifier.height(16.dp))
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .auroraCenteredMaxWidth(contentMaxWidthDp),
            contentPadding = PaddingValues(horizontal = sectionHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            items(
                items = items,
                key = { it.rowType.key + ":" + it.cleanTitle },
                contentType = { "home_hub_discovery_card" },
            ) { item ->
                val cardModifier = if (colors.isEInk) {
                    Modifier.width(cardWidth)
                } else {
                    Modifier
                        .animateItem(
                            fadeInSpec = tween(180, easing = LinearEasing),
                            fadeOutSpec = tween(180, easing = LinearEasing),
                            placementSpec = null,
                        )
                        .width(cardWidth)
                }.trackDiscoveryExposure(
                    tracker = tracker,
                    item = item,
                    isSectionActive = isSectionActive,
                    isResumed = isResumed,
                )
                DiscoveryPosterCard(
                    modifier = cardModifier,
                    title = item.title,
                    coverUrl = item.coverUrl,
                    subtitle = discoveryReasonOrNull(item),
                    deviceClass = auroraAdaptiveSpec.deviceClass,
                    coverMediaType = coverMediaType,
                    coverProvider = item.provider,
                    showsDirectOpenBadge = item.showsDirectOpenBadge(),
                    onLongClick = onLongClick?.let { { it(item) } },
                    onClick = {
                        appHaptics.tap()
                        onItemClick(item)
                    },
                )
            }
        }
    }
}

/** Полоса из 3 плиток под compact-hero в гибридном режиме (гармонизирована с HomeHubRecentPosterCard). */
@Composable
internal fun HybridDiscoveryStrip(
    items: List<HomeHubDiscoveryItem>,
    coverMediaType: DiscoveryMediaType,
    onMoreClick: () -> Unit,
    onItemClick: (HomeHubDiscoveryItem) -> Unit,
    onLongClick: ((HomeHubDiscoveryItem) -> Unit)? = null,
    isRefreshing: Boolean = false,
    onRefreshClick: (() -> Unit)? = null,
    tracker: HomeDiscoveryExposureTracker? = null,
    isSectionActive: Boolean = true,
    isResumed: Boolean = true,
) {
    if (items.isEmpty()) return
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val stripAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val stripMaxWidthDp = stripAdaptiveSpec.updatesMaxWidthDp ?: stripAdaptiveSpec.entryMaxWidthDp
    val layoutSpec = remember(stripAdaptiveSpec.deviceClass) {
        resolveHybridDiscoveryStripLayoutSpec(stripAdaptiveSpec.deviceClass)
    }
    val cardWidth = layoutSpec.cardWidth.dp
    val sectionHorizontalPadding = layoutSpec.sectionHorizontalPadding.dp
    val rowSpacing = layoutSpec.rowSpacing.dp
    val posterSpec = remember(stripAdaptiveSpec.deviceClass) {
        resolveHomeHubRecentPosterCardSpec(stripAdaptiveSpec.deviceClass)
    }
    val surfaceSpec = remember(colors.isDark) {
        resolveHomeHubRecentPosterSurfaceSpec(colors.isDark)
    }
    val cardShape = RoundedCornerShape(18.dp)
    val posterShape = RoundedCornerShape(16.dp)
    val isLightTheme = !colors.isDark && !colors.isEInk
    val outerSurface = if (colors.isDark) {
        colors.glass.copy(alpha = surfaceSpec.containerAlpha)
    } else if (colors.isEInk) {
        resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Glass)
    } else {
        Color.Transparent
    }
    val posterSurface = if (colors.isDark) {
        colors.cardBackground.copy(alpha = surfaceSpec.posterAlpha)
    } else {
        resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Subtle)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .auroraCenteredMaxWidth(stripMaxWidthDp)
            .padding(top = 12.dp, bottom = 16.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = sectionHorizontalPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(AYMR.strings.aurora_for_you),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (onRefreshClick != null) {
                    Spacer(Modifier.width(10.dp))
                    DiscoveryRefreshIconButton(isRefreshing = isRefreshing, onClick = onRefreshClick)
                }
            }
            Text(
                stringResource(AYMR.strings.for_you_all_picks),
                color = colors.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    appHaptics.tap()
                    onMoreClick()
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = sectionHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            items(
                items = items,
                key = { it.rowType.key + ":" + it.cleanTitle },
                contentType = { "hybrid_discovery_tile" },
            ) { item ->
                val cardModifier = Modifier
                    .width(cardWidth)
                    .trackDiscoveryExposure(
                        tracker = tracker,
                        item = item,
                        isSectionActive = isSectionActive,
                        isResumed = isResumed,
                    )
                DiscoveryPosterCard(
                    modifier = cardModifier,
                    title = item.title,
                    coverUrl = item.coverUrl,
                    subtitle = discoveryReasonOrNull(item),
                    deviceClass = stripAdaptiveSpec.deviceClass,
                    coverMediaType = coverMediaType,
                    coverProvider = item.provider,
                    showsDirectOpenBadge = item.showsDirectOpenBadge(),
                    onClick = {
                        appHaptics.tap()
                        onItemClick(item)
                    },
                    onLongClick = onLongClick?.let { { it(item) } },
                )
            }
            if (items.isNotEmpty()) {
                item(key = "hybrid_discovery_more", contentType = "hybrid_discovery_more") {
                    val moreContent: @Composable () -> Unit = {
                        Column(modifier = Modifier.padding(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(posterSpec.posterAspectRatio)
                                    .clip(posterShape)
                                    .background(posterSurface)
                                    .border(
                                        1.dp,
                                        Brush.verticalGradient(
                                            listOf(
                                                colors.accent.copy(alpha = 0.40f),
                                                Color.Transparent,
                                            ),
                                        ),
                                        posterShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(12.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(colors.accent.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Outlined.ArrowForward,
                                            contentDescription = null,
                                            tint = colors.accent,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        stringResource(AYMR.strings.for_you_all_picks),
                                        color = colors.textPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 14.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.height(posterSpec.textTopSpacingDp.dp))
                            Spacer(Modifier.height(posterSpec.textBlockMinHeightDp.dp))
                        }
                    }

                    if (isLightTheme) {
                        Box(
                            modifier = Modifier
                                .width(cardWidth)
                                .drawBehind {
                                    val radius = 18.dp.toPx()
                                    val cornerRadius = CornerRadius(radius, radius)
                                    val neutralOffsetY = 3.dp.toPx()
                                    val warmOffsetY = 5.dp.toPx()
                                    val neutralInset = 1.dp.toPx()
                                    val warmInset = 3.dp.toPx()

                                    drawRoundRect(
                                        color = Color.Black.copy(alpha = 0.035f),
                                        topLeft = Offset(x = neutralInset, y = neutralOffsetY),
                                        size = Size(width = size.width - neutralInset * 2, height = size.height),
                                        cornerRadius = cornerRadius,
                                    )
                                    drawRoundRect(
                                        color = Color(0xFF6B4E28).copy(alpha = 0.04f),
                                        topLeft = Offset(x = warmInset, y = warmOffsetY),
                                        size = Size(width = size.width - warmInset * 2, height = size.height),
                                        cornerRadius = cornerRadius,
                                    )
                                }
                                .background(
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.78f),
                                            Color.White.copy(alpha = 0.68f),
                                            Color.White.copy(alpha = 0.60f),
                                        ),
                                    ),
                                    shape = cardShape,
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.75f),
                                            Color.White.copy(alpha = 0.28f),
                                            Color.White.copy(alpha = 0.12f),
                                        ),
                                    ),
                                    shape = cardShape,
                                )
                                .clip(cardShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    onClick = {
                                        appHaptics.tap()
                                        onMoreClick()
                                    },
                                ),
                        ) {
                            moreContent()
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .width(cardWidth)
                                .clip(cardShape)
                                .background(outerSurface)
                                .then(
                                    if (colors.isDark || colors.isEInk) {
                                        Modifier.border(
                                            width = 1.dp,
                                            color = if (colors.isDark) {
                                                Color.White.copy(alpha = 0.06f)
                                            } else {
                                                Color.Black.copy(alpha = 0.05f)
                                            },
                                            shape = cardShape,
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    onClick = {
                                        appHaptics.tap()
                                        onMoreClick()
                                    },
                                ),
                        ) {
                            moreContent()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hero «Коллаж»: мозаика 440dp из топ-5 смешанного потока
 * (доминантная плитка + 4 малых), реролл = ротация кэша без сети.
 */
@Composable
internal fun DiscoveryHeroCollage(
    items: List<HomeHubDiscoveryItem>,
    coverMediaType: DiscoveryMediaType,
    onMoreClick: () -> Unit,
    onItemClick: (HomeHubDiscoveryItem) -> Unit,
    onLongClick: ((HomeHubDiscoveryItem) -> Unit)? = null,
    onItemFocused: (HomeHubDiscoveryItem) -> Unit = {},
) {
    if (items.isEmpty()) return
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val discoveryPreferences = remember { Injekt.get<DiscoveryPreferences>() }
    val intervalHours by discoveryPreferences.collageRotationIntervalHours().collectAsStateWithLifecycle()
    val animSpeed by discoveryPreferences.collageAnimationSpeed().collectAsStateWithLifecycle()
    var offset by rememberSaveable { mutableIntStateOf(discoveryPreferences.collageOffset(coverMediaType).get()) }
    var userInteractionToken by remember { mutableIntStateOf(0) }

    val updateOffset: (Int) -> Unit = { newOffset ->
        offset = newOffset
        discoveryPreferences.collageOffset(coverMediaType).set(newOffset)
    }

    // Авто-ротация с настраиваемым интервалом (от 1 до 24 ч, 0 = отключено)
    if (!colors.isEInk && items.size > 5 && intervalHours > 0) {
        LaunchedEffect(items.size, intervalHours, userInteractionToken) {
            val intervalMillis = intervalHours * 3600_000L
            while (isActive) {
                val now = System.currentTimeMillis()
                val lastTime = discoveryPreferences.collageLastRotationTime(coverMediaType).get()
                val elapsed = now - lastTime
                if (lastTime == 0L) {
                    discoveryPreferences.collageLastRotationTime(coverMediaType).set(now)
                    delay(intervalMillis)
                } else if (elapsed >= intervalMillis) {
                    updateOffset(offset + 1)
                    discoveryPreferences.collageLastRotationTime(coverMediaType).set(now)
                    delay(intervalMillis)
                } else {
                    val remaining = maxOf(1000L, intervalMillis - elapsed)
                    delay(remaining)
                    updateOffset(offset + 1)
                    discoveryPreferences.collageLastRotationTime(coverMediaType).set(System.currentTimeMillis())
                }
            }
        }
    }

    val staggerStep = when (animSpeed) {
        "fast" -> 40
        "smooth" -> 110
        else -> 70
    }

    // Окно до 5 плиток: свежее (48h-метки показа) вперёд, franchise-кластеризация —
    // не больше одной плитки серии; реролл (offset+1) меняет состав в обеих группах
    // (фикс «мёртвого реролла» и дублей при <5 айтемах сохранён).
    val tiles = remember(items, offset) { collageTiles(items, offset) }
    LaunchedEffect(tiles) {
        delay(1200)
        tiles.firstOrNull()?.let(onItemFocused)
    }
    val rest = tiles.drop(1)
    val col1 = listOfNotNull(rest.getOrNull(0), rest.getOrNull(2))
    val col2 = listOfNotNull(rest.getOrNull(1), rest.getOrNull(3))
    val outerShape = RoundedCornerShape(20.dp)
    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val contentMaxWidthDp = auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp

    Box(
        Modifier
            .fillMaxWidth()
            .auroraCenteredMaxWidth(contentMaxWidthDp)
            .height(440.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clip(outerShape)
            .background(colors.cardBackground)
            .then(
                if (colors.isDark || colors.isEInk) {
                    Modifier.border(1.dp, colors.divider, outerShape)
                } else {
                    Modifier
                },
            ),
    ) {
        Row(Modifier.fillMaxSize().padding(5.dp)) {
            AnimatedContent(
                targetState = tiles[0],
                transitionSpec = {
                    resolveCollageSlotTransition(
                        delayMillis = 0,
                        isEInk = colors.isEInk,
                        speed = animSpeed,
                    )
                },
                modifier = Modifier.weight(1.55f).fillMaxHeight(),
                label = "collage_hero_slot",
            ) { targetHero ->
                CollageTile(
                    item = targetHero,
                    big = true,
                    modifier = Modifier.fillMaxSize(),
                    coverMediaType = coverMediaType,
                    coverProvider = targetHero.provider,
                    onClick = { onItemClick(targetHero) },
                    onLongClick = onLongClick?.let { { it(targetHero) } },
                )
            }
            if (col1.isNotEmpty()) {
                Spacer(Modifier.width(5.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    col1.forEachIndexed { index, item ->
                        if (index > 0) Spacer(Modifier.height(5.dp))
                        AnimatedContent(
                            targetState = item,
                            transitionSpec = {
                                resolveCollageSlotTransition(
                                    delayMillis = staggerStep + index * staggerStep,
                                    isEInk = colors.isEInk,
                                    speed = animSpeed,
                                )
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            label = "collage_col1_$index",
                        ) { targetItem ->
                            CollageTile(
                                item = targetItem,
                                big = false,
                                modifier = Modifier.fillMaxSize(),
                                coverMediaType = coverMediaType,
                                coverProvider = targetItem.provider,
                                onClick = { onItemClick(targetItem) },
                                onLongClick = onLongClick?.let { { it(targetItem) } },
                            )
                        }
                    }
                }
            }
            if (col2.isNotEmpty()) {
                Spacer(Modifier.width(5.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    col2.forEachIndexed { index, item ->
                        if (index > 0) Spacer(Modifier.height(5.dp))
                        AnimatedContent(
                            targetState = item,
                            transitionSpec = {
                                resolveCollageSlotTransition(
                                    delayMillis = (staggerStep * 3) + index * staggerStep,
                                    isEInk = colors.isEInk,
                                    speed = animSpeed,
                                )
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            label = "collage_col2_$index",
                        ) { targetItem ->
                            CollageTile(
                                item = targetItem,
                                big = false,
                                modifier = Modifier.fillMaxSize(),
                                coverMediaType = coverMediaType,
                                coverProvider = targetItem.provider,
                                onClick = { onItemClick(targetItem) },
                                onLongClick = onLongClick?.let { { it(targetItem) } },
                            )
                        }
                    }
                }
            }
        }

        // Кнопка обновления в правом верхнем углу: высокий контраст на любых фонах
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (colors.isEInk) {
                        colors.cardBackground
                    } else if (colors.isDark) {
                        Color.Black.copy(alpha = 0.75f)
                    } else {
                        Color.White.copy(alpha = 0.90f)
                    },
                )
                .border(
                    BorderStroke(
                        width = 1.dp,
                        color = if (colors.isEInk) colors.divider else Color.White.copy(alpha = 0.22f),
                    ),
                    CircleShape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 20.dp),
                    onClick = {
                        appHaptics.tap()
                        discoveryPreferences.collageLastRotationTime(coverMediaType).set(System.currentTimeMillis())
                        userInteractionToken++
                        updateOffset(offset + 1)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = stringResource(AYMR.strings.for_you_collage_reroll),
                tint = if (colors.isDark && !colors.isEInk) colors.accent else colors.textPrimary,
                modifier = Modifier.size(20.dp),
            )
        }

        // Кнопка перехода в стиле Aurora Hero CTA («Продолжить / Читать»)
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)) {
            val buttonInteractionSource = remember { MutableInteractionSource() }
            AuroraGlassCtaSurface(
                mode = AuroraHeroCtaMode.Aurora,
                onClick = {
                    appHaptics.tap()
                    onMoreClick()
                },
                modifier = Modifier.height(44.dp),
                isHome = true,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
                interactionSource = buttonInteractionSource,
            ) { contentColor ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(AYMR.strings.for_you_all_picks),
                        color = contentColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

internal fun resolveCollageSlotTransition(
    delayMillis: Int,
    isEInk: Boolean,
    speed: String = "normal",
): ContentTransform {
    return if (isEInk) {
        fadeIn(animationSpec = tween(0)) togetherWith fadeOut(animationSpec = tween(0))
    } else {
        val (enterDuration, exitDuration) = when (speed) {
            "fast" -> 250 to 200
            "smooth" -> 700 to 500
            else -> 420 to 300
        }
        val enter = fadeIn(animationSpec = tween(durationMillis = enterDuration, delayMillis = delayMillis)) +
            scaleIn(
                initialScale = 0.95f,
                animationSpec = tween(durationMillis = enterDuration, delayMillis = delayMillis),
            )
        val exit = fadeOut(animationSpec = tween(durationMillis = exitDuration)) +
            scaleOut(targetScale = 1.02f, animationSpec = tween(durationMillis = exitDuration))
        enter togetherWith exit
    }
}

// ==================== Hero «Кинематографичный фокус» (stage) ====================

/** Сколько слотов держим по каждую сторону от фокуса: 5 видимых (−2..+2) + 2 буфера (±3). */
private const val STAGE_BUFFER = 3

/**
 * B1: путь одного слота при драге. Смещение соседа в позе = 42% ширины слота,
 * слот = 60% ширины сцены: 0.42 * 0.60 = 0.252 — постер следует за пальцем 1:1.
 */
private const val STAGE_DRAG_TRAVEL_FRACTION = 0.252f

/** B2: порог визуального фокуса — подпись и подсветка переключаются при пересечении центра слота. */
private const val STAGE_FOCUS_EPSILON = 0.5f

/** B1: минимальное смещение драга, при котором скорость броска вообще учитывается: микро-драги не листают. */
private const val STAGE_FLICK_MIN_DP = 16f

/** B1: порог флика — скорость, проходящая страницу за 300 мс (страниц/мс). Не зависит от плотности экрана. */
private const val STAGE_FLICK_VELOCITY_MIN_PAGES = 1f / 300f

/** B1: потолок скорости броска (страниц/мс): реальные флики ~0.02, выше — цифрайзерные спайки. */
private const val STAGE_FLICK_VELOCITY_MAX_PAGES = 0.03f

/**
 * B1: драг меньше этой величины — максимум ОДИН слот за отпускание, независимо от скорости:
 * «небольшой свайп = следующий тайтл». До двух страниц доходит только длинный свайп
 * (примерно треть экрана при пути 1:1) — «больше порога = запускается скролл».
 */
private const val STAGE_SINGLE_STEP_DRAG_PAGES = 1.25f

/** B1: миллисекунды «свободного полёта», которые скорость проецирует после отпускания. */
private const val STAGE_FLING_PROJECTION_MILLIS = 120f

/** B1: порог выбора оси жеста — стандартный touch slop Android. */
private const val STAGE_TOUCH_SLOP_DP = 8f

/** B1: касание считается «ловлей летящей сцены», если визуальный центр ушёл от коммита. */
private const val STAGE_CAUGHT_EPSILON = 0.01f

private const val STAGE_KEN_BURNS_MIN_SCALE = 1.04f
private const val STAGE_KEN_BURNS_MAX_SCALE = 1.14f

/** Ken-burns профиль слота (B3): разбег наезда и направление дрейфа, от постера. */
internal data class StageKenBurnsProfile(
    val scaleAmplitude: Float,
    val driftXFraction: Float,
    val driftYFraction: Float,
)

/**
 * B3: профиль ken-burns по стабильному хешу cleanTitle — у каждого постера свой
 * разбег наезда и своё направление дрейфа, смена фокуса меняет характер движения.
 * |дрейф| ≤ амплитуда/2: наезд всегда накрывает сдвиг, пустых краёв не бывает.
 * Хеш прогоняется через avalanche-миксер: голый String.hashCode даёт
 * коррелированные младшие биты у похожих тайтлов (одинаковые профили).
 */
internal fun stageKenBurnsProfile(cleanTitle: String): StageKenBurnsProfile {
    val hash = mixStageHash(cleanTitle.hashCode())
    val amplitude = 0.05f + (hash ushr 8 and 0xF) / 15f * 0.07f
    val maxDrift = amplitude / 2f
    val dirX = if (hash and 0x1 == 0) 1f else -1f
    val dirY = if (hash and 0x2 == 0) 1f else -1f
    val driftX = dirX * (0.01f + (hash ushr 4 and 0x7) / 7f * 0.02f).coerceAtMost(maxDrift)
    val driftY = dirY * (0.005f + (hash ushr 12 and 0x3) / 3f * 0.01f).coerceAtMost(maxDrift)
    return StageKenBurnsProfile(
        scaleAmplitude = amplitude,
        driftXFraction = driftX,
        driftYFraction = driftY,
    )
}

/** Avalanche-финализатор хеша (murmur-подобный): равномерное распределение бит. */
internal fun mixStageHash(hash: Int): Int {
    var h = hash
    h = h xor (h ushr 16)
    h *= 0x7feb352d
    h = h xor (h ushr 15)
    h *= 0x846ca68b.toInt()
    h = h xor (h ushr 16)
    return h
}

/** Поза слота карусели: только числа — держим её чистой и тестируемой. */
@androidx.compose.runtime.Immutable
internal data class StageSlotPose(
    val scale: Float,
    val alpha: Float,
    val dimAlpha: Float,
    val translationXPercent: Float,
    val rotationYDeg: Float,
)

/**
 * Поза по расстоянию до фокуса: фокус → соседи (±1) → дальние (±2) → невидимый буфер (|rel| ≥ 3).
 * Затемнение выражено [StageSlotPose.dimAlpha], потому что brightness в graphicsLayer недоступен.
 */
internal fun resolveStageSlotPose(rel: Int): StageSlotPose {
    val sign = if (rel < 0) -1f else 1f
    return when (abs(rel)) {
        0 -> StageSlotPose(scale = 1f, alpha = 1f, dimAlpha = 0f, translationXPercent = 0f, rotationYDeg = 0f)
        1 -> StageSlotPose(
            scale = 0.82f,
            alpha = 1f,
            dimAlpha = 0.38f,
            translationXPercent = 42f * sign,
            rotationYDeg = -15f * sign,
        )
        2 -> StageSlotPose(
            scale = 0.7f,
            alpha = 0.72f,
            dimAlpha = 0.6f,
            translationXPercent = 76f * sign,
            rotationYDeg = -24f * sign,
        )
        else -> StageSlotPose(
            scale = 0.62f,
            alpha = 0f,
            dimAlpha = 0.7f,
            translationXPercent = 104f * sign,
            rotationYDeg = -28f * sign,
        )
    }
}

/** Линейная интерполяция позы: карусель движется плавно между целыми позициями. */
internal fun lerpStageSlotPose(from: StageSlotPose, to: StageSlotPose, fraction: Float): StageSlotPose {
    val f = fraction.coerceIn(0f, 1f)
    fun mix(a: Float, b: Float) = a + (b - a) * f
    return StageSlotPose(
        scale = mix(from.scale, to.scale),
        alpha = mix(from.alpha, to.alpha),
        dimAlpha = mix(from.dimAlpha, to.dimAlpha),
        translationXPercent = mix(from.translationXPercent, to.translationXPercent),
        rotationYDeg = mix(from.rotationYDeg, to.rotationYDeg),
    )
}

/** Данные слота берутся по модулю: у ленты нет ни начала, ни конца. */
internal fun stageItemIndex(center: Int, slot: Int, size: Int): Int {
    if (size <= 0) return 0
    return ((center + slot) % size + size) % size
}

/**
 * Ре-анкор центра при смене СОСТАВА ленты (items): центр переносится на новый
 * индекс тайтла, который был в фокусе; если тайтл исчез из подборки (скрыт, отмечен
 * как просмотренный), сцена остаётся на текущем слоте (по модулю размера),
 * показывая карточку-замену вместо скачка к началу. Без смены состава позиция сохраняется.
 * Чистая функция — тестируется без Compose.
 */
internal fun resolveStageReanchorCenter(
    itemsChanged: Boolean,
    previousFocusedTitle: String?,
    newOrderedTitles: List<String>,
    currentCenter: Int,
): Int {
    if (!itemsChanged || previousFocusedTitle == null) return currentCenter
    if (newOrderedTitles.isEmpty()) return currentCenter
    val newIndex = newOrderedTitles.indexOf(previousFocusedTitle)
    return if (newIndex >= 0) newIndex else 0
}

/**
 * Порядок hero-карусели Stage: непоказанные за 48h-окно вперёд, показанные —
 * в хвост. Если переданы [currentVisibleTitles] (при ручном реролле/обновлении),
 * тайтлы, не видимые прямо сейчас на экране, выносятся в начало групп.
 * Обе группы шафлятся по одному seed — реролл живой в обеих группах, детерминизм сохранён.
 * Уникальность здесь — приоритет порядка, не отсечение:
 * бесконечная карусель доходит до хвоста только когда свежее кончилось.
 */
internal fun stageHeroOrder(
    items: List<HomeHubDiscoveryItem>,
    seed: Int,
    currentVisibleTitles: Set<String> = emptySet(),
): List<HomeHubDiscoveryItem> {
    if (items.size <= 1) return items
    val random = kotlin.random.Random(seed)
    val fresh = items.filter { (it.shownAt ?: 0L) <= 0L }
    val shown = items.filter { (it.shownAt ?: 0L) > 0L }

    fun prioritizeNotVisible(pool: List<HomeHubDiscoveryItem>): List<HomeHubDiscoveryItem> {
        if (currentVisibleTitles.isEmpty()) return pool.shuffled(random)
        val notVisible = pool.filterNot { it.cleanTitle in currentVisibleTitles }.shuffled(random)
        val visible = pool.filter { it.cleanTitle in currentVisibleTitles }.shuffled(random)
        return notVisible + visible
    }

    if (fresh.isEmpty() || shown.isEmpty()) return prioritizeNotVisible(items)
    return prioritizeNotVisible(fresh) + prioritizeNotVisible(shown)
}

/**
 * Вычисляет целевой центр карусели Stage при реролле, чтобы видимые слоты (-2..2)
 * имели минимальное (при пуле >= 10 — строго нулевое) пересечение с [currentVisibleTitles].
 */
internal fun stageRerollTargetCenter(
    currentCenter: Int,
    currentVisibleTitles: Set<String>,
    newOrdered: List<HomeHubDiscoveryItem>,
): Int {
    val size = newOrdered.size
    if (size <= 0) return currentCenter
    if (size <= 5 || currentVisibleTitles.isEmpty()) {
        return currentCenter + 1
    }

    var bestCenter = currentCenter + 5
    var minOverlap = Int.MAX_VALUE

    for (step in 1 until size) {
        val candidateCenter = currentCenter + step
        val visibleAtCandidate = (-2..2).map { slot ->
            newOrdered[stageItemIndex(candidateCenter, slot, size)].cleanTitle
        }.toSet()

        val overlap = visibleAtCandidate.intersect(currentVisibleTitles).size
        if (overlap < minOverlap) {
            minOverlap = overlap
            bestCenter = candidateCenter
            if (minOverlap == 0) {
                break
            }
        }
    }
    return bestCenter
}

/**
 * Точечное обновление порядка карусели Stage при удалении/скрытии/отметке тайтла:
 * сохраняет относительный порядок и позиции всех оставшихся валидных карточек,
 * заменяя выпавшую карточку новым кандидатом из [newPool] на том же месте.
 */
internal fun patchStageOrder(
    currentOrdered: List<HomeHubDiscoveryItem>,
    newPool: List<HomeHubDiscoveryItem>,
): List<HomeHubDiscoveryItem> {
    if (currentOrdered.isEmpty()) return newPool
    if (newPool.isEmpty()) return emptyList()

    val validCleanTitles = newPool.mapTo(HashSet()) { it.cleanTitle }
    val currentCleanTitles = currentOrdered.mapTo(HashSet()) { it.cleanTitle }
    val replacementCandidates = ArrayDeque(newPool.filterNot { it.cleanTitle in currentCleanTitles })

    val result = mutableListOf<HomeHubDiscoveryItem>()
    for (item in currentOrdered) {
        if (item.cleanTitle in validCleanTitles) {
            val updated = newPool.firstOrNull { it.cleanTitle == item.cleanTitle } ?: item
            result.add(updated)
        } else {
            if (replacementCandidates.isNotEmpty()) {
                result.add(replacementCandidates.removeFirst())
            }
        }
    }

    val usedTitles = result.mapTo(HashSet()) { it.cleanTitle }
    for (candidate in replacementCandidates) {
        if (candidate.cleanTitle !in usedTitles) {
            result.add(candidate)
            usedTitles.add(candidate.cleanTitle)
        }
    }

    return result
}

/**
 * Плитки Коллажа: до 5, свежее (48h) вперёд, не больше одной плитки франшизы.
 * Коллаж рендерит полный пул подборки (как Stage), а не тизерное окно.
 */
internal fun collageTiles(items: List<HomeHubDiscoveryItem>, seed: Int): List<HomeHubDiscoveryItem> =
    filterFranchiseClusteringGeneric(
        items = stageHeroOrder(items, seed),
        titleExtractor = { it.title },
        cleanTitleExtractor = { it.cleanTitle },
        maxPerSeries = 1,
    ).take(5)

/** Длительности перехода карусели: e-ink и выключенные системные анимации дают мгновенную смену кадра. */
internal data class StageMotionSpec(val settleMillis: Int, val fadeMillis: Int)

internal fun resolveStageMotionSpec(speed: String, isEInk: Boolean, animationsEnabled: Boolean): StageMotionSpec {
    if (isEInk || !animationsEnabled) return StageMotionSpec(settleMillis = 0, fadeMillis = 0)
    return when (speed) {
        "fast" -> StageMotionSpec(settleMillis = 250, fadeMillis = 200)
        "smooth" -> StageMotionSpec(settleMillis = 700, fadeMillis = 500)
        else -> StageMotionSpec(settleMillis = 420, fadeMillis = 300)
    }
}

/**
 * B1: цель довода после отпускания (чистая, тестируемая). Все расстояния и скорость —
 * в страницах: px конвертируются в месте жеста, поэтому проекция не зависит от плотности экрана.
 * Скорость не ПРИБАВЛЯЕТ страницы к протащенному смещению (иначе сосед + флик = +3), а проецирует
 * продолжение полёта от позиции пальца; суммарный шаг от слота на касании ограничен [maxPages].
 * Драг меньше [STAGE_SINGLE_STEP_DRAG_PAGES] — шаг ограничен одним слотом: небольшой свайп
 * всегда даёт следующий тайтл, до двух доходит только длинный свайп.
 * Флик действует только когда скорость СОВПАДАЕТ по направлению с драгом: отскок пальца при
 * подъёме (скорость против смещения) — это не флик, сцена доводится к ближайшему слоту.
 * Микро-драг (< [flickMinPages]) всегда возвращает к слоту на касании.
 */
internal fun resolveStageDragTarget(
    dragBase: Float,
    intent: Float,
    velocityPagesPerMs: Float,
    displacementPages: Float,
    flickMinPages: Float,
    maxPages: Int = 2,
): Int {
    val base = dragBase.roundToInt()
    if (abs(displacementPages) < flickMinPages) return base
    val isFlick = abs(velocityPagesPerMs) >= STAGE_FLICK_VELOCITY_MIN_PAGES &&
        velocityPagesPerMs * displacementPages > 0f
    val projected = if (isFlick) {
        // Свайп влево (velocity < 0) уводит сцену вперёд: проекция со знаком минус.
        intent - velocityPagesPerMs * STAGE_FLING_PROJECTION_MILLIS
    } else {
        intent
    }
    // Небольшой драг — максимум один слот: скорость лишь доталкивает до границы, не дальше.
    val deltaCap = if (abs(displacementPages) < STAGE_SINGLE_STEP_DRAG_PAGES) 1 else maxPages
    val delta = (projected.roundToInt() - base).coerceIn(-deltaCap, deltaCap)
    return base + delta
}

/** Авто-ротация: та же политика, что у фоновых анимаций Aurora (e-ink, lifecycle, системные анимации). */
internal fun shouldAutoRotateStage(
    isEInk: Boolean,
    intervalHours: Int,
    isLifecycleResumed: Boolean,
    systemAnimationsEnabled: Boolean,
    isScrollInProgress: Boolean = false,
): Boolean = !isScrollInProgress && shouldAnimateAuroraBackground(
    userEnabled = !isEInk && intervalHours > 0,
    isLifecycleResumed = isLifecycleResumed,
    systemAnimationsEnabled = systemAnimationsEnabled,
)

/**
 * Hero «Кинематографичный фокус»: бесконечная карусель подборки — один постер в фокусе,
 * соседи уходят в перспективу. Слоты живут в окне ±3 от непрерывного центра, данные берутся
 * по модулю, поэтому листание идёт по кругу в обе стороны. Драг (B1) ведёт сцену за пальцем,
 * подпись (B2) и ken-burns-профиль (B3) сменяются по визуальному фокусу, за фокусом — ambient-свечение (B6).
 */
@Composable
internal fun DiscoveryHeroStage(
    items: List<HomeHubDiscoveryItem>,
    coverMediaType: DiscoveryMediaType,
    onMoreClick: () -> Unit,
    onItemClick: (HomeHubDiscoveryItem) -> Unit,
    onLongClick: ((HomeHubDiscoveryItem) -> Unit)? = null,
    onItemFocused: (HomeHubDiscoveryItem) -> Unit = {},
) {
    if (items.isEmpty()) return
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val discoveryPreferences = remember { Injekt.get<DiscoveryPreferences>() }
    val intervalHours by discoveryPreferences.stageRotationIntervalHours().collectAsStateWithLifecycle()
    val speed by discoveryPreferences.stageAnimationSpeed().collectAsStateWithLifecycle()
    var offset by rememberSaveable { mutableIntStateOf(discoveryPreferences.stageOffset(coverMediaType).get()) }
    var userInteractionToken by remember { mutableIntStateOf(0) }
    var center by rememberSaveable { mutableIntStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }

    // B1: непрерывный центр сцены. Драг двигает его за пальцем (snapTo), отпускание/кнопки/авто-ротация
    // доводят tween'ом от текущей визуальной позиции — без скачков. Касание ловит сцену в любой точке
    // (см. жест ниже). Значение читается только внутри graphicsLayer/derivedStateOf: рекомпозиций на кадр нет.
    val centerAnim = remember { Animatable(center.toFloat()) }
    val scope = rememberCoroutineScope()

    // 48h-уникальность hero: непоказанное вперёд (см. stageHeroOrder), реролл — seeded
    // shuffle внутри групп. Фокус и авто-ротация ходят по свежему в первую очередь.
    val orderedState = remember { mutableStateOf(stageHeroOrder(items, offset)) }
    val lastOffset = remember { mutableIntStateOf(offset) }
    val lastItems = remember { mutableStateOf(items) }

    if (offset != lastOffset.intValue) {
        lastOffset.intValue = offset
        lastItems.value = items
    } else if (items !== lastItems.value) {
        if (orderedState.value.isEmpty()) {
            orderedState.value = stageHeroOrder(items, offset)
        } else {
            val currentTitles = orderedState.value.map { it.cleanTitle }.toSet()
            val newTitles = items.map { it.cleanTitle }.toSet()
            if (currentTitles != newTitles) {
                orderedState.value = patchStageOrder(orderedState.value, items)
            }
        }
        lastItems.value = items
    }
    val ordered = orderedState.value
    var focusMarkJob by remember { mutableStateOf<Job?>(null) }
    fun markFocusedSoon() {
        focusMarkJob?.cancel()
        focusMarkJob = scope.launch {
            delay(1200) // визуальная устойчивость фокуса, не пересборка
            ordered.getOrNull(stageItemIndex(center, 0, ordered.size))?.let(onItemFocused)
        }
    }
    LaunchedEffect(ordered) { markFocusedSoon() }
    val systemAnimationsEnabled = ValueAnimator.areAnimatorsEnabled()
    val motionSpec = remember(speed, colors.isEInk, systemAnimationsEnabled) {
        resolveStageMotionSpec(speed = speed, isEInk = colors.isEInk, animationsEnabled = systemAnimationsEnabled)
    }

    // Единая точка довода [settleTo]: коммит центра и анимация всегда идут парой, все источники
    // движения (кнопки, авто-ротация, жесты) проходят только через неё. Спокойный довод —
    // fast-out-slow-in (tween без bounce: недодемпфированная пружина давала «заряженность»
    // на микро-драгах); флик — linear-out-slow-in: импульс продолжается быстро и тормозит к слоту.
    // e-ink и выключенные анимации — мгновенно.
    // Пользовательское движение штампует stageLastRotationTime: авто-ротация не дёргает
    // ленту сразу после ручного перехода (штамп учитывается при следующем пересчёте ожидания).
    fun settleTo(target: Int, isFlick: Boolean, userInitiated: Boolean = true) {
        if (userInitiated) {
            discoveryPreferences.stageLastRotationTime(coverMediaType).set(System.currentTimeMillis())
        }
        if (target != center) {
            center = target
        }
        markFocusedSoon()
        if (motionSpec.settleMillis == 0) {
            scope.launch(start = CoroutineStart.UNDISPATCHED) { centerAnim.snapTo(target.toFloat()) }
            return
        }
        val easing = if (isFlick) LinearOutSlowInEasing else FastOutSlowInEasing
        scope.launch { centerAnim.animateTo(target.toFloat(), tween(motionSpec.settleMillis, easing = easing)) }
    }

    // Ре-анкор фокуса при смене СОСТАВА ленты (items) — синхронно, до первого кадра:
    // центр переносится на новый индекс сфокусированного тайтла (если он остался),
    // иначе сцена сбрасывается к началу. Реролл (смена offset без смены items) НЕ
    // ре-анкорит — «смена порядка под позицией» сохранена by design.
    val focusAnchor = remember {
        object {
            var items: List<HomeHubDiscoveryItem>? = null
            var orderedTitles: List<String> = emptyList()
            var center: Int = 0
        }
    }
    if (ordered.isNotEmpty()) {
        val reanchorCenter = resolveStageReanchorCenter(
            itemsChanged = focusAnchor.items != null && focusAnchor.items !== items,
            previousFocusedTitle = focusAnchor.orderedTitles.getOrNull(
                stageItemIndex(focusAnchor.center, 0, focusAnchor.orderedTitles.size),
            ),
            newOrderedTitles = ordered.map { it.cleanTitle },
            currentCenter = center,
        )
        if (reanchorCenter != center) {
            center = reanchorCenter
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                centerAnim.snapTo(reanchorCenter.toFloat())
            }
            markFocusedSoon()
        }
        focusAnchor.items = items
        focusAnchor.orderedTitles = ordered.map { it.cleanTitle }
        focusAnchor.center = center
    }
    // Ken-burns у фокуса: состояние читается внутри graphicsLayer, поэтому кадры не рекомпозируют слоты.
    // B3: профиль (направление/дрейф) выбирается по постеру и применяется в слое слота.
    val kenBurnsTransition = rememberInfiniteTransition(label = "stage_ken_burns")
    val kenBurnsScale = kenBurnsTransition.animateFloat(
        initialValue = STAGE_KEN_BURNS_MIN_SCALE,
        targetValue = STAGE_KEN_BURNS_MAX_SCALE,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "stage_ken_burns_scale",
    )
    val lifecycleOwner = LocalLifecycleOwner.current
    var isLifecycleResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isLifecycleResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val contentMaxWidthDp = auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp
    val outerShape = RoundedCornerShape(20.dp)

    val isScrollInProgress = isDragging || centerAnim.isRunning
    // Авто-ротация по кругу: выключается в e-ink, при нулевом интервале, на паузе и без системных анимаций.
    if (ordered.size > 1) {
        if (shouldAutoRotateStage(
                colors.isEInk,
                intervalHours,
                isLifecycleResumed,
                systemAnimationsEnabled,
                isScrollInProgress,
            )
        ) {
            LaunchedEffect(ordered.size, intervalHours, userInteractionToken) {
                val intervalMillis = intervalHours * 3600_000L
                while (isActive) {
                    val last = discoveryPreferences.stageLastRotationTime(coverMediaType).get()
                    val elapsed = if (last == 0L) 0L else System.currentTimeMillis() - last
                    delay(if (last == 0L) intervalMillis else (intervalMillis - elapsed).coerceAtLeast(1000L))
                    if (isDragging || centerAnim.isRunning) continue
                    // Пользователь взаимодействовал во время ожидания (settleTo обновил штамп):
                    // пересчитываем остаток вместо немедленного рывка ленты «под рукой».
                    val lastAfter = discoveryPreferences.stageLastRotationTime(coverMediaType).get()
                    if (lastAfter > last && lastAfter != 0L) {
                        val elapsedAfter = System.currentTimeMillis() - lastAfter
                        if (elapsedAfter < intervalMillis) continue
                    }
                    discoveryPreferences.stageLastRotationTime(coverMediaType).set(System.currentTimeMillis())
                    settleTo(center + 1, isFlick = false, userInitiated = false)
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .auroraCenteredMaxWidth(contentMaxWidthDp)
            .height(440.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            // Клип держим на контейнере: свечение затухает внутри области, а соседи не вылезают на соседние секции.
            .clip(outerShape),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 5.dp, vertical = 10.dp)
                .pointerInput(ordered.size) {
                    // Ключ зависит только от размера ленты: жест не рвётся при смене позиции.
                    // Путь одного слота согласован с позой соседа: палец и постер движутся 1:1.
                    val travelPx = size.width * STAGE_DRAG_TRAVEL_FRACTION
                    val slopPx = STAGE_TOUCH_SLOP_DP.dp.toPx()
                    val flickMinPx = STAGE_FLICK_MIN_DP.dp.toPx()
                    awaitEachGesture {
                        // Касание = ловля: сцена замирает под пальцем ДО slop и до выбора оси —
                        // «удержание» останавливает ленту в любой точке полёта.
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        // Остановка довода на касании — UNDISPATCHED: встаёт в очередь раньше
                        // snap'ов драга и выполняется без задержки диспетчера.
                        scope.launch(start = CoroutineStart.UNDISPATCHED) { centerAnim.stop() }
                        // База драга — позиция сцены на момент касания: захват середины довода
                        // не телепортирует карусель к целому слоту.
                        val dragBase = centerAnim.value
                        val caughtMoving = abs(dragBase - center) > STAGE_CAUGHT_EPSILON
                        var lockedAxis = 0 // 0 — ось не выбрана, 1 — горизонталь (наш драг), −1 — вертикаль
                        var totalDx = 0f
                        var totalDy = 0f
                        var dragAccumulator = 0f
                        val pointerId = down.id
                        try {
                            while (true) {
                                // Initial-пасс: родитель видит движение раньше детей — потребление здесь
                                // глушит click/long-press слотов и вертикальный скролл родителя.
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                if (!change.pressed) {
                                    // Отпускание отслеживаемого пальца — финализируем жест.
                                    // Якорь финальной позиции: без него VelocityTracker считает скорость
                                    // по устаревшим сэмплам и выдаёт спайк при подъёме пальца.
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    if (lockedAxis == 1 || (caughtMoving && lockedAxis == 0)) {
                                        change.consume()
                                    }
                                    break
                                }
                                val delta = change.positionChange()
                                if (delta != Offset.Zero) {
                                    if (lockedAxis == 0) {
                                        totalDx += delta.x
                                        totalDy += delta.y
                                        if (abs(totalDx) > slopPx || abs(totalDy) > slopPx) {
                                            lockedAxis = if (abs(totalDx) >= abs(totalDy)) 1 else -1
                                            if (lockedAxis == 1) {
                                                isDragging = true
                                                dragAccumulator = totalDx
                                                tracker.addPosition(change.uptimeMillis, change.position)
                                            }
                                        }
                                    } else if (lockedAxis == 1) {
                                        isDragging = true
                                        dragAccumulator += delta.x
                                        tracker.addPosition(change.uptimeMillis, change.position)
                                        // Сцена едет за пальцем. Restricted-ско́п жеста не позволяет
                                        // звать suspend напрямую — UNDISPATCHED-запуск выполняется
                                        // синхронно до первой приостановки, а snapTo на свободном
                                        // (уже остановленном на down) мьютексе не подвисает.
                                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                            centerAnim.snapTo(dragBase - dragAccumulator / travelPx)
                                        }
                                    }
                                }
                                if (lockedAxis == 1 || (caughtMoving && lockedAxis == 0)) {
                                    // Наш драг или ловля летящей сцены: клики слота под пальцем подавлены.
                                    change.consume()
                                }
                                // lockedAxis == −1: вертикаль отдаётся родителю без потребления.
                            }
                        } finally {
                            isDragging = false
                        }
                        if (lockedAxis == 1) {
                            // Отпускание с драгом: флик проецирует полёт от позиции пальца.
                            // Клампа скорости: реальные флики ~0.02 стр/мс, выше — цифрайзерные
                            // спайки (завышенная или развёрнутая скорость при подъёме пальца).
                            val maxVelocityPxPerMs = STAGE_FLICK_VELOCITY_MAX_PAGES * travelPx
                            val velocityPxPerMs = tracker.calculateVelocity().x
                                .coerceIn(-maxVelocityPxPerMs, maxVelocityPxPerMs)
                            val velocityPagesPerMs = velocityPxPerMs / travelPx
                            val displacementPages = dragAccumulator / travelPx
                            val target = resolveStageDragTarget(
                                dragBase = dragBase,
                                intent = dragBase - displacementPages,
                                velocityPagesPerMs = velocityPagesPerMs,
                                displacementPages = displacementPages,
                                flickMinPages = flickMinPx / travelPx,
                            )
                            val isFlick = abs(velocityPagesPerMs) >= STAGE_FLICK_VELOCITY_MIN_PAGES &&
                                abs(dragAccumulator) >= flickMinPx &&
                                velocityPagesPerMs * dragAccumulator > 0f
                            if (target != center) {
                                appHaptics.tap()
                                userInteractionToken++
                            }
                            settleTo(target, isFlick)
                        } else if (caughtMoving) {
                            // Тап/удержание поймали летящую сцену — спокойно доводим до ближайшего.
                            settleTo(centerAnim.value.roundToInt(), isFlick = false)
                        }
                        // Чистый тап по спокойной сцене: события не потреблены, clickable слота работает.
                    }
                },
        ) {
            // Окно слотов следует за ВИЗУАЛЬНЫМ центром: при пути 1:1 свайп на весь экран ≈ 4 страницы,
            // и коммитное окно ±3 оставило бы пустой край. derivedStateOf рекомпозирует только на
            // пересечении целых границ; слоты переиспользуются по key(absIndex).
            val windowBase by remember { derivedStateOf { centerAnim.value.roundToInt() } }
            for (absIndex in (windowBase - STAGE_BUFFER)..(windowBase + STAGE_BUFFER)) {
                val item = ordered[stageItemIndex(absIndex, 0, ordered.size)]
                val rel = absIndex - windowBase
                key(absIndex) {
                    StageSlot(
                        item = item,
                        rel = rel,
                        absIndex = absIndex,
                        distanceToFocus = abs(rel),
                        animatedCenter = centerAnim,
                        kenBurnsScale = kenBurnsScale,
                        kenBurnsProfile = remember(item.cleanTitle) {
                            stageKenBurnsProfile(item.cleanTitle)
                        },
                        useKenBurns = !colors.isEInk,
                        coverMediaType = coverMediaType,
                        onClick = {
                            appHaptics.tap()
                            if (rel == 0) {
                                onItemClick(item)
                            } else {
                                // Шаг всегда один: слоты живут окном вокруг центра, переброс не нужен.
                                settleTo(center + if (rel > 0) 1 else -1, isFlick = false)
                                userInteractionToken++
                            }
                        },
                        onLongClick = onLongClick?.let { callback -> { callback(item) } },
                    )
                }
            }
        }

        // Стрелки: у ленты нет конца, поэтому обе кнопки всегда активны.
        StageNavButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = stringResource(AYMR.strings.for_you_stage_prev),
            onClick = {
                appHaptics.tap()
                settleTo(center - 1, isFlick = false)
                userInteractionToken++
            },
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
        )
        StageNavButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(AYMR.strings.for_you_stage_next),
            onClick = {
                appHaptics.tap()
                settleTo(center + 1, isFlick = false)
                userInteractionToken++
            },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
        )

        // Реролл: новый порядок подборки, позиция сохраняется (без «проезда» через всю ленту).
        // Кнопка без подложки — только иконка с тенью-двойником, чтобы читалась на любом постере.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(34.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 17.dp),
                    onClick = {
                        appHaptics.tap()
                        val currentVisible = (-2..2).map { slot ->
                            ordered[stageItemIndex(center, slot, ordered.size)].cleanTitle
                        }.toSet()
                        offset += 1
                        val nextOrdered = stageHeroOrder(items, offset, currentVisible)
                        val targetCenter = stageRerollTargetCenter(center, currentVisible, nextOrdered)
                        center = targetCenter
                        orderedState.value = nextOrdered
                        lastOffset.intValue = offset
                        lastItems.value = items
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            centerAnim.snapTo(center.toFloat())
                        }
                        discoveryPreferences.stageOffset(coverMediaType).set(offset)
                        discoveryPreferences.stageLastRotationTime(coverMediaType).set(System.currentTimeMillis())
                        userInteractionToken++
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            StageFloatingIcon(
                icon = Icons.Filled.Refresh,
                contentDescription = stringResource(AYMR.strings.for_you_collage_reroll),
                size = 21.dp,
                tint = colors.accent,
            )
        }

        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)) {
            val buttonInteractionSource = remember { MutableInteractionSource() }
            AuroraGlassCtaSurface(
                mode = AuroraHeroCtaMode.Aurora,
                onClick = {
                    appHaptics.tap()
                    onMoreClick()
                },
                modifier = Modifier.height(44.dp),
                isHome = true,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
                interactionSource = buttonInteractionSource,
            ) { contentColor ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(AYMR.strings.for_you_all_picks),
                        color = contentColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * Иконка поверх постера без подложки: тень-двойник держит читаемость на светлых и тёмных обложках.
 * В e-ink тень не нужна — там иконка идёт сплошным чёрным.
 */
@Composable
private fun StageFloatingIcon(
    icon: ImageVector,
    contentDescription: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val colors = AuroraTheme.colors
    val onDarkTheme = colors.isDark && !colors.isEInk
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (!colors.isEInk) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (onDarkTheme) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.90f),
                modifier = Modifier
                    .size(size)
                    .offset(y = 1.dp),
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint ?: if (onDarkTheme) Color.White else colors.textPrimary,
            modifier = Modifier.size(size),
        )
    }
}

@Composable
private fun StageNavButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 16.dp),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        StageFloatingIcon(icon = icon, contentDescription = contentDescription, size = 22.dp)
    }
}

/**
 * Слот карусели. Обложка создаётся только для слотов внутри окна (|rel| ≤ 2), буферные слоты
 * остаются пустыми — так 7 загрузок Coil не плодятся на каждый переброс.
 */
@Composable
private fun BoxScope.StageSlot(
    item: HomeHubDiscoveryItem,
    rel: Int,
    absIndex: Int,
    distanceToFocus: Int,
    animatedCenter: Animatable<Float, AnimationVector1D>,
    kenBurnsScale: State<Float>,
    kenBurnsProfile: StageKenBurnsProfile,
    useKenBurns: Boolean,
    coverMediaType: DiscoveryMediaType,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val colors = AuroraTheme.colors
    val context = LocalContext.current
    val coverReloadTick = rememberCoverReloadTick()
    val coverRequest = remember(context, item.coverUrl, coverMediaType, item.provider, coverReloadTick) {
        buildAuroraCoverImageRequest(context, discoveryCoverData(coverMediaType, item.provider, item.coverUrl))
    }
    val fallbackPainter = rememberThemeAwareCoverErrorPainter(variant = AuroraCoverPlaceholderVariant.Wide)
    val tileShape = RoundedCornerShape(18.dp)
    // B2: фокус считается по визуальному центру — подпись, градиент и подсветка сменяются
    // в середине перелёта, а не по коммиту. derivedStateOf рекомпозирует слот только на пересечении.
    val isVisualFocus by remember {
        derivedStateOf { abs(absIndex - animatedCenter.value) < STAGE_FOCUS_EPSILON }
    }
    val focusReason = if (isVisualFocus) discoveryReasonOrNull(item) else null

    Box(
        Modifier
            .align(Alignment.Center)
            .fillMaxHeight(0.92f)
            .fillMaxWidth(0.6f)
            .zIndex(10f - distanceToFocus)
            .graphicsLayer {
                val relFloat = absIndex - animatedCenter.value
                val base = floor(relFloat).toInt()
                val pose = lerpStageSlotPose(
                    from = resolveStageSlotPose(base),
                    to = resolveStageSlotPose(base + 1),
                    fraction = relFloat - base,
                )
                translationX = size.width * pose.translationXPercent / 100f
                scaleX = pose.scale
                scaleY = pose.scale
                rotationY = pose.rotationYDeg
                cameraDistance = 12f * density
                alpha = pose.alpha
            }
            .then(
                if (isVisualFocus) {
                    Modifier.semantics {
                        contentDescription = listOfNotNull(item.title, focusReason).joinToString(", ")
                    }
                } else {
                    Modifier
                },
            )
            .clip(tileShape)
            .background(colors.cardBackground)
            .then(
                if (colors.isEInk) {
                    Modifier.border(1.dp, colors.divider, tileShape)
                } else {
                    Modifier
                },
            )
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            ),
    ) {
        if (distanceToFocus <= 2) {
            // Нейтральная подложка на время загрузки: тематическая Aurora-заглушка слишком яркая для hero-слота.
            val neutralCoverBrush = remember(colors) {
                Brush.verticalGradient(
                    listOf(
                        colors.cardBackground,
                        colors.divider.copy(alpha = if (colors.isEInk) 0.30f else 0.22f),
                    ),
                )
            }
            Box(Modifier.fillMaxSize().background(neutralCoverBrush))
            AsyncImage(
                model = coverRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = rememberAuroraPosterColorFilter(),
                // Ken-burns живёт только на обложке: раньше он масштабировал весь слот вместе с подписью.
                // B3: профиль по хешу постера — наезд/отъезд и дрейф различаются между соседями;
                // дрейф в фазе с наездом (|сдвиг| ≤ амплитуда/2), края не оголяются.
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (useKenBurns && isVisualFocus) {
                            val phase = (
                                (kenBurnsScale.value - STAGE_KEN_BURNS_MIN_SCALE) /
                                    (STAGE_KEN_BURNS_MAX_SCALE - STAGE_KEN_BURNS_MIN_SCALE)
                                ).coerceIn(0f, 1f)
                            val scale = 1f + kenBurnsProfile.scaleAmplitude * phase
                            scaleX = scale
                            scaleY = scale
                            translationX = size.width * kenBurnsProfile.driftXFraction * phase
                            translationY = size.height * kenBurnsProfile.driftYFraction * phase
                        } else {
                            scaleX = 1f
                            scaleY = 1f
                        }
                    },
                error = fallbackPainter,
                fallback = fallbackPainter,
            )
            // Затемнение соседей: brightness в graphicsLayer нет, поэтому кладём scrim-слой.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val relFloat = absIndex - animatedCenter.value
                        val base = floor(relFloat).toInt()
                        alpha = lerpStageSlotPose(
                            from = resolveStageSlotPose(base),
                            to = resolveStageSlotPose(base + 1),
                            fraction = relFloat - base,
                        ).dimAlpha
                    }
                    .background(if (colors.isEInk) Color.White else Color.Black),
            )
            if (isVisualFocus) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1.0f to if (colors.isEInk) Color.White.copy(alpha = 0.95f) else Color(0xCC04060A),
                        ),
                    ),
                )
            }
        }

        // V1: подпись переключается мгновенно на пересечении визуального фокуса (как в прототипе),
        // без enter/exit-анимаций — кадр меняется целиком, кино даёт движение сцены, а не текста.
        if (isVisualFocus) {
            Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 14.dp, bottom = 70.dp)) {
                Text(
                    item.title,
                    color = if (colors.isEInk) Color.Black else Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp,
                )
                focusReason?.let { reason ->
                    Text(
                        reason,
                        color = colors.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CollageTile(
    item: HomeHubDiscoveryItem,
    big: Boolean,
    modifier: Modifier = Modifier,
    coverMediaType: DiscoveryMediaType? = null,
    coverProvider: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = AuroraTheme.colors
    val context = LocalContext.current
    val appHaptics = LocalAppHaptics.current
    val coverReloadTick = rememberCoverReloadTick()
    val coverRequest = remember(context, item.coverUrl, coverMediaType, coverProvider, coverReloadTick) {
        buildAuroraCoverImageRequest(context, discoveryCoverData(coverMediaType, coverProvider, item.coverUrl))
    }
    val fallbackPainter = rememberThemeAwareCoverErrorPainter(variant = AuroraCoverPlaceholderVariant.Wide)
    val tileShape = RoundedCornerShape(16.dp)

    Box(
        modifier
            .clip(tileShape)
            .background(colors.cardBackground)
            .then(
                if (colors.isDark || colors.isEInk) {
                    Modifier.border(1.dp, colors.divider, tileShape)
                } else {
                    Modifier
                },
            )
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = {
                            appHaptics.tap()
                            onClick()
                        },
                        onLongClick = {
                            appHaptics.tap()
                            onLongClick()
                        },
                    )
                } else {
                    Modifier.clickable {
                        appHaptics.tap()
                        onClick()
                    }
                },
            ),
    ) {
        AsyncImage(
            model = coverRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = rememberAuroraPosterColorFilter(),
            modifier = Modifier.fillMaxSize(),
            error = fallbackPainter,
            fallback = fallbackPainter,
        )
        // Скрим только на большой плитке и только в нижней трети под заголовком:
        // постеры остаются яркими, как в обычных карточках, а малые плитки без
        // текста не затемняются вовсе.
        if (big) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1.0f to if (colors.isEInk) Color.White.copy(alpha = 0.95f) else Color(0xCC04060A),
                    ),
                ),
            )
        }
        if (big) {
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(
                    item.title,
                    color = if (colors.isEInk) Color.Black else Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp,
                )
                discoveryReasonOrNull(item)?.let { reason ->
                    Text(
                        reason,
                        color = colors.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Пустая лента: карточка-вход на полный экран «Для тебя» (там есть рефреш).
 * Без неё при пустом кэше рефреш недостижим (тизер скрывался вместе с точкой входа).
 */
@Composable
private fun EmptyForYouCard(onMoreClick: () -> Unit) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val emptyAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val emptyMaxWidthDp = emptyAdaptiveSpec.updatesMaxWidthDp ?: emptyAdaptiveSpec.entryMaxWidthDp
    Column(
        Modifier
            .fillMaxWidth()
            .auroraCenteredMaxWidth(emptyMaxWidthDp)
            .padding(start = 24.dp, end = 24.dp, top = 24.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (colors.isDark) colors.glass.copy(alpha = 0.10f) else colors.cardBackground)
            .then(
                if (colors.isDark || colors.isEInk) {
                    Modifier.border(1.dp, colors.divider, RoundedCornerShape(20.dp))
                } else {
                    Modifier
                },
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(AYMR.strings.for_you_empty_title),
            color = colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(AYMR.strings.for_you_empty_subtitle),
            color = colors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(colors.accent)
                .clickable {
                    appHaptics.tap()
                    onMoreClick()
                }
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(
                stringResource(AYMR.strings.for_you_all_picks),
                color = colors.textOnAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** B2: long-press меню — «Больше такого», «Просмотрено», скрыть тайтл или тег (TASTE). */
@Composable
internal fun DiscoveryHideOptionsSheet(
    itemTitle: String,
    tag: String?,
    onHide: () -> Unit,
    onBlacklistTag: (String) -> Unit,
    onDismiss: () -> Unit,
    // Taste Engine: явный лайк — сильный позитивный сигнал (жанры/источник тайтла).
    onMoreLikeThis: (() -> Unit)? = null,
    // Taste Engine: «просмотрено» — нейтральное исключение тайтла (вкус не трогает).
    onMarkConsumed: (() -> Unit)? = null,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val tagEnabled = !tag.isNullOrBlank()
    val supportsBlurBehind = rememberSupportsBlurBehind(colors.isEInk)
    val sheetContainer = when {
        colors.isEInk -> MaterialTheme.colorScheme.surfaceContainerHigh
        !supportsBlurBehind -> colors.surface
        colors.isDark -> Color.Black.copy(alpha = 0.70f)
        else -> Color.White.copy(alpha = 0.88f)
    }
    var sheetReveal by remember { mutableFloatStateOf(0f) }
    AdaptiveSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetContainer,
        scrimAlpha = if (supportsBlurBehind) 0f else 0.5f,
        applyStatusBarsPadding = false,
        onRevealChange = { sheetReveal = it },
    ) {
        AuroraSheetWindowFx(sheetReveal)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (colors.isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.15f),
                    ),
            )
            Text(
                itemTitle,
                color = colors.textPrimary,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp),
            )
            Box(
                modifier = Modifier
                    .padding(start = 24.dp, end = 24.dp, bottom = 10.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, colors.accent.copy(alpha = 0.22f), Color.Transparent),
                        ),
                    ),
            )
            Column(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (onMoreLikeThis != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                appHaptics.tap()
                                onMoreLikeThis()
                            }
                            .padding(vertical = 13.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            stringResource(AYMR.strings.for_you_more_like_this),
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                if (onMarkConsumed != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                appHaptics.tap()
                                onMarkConsumed()
                            }
                            .padding(vertical = 13.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.TaskAlt,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            stringResource(AYMR.strings.for_you_mark_consumed),
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            appHaptics.tap()
                            onHide()
                        }
                        .padding(vertical = 13.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.VisibilityOff,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        stringResource(AYMR.strings.for_you_tag_hide_title),
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = tagEnabled) {
                            appHaptics.tap()
                            tag?.let { onBlacklistTag(it) }
                        }
                        .padding(vertical = 13.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.LabelOff,
                        contentDescription = null,
                        tint = if (tagEnabled) colors.accent else colors.textSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            if (tagEnabled) {
                                stringResource(AYMR.strings.for_you_tag_blacklist_action, tag)
                            } else {
                                stringResource(AYMR.strings.for_you_tag_blacklist_action_generic)
                            },
                            color = if (tagEnabled) colors.textPrimary else colors.textSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        if (!tagEnabled) {
                            Text(
                                stringResource(AYMR.strings.for_you_tag_blacklist_disabled),
                                color = colors.textSecondary,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
