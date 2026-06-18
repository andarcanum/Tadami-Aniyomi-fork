package tachiyomi.data.achievement.rules

object GenreAliases {

    private val genreAliases: Map<String, List<String>> = mapOf(
        "Harem" to listOf("Гарем"),
        "Isekai" to listOf("Исекай"),
        "Shounen" to listOf("Сёнэн", "Шонен"),
        "Super Power" to listOf("Суперсила", "Сверхспособности"),
        "Military" to listOf("Военное", "Военные"),
        "Psychological" to listOf("Психологическое", "Психологический"),
        "Tragedy" to listOf("Трагедия"),
        "Drama" to listOf("Драма"),
        "romance" to listOf("Романтика"),
        "horror" to listOf("Ужасы", "Хоррор"),
        "slice of life" to listOf("Повседневность"),
        "dark fantasy" to listOf("Тёмное фэнтези", "Дарк фэнтези"),
        "Fantasy" to listOf("Фэнтези"),
        "Horror" to listOf("Ужасы", "Хоррор"),
        "Dark" to listOf("Тёмный", "Мрачный"),
    )

    // Groups used by DarkFantasyRule for the combo check (Dark/Horror + Fantasy).
    val darkGroupA: List<String> = listOf("Dark", "Horror", "dark fantasy")
    val darkGroupB: List<String> = listOf("Fantasy")

    private val titleAliases: Map<String, List<String>> = mapOf(
        "jojo" to listOf("джоджо", "джо джо"),
    )

    fun allGenreSearchTerms(canonicalGenre: String): List<String> {
        return listOf(canonicalGenre) + (genreAliases[canonicalGenre].orEmpty())
    }

    fun allTitleSearchTerms(canonicalPattern: String): List<String> {
        return listOf(canonicalPattern) + (titleAliases[canonicalPattern].orEmpty())
    }

    fun genreMatches(genreEntry: String, canonicalGenres: Collection<String>): Boolean {
        return canonicalGenres.any { canonical ->
            allGenreSearchTerms(canonical).any { alias ->
                genreEntry.equals(alias, ignoreCase = true)
            }
        }
    }
}
