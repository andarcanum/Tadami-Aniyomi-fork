package eu.kanade.tachiyomi.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Порог видимости площади карточки для засчитывания показа (50%). */
const val DISCOVERY_EXPOSURE_MIN_VISIBILITY_RATIO = 0.50f

/** Минимальная непрерывная длительность нахождения карточки на экране (1000 мс). */
const val DISCOVERY_EXPOSURE_MIN_DURATION_MS = 1000L

/**
 * Трекер реальных экспозиций рекомендаций Home-ленты (PR 5):
 * 1. Засчитывает показ обычной карточки только если она видна >= 50% площади непрерывно >= 1 сек.
 * 2. Учитывает активность вкладки (секции) и жизненный цикл экрана (RESUMED).
 * 3. Отменяет таймер при быстром пролистывании, смене таба или уходе приложения в фон.
 * 4. Направляет фокусную регистрацию Stage/Collage в единый трекер.
 */
internal class HomeDiscoveryExposureTracker(
    private val scope: CoroutineScope,
    private val onExposure: (HomeHubDiscoveryItem) -> Unit,
) {
    private val activeJobs = mutableMapOf<String, Job>()
    private val recordedTitlesWithTime = mutableMapOf<String, Long>()

    fun onVisibilityChanged(
        item: HomeHubDiscoveryItem,
        visibleRatio: Float,
        isSectionActive: Boolean,
        isResumed: Boolean,
    ) {
        val cleanTitle = item.cleanTitle
        if (cleanTitle.isBlank()) return

        val isEligible = visibleRatio >= DISCOVERY_EXPOSURE_MIN_VISIBILITY_RATIO &&
            isSectionActive &&
            isResumed

        if (!isEligible) {
            activeJobs.remove(cleanTitle)?.cancel()
            return
        }

        val now = System.currentTimeMillis()
        val lastRecorded = recordedTitlesWithTime[cleanTitle]
        if (lastRecorded != null && (now - lastRecorded) < DEDUPE_INTERVAL_MS) return
        if (activeJobs[cleanTitle]?.isActive == true) return

        activeJobs[cleanTitle] = scope.launch {
            delay(DISCOVERY_EXPOSURE_MIN_DURATION_MS)
            recordedTitlesWithTime[cleanTitle] = System.currentTimeMillis()
            activeJobs.remove(cleanTitle)
            onExposure(item)
        }
    }

    fun recordStageFocus(
        item: HomeHubDiscoveryItem,
        isSectionActive: Boolean,
        isResumed: Boolean,
    ) {
        val cleanTitle = item.cleanTitle
        if (cleanTitle.isBlank() || !isSectionActive || !isResumed) return
        val now = System.currentTimeMillis()
        val lastRecorded = recordedTitlesWithTime[cleanTitle]
        if (lastRecorded != null && (now - lastRecorded) < DEDUPE_INTERVAL_MS) return

        recordedTitlesWithTime[cleanTitle] = now
        onExposure(item)
    }

    fun onPauseOrInactive() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }

    fun clearSession() {
        onPauseOrInactive()
        recordedTitlesWithTime.clear()
    }

    companion object {
        const val DEDUPE_INTERVAL_MS = 15 * 60_000L
    }
}

/**
 * Modifier для отслеживания видимости карточки Home-ленты.
 * Рассчитывает пересечение геометрии карточки с экранным viewport (root).
 */
@Composable
internal fun Modifier.trackDiscoveryExposure(
    tracker: HomeDiscoveryExposureTracker?,
    item: HomeHubDiscoveryItem,
    isSectionActive: Boolean,
    isResumed: Boolean,
): Modifier {
    if (tracker == null) return this
    val currentTracker by rememberUpdatedState(tracker)
    val currentItem by rememberUpdatedState(item)
    val currentSectionActive by rememberUpdatedState(isSectionActive)
    val currentResumed by rememberUpdatedState(isResumed)

    val capturedItem = item
    val capturedTracker = tracker
    DisposableEffect(capturedItem.cleanTitle, isSectionActive, isResumed) {
        if (!isSectionActive || !isResumed) {
            capturedTracker.onVisibilityChanged(
                item = capturedItem,
                visibleRatio = 0f,
                isSectionActive = false,
                isResumed = false,
            )
        }
        onDispose {
            capturedTracker.onVisibilityChanged(
                item = capturedItem,
                visibleRatio = 0f,
                isSectionActive = false,
                isResumed = false,
            )
        }
    }

    return this.onGloballyPositioned { coordinates ->
        if (!coordinates.isAttached) {
            currentTracker.onVisibilityChanged(currentItem, 0f, currentSectionActive, currentResumed)
            return@onGloballyPositioned
        }
        val cardArea = coordinates.size.width * coordinates.size.height
        if (cardArea <= 0) {
            currentTracker.onVisibilityChanged(currentItem, 0f, currentSectionActive, currentResumed)
            return@onGloballyPositioned
        }
        val root = coordinates.findRootCoordinates()
        val rootRect = Rect(0f, 0f, root.size.width.toFloat(), root.size.height.toFloat())
        val cardBoundsInRoot = coordinates.boundsInRoot()
        val visibleLeft = maxOf(rootRect.left, cardBoundsInRoot.left)
        val visibleTop = maxOf(rootRect.top, cardBoundsInRoot.top)
        val visibleRight = minOf(rootRect.right, cardBoundsInRoot.right)
        val visibleBottom = minOf(rootRect.bottom, cardBoundsInRoot.bottom)
        val visibleWidth = maxOf(0f, visibleRight - visibleLeft)
        val visibleHeight = maxOf(0f, visibleBottom - visibleTop)
        val visibleArea = visibleWidth * visibleHeight
        val ratio = visibleArea / cardArea.toFloat()

        currentTracker.onVisibilityChanged(
            item = currentItem,
            visibleRatio = ratio,
            isSectionActive = currentSectionActive,
            isResumed = currentResumed,
        )
    }
}
