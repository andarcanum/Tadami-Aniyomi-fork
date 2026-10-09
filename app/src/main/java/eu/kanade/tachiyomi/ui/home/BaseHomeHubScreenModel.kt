package eu.kanade.tachiyomi.ui.home

import android.os.Handler
import android.os.Looper
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.ui.UserProfilePreferences
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO

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

    val effects: SharedFlow<HomeHubEffect> = _effects.asSharedFlow()

    protected fun emitEffect(effect: HomeHubEffect) {
        _effects.tryEmit(effect)
    }

    open fun rotateOrRefreshDiscovery() {}
    open fun onScreenReentry() {}

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
