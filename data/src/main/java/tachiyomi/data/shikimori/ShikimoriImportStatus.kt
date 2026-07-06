package tachiyomi.data.shikimori

enum class ShikimoriImportStatus(val apiValue: String) {
    WATCHING("watching"),
    COMPLETED("completed"),
    PLANNED("planned"),
    ON_HOLD("on_hold"),
    DROPPED("dropped"),
    REWATCHING("rewatching"),
    ;

    companion object {
        fun fromApi(value: String?): ShikimoriImportStatus? {
            val key = value?.trim()?.lowercase().orEmpty()
            if (key.isEmpty()) return null
            return entries.firstOrNull { it.apiValue == key }
        }
    }
}
