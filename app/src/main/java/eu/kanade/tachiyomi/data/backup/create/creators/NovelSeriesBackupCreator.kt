package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupNovelSeries
import eu.kanade.tachiyomi.data.backup.models.BackupSeriesEntryRef
import eu.kanade.tachiyomi.data.cache.SeriesCoverCache
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.series.model.SeriesCoverMode
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelSeriesBackupCreator(
    private val handler: NovelDatabaseHandler = Injekt.get(),
    private val getCategories: GetNovelCategories = Injekt.get(),
    private val seriesCoverCache: SeriesCoverCache = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupNovelSeries> {
        val series = handler.awaitList { db ->
            db.novel_seriesQueries.getAllSeries { id, title, description, categoryId, sortOrder, dateAdded, coverLastModified, pinned, coverMode, coverEntryId ->
                NovelSeriesRow(
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
                db.novel_series_entriesQueries.getEntriesBySeriesId(row.id) { _, _, novelId, position ->
                    NovelSeriesEntryRow(novelId = novelId, position = position.toInt())
                }
            }
            val entries = entryRows.mapNotNull { entry ->
                val novel = handler.awaitOneOrNull { db -> db.novelsQueries.getNovelById(entry.novelId) }
                novel?.let { BackupSeriesEntryRef(source = it.source, url = it.url, position = entry.position) }
            }

            val coverEntry = row.coverEntryId?.let { coverEntryId ->
                handler.awaitOneOrNull { db -> db.novelsQueries.getNovelById(coverEntryId) }
            }
            val customCover = if (row.coverMode == SeriesCoverMode.CUSTOM.value) {
                seriesCoverCache.getNovelSeriesCoverFile(row.id).takeIf { it.exists() }?.readBytes()
            } else {
                null
            }

            BackupNovelSeries(
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

private data class NovelSeriesRow(
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

private data class NovelSeriesEntryRow(
    val novelId: Long,
    val position: Int,
)
