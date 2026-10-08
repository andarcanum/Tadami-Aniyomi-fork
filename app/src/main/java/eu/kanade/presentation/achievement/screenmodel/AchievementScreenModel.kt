package eu.kanade.presentation.achievement.screenmodel

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.easteregg.aurora.AuroraHeartManager
import eu.kanade.presentation.achievement.utils.AchievementRevealHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.data.achievement.handler.AchievementHandler
import tachiyomi.data.achievement.handler.PointsManager
import tachiyomi.data.achievement.loader.AchievementLoader
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.DayActivity
import tachiyomi.domain.achievement.model.MonthStats
import tachiyomi.domain.achievement.model.UserPoints
import tachiyomi.domain.achievement.repository.AchievementRepository
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AchievementScreenModel(
    private val repository: AchievementRepository = Injekt.get(),
    private val loader: AchievementLoader = Injekt.get(),
    private val pointsManager: PointsManager = Injekt.get(),
    private val activityDataRepository: ActivityDataRepository = Injekt.get(),
    private val auroraHeartManager: AuroraHeartManager = Injekt.get(),
    private val achievementHandler: AchievementHandler? = runCatching { Injekt.get<AchievementHandler>() }.getOrNull(),
) : ScreenModel {

    // Separate state for category to avoid being overwritten by combine
    private val _categoryState = MutableStateFlow(AchievementCategory.BOTH)
    val categoryState: StateFlow<AchievementCategory> = _categoryState

    private val selectedAchievementState = MutableStateFlow<Achievement?>(null)

    init {
        screenModelScope.launch {
            loader.loadAchievements()
            achievementHandler?.recomputeStreakAchievements()
        }
    }

    val state: StateFlow<AchievementScreenState> = combine(
        repository.getAll(),
        repository.getAllProgress(),
        pointsManager.subscribeToPoints(),
        categoryState,
        activityDataRepository.getActivityData(365),
    ) { achievements, progress, userPoints, selectedCategory, activityData ->
        val currentStats = activityDataRepository.getCurrentMonthStats()
        val previousStats = activityDataRepository.getPreviousMonthStats()
        val yearlyStats = activityDataRepository.getLastTwelveMonthsStats()

        AchievementScreenState.Success(
            achievements = achievements,
            progress = progress.associateBy { it.achievementId },
            userPoints = userPoints,
            selectedCategory = selectedCategory,
            activityData = activityData,
            yearlyStats = yearlyStats,
            currentMonthStats = currentStats,
            previousMonthStats = previousStats,
        )
    }
        .combine(auroraHeartManager.state) { state, auroraState ->
            // The pipeline above always emits Success, so state is already narrowed
            state.copy(
                auroraQuestStarted = auroraState.hintRevealed ||
                    auroraState.stageIndex > 0 ||
                    auroraState.unlocked,
            )
        }
        .combine(selectedAchievementState) { state, selectedAchievement ->
            state.copy(selectedAchievement = selectedAchievement)
        }
        .catch { error ->
            error.printStackTrace()
        }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), AchievementScreenState.Loading)

    fun onCategoryChanged(category: AchievementCategory) {
        _categoryState.value = category
    }

    fun refreshAchievements() {
        screenModelScope.launch {
            loader.loadAchievements()
            achievementHandler?.recomputeStreakAchievements()
        }
    }

    fun onAchievementClick(achievement: Achievement) {
        selectedAchievementState.update { achievement }
    }

    fun onDialogDismiss() {
        selectedAchievementState.update { null }
    }
}

@Immutable
data class UserLevelInfo(
    val level: Int,
    val rankName: String,
    val currentXp: Int,
    val requiredXpForNext: Int,
    val progressFraction: Float,
)

@Immutable
sealed interface AchievementScreenState {
    @Immutable
    data object Loading : AchievementScreenState

    @Immutable
    data class Success(
        val achievements: List<Achievement> = emptyList(),
        val progress: Map<String, AchievementProgress> = emptyMap(),
        val userPoints: UserPoints = UserPoints(),
        val selectedCategory: AchievementCategory = AchievementCategory.BOTH,
        val selectedAchievement: Achievement? = null,
        val activityData: List<DayActivity> = emptyList(),
        val auroraQuestStarted: Boolean = false,
        val yearlyStats: List<Pair<java.time.YearMonth, MonthStats>> = emptyList(),
        val currentMonthStats: MonthStats = MonthStats(0, 0, 0, 0),
        val previousMonthStats: MonthStats = MonthStats(0, 0, 0, 0),
    ) : AchievementScreenState {
        val levelInfo: UserLevelInfo
            get() = calculateLevelInfo(totalPoints)

        private fun calculateLevelInfo(totalPoints: Int): UserLevelInfo {
            var points = totalPoints
            var currentLevel = 1
            var requiredXpForNext = 1000

            while (points >= requiredXpForNext) {
                points -= requiredXpForNext
                currentLevel++
                requiredXpForNext = 1000 + (currentLevel - 1) * 500
            }

            val rankName = when (currentLevel) {
                in 1..5 -> "Novice Reader"
                in 6..10 -> "Avid Reader"
                in 11..15 -> "Ranobe Master"
                in 16..20 -> "Grandmaster"
                else -> "Legendary Scholar"
            }

            return UserLevelInfo(
                level = currentLevel,
                rankName = rankName,
                currentXp = points,
                requiredXpForNext = requiredXpForNext,
                progressFraction = points.toFloat() / requiredXpForNext,
            )
        }

        val filteredAchievements: List<Achievement>
            get() {
                val visible = achievements.filter { achievement ->
                    !AchievementRevealHelper.isCompletelyHiddenUntilUnlocked(achievement) ||
                        progress[achievement.id]?.isUnlocked == true ||
                        (achievement.id == "aurora_heart" && auroraQuestStarted)
                }
                return when (selectedCategory) {
                    AchievementCategory.BOTH -> visible
                    AchievementCategory.ANIME -> visible.filter {
                        it.category == AchievementCategory.ANIME ||
                            it.category == AchievementCategory.BOTH
                    }
                    AchievementCategory.MANGA -> visible.filter {
                        it.category == AchievementCategory.MANGA ||
                            it.category == AchievementCategory.BOTH
                    }
                    AchievementCategory.NOVEL -> visible.filter {
                        it.category == AchievementCategory.NOVEL ||
                            it.category == AchievementCategory.BOTH
                    }
                    AchievementCategory.SECRET -> visible.filter {
                        it.category == AchievementCategory.SECRET || it.isSecret
                    }
                }
            }

        val totalPoints: Int
            get() = userPoints.totalPoints

        val unlockedCount: Int
            get() = progress.count { it.value.isUnlocked }

        val totalCount: Int
            get() = achievements.count { achievement ->
                !AchievementRevealHelper.isCompletelyHiddenUntilUnlocked(achievement) ||
                    progress[achievement.id]?.isUnlocked == true ||
                    (achievement.id == "aurora_heart" && auroraQuestStarted)
            }

        val currentStreak: Int
            get() = calculateCurrentStreak(activityData)

        private fun calculateCurrentStreak(activities: List<DayActivity>): Int {
            if (activities.isEmpty()) return 0

            val activityByDate = activities.associateBy { it.date }
            val today = java.time.LocalDate.now()
            val hasActivityToday = (activityByDate[today]?.level ?: 0) > 0
            var checkDate = if (hasActivityToday) today else today.minusDays(1)
            var streak = 0

            while (true) {
                val level = activityByDate[checkDate]?.level ?: 0
                if (level <= 0) break
                streak++
                checkDate = checkDate.minusDays(1)
            }

            return streak
        }
    }
}
