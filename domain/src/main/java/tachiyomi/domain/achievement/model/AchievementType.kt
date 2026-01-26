package tachiyomi.domain.achievement.model

enum class AchievementType {
    QUANTITY,    // Количество глав/серий прочитано
    EVENT,       // Одноразовые события (первая глава, первое добавление)
    DIVERSITY,   // Разнообразие (жанры, источники)
    STREAK,      // Дни подряд
    ;
}
