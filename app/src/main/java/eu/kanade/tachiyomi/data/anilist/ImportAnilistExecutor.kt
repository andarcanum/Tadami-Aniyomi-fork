package eu.kanade.tachiyomi.data.anilist

import tachiyomi.data.anilist.AnilistImportMediaType
import tachiyomi.data.anilist.AnilistImportPlanner

class ImportAnilistExecutor(
    private val importAnimeEntries: ImportAnilistEntries,
    private val importMangaEntries: ImportAnilistMangaEntries,
) {

    data class Report(
        val added: Int,
        val alreadyInLibrary: Int,
        val failed: Int,
        val trackerBound: Int,
    )

    suspend fun await(
        mediaType: AnilistImportMediaType,
        plan: AnilistImportPlanner.Plan,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): Report {
        return when (mediaType) {
            AnilistImportMediaType.ANIME -> {
                val actions = plan.actions.map {
                    ImportAnilistEntries.Action(it.entry, it.candidate, it.categoryIds)
                }
                val raw = importAnimeEntries.await(actions, onProgress)
                Report(raw.added, raw.alreadyInLibrary, raw.failed, raw.trackerBound)
            }
            AnilistImportMediaType.MANGA -> {
                val actions = plan.actions.map {
                    ImportAnilistMangaEntries.Action(it.entry, it.candidate, it.categoryIds)
                }
                val raw = importMangaEntries.await(actions, onProgress)
                Report(raw.added, raw.alreadyInLibrary, raw.failed, raw.trackerBound)
            }
        }
    }
}
