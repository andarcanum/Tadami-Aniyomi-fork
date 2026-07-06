package tachiyomi.data.shikimori

import tachiyomi.data.anixart.AnixartRow

/**
 * One anime entry fetched from the user's Shikimori list, prepared for
 * catalogue-source matching (same title semantics as [AnixartRow]).
 */
data class ShikimoriImportEntry(
    val rateId: Long,
    val remoteAnimeId: Long,
    val name: String,
    val russian: String?,
    val status: String,
    val score: Int,
    val episodes: Int,
    val totalEpisodes: Long?,
    val thumbnailUrl: String?,
) {
    fun candidateTitles(): List<String> {
        val raw = buildList {
            add(name)
            add(AnixartRow.cleanAnimeTitle(name))
            russian?.let {
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
            add(AnixartRow.cleanAnimeTitle(name))
            russian?.let { add(AnixartRow.cleanAnimeTitle(it)) }
        }
        val seen = HashSet<String>()
        return raw
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { seen.add(it.lowercase()) }
    }
}
