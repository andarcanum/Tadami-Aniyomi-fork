package eu.kanade.tachiyomi.data.backup.restore

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.backup.BackupImportPolicy
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR

data class RestoreOptions(
    val libraryEntries: Boolean = true,
    val restoreManga: Boolean = true,
    val restoreAnime: Boolean = true,
    val restoreNovel: Boolean = true,
    val categories: Boolean = true,
    val appSettings: Boolean = true,
    val extensionRepoSettings: Boolean = true,
    val customButtons: Boolean = true,
    val sourceSettings: Boolean = true,
    val extensions: Boolean = false,
    val achievements: Boolean = true,
    val stats: Boolean = true,
    val reelsFavorites: Boolean = true,
    /**
     * Explicit user statement that a markerless, Mihon shaped file is an old Tadami sister export.
     *
     * Deliberately not part of [options]: it is not a "what to restore" toggle but a claim about
     * the file itself, it is only offered when such a file is actually selected, and it is the only
     * way installed sources are ever allowed to influence how entries are split.
     */
    val legacySisterFallback: Boolean = false,
) {

    private fun hasAnySelectedLibraryType(): Boolean {
        return restoreManga || restoreAnime || restoreNovel
    }

    /** Import rules handed to the decoder. */
    fun importPolicy() = BackupImportPolicy(legacySisterFallback = legacySisterFallback)

    fun asBooleanArray() = booleanArrayOf(
        libraryEntries,
        categories,
        appSettings,
        extensionRepoSettings,
        customButtons,
        sourceSettings,
        extensions,
        achievements,
        stats,
        restoreManga,
        restoreAnime,
        restoreNovel,
        legacySisterFallback,
        reelsFavorites,
    )

    fun canRestore(): Boolean {
        if (libraryEntries && !hasAnySelectedLibraryType()) return false
        return libraryEntries ||
            categories ||
            appSettings ||
            extensionRepoSettings ||
            customButtons ||
            sourceSettings ||
            extensions ||
            achievements ||
            stats ||
            reelsFavorites
    }

    companion object {
        val options = persistentListOf(
            Entry(
                label = MR.strings.label_library,
                getter = RestoreOptions::libraryEntries,
                setter = { options, enabled -> options.copy(libraryEntries = enabled) },
            ),
            Entry(
                label = AYMR.strings.label_manga,
                getter = RestoreOptions::restoreManga,
                setter = { options, enabled -> options.copy(restoreManga = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = AYMR.strings.label_anime,
                getter = RestoreOptions::restoreAnime,
                setter = { options, enabled -> options.copy(restoreAnime = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = AYMR.strings.label_novel,
                getter = RestoreOptions::restoreNovel,
                setter = { options, enabled -> options.copy(restoreNovel = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = MR.strings.categories,
                getter = RestoreOptions::categories,
                setter = { options, enabled -> options.copy(categories = enabled) },
            ),
            Entry(
                label = MR.strings.reels_favorites_title,
                getter = RestoreOptions::reelsFavorites,
                setter = { options, enabled -> options.copy(reelsFavorites = enabled) },
            ),
            Entry(
                label = MR.strings.app_settings,
                getter = RestoreOptions::appSettings,
                setter = { options, enabled -> options.copy(appSettings = enabled) },
            ),
            Entry(
                label = MR.strings.extensionRepo_settings,
                getter = RestoreOptions::extensionRepoSettings,
                setter = { options, enabled -> options.copy(extensionRepoSettings = enabled) },
            ),
            Entry(
                label = AYMR.strings.custom_button_settings,
                getter = RestoreOptions::customButtons,
                setter = { options, enabled -> options.copy(customButtons = enabled) },
            ),
            Entry(
                label = MR.strings.source_settings,
                getter = RestoreOptions::sourceSettings,
                setter = { options, enabled -> options.copy(sourceSettings = enabled) },
            ),
            Entry(
                label = MR.strings.label_extensions,
                getter = RestoreOptions::extensions,
                setter = { options, enabled -> options.copy(extensions = enabled) },
            ),
            Entry(
                label = AYMR.strings.achievements,
                getter = RestoreOptions::achievements,
                setter = { options, enabled -> options.copy(achievements = enabled) },
            ),
            Entry(
                label = AYMR.strings.stats,
                getter = RestoreOptions::stats,
                setter = { options, enabled -> options.copy(stats = enabled) },
            ),
        )

        fun fromBooleanArray(array: BooleanArray) = RestoreOptions(
            libraryEntries = array.getOrNull(0) ?: true,
            categories = array.getOrNull(1) ?: true,
            appSettings = array.getOrNull(2) ?: true,
            extensionRepoSettings = array.getOrNull(3) ?: true,
            customButtons = array.getOrNull(4) ?: true,
            sourceSettings = array.getOrNull(5) ?: true,
            extensions = array.getOrNull(6) ?: false,
            achievements = array.getOrNull(7) ?: true,
            stats = array.getOrNull(8) ?: true,
            restoreManga = array.getOrNull(9) ?: true,
            restoreAnime = array.getOrNull(10) ?: true,
            restoreNovel = array.getOrNull(11) ?: true,
            legacySisterFallback = array.getOrNull(12) ?: false,
            reelsFavorites = array.getOrNull(13) ?: true,
        )
    }

    data class Entry(
        val label: StringResource,
        val getter: (RestoreOptions) -> Boolean,
        val setter: (RestoreOptions, Boolean) -> RestoreOptions,
        val enabled: (RestoreOptions) -> Boolean = { true },
    )
}
