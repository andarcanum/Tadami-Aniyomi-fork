package tachiyomi.data.anilist

enum class AnilistImportStatus(val apiValue: String) {
    CURRENT("CURRENT"),
    COMPLETED("COMPLETED"),
    PLANNING("PLANNING"),
    PAUSED("PAUSED"),
    DROPPED("DROPPED"),
    REPEATING("REPEATING"),
    ;

    companion object {
        fun fromApi(value: String?): AnilistImportStatus? {
            val key = value?.trim()?.uppercase().orEmpty()
            if (key.isEmpty()) return null
            return entries.firstOrNull { it.apiValue == key }
        }

        fun forMediaType(@Suppress("UNUSED_PARAMETER") mediaType: AnilistImportMediaType): List<AnilistImportStatus> =
            listOf(
                CURRENT,
                COMPLETED,
                PLANNING,
                PAUSED,
                DROPPED,
                REPEATING,
            )
    }
}
