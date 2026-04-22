package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupMangaSeries
import eu.kanade.tachiyomi.data.backup.models.BackupSeriesEntryRef
import eu.kanade.tachiyomi.data.cache.SeriesCoverCache
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.series.model.SeriesCoverMode
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaSeriesBackupCreator(
    private val handler: MangaDatabaseHandler = Injekt.get(),
    private val getCategories: GetMangaCategories = Injekt.get(),
    private val seriesCoverCache: SeriesCoverCache = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupMangaSeries> {
        val series = handler.awaitList { db ->
            db.manga_seriesQueries.getAllSeries { id, title, description, categoryId, sortOrder, dateAdded, coverLastModified, pinned, coverMode, coverEntryId ->
                MangaSeriesRow(
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
                db.manga_series_entriesQueries.getEntriesBySeriesId(row.id) { _, _, mangaId, position ->
                    MangaSeriesEntryRow(mangaId = mangaId, position = position.toInt())
                }
            }
            val entries = entryRows.mapNotNull { entry ->
                val manga = handler.awaitOneOrNull { db -> db.mangasQueries.getMangaById(entry.mangaId) }
                manga?.let { BackupSeriesEntryRef(source = it.source, url = it.url, position = entry.position) }
            }

            val coverEntry = row.coverEntryId?.let { coverEntryId ->
                handler.awaitOneOrNull { db -> db.mangasQueries.getMangaById(coverEntryId) }
            }
            val customCover = if (row.coverMode == SeriesCoverMode.CUSTOM.value) {
                seriesCoverCache.getMangaSeriesCoverFile(row.id).takeIf { it.exists() }?.readBytes()
            } else {
                null
            }

            BackupMangaSeries(
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

private data class MangaSeriesRow(
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

private data class MangaSeriesEntryRow(
    val mangaId: Long,
    val position: Int,
)
