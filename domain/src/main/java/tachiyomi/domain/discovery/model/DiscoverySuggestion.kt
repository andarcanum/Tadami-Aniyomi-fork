package tachiyomi.domain.discovery.model

enum class DiscoveryMediaType(val key: String) {
    ANIME("anime"),
    MANGA("manga"),
    NOVEL("novel"),
    ;

    companion object {
        fun fromKey(key: String): DiscoveryMediaType? = entries.firstOrNull { it.key == key }
    }
}

/**
 * V1: статус выпуска тайтла для фильтра трендов. Ключ — стабильный идентификатор
 * (префы/UI); для серверов — валидные значения Shikimori/AniList.
 */
enum class DiscoveryReleaseStatus(
    val key: String,
    val shikimoriValue: String,
    val anilistValue: String,
) {
    ONGOING("ongoing", "ongoing", "RELEASING"),
    FINISHED("finished", "released", "FINISHED"),
    ANONS("anons", "anons", "NOT_YET_RELEASED"),
    PAUSED("paused", "on_hiatus", "HIATUS"),
    ;

    companion object {
        fun fromKey(key: String): DiscoveryReleaseStatus? = entries.firstOrNull { it.key == key }

        /** CSV-ключи → множество; мусорные ключи и пустота молча отбрасываются. */
        fun parseCsv(csv: String?): Set<DiscoveryReleaseStatus> =
            csv.orEmpty().splitToSequence(",")
                .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                .mapNotNull(::fromKey)
                .toSet()
    }
}

/** Порядок enum = приоритет межрядового дедупа (LIKE > TASTE > TREND > SOURCE). */
enum class DiscoveryRowType(val key: String) {
    LIKE("like"),
    TASTE("taste"),
    TREND("trend"),
    SOURCE("source"),
    ;

    companion object {
        fun fromKey(key: String): DiscoveryRowType? = entries.firstOrNull { it.key == key }
    }
}

data class DiscoverySuggestion(
    val id: Long,
    val mediaType: DiscoveryMediaType,
    val rowType: DiscoveryRowType,
    val title: String,
    val cleanTitle: String,
    val coverUrl: String?,
    val reason: String?,
    val seedTitle: String?,
    val provider: String,
    val score: Double,
    val position: Long,
    val createdAt: Long,
    val sourceId: Long? = null,
    val sourceUrl: String? = null,
    val genres: List<String> = emptyList(),
)

/** Строка `discovery_hidden` для backup (негативный сигнал пользователя). */
data class DiscoveryHiddenEntry(
    val cleanTitle: String,
    val hiddenAt: Long,
)

/** Строка `discovery_blacklist_tags` для backup (теговый «не интересно»). */
data class DiscoveryBlacklistEntry(
    val tag: String,
    val addedAt: Long,
)

fun normalizeDiscoveryTitle(raw: String): String =
    raw.trim().lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
