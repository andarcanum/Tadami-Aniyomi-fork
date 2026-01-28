package tachiyomi.domain.achievement.model

import androidx.compose.runtime.Immutable

/**
 * Типы наград за достижения
 */
@Immutable
enum class RewardType {
    /** Опыт профиля (XP) */
    EXPERIENCE,

    /** Звание пользователя (отображается в профиле) */
    TITLE,

    /** Разблокировка темы оформления */
    THEME,

    /** Разблокировка значков/бейджей */
    BADGE,

    /** Специальные возможности */
    SPECIAL
}

/**
 * Награда за достижение или уровень достижения
 *
 * @property type Тип награды
 * @property id Уникальный идентификатор награды
 * @property value Значение награды (например, количество XP)
 * @property title Название награды для отображения
 * @property description Описание награды
 * @property icon Иконка награды
 * @property unlocked Разблокирована ли награда
 */
@Immutable
data class Reward(
    val type: RewardType,
    val id: String,
    val value: Int = 0,
    val title: String,
    val description: String? = null,
    val icon: String? = null,
    val unlocked: Boolean = false,
) {
    companion object {
        /**
         * Создать награду опытом (XP)
         */
        fun experience(
            amount: Int,
            title: String = "Опыт",
            description: String? = null,
        ) = Reward(
            type = RewardType.EXPERIENCE,
            id = "xp_$amount",
            value = amount,
            title = title,
            description = description ?: "+$amount XP",
        )

        /**
         * Создать награду званием
         */
        fun title(
            titleId: String,
            title: String,
            description: String? = null,
            icon: String? = null,
        ) = Reward(
            type = RewardType.TITLE,
            id = "title_$titleId",
            value = 1,
            title = title,
            description = description,
            icon = icon,
        )

        /**
         * Создать награду темой
         */
        fun theme(
            themeId: String,
            themeName: String,
            description: String? = null,
        ) = Reward(
            type = RewardType.THEME,
            id = "theme_$themeId",
            value = 1,
            title = themeName,
            description = description ?: "Тема: $themeName",
        )

        /**
         * Создать награду бейджем
         */
        fun badge(
            badgeId: String,
            badgeName: String,
            description: String? = null,
            icon: String? = null,
        ) = Reward(
            type = RewardType.BADGE,
            id = "badge_$badgeId",
            value = 1,
            title = badgeName,
            description = description,
            icon = icon,
        )
    }
}

/**
 * Список наград за достижение
 */
@Immutable
data class RewardList(
    val rewards: List<Reward> = emptyList(),
) {
    /**
     * Получить все разблокированные награды
     */
    fun getUnlockedRewards(): List<Reward> {
        return rewards.filter { it.unlocked }
    }

    /**
     * Получить награды по типу
     */
    fun getRewardsByType(type: RewardType): List<Reward> {
        return rewards.filter { it.type == type }
    }

    /**
     * Получить общий XP из всех наград
     */
    fun getTotalXP(): Int {
        return rewards
            .filter { it.type == RewardType.EXPERIENCE && it.unlocked }
            .sumOf { it.value }
    }

    companion object {
        /**
         * Создать пустой список наград
         */
        val Empty = RewardList()
    }
}
