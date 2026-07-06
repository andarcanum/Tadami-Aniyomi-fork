package tachiyomi.data.anixart

/**
 * Watch status as exported by the Anixart app.
 *
 * The Russian labels below are the exact strings written into the CSV export
 * (see "Статус просмотра" column). Anixart does NOT export an "on hold" state,
 * so there is intentionally no [ON_HOLD] mapping here.
 */
enum class AnixartStatus(val csvLabel: String) {
    PLAN_TO_WATCH("В планах"),
    WATCHING("Смотрю"),
    COMPLETED("Просмотрено"),
    DROPPED("Брошено"),
    ;

    companion object {
        private val byLabel = values().associateBy { it.csvLabel.lowercase() }

        /** Resolves a raw CSV status cell to a status, or null if empty/unknown. */
        fun fromCsv(value: String?): AnixartStatus? {
            val key = value?.trim()?.lowercase().orEmpty()
            if (key.isEmpty()) return null
            return byLabel[key]
        }
    }
}

/**
 * A single bookmark row parsed from an Anixart CSV export.
 *
 * Field semantics mirror the real export layout (confirmed against the
 * anixart-to-mal reference parser):
 *  - titles come in three flavours (RU / original / alternatives);
 *  - [favorite] is the literal "Добавлено" when the title was starred;
 *  - [rating] is "N из 5" or blank.
 */
data class AnixartRow(
    val index: Int,
    val russianTitle: String,
    val originalTitle: String,
    val alternativeTitles: String,
    val favoriteRaw: String,
    val statusRaw: String,
    val ratingRaw: String,
) {
    /** Whether this entry was starred ("Добавлено в избранное" == "Добавлено"). */
    val isFavorite: Boolean
        get() = favoriteRaw.trim().equals(FAVORITE_MARKER, ignoreCase = true)

    /** Parsed watch status, or null if blank/unknown. */
    val status: AnixartStatus?
        get() = AnixartStatus.fromCsv(statusRaw)

    /**
     * Personal rating mapped to a 1..10 scale ("N из 5" -> N*2), or null when
     * unrated. Anixart only ever exports whole stars 1..5.
     */
    val ratingOutOfTen: Int?
        get() {
            val star = RATING_REGEX.find(ratingRaw.trim())?.groupValues?.get(1)?.toIntOrNull()
                ?: return null
            return if (star in 1..5) star * 2 else null
        }

    /**
     * Candidate titles to match against installed sources, in priority order:
     * original -> russian -> each alternative. Placeholder/junk values are
     * dropped and duplicates are removed case-insensitively while preserving
     * priority order.
     */
    fun candidateTitles(): List<String> {
        val raw = buildList {
            add(originalTitle)
            add(cleanAnimeTitle(originalTitle))
            add(russianTitle)
            add(cleanAnimeTitle(russianTitle))
            alternativeTitles.split(',').forEach {
                add(it)
                add(cleanAnimeTitle(it))
            }
        }
        val seen = HashSet<String>()
        return raw
            .map { it.trim() }
            .filter { it.isNotEmpty() && !isPlaceholder(it) }
            .filter { seen.add(it.lowercase()) }
    }

    /**
     * Cleaned version of candidate titles to use for catalog search.
     * Strips suffixes, brackets, parentheses, and common anime metadata.
     */
    fun searchQueries(): List<String> {
        val raw = buildList {
            add(cleanAnimeTitle(originalTitle))
            add(cleanAnimeTitle(russianTitle))
            alternativeTitles.split(',').forEach { add(cleanAnimeTitle(it)) }
        }
        val seen = HashSet<String>()
        return raw
            .map { it.trim() }
            .filter { it.isNotEmpty() && !isPlaceholder(it) }
            .filter { seen.add(it.lowercase()) }
    }

    companion object {
        private const val FAVORITE_MARKER = "Добавлено"
        private val RATING_REGEX = Regex("""(\d+)\s*из\s*5""", RegexOption.IGNORE_CASE)

        /**
         * Values Anixart writes for missing/removed titles. These must never be
         * treated as real titles to match on.
         */
        private val PLACEHOLDERS = setOf(
            "не указаны",
            "не указано",
            "[удалено]",
            "дубль удалить",
        )

        fun isPlaceholder(value: String): Boolean = value.trim().lowercase() in PLACEHOLDERS

        /**
         * Strips bracket/parenthesis metadata and trailing season/type suffixes in
         * both Cyrillic (Anixart RU exports) and Latin (romanized / EN catalogue titles).
         */
        fun cleanAnimeTitle(title: String): String {
            var cleaned = title
            cleaned = cleaned.replace(Regex("""\s*\[.*?\]"""), "")
            cleaned = cleaned.replace(Regex("""\s*\(.*?\)"""), "")
            cleaned = cleaned.replace(TRAILING_SUFFIX, "")
            cleaned = cleaned.trim().trimEnd('.', ',', '-', ':')
            return cleaned.trim()
        }

        private val TRAILING_SUFFIX = Regex(
            """\s+(?:
                ТВ-\d+|ТВ|
                сезон\s+\d+|\d+\s+сезон|
                фильм|спешл|спэшл|
                TV-\d+|TV|
                Season\s+\d+|\d+(?:st|nd|rd|th)?\s+Season|
                Movie|Special|Film|
                OVA|ONA|OVA-\d+|ONA-\d+
            )\s*$""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.COMMENTS),
        )
    }
}
