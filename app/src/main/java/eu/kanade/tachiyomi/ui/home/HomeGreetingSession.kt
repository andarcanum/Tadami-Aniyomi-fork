package eu.kanade.tachiyomi.ui.home

import eu.kanade.domain.ui.UserProfilePreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class HomeGreetingStats(
    val achievementCount: Int = 0,
    val episodesWatched: Int = 0,
    val librarySize: Int = 0,
    val currentStreak: Int = 0,
)

internal data class HomeGreetingSelectionRequest(
    val lastOpenedTime: Long,
    val achievementCount: Int,
    val episodesWatched: Int,
    val librarySize: Int,
    val currentStreak: Int,
    val isFirstTime: Boolean,
    val totalLaunches: Long,
    val recentGreetingIds: List<String>,
    val recentScenarioIds: List<String>,
    val blockedGreetingIds: Set<String>,
    val nowMillis: Long,
)

internal fun interface HomeGreetingSelector {
    fun select(request: HomeGreetingSelectionRequest): GreetingProvider.GreetingSelection
}

internal object HomeGreetingSession {
    private val mutex = Mutex()

    @Volatile
    private var cachedSelection: GreetingProvider.GreetingSelection? = null

    suspend fun resolveGreeting(
        userProfilePreferences: UserProfilePreferences,
        stats: HomeGreetingStats = HomeGreetingStats(),
        nowMillis: () -> Long = System::currentTimeMillis,
        selector: HomeGreetingSelector = HomeGreetingSelector { request ->
            GreetingProvider.selectGreeting(
                lastOpenedTime = request.lastOpenedTime,
                achievementCount = request.achievementCount,
                episodesWatched = request.episodesWatched,
                librarySize = request.librarySize,
                currentStreak = request.currentStreak,
                isFirstTime = request.isFirstTime,
                totalLaunches = request.totalLaunches,
                recentGreetingIds = request.recentGreetingIds,
                recentScenarioIds = request.recentScenarioIds,
                blockedGreetingIds = request.blockedGreetingIds,
                nowMillis = request.nowMillis,
            )
        },
    ): GreetingProvider.GreetingSelection {
        cachedSelection?.let { return it }

        return mutex.withLock {
            cachedSelection?.let { return@withLock it }

            val launchNow = nowMillis()
            val lastOpened = userProfilePreferences.lastOpenedTime().get()
            val totalLaunches = userProfilePreferences.totalLaunches().get()
            val recentGreetingIds = userProfilePreferences.getRecentGreetingHistory()
            val recentScenarioIds = userProfilePreferences.getRecentScenarioHistory()
            val blockedGreetingIds = userProfilePreferences.getGreetingIdsShownWithin(
                windowMs = GREETING_REPEAT_COOLDOWN_MS,
                nowMillis = launchNow,
            )

            val request = HomeGreetingSelectionRequest(
                lastOpenedTime = lastOpened,
                achievementCount = stats.achievementCount,
                episodesWatched = stats.episodesWatched,
                librarySize = stats.librarySize,
                currentStreak = stats.currentStreak,
                isFirstTime = lastOpened == 0L,
                totalLaunches = totalLaunches,
                recentGreetingIds = recentGreetingIds,
                recentScenarioIds = recentScenarioIds,
                blockedGreetingIds = blockedGreetingIds,
                nowMillis = launchNow,
            )
            val selection = selector.select(request)

            userProfilePreferences.lastOpenedTime().set(launchNow)
            userProfilePreferences.totalLaunches().set(totalLaunches + 1)
            userProfilePreferences.appendRecentGreetingId(selection.greetingId)
            userProfilePreferences.appendRecentScenarioId(selection.scenarioId)
            userProfilePreferences.appendRecentGreetingEvent(
                id = selection.greetingId,
                shownAtMillis = launchNow,
            )

            cachedSelection = selection
            selection
        }
    }

    internal fun resetForTests() {
        cachedSelection = null
    }

    private const val GREETING_REPEAT_COOLDOWN_MS = 24L * 60L * 60L * 1000L
}
