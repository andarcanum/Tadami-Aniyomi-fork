package eu.kanade.tachiyomi.ui.more

/**
 * Stable identity of every entry the Aurora «More» screen can render.
 *
 * Declaration order IS the default order of the screen: [resolveMoreMenuLayout] falls back to
 * [MoreEntryId.entries] for the ids the saved order does not mention, so an entry added in a future
 * version shows up in its natural place without any migration.
 */
enum class MoreEntryId {
    /** Bottom-nav tab moved into «More» by [eu.kanade.domain.ui.model.NavStyle]; the only path to it. */
    MOVED_TAB,
    REELS,
    SETTINGS,
    PLAYER_SETTINGS,
    READER_MANGA,
    READER_NOVEL,
    QUOTES,
    STATS,
    ACHIEVEMENTS,
    TREASURY,
    DATA_STORAGE,
    UPDATE_ERRORS,
    DOWNLOADS,
    CATEGORIES,
    DOWNLOADED_ONLY,
    INCOGNITO,
    ABOUT,
    LATTICE_GRID,
    DEBUG_APP_UPDATE,
    DEBUG_CHANGELOG,
    DEBUG_RESET_HEART,
    DEBUG_RESET_LATTICE,
    DEBUG_FORCE_BREACH,
    DEBUG_CRYSTAL,
    HELP,
    ;

    /** Pinned entries cannot be hidden: doing so would cut the only path to the destination. */
    val isPinned: Boolean
        get() = this == MOVED_TAB
}

/** Result of applying the saved order and the hidden set to the currently available entries. */
data class MoreMenuLayout(
    val visible: List<MoreEntryId>,
    val hidden: List<MoreEntryId>,
)

private val MORE_ENTRY_BY_NAME: Map<String, MoreEntryId> = MoreEntryId.entries.associateBy { it.name }

private val DEBUG_ENTRIES = setOf(
    MoreEntryId.DEBUG_APP_UPDATE,
    MoreEntryId.DEBUG_CHANGELOG,
    MoreEntryId.DEBUG_RESET_HEART,
    MoreEntryId.DEBUG_RESET_LATTICE,
    MoreEntryId.DEBUG_FORCE_BREACH,
    MoreEntryId.DEBUG_CRYSTAL,
)

/** Entries that can appear on the screen right now; conditional ones follow their runtime flags. */
fun availableMoreEntryIds(
    showReelsEntry: Boolean,
    latticeGridAvailable: Boolean,
    isDebugBuild: Boolean,
): Set<MoreEntryId> {
    return MoreEntryId.entries.filterTo(mutableSetOf()) { id ->
        when (id) {
            MoreEntryId.REELS -> showReelsEntry
            MoreEntryId.LATTICE_GRID -> latticeGridAvailable
            in DEBUG_ENTRIES -> isDebugBuild
            else -> true
        }
    }
}

/**
 * Merges the saved order and the hidden set with the entries available right now.
 *
 * Unknown ids (renamed or removed entries restored from an old backup) are dropped, ids missing from
 * the saved order are appended in default order, and pinned entries always stay visible.
 */
fun resolveMoreMenuLayout(
    available: Set<MoreEntryId>,
    savedOrderRaw: String,
    hiddenRaw: Set<String>,
): MoreMenuLayout {
    val saved = parseMoreMenuOrder(savedOrderRaw).filterTo(mutableListOf()) { it in available }
    val ordered = saved + MoreEntryId.entries.filter { it in available && it !in saved }
    val hiddenIds = hiddenRaw.mapNotNullTo(mutableSetOf()) { MORE_ENTRY_BY_NAME[it] }
        .filterNotTo(mutableSetOf()) { it.isPinned }

    return MoreMenuLayout(
        visible = ordered.filterNot { it in hiddenIds },
        hidden = ordered.filter { it in hiddenIds },
    )
}

/** Parses the persisted order, dropping ids this version does not know. */
fun parseMoreMenuOrder(raw: String): List<MoreEntryId> {
    if (raw.isEmpty()) return emptyList()
    return raw.split(',').mapNotNull { MORE_ENTRY_BY_NAME[it.trim()] }
}

fun serializeMoreMenuOrder(order: List<MoreEntryId>): String {
    return order.joinToString(separator = ",") { it.name }
}

/** Moves [from] to [to], clamping an out-of-range target; an invalid [from] changes nothing. */
fun moveMoreMenuEntry(order: List<MoreEntryId>, from: Int, to: Int): List<MoreEntryId> {
    if (from !in order.indices) return order
    val target = to.coerceIn(0, order.lastIndex)
    if (from == target) return order

    val moved = order.toMutableList()
    moved.add(target, moved.removeAt(from))
    return moved
}
