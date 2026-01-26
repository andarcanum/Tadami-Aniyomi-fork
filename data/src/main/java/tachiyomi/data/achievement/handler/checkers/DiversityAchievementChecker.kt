package tachiyomi.data.achievement.handler.checkers

/**
 * Проверщик достижений разнообразия.
 *
 * Вычисляет разнообразие в пользовательской активности для достижений типа DIVERSITY.
 * Поддерживает кэширование для оптимизации производительности.
 *
 * Типы разнообразия:
 * - Жанры (Genre): Количество уникальных жанров в библиотеке
 * - Источники (Source): Количество уникальных источников в библиотеке
 *
 * Категории:
 * - Manga: Только манга
 * - Anime: Только аниме
 * - Both: Манга + аниме вместе
 *
 * Кэширование:
 * - Результаты кэшируются на 5 минут
 * - Кэш очищается при изменениях библиотеки
 *
 * @param mangaHandler Обработчик БД манги для запросов
 * @param animeHandler Обработчик БД аниме для запросов
 *
 * @see AchievementType.DIVERSITY
 */
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler

class DiversityAchievementChecker(
    private val mangaHandler: MangaDatabaseHandler,
    private val animeHandler: AnimeDatabaseHandler,
) {
    // Cache for diversity calculations
    private var genreCache: Pair<Int, Long>? = null
    private var sourceCache: Pair<Int, Long>? = null
    private var mangaGenreCache: Pair<Int, Long>? = null
    private var animeGenreCache: Pair<Int, Long>? = null
    private var mangaSourceCache: Pair<Int, Long>? = null
    private var animeSourceCache: Pair<Int, Long>? = null

    /** Продолжительность кэша в миллисекундах (5 минут) */
    private val cacheDuration = 5 * 60 * 1000 // 5 minutes

    /**
     * Get total count of unique genres across both manga and anime library
     */
    suspend fun getGenreDiversity(): Int {
        val now = System.currentTimeMillis()
        genreCache?.let { (count, timestamp) ->
            if (now - timestamp < cacheDuration) {
                return count
            }
        }

        val mangaGenres = mangaHandler.awaitList {
            mangasQueries.getLibraryGenres { genre ->
                genre
            }
        }

        val animeGenres = animeHandler.awaitList {
            animesQueries.getLibraryGenres { genre ->
                genre
            }
        }

        // Combine and parse unique genres from both manga and anime
        val allGenreStrings = mangaGenres + animeGenres
        val count = parseAndGetUniqueGenres(allGenreStrings)
        genreCache = Pair(count, now)
        return count
    }

    /**
     * Get total count of unique sources across both manga and anime library
     */
    suspend fun getSourceDiversity(): Int {
        val now = System.currentTimeMillis()
        sourceCache?.let { (count, timestamp) ->
            if (now - timestamp < cacheDuration) {
                return count
            }
        }

        val mangaSources = mangaHandler.awaitList {
            mangasQueries.getLibrarySources { source ->
                source
            }
        }

        val animeSources = animeHandler.awaitList {
            animesQueries.getLibrarySources { source ->
                source
            }
        }

        // Combine and get unique sources from both manga and anime
        val count = (mangaSources + animeSources).distinct().size
        sourceCache = Pair(count, now)
        return count
    }

    /**
     * Get unique genres count from manga library only
     */
    suspend fun getMangaGenreDiversity(): Int {
        val now = System.currentTimeMillis()
        mangaGenreCache?.let { (count, timestamp) ->
            if (now - timestamp < cacheDuration) {
                return count
            }
        }

        val mangaGenres = mangaHandler.awaitList {
            mangasQueries.getLibraryGenres { genre ->
                genre
            }
        }

        val count = parseAndGetUniqueGenres(mangaGenres)
        mangaGenreCache = Pair(count, now)
        return count
    }

    /**
     * Get unique genres count from anime library only
     */
    suspend fun getAnimeGenreDiversity(): Int {
        val now = System.currentTimeMillis()
        animeGenreCache?.let { (count, timestamp) ->
            if (now - timestamp < cacheDuration) {
                return count
            }
        }

        val animeGenres = animeHandler.awaitList {
            animesQueries.getLibraryGenres { genre ->
                genre
            }
        }

        val count = parseAndGetUniqueGenres(animeGenres)
        animeGenreCache = Pair(count, now)
        return count
    }

    /**
     * Get unique sources count from manga library only
     */
    suspend fun getMangaSourceDiversity(): Int {
        val now = System.currentTimeMillis()
        mangaSourceCache?.let { (count, timestamp) ->
            if (now - timestamp < cacheDuration) {
                return count
            }
        }

        val mangaSources = mangaHandler.awaitList {
            mangasQueries.getLibrarySources { source ->
                source
            }
        }

        val count = mangaSources.distinct().size
        mangaSourceCache = Pair(count, now)
        return count
    }

    /**
     * Get unique sources count from anime library only
     */
    suspend fun getAnimeSourceDiversity(): Int {
        val now = System.currentTimeMillis()
        animeSourceCache?.let { (count, timestamp) ->
            if (now - timestamp < cacheDuration) {
                return count
            }
        }

        val animeSources = animeHandler.awaitList {
            animesQueries.getLibrarySources { source ->
                source
            }
        }

        val count = animeSources.distinct().size
        animeSourceCache = Pair(count, now)
        return count
    }

    /**
     * Clear all caches (call when library changes)
     */
    fun clearCache() {
        genreCache = null
        sourceCache = null
        mangaGenreCache = null
        animeGenreCache = null
        mangaSourceCache = null
        animeSourceCache = null
    }

    /**
     * Parse genre strings (comma-separated) and return count of unique genres
     */
    private fun parseAndGetUniqueGenres(genreStrings: List<String?>): Int {
        return genreStrings
            .filterNotNull()
            .flatMap { genreString ->
                // Parse comma-separated genre strings
                genreString.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }
            .distinct()
            .size
    }
}
