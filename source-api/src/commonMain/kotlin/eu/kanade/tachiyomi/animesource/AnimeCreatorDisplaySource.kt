package eu.kanade.tachiyomi.animesource

/**
 * Optional display-only capability (contract v30). Creator IDs remain opaque source identities
 * in ShortVideoItem.author, creator requests, follows and hidden-author rows.
 */
interface AnimeCreatorDisplaySource {

    /**
     * Returns a human label for [creatorId], or null when it is unknown. Must be a cheap local
     * lookup: no network, blocking disk reads or identity migration. Never resolve an ambiguous
     * legacy alias to a different creator merely to obtain a label.
     */
    fun creatorDisplayName(creatorId: String): String?
}
