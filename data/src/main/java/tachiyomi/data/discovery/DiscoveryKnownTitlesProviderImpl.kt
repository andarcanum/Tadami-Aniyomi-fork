package tachiyomi.data.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryTitleIdentity
import tachiyomi.domain.discovery.model.canonicalDiscoveryKey
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle
import tachiyomi.domain.discovery.repository.DiscoveryKnownTitlesProvider

/**
 * Reads known titles from the three native databases. Queries are bulk and
 * query-only; the caller holds the shared mutation fence around the snapshot
 * and the following Discovery transaction.
 */
class DiscoveryKnownTitlesProviderImpl(
    private val mangaHandler: MangaDatabaseHandler,
    private val animeHandler: AnimeDatabaseHandler,
    private val novelHandler: NovelDatabaseHandler,
) : DiscoveryKnownTitlesProvider {

    override suspend fun snapshot(media: DiscoveryMediaType): Set<DiscoveryTitleIdentity> = when (media) {
        DiscoveryMediaType.MANGA -> mangaHandler.awaitList { db ->
            db.discovery_known_mangaQueries.selectKnownTitles { sourceId, sourceUrl, title, _, _ ->
                identity(media, sourceId, sourceUrl, title)
            }
        }

        DiscoveryMediaType.ANIME -> animeHandler.awaitList { db ->
            db.discovery_known_animeQueries.selectKnownTitles { sourceId, sourceUrl, title, _, _ ->
                identity(media, sourceId, sourceUrl, title)
            }
        }

        DiscoveryMediaType.NOVEL -> novelHandler.awaitList { db ->
            db.discovery_known_novelQueries.selectKnownTitles { sourceId, sourceUrl, title, _ ->
                identity(media, sourceId, sourceUrl, title)
            }
        }
    }.toSet()

    override fun observe(media: DiscoveryMediaType): Flow<Set<DiscoveryTitleIdentity>> = when (media) {
        DiscoveryMediaType.MANGA -> mangaHandler.subscribeToList { db ->
            db.discovery_known_mangaQueries.selectKnownTitles { sourceId, sourceUrl, title, _, _ ->
                identity(media, sourceId, sourceUrl, title)
            }
        }

        DiscoveryMediaType.ANIME -> animeHandler.subscribeToList { db ->
            db.discovery_known_animeQueries.selectKnownTitles { sourceId, sourceUrl, title, _, _ ->
                identity(media, sourceId, sourceUrl, title)
            }
        }

        DiscoveryMediaType.NOVEL -> novelHandler.subscribeToList { db ->
            db.discovery_known_novelQueries.selectKnownTitles { sourceId, sourceUrl, title, _ ->
                identity(media, sourceId, sourceUrl, title)
            }
        }
    }.map { rows -> rows.toSet() }

    private fun identity(
        media: DiscoveryMediaType,
        sourceId: Long?,
        sourceUrl: String?,
        title: String?,
    ): DiscoveryTitleIdentity {
        val displayTitle = title.orEmpty()
        val cleanTitle = normalizeDiscoveryTitle(displayTitle)
        return DiscoveryTitleIdentity(
            mediaType = media,
            identityKey = canonicalDiscoveryKey(media, sourceId, sourceUrl, cleanTitle),
            title = displayTitle,
            primaryCleanTitle = cleanTitle,
            sourceId = sourceId,
            sourceUrl = sourceUrl,
        )
    }
}
