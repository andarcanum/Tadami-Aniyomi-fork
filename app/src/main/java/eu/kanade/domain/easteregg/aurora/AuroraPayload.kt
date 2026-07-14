package eu.kanade.domain.easteregg.aurora

import kotlinx.serialization.Serializable

/**
 * Содержимое расшифрованной ступени.
 * kind = "riddle" — промежуточное эхо со следующей загадкой.
 * kind = "final" — финал: достижение, титул, тема, письмо, очки.
 * Всё это существует только внутри шифртекста до момента разгадки.
 */
@Serializable
data class AuroraPayload(
    val kind: String,
    val riddle: String? = null,
    val echoTitle: String? = null,
    val achievementTitle: String? = null,
    val achievementDescription: String? = null,
    val holderTitle: String? = null,
    val letter: String? = null,
    val themeName: String? = null,
    val themeColors: Map<String, String>? = null,
    val themeMaterial: Map<String, String>? = null,
    val bonusPoints: Int? = null,
)
