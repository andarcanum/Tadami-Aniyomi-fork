package eu.kanade.tachiyomi.ui.home

import android.os.Handler
import android.os.Looper
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.tachiyomi.ui.discovery.manualRefreshWorkInfoFlow
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.discovery.model.DiscoveryMediaType

internal abstract class BaseHomeHubScreenModel(
    protected val context: android.content.Context,
    initialState: HomeHubUiState,
    protected val userProfilePreferences: UserProfilePreferences,
) : StateScreenModel<HomeHubUiState>(initialState) {

    protected abstract val avatarFileName: String

    /**
     * Разовые UI-эффекты (тосты и т.п.). Модель не показывает их сама: пайплайны Home Hub
     * работают на Dispatchers.IO, а Toast требует Looper — прямой вызов из модели роняет процесс
     * (NPE «Can't toast on a thread that has not called Looper.prepare()», краш 0.62.9).
     * Без replay: если экран не подписан, эффект не показывается вовсе, а не всплывает позже
     * при возврате на вкладку.
     */
    private val _effects = MutableSharedFlow<HomeHubEffect>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _discoveryRefreshFailures = MutableStateFlow<Map<DiscoveryMediaType, String>>(emptyMap())

    val effects: SharedFlow<HomeHubEffect> = _effects.asSharedFlow()
    val discoveryRefreshFailures = _discoveryRefreshFailures.asStateFlow()

    fun acknowledgeDiscoveryRefreshFailure(mediaType: DiscoveryMediaType, requestId: String) {
        _discoveryRefreshFailures.update { pending ->
            if (pending[mediaType] == requestId) pending - mediaType else pending
        }
    }

    protected fun emitEffect(effect: HomeHubEffect) {
        _effects.tryEmit(effect)
    }

    protected fun observeDiscoveryRefreshFailure(preferences: DiscoveryPreferences, mediaType: DiscoveryMediaType) {
        screenModelScope.launchIO {
            manualRefreshWorkInfoFlow(context.workManager, preferences, mediaType).collectLatest { workInfo ->
                if (workInfo?.state == androidx.work.WorkInfo.State.FAILED) {
                    _discoveryRefreshFailures.update { it + (mediaType to workInfo.id.toString()) }
                }
            }
        }
    }

    protected open val discoveryController: HomeDiscoveryController? = null

    open fun likeDiscoveryItem(item: HomeHubDiscoveryItem) {
        discoveryController?.likeDiscoveryItem(item)
    }

    open fun restoreHiddenTeaserItem(item: HomeHubDiscoveryItem) {
        discoveryController?.restoreHiddenTeaserItem(item)
    }

    protected open fun onItemDismissedFromPool(cleanTitle: String) {
        discoveryController?.onItemDismissedFromPool(cleanTitle)
    }

    protected open fun onItemRestoredToPool(cleanTitle: String) {}

    open fun rotateOrRefreshDiscovery() {
        discoveryController?.rotateOrRefreshDiscovery()
    }

    open fun onScreenReentry() {
        discoveryController?.onScreenReentry()
    }

    open fun markDiscoveryItemShown(item: HomeHubDiscoveryItem) {
        discoveryController?.markDiscoveryItemShown(item)
    }

    protected abstract fun updateCacheUserName(name: String)
    protected abstract fun updateCacheUserAvatar(path: String)

    protected open suspend fun loadGreetingStats(): HomeGreetingStats = HomeGreetingStats()

    suspend fun resolveAndSetGreeting() {
        val stats = loadGreetingStats()
        val greetingSelection = HomeGreetingSession.resolveGreeting(
            userProfilePreferences = userProfilePreferences,
            stats = stats,
        )

        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val currentTimeOfDay = when (hour) {
            in 5..11 -> 0
            in 12..16 -> 1
            in 17..21 -> 2
            else -> 3
        }
        userProfilePreferences.lastGreetingId().set(greetingSelection.greetingId)
        userProfilePreferences.lastGreetingTimeOfDay().set(currentTimeOfDay)

        val wasReady = state.value.greetingReady
        if (!wasReady) {
            mutableState.update {
                it.copy(
                    greeting = greetingSelection.greeting,
                    greetingReady = true,
                )
            }
        }
    }

    protected fun initializeGreeting() {
        screenModelScope.launchIO {
            resolveAndSetGreeting()
        }
    }

    /**
     * PERF: Defer greeting + heavy stats loading until after the first frame.
     * This is critical for fast Home screen appearance on cold start.
     */
    protected fun initializeGreetingDeferred() {
        Handler(Looper.getMainLooper()).post {
            screenModelScope.launchIO {
                resolveAndSetGreeting()
            }
        }
    }

    fun updateUserName(name: String) {
        val previousName = userProfilePreferences.name().get()
        userProfilePreferences.name().set(name)
        if (name != previousName) {
            userProfilePreferences.nameEdited().set(true)
        }
        updateCacheUserName(name)
        mutableState.update { it.copy(userName = name) }
    }

    fun updateUserAvatar(uriString: String) {
        try {
            val uri = android.net.Uri.parse(uriString)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return
            val file = java.io.File(context.filesDir, avatarFileName)
            file.outputStream().use { output ->
                inputStream.use { input -> input.copyTo(output) }
            }
            val path = file.absolutePath
            userProfilePreferences.avatarUrl().set(path)
            updateCacheUserAvatar(path)
            mutableState.update { it.copy(userAvatar = path) }
        } catch (_: Exception) {
        }
    }
}

/** Разовые UI-эффекты Home Hub, которые показывает экран (см. [BaseHomeHubScreenModel.effects]). */
internal sealed interface HomeHubEffect {
    /** Пул подборки «Для вас» мал или пересекается с текущим окном: обновление почти не меняет ленту. */
    data object DiscoveryPoolLimited : HomeHubEffect
}

/**
 * Минимальный интервал между ротациями тизера «Для тебя» по возвращению на экран.
 * Ротация метит окно тизера как показанное (48h-уникальность): дебаунс 500 мс
 * позволял флипу вкладок отравить всё окно за секунды. 60 с = «вернулся позже»
 * всё ещё ротирует, а частые возвраты пул не метят.
 */
internal const val DISCOVERY_REENTRY_COOLDOWN_MS = 60_000L

/** Нужна ли ротация тизера на reentry: прошла [cooldownMs] с последней ротации. */
internal fun shouldAdvanceDiscoveryTeaserOnReentry(
    nowMs: Long,
    lastReentryTimeMs: Long,
    cooldownMs: Long = DISCOVERY_REENTRY_COOLDOWN_MS,
): Boolean = lastReentryTimeMs <= 0L || (nowMs - lastReentryTimeMs) >= cooldownMs

/**
 * Ограничение размера сессионного кэша исключённых тайтлов:
 * если размер превышает [maxLimit], оставляет последние [keepRecent] элементов.
 */
internal fun pruneDismissedSessionTitles(
    titles: Set<String>,
    maxLimit: Int = 100,
    keepRecent: Int = 50,
): Set<String> {
    if (titles.size <= maxLimit) return titles
    return titles.toList().takeLast(keepRecent).toSet()
}
