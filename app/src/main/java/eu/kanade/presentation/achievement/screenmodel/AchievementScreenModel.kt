package eu.kanade.presentation.achievement.screenmodel

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.StateScreenModel
import kotlinx.coroutines.flow.MutableStateFlow
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import tachiyomi.data.achievement.loader.AchievementLoader
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.UserPoints
import tachiyomi.domain.achievement.repository.AchievementRepository
import tachiyomi.data.achievement.handler.PointsManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AchievementScreenModel(
    private val repository: AchievementRepository = Injekt.get(),
    private val loader: AchievementLoader = Injekt.get(),
    private val pointsManager: PointsManager = Injekt.get(),
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
                categoryState, // Include categoryState in combine
            ) { achievements, progress, userPoints, selectedCategory ->
                AchievementScreenState.Success(
                    achievements = achievements,
                    progress = progress.associateBy { it.achievementId },
                    userPoints = userPoints,
                    selectedCategory = selectedCategory,
                )
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
    ) : AchievementScreenState {
        val filteredAchievements: List<Achievement>
            get() = when (selectedCategory) {
                AchievementCategory.BOTH -> achievements.filter { it.category != AchievementCategory.SECRET }
                AchievementCategory.ANIME -> achievements.filter { it.category == AchievementCategory.ANIME || it.category == AchievementCategory.BOTH }
                AchievementCategory.MANGA -> achievements.filter { it.category == AchievementCategory.MANGA || it.category == AchievementCategory.BOTH }
                AchievementCategory.SECRET -> achievements.filter { it.category == AchievementCategory.SECRET }
            }

        val totalPoints: Int
            get() = userPoints.totalPoints

        val unlockedCount: Int
            get() = progress.count { it.value.isUnlocked }

        val totalCount: Int
            get() = achievements.size
    }
}
