package tachiyomi.domain.achievement.model

import androidx.compose.runtime.Immutable

/**
 * Представляет отдельный уровень в многоуровневом достижении
 *
 * @property level Уровень (1, 2, 3, ...) или название ("BRONZE", "SILVER", "GOLD")
 * @property threshold Пороговое значение для разблокировки этого уровня
 * @property points Очки, начисляемые за этот уровень
 * @property title Название достижения для этого уровня (например, "Бронзовый чтец")
 * @property description Описание достижения для этого уровня
 * @property badgeIcon Иконка бейджа для этого уровня
 */
@Immutable
data class AchievementTier(
    val level: Int,
    val threshold: Int,
    val points: Int,
    val title: String,
    val description: String? = null,
    val badgeIcon: String? = null,
) {
    companion object {
        // Предопределенные уровни для стандартной системы
        fun bronze(
            threshold: Int,
            points: Int,
            title: String,
            description: String? = null,
            badgeIcon: String? = null,
        ) = AchievementTier(1, threshold, points, title, description, badgeIcon)

        fun silver(
            threshold: Int,
            points: Int,
            title: String,
            description: String? = null,
            badgeIcon: String? = null,
        ) = AchievementTier(2, threshold, points, title, description, badgeIcon)

        fun gold(
            threshold: Int,
            points: Int,
            title: String,
            description: String? = null,
            badgeIcon: String? = null,
        ) = AchievementTier(3, threshold, points, title, description, badgeIcon)

        // Для произвольного количества уровней
        fun custom(
            level: Int,
            threshold: Int,
            points: Int,
            title: String,
            description: String? = null,
            badgeIcon: String? = null,
        ) = AchievementTier(level, threshold, points, title, description, badgeIcon)
    }
}

/**
 * Получить уровень по названию (для классической системы BRONZE/SILVER/GOLD)
 */
enum class TierLevel(val displayName: String, val level: Int) {
    BRONZE("Бронза", 1),
    SILVER("Серебро", 2),
    GOLD("Золото", 3),
    PLATINUM("Платина", 4),
    DIAMOND("Алмаз", 5),
    MASTER("Мастер", 6),
    GRANDMASTER("Грандмастер", 7);

    companion object {
        fun fromLevel(level: Int): TierLevel? = entries.find { it.level == level }
    }
}
