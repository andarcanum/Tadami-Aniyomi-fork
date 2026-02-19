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
import tachiyomi.data.handlers.novel.NovelDatabaseHandler

class DiversityAchievementChecker(
    private val mangaHandler: MangaDatabaseHandler,
    private val animeHandler: AnimeDatabaseHandler,
    private val novelHandler: NovelDatabaseHandler,
) {
    // Cache for diversity calculations
    private var genreCache: Pair<Int, Long>? = null
    private var sourceCache: Pair<Int, Long>? = null
    private var mangaGenreCache: Pair<Int, Long>? = null
    private var animeGenreCache: Pair<Int, Long>? = null
    private var novelGenreCache: Pair<Int, Long>? = null
    private var mangaSourceCache: Pair<Int, Long>? = null
    private var animeSourceCache: Pair<Int, Long>? = null
    private var novelSourceCache: Pair<Int, Long>? = null

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
            mangasQueries.getLibraryGenres()
        }

        val animeGenres = animeHandler.awaitList {
            animesQueries.getLibraryGenres()
        }

        val novelGenres = novelHandler.awaitList {
            novelsQueries.getLibraryGenres()
        }

        // Combine and parse unique genres from both manga and anime
        val allGenreStrings = mangaGenres + animeGenres + novelGenres
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
            mangasQueries.getLibrarySources()
        }

        val animeSources = animeHandler.awaitList {
            animesQueries.getLibrarySources()
        }

        val novelSources = novelHandler.awaitList {
            novelsQueries.getLibrarySources()
        }

        // Combine and get unique sources from both manga and anime
        val count = (mangaSources + animeSources + novelSources).distinct().size
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
            mangasQueries.getLibraryGenres()
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
            animesQueries.getLibraryGenres()
        }

        val count = parseAndGetUniqueGenres(animeGenres)
        animeGenreCache = Pair(count, now)
        return count
    }

    /**
     * Get unique genres count from novel library only
     */
    suspend fun getNovelGenreDiversity(): Int {
        val now = System.currentTimeMillis()
        novelGenreCache?.let { (count, timestamp) ->
            if (now - timestamp < cacheDuration) {
                return count
            }
        }

        val novelGenres = novelHandler.awaitList {
            novelsQueries.getLibraryGenres()
        }

        val count = parseAndGetUniqueGenres(novelGenres)
        novelGenreCache = Pair(count, now)
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
            mangasQueries.getLibrarySources()
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
            animesQueries.getLibrarySources()
        }

        val count = animeSources.distinct().size
        animeSourceCache = Pair(count, now)
        return count
    }

    /**
     * Get unique sources count from novel library only
     */
    suspend fun getNovelSourceDiversity(): Int {
        val now = System.currentTimeMillis()
        novelSourceCache?.let { (count, timestamp) ->
            if (now - timestamp < cacheDuration) {
                return count
            }
        }

        val novelSources = novelHandler.awaitList {
            novelsQueries.getLibrarySources()
        }

        val count = novelSources.distinct().size
        novelSourceCache = Pair(count, now)
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
        novelGenreCache = null
        mangaSourceCache = null
        animeSourceCache = null
        novelSourceCache = null
    }

    /**
     * Parse genre strings (comma-separated) and return count of unique genres
     */
    private fun parseAndGetUniqueGenres(genreLists: List<List<String?>>): Int {
        return genreLists
            .flatten()
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
