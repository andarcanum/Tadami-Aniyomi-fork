package eu.kanade.domain.easteregg.aurora

import kotlinx.serialization.Serializable

/**
 * Запись «Кодекса Авроры» — пройденное эхо и открытая им загадка.
 * Записывается ТОЛЬКО после реального прохождения ступени —
 * секретов будущих ступеней здесь нет и быть не может.
 */
@Serializable
data class AuroraCodexEntry(
    val title: String? = null,
    val riddle: String? = null,
)
