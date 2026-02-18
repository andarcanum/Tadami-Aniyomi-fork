package eu.kanade.presentation.achievement.screenmodel

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
) : StateScreenModel<AchievementScreenState>(AchievementScreenState.Loading) {

    // Separate state for category to avoid being overwritten by combine
    private val _categoryState = MutableStateFlow(AchievementCategory.BOTH)
    val categoryState: StateFlow<AchievementCategory> = _categoryState

    init {
        screenModelScope.launch {
            combine(
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
            }.catch { error ->
                error.printStackTrace()
            }.collect { state ->
                mutableState.update { state }
            }
        }
    }

    fun onCategoryChanged(category: AchievementCategory) {
        _categoryState.value = category
    }

    fun onAchievementClick(achievement: Achievement) {
        mutableState.update {
            when (it) {
                AchievementScreenState.Loading -> it
                is AchievementScreenState.Success -> it.copy(selectedAchievement = achievement)
            }
        }
    }

    fun onDialogDismiss() {
        mutableState.update {
            when (it) {
                AchievementScreenState.Loading -> it
                is AchievementScreenState.Success -> it.copy(selectedAchievement = null)
            }
        }
    }
}

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
        val yearlyStats: List<Pair<java.time.YearMonth, MonthStats>> = emptyList(),
        val currentMonthStats: MonthStats = MonthStats(0, 0, 0, 0),
        val previousMonthStats: MonthStats = MonthStats(0, 0, 0, 0),
    ) : AchievementScreenState {
        val filteredAchievements: List<Achievement>
            get() = when (selectedCategory) {
                AchievementCategory.BOTH -> achievements
                AchievementCategory.ANIME -> achievements.filter {
                    it.category == AchievementCategory.ANIME ||
                        it.category == AchievementCategory.BOTH
                }
                AchievementCategory.MANGA -> achievements.filter {
                    it.category == AchievementCategory.MANGA ||
                        it.category == AchievementCategory.BOTH
                }
                AchievementCategory.NOVEL -> achievements.filter {
                    it.category == AchievementCategory.NOVEL ||
                        it.category == AchievementCategory.BOTH
                }
                AchievementCategory.SECRET -> achievements.filter { it.category == AchievementCategory.SECRET }
            }

        val totalPoints: Int
            get() = userPoints.totalPoints

        val unlockedCount: Int
            get() = progress.count { it.value.isUnlocked }

        val totalCount: Int
            get() = achievements.size

        val currentStreak: Int
            get() = calculateCurrentStreak(activityData)

        private fun calculateCurrentStreak(activities: List<DayActivity>): Int {
            var streak = 0
            val today = java.time.LocalDate.now()

            for (i in 0 until activities.size) {
                val date = today.minusDays(i.toLong())
                val activity = activities.find { it.date == date }

                if (activity != null && activity.level > 0) {
                    streak++
                } else {
                    break
                }
            }

            return streak
        }
    }
}
