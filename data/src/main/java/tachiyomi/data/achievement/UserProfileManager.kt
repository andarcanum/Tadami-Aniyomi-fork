package tachiyomi.data.achievement

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import logcat.LogPriority
import logcat.logcat
import tachiyomi.domain.achievement.model.Reward
import tachiyomi.domain.achievement.model.RewardType
import tachiyomi.domain.achievement.model.UserProfile

/**
 * Менеджер профиля пользователя
 * Управляет XP, уровнями, званиями и наградами
 * Временно хранит профиль в памяти (сохранение в Preferences - TODO)
 */
class UserProfileManager() {

    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
    )

    private val _profile = MutableStateFlow<UserProfile>(UserProfile.createDefault())
    val profile: Flow<UserProfile> = _profile

    /**
     * Получить текущий профиль
     */
    suspend fun getCurrentProfile(): UserProfile {
        return _profile.value
    }

    /**
     * Добавить XP профилю
     * @return true если уровень повысился
     */
    suspend fun addXP(amount: Int): Boolean {
        if (amount <= 0) return false

        val currentProfile = getCurrentProfile()
        val newTotalXP = currentProfile.totalXP + amount
        val newLevel = UserProfile.getLevelFromXP(newTotalXP)

        val oldLevel = currentProfile.level
        val levelUp = newLevel > oldLevel

        val updatedProfile = currentProfile.copy(
            totalXP = newTotalXP,
            level = newLevel,
            currentXP = calculateCurrentLevelXP(newTotalXP, newLevel),
            xpToNextLevel = UserProfile.getXPForLevel(newLevel + 1),
        )

        _profile.value = updatedProfile

        if (levelUp) {
            logcat(LogPriority.INFO) {
                "[ACHIEVEMENTS] LEVEL UP! $oldLevel -> $newLevel (Total XP: $newTotalXP)"
            }
        }

        return levelUp
    }

    /**
     * Добавить звание профилю
     */
    suspend fun addTitle(title: String) {
        val currentProfile = getCurrentProfile()
        if (currentProfile.titles.contains(title)) return

        val updatedProfile = currentProfile.copy(
            titles = currentProfile.titles + title,
        )

        _profile.value = updatedProfile

        logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Title unlocked: $title" }
    }

    /**
     * Добавить бейдж профилю
     */
    suspend fun addBadge(badge: String) {
        val currentProfile = getCurrentProfile()
        if (currentProfile.badges.contains(badge)) return

        val updatedProfile = currentProfile.copy(
            badges = currentProfile.badges + badge,
        )

        _profile.value = updatedProfile

        logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Badge unlocked: $badge" }
    }

    /**
     * Разблокировать тему
     * @param themeId ID темы для разблокировки
     */
    suspend fun unlockTheme(themeId: String) {
        val currentProfile = getCurrentProfile()
        if (currentProfile.unlockedThemes.contains(themeId)) return

        val updatedProfile = currentProfile.copy(
            unlockedThemes = currentProfile.unlockedThemes + themeId,
        )

        _profile.value = updatedProfile

        logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Theme unlocked: $themeId" }
    }

    /**
     * Получить список ID разблокированных тем
     */
    fun getUnlockedThemes(): List<String> {
        return _profile.value.unlockedThemes
    }

    /**
     * Выдать награды за достижение
     */
    suspend fun grantRewards(rewards: List<Reward>) {
        rewards.forEach { reward ->
            when (reward.type) {
                RewardType.EXPERIENCE -> {
                    addXP(reward.value)
                }
                RewardType.TITLE -> {
                    addTitle(reward.title)
                }
                RewardType.BADGE -> {
                    addBadge(reward.title)
                }
                RewardType.THEME -> {
                    // Используем reward.id как ID темы
                    unlockTheme(reward.id.removePrefix("theme_"))
                }
                RewardType.SPECIAL -> {
                    // Специальные награды обрабатываются отдельно
                    logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Special reward: ${reward.title}" }
                }
            }
        }
    }

    /**
     * Обновить количество разблокированных достижений
     */
    suspend fun updateAchievementsCount(unlocked: Int, total: Int) {
        val currentProfile = getCurrentProfile()
        val updatedProfile = currentProfile.copy(
            achievementsUnlocked = unlocked,
            totalAchievements = total,
        )

        _profile.value = updatedProfile
    }

    /**
     * Рассчитать XP для текущего уровня
     */
    private fun calculateCurrentLevelXP(totalXP: Int, level: Int): Int {
        var xpNeeded = 0
        for (l in 1 until level) {
            xpNeeded += UserProfile.getXPForLevel(l)
        }
        return totalXP - xpNeeded
    }

    /**
     * Загрузить профиль (временно загружает дефолтный профиль)
     * TODO: Загружать из Preferences
     */
    suspend fun loadProfile() {
        _profile.value = UserProfile.createDefault()
    }
}
