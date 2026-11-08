package tachiyomi.data.easteregg.crystal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.easteregg.crystal.CrystalCatalogBinding
import tachiyomi.domain.easteregg.crystal.CrystalEntryProgress
import tachiyomi.domain.easteregg.crystal.CrystalMedia
import tachiyomi.domain.easteregg.crystal.CrystalProgressCounter
import tachiyomi.domain.easteregg.crystal.CrystalProgressEntry
import tachiyomi.domain.easteregg.crystal.CrystalProgressItem
import tachiyomi.domain.easteregg.crystal.CrystalProgressResult
import tachiyomi.domain.easteregg.crystal.CrystalProgressSource
import tachiyomi.domain.easteregg.crystal.CrystalTitleInput
import tachiyomi.domain.easteregg.crystal.CrystalTitleMatchReason
import tachiyomi.domain.easteregg.crystal.CrystalTitleMatcher
import tachiyomi.domain.easteregg.crystal.CrystalWork

/** Reads only persisted source metadata, local tracking bindings and current read/seen flags. */
class CrystalProgressDataSource(
    private val manga: MangaDatabaseHandler,
    private val anime: AnimeDatabaseHandler,
    private val novel: NovelDatabaseHandler,
    private val trackerCatalog: (Long) -> String? = { null },
) : CrystalProgressSource {

    // Never distinct these lists or Unit signals: 9 -> 10 can leave every candidate _id/title unchanged.
    override fun changes(): Flow<Unit> = merge(
        manga.subscribeToList { it.crystal_progressQueries.getCandidates() }.map { Unit },
        manga.subscribeToList { it.crystal_progressQueries.getLocalTracks() }.map { Unit },
        anime.subscribeToList { it.crystal_progressQueries.getCandidates() }.map { Unit },
        anime.subscribeToList { it.crystal_progressQueries.getLocalTracks() }.map { Unit },
        novel.subscribeToList { it.crystal_progressQueries.getCandidates() }.map { Unit },
        novel.subscribeToList { it.crystal_progressQueries.getLocalTracks() }.map { Unit },
    ).conflate()

    override suspend fun snapshot(): CrystalProgressResult = withContext(Dispatchers.IO) {
        CrystalProgressResult(mangaProgress() + animeProgress() + novelProgress())
    }

    private suspend fun mangaProgress(): List<CrystalEntryProgress> {
        val candidates = manga.awaitList { it.crystal_progressQueries.getCandidates() }
        return candidates.mapNotNull { candidate ->
            manga.await(inTransaction = true) { db ->
                val queries = db.crystal_progressQueries
                val current = queries.getCandidate(candidate._id).executeAsOneOrNull() ?: return@await null
                progress(
                    current._id,
                    CrystalMedia.MANGA,
                    current.title,
                    queries.getTracksForEntry(current._id, mapper = ::LocalTrack).executeAsList(),
                ) {
                    queries.getItemsForEntry(current._id, mapper = ::CrystalProgressItem).executeAsList()
                }
            }
        }
    }

    private suspend fun animeProgress(): List<CrystalEntryProgress> {
        val candidates = anime.awaitList { it.crystal_progressQueries.getCandidates() }
        return candidates.mapNotNull { candidate ->
            anime.await(inTransaction = true) { db ->
                val queries = db.crystal_progressQueries
                val current = queries.getCandidate(candidate._id).executeAsOneOrNull() ?: return@await null
                progress(
                    current._id,
                    CrystalMedia.ANIME,
                    current.title,
                    queries.getTracksForEntry(current._id, mapper = ::LocalTrack).executeAsList(),
                ) {
                    queries.getItemsForEntry(current._id, mapper = ::CrystalProgressItem).executeAsList()
                }
            }
        }
    }

    private suspend fun novelProgress(): List<CrystalEntryProgress> {
        val candidates = novel.awaitList { it.crystal_progressQueries.getCandidates() }
        return candidates.mapNotNull { candidate ->
            novel.await(inTransaction = true) { db ->
                val queries = db.crystal_progressQueries
                val current = queries.getCandidate(candidate._id).executeAsOneOrNull() ?: return@await null
                progress(
                    current._id,
                    CrystalMedia.NOVEL,
                    current.title,
                    queries.getTracksForEntry(current._id, mapper = ::LocalTrack).executeAsList(),
                ) {
                    queries.getItemsForEntry(current._id, mapper = ::CrystalProgressItem).executeAsList()
                }
            }
        }
    }

    private fun progress(
        entryId: Long,
        media: CrystalMedia,
        sourceTitle: String,
        tracks: List<LocalTrack>,
        loadItems: () -> List<CrystalProgressItem>,
    ): CrystalEntryProgress? {
        val bindings = tracks.mapNotNull { track ->
            trackerCatalog(track.syncId)?.takeIf { it in SUPPORTED_CATALOGS }?.let { catalog ->
                CrystalCatalogBinding(catalog, media, track.remoteId)
            }
        }
        val input = CrystalTitleInput(
            media = media,
            sourceTitle = sourceTitle,
            alternativeTitles = tracks.map { it.title },
            trustedBindings = bindings,
        )
        // This whitelist is complete for supported namespaces: every other ID is an explicit conflict.
        val matcher = CrystalTitleMatcher(bindings.associateWith { VERIFIED_BINDINGS[it] })
        if (matcher.match(input).reason == CrystalTitleMatchReason.NO_MATCH) return null

        val hasTargetMetadata = bindings.any { it in VERIFIED_BINDINGS } ||
            ALIAS_MATCHER.match(input.copy(trustedBindings = emptyList())).reason != CrystalTitleMatchReason.NO_MATCH
        // Keep conflict diagnostics, but do not load unrelated titles' children just to count rejected IDs.
        // Such non-target conflicts have a diagnostic count of zero; matching/conflicting aliases count normally.
        val items = if (hasTargetMetadata) loadItems() else emptyList()
        return CrystalProgressCounter(matcher).evaluate(
            listOf(CrystalProgressEntry(entryId, input, items, isLocal = true)),
        ).entries.single()
    }

    private data class LocalTrack(val syncId: Long, val remoteId: Long, val title: String)

    private companion object {
        val SUPPORTED_CATALOGS = setOf("anilist", "myanimelist")
        val ALIAS_MATCHER = CrystalTitleMatcher()

        // Reviewed source proof: app/src/main/assets/easteregg/crystal/title_catalog.json.
        // Identity includes the medium; there is no supported Roommate novel adaptation.
        val VERIFIED_BINDINGS = mapOf(
            CrystalCatalogBinding("anilist", CrystalMedia.ANIME, 13759L) to CrystalWork.SAKURASOU,
            CrystalCatalogBinding("anilist", CrystalMedia.MANGA, 58107L) to CrystalWork.SAKURASOU,
            CrystalCatalogBinding("anilist", CrystalMedia.NOVEL, 60051L) to CrystalWork.SAKURASOU,
            CrystalCatalogBinding("anilist", CrystalMedia.ANIME, 103874L) to CrystalWork.ROOMMATE_CAT,
            CrystalCatalogBinding("anilist", CrystalMedia.MANGA, 97363L) to CrystalWork.ROOMMATE_CAT,
            CrystalCatalogBinding("myanimelist", CrystalMedia.ANIME, 13759L) to CrystalWork.SAKURASOU,
            CrystalCatalogBinding("myanimelist", CrystalMedia.MANGA, 28107L) to CrystalWork.SAKURASOU,
            CrystalCatalogBinding("myanimelist", CrystalMedia.NOVEL, 30051L) to CrystalWork.SAKURASOU,
            CrystalCatalogBinding("myanimelist", CrystalMedia.ANIME, 38145L) to CrystalWork.ROOMMATE_CAT,
            CrystalCatalogBinding("myanimelist", CrystalMedia.MANGA, 99391L) to CrystalWork.ROOMMATE_CAT,
        )
    }
}
