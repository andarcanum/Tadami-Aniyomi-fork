package tachiyomi.data.anilist

import tachiyomi.data.anixart.AnixartRow

/**
 * One AniList list entry prepared for catalogue-source matching.
 * Mirrors [tachiyomi.data.shikimori.ShikimoriImportEntry].
 */
data class AnilistImportEntry(
    val mediaType: AnilistImportMediaType,
    val listEntryId: Long,
    val remoteId: Long,
    val name: String,
    val english: String?,
    val status: String,
    val score: Int,
    val progress: Int,
    val totalCount: Long?,
    val thumbnailUrl: String?,
) {
    fun candidateTitles(): List<String> {
        val raw = buildList {
            add(name)
            add(AnixartRow.cleanAnimeTitle(name))
            english?.let {
                add(it)
                add(AnixartRow.cleanAnimeTitle(it))
            }
        }
        val seen = HashSet<String>()
        return raw
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { seen.add(it.lowercase()) }
    }

    fun searchQueries(): List<String> {
        val raw = buildList {
            add(name)
            english?.let { add(it) }
            add(AnixartRow.cleanAnimeTitle(name))
            english?.let { add(AnixartRow.cleanAnimeTitle(it)) }
        }
        val seen = HashSet<String>()
        return raw
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { seen.add(it.lowercase()) }
    }
}
