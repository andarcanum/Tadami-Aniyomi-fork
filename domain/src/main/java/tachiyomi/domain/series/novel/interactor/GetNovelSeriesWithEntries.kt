package tachiyomi.domain.series.novel.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.series.novel.model.LibraryNovelSeries
import tachiyomi.domain.series.novel.model.LibraryNovelSeriesWithEntryIds
import tachiyomi.domain.series.novel.repository.NovelSeriesRepository

class GetNovelSeriesWithEntries(
    private val repository: NovelSeriesRepository,
    private val novelRepository: NovelRepository,
) {
    fun subscribe(seriesId: Long): Flow<LibraryNovelSeriesWithEntryIds?> {
        return combine(
            repository.getSeriesById(seriesId),
            repository.getEntriesForSeries(seriesId),
            novelRepository.getLibraryNovelAsFlow(),
        ) { series, entries, libraryNovels ->
            if (series == null) return@combine null
            val novelsById = libraryNovels.associateBy { it.novel.id }
            val seriesLibraryNovels = entries
                .sortedBy { it.position }
                .mapNotNull { novelsById[it.novelId] }
            
            val entryIds = entries.associate { it.novelId to it.id }
            LibraryNovelSeriesWithEntryIds(
                series = LibraryNovelSeries(series, seriesLibraryNovels),
                entryIds = entryIds,
            )
        }
    }
}
