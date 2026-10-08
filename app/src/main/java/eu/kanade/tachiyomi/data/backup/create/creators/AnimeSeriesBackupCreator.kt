package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupAnimeSeries
import eu.kanade.tachiyomi.data.backup.models.BackupSeriesEntryRef
import eu.kanade.tachiyomi.data.cache.SeriesCoverCache
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.series.model.SeriesCoverMode
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeSeriesBackupCreator(
    private val handler: AnimeDatabaseHandler = Injekt.get(),
    private val getCategories: GetAnimeCategories = Injekt.get(),
    private val seriesCoverCache: SeriesCoverCache = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupAnimeSeries> {
        val series = handler.awaitList { db ->
            db.anime_seriesQueries.getAllSeries {
                    id,
                    title,
                    description,
                    categoryId,
                    sortOrder,
                    dateAdded,
                    coverLastModified,
                    pinned,
                    coverMode,
                    coverEntryId,
                ->
                AnimeSeriesRow(
                    id = id,
                    title = title,
                    description = description,
                    categoryId = categoryId,
                    sortOrder = sortOrder,
                    dateAdded = dateAdded,
                    coverLastModified = coverLastModified,
                    pinned = pinned,
                    coverMode = coverMode,
                    coverEntryId = coverEntryId,
                )
            }
        }
        if (series.isEmpty()) return emptyList()

        val categoriesById = getCategories.await().associateBy { it.id }
        return series.map { row ->
            val entryRows = handler.awaitList { db ->
                db.anime_series_entriesQueries.getEntriesBySeriesId(row.id) { _, _, animeId, position ->
                    AnimeSeriesEntryRow(animeId = animeId, position = position.toInt())
                }
            }
            val entries = entryRows.mapNotNull { entry ->
                val anime = handler.awaitOneOrNull { db -> db.animesQueries.getAnimeById(entry.animeId) }
                anime?.let { BackupSeriesEntryRef(source = it.source, url = it.url, position = entry.position) }
            }

            val coverEntry = row.coverEntryId?.let { coverEntryId ->
                handler.awaitOneOrNull { db -> db.animesQueries.getAnimeById(coverEntryId) }
            }
            val customCover = if (row.coverMode == SeriesCoverMode.CUSTOM.value) {
                seriesCoverCache.getAnimeSeriesCoverFile(row.id).takeIf { it.exists() }?.readBytes()
            } else {
                null
            }

            BackupAnimeSeries(
                title = row.title,
                description = row.description,
                categoryName = categoriesById[row.categoryId]?.name,
                sortOrder = row.sortOrder,
                dateAdded = row.dateAdded,
                coverLastModified = row.coverLastModified,
                pinned = row.pinned,
                coverMode = row.coverMode,
                coverEntrySource = coverEntry?.source,
                coverEntryUrl = coverEntry?.url,
                entries = entries.sortedBy { it.position },
                customCover = customCover,
            )
        }
    }
}

private data class AnimeSeriesRow(
    val id: Long,
    val title: String,
    val description: String?,
    val categoryId: Long,
    val sortOrder: Long,
    val dateAdded: Long,
    val coverLastModified: Long,
    val pinned: Boolean,
    val coverMode: Long,
    val coverEntryId: Long?,
)

private data class AnimeSeriesEntryRow(
    val animeId: Long,
    val position: Int,
)
