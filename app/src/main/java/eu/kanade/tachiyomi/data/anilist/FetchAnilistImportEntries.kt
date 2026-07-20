package eu.kanade.tachiyomi.data.anilist

import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.HttpException
import tachiyomi.data.anilist.AnilistImportEntry
import tachiyomi.data.anilist.AnilistImportMediaType
import java.io.IOException

/**
 * Fetches the logged-in user's AniList lists (single MediaListCollection
 * GraphQL request per media type) and converts them into import entries.
 * Mirrors [eu.kanade.tachiyomi.data.shikimori.FetchShikimoriImportEntries].
 */
class FetchAnilistImportEntries(
    private val trackerManager: TrackerManager,
) {

    class NotLoggedInException : Exception()

    class NetworkException(cause: Throwable? = null) : Exception(cause)

    class RateLimitedException : Exception()

    suspend fun await(mediaType: AnilistImportMediaType): List<AnilistImportEntry> {
        val aniList = trackerManager.aniList
        if (!aniList.isLoggedIn) throw NotLoggedInException()

        return try {
            val (userId, _) = aniList.api.getCurrentUser()
            aniList.api.getUserMediaList(userId, mediaType.name)
                .distinctBy { it.id }
                .mapNotNull { raw ->
                    val media = raw.media ?: return@mapNotNull null
                    val romaji = media.title?.romaji?.takeIf { it.isNotBlank() }
                        ?: media.title?.english?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    AnilistImportEntry(
                        mediaType = mediaType,
                        listEntryId = raw.id,
                        remoteId = media.id,
                        name = romaji,
                        english = media.title?.english
                            ?.takeIf { it.isNotBlank() && !it.equals(romaji, ignoreCase = true) },
                        status = raw.status.orEmpty(),
                        score = raw.scoreRaw,
                        progress = raw.progress,
                        totalCount = when (mediaType) {
                            AnilistImportMediaType.ANIME -> media.episodes
                            AnilistImportMediaType.MANGA -> media.chapters
                        },
                        thumbnailUrl = media.coverImage?.large,
                    )
                }
        } catch (e: NotLoggedInException) {
            throw e
        } catch (e: HttpException) {
            if (e.code == 429) throw RateLimitedException()
            throw NetworkException(e)
        } catch (e: IOException) {
            throw NetworkException(e)
        } catch (e: Exception) {
            throw NetworkException(e)
        }
    }
}
