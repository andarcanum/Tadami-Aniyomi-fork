package eu.kanade.tachiyomi.data.backup.create

import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR

data class BackupOptions(
    val libraryEntries: Boolean = true,
    val backupManga: Boolean = true,
    val backupAnime: Boolean = true,
    val backupNovel: Boolean = true,
    val categories: Boolean = true,
    val chapters: Boolean = true,
    val tracking: Boolean = true,
    val history: Boolean = true,
    val readEntries: Boolean = true,
    val appSettings: Boolean = true,
    val extensionRepoSettings: Boolean = true,
    val customButton: Boolean = true,
    val sourceSettings: Boolean = true,
    val privateSettings: Boolean = false,
    val extensions: Boolean = false,
    val achievements: Boolean = true,
    val stats: Boolean = true,
) {

    private fun hasAnySelectedLibraryType(): Boolean {
        return backupManga || backupAnime || backupNovel
    }

    fun asBooleanArray() = booleanArrayOf(
        libraryEntries,
        categories,
        chapters,
        tracking,
        history,
        readEntries,
        appSettings,
        extensionRepoSettings,
        customButton,
        sourceSettings,
        privateSettings,
        extensions,
        achievements,
        stats,
        backupManga,
        backupAnime,
        backupNovel,
    )

    fun canCreate(): Boolean {
        if (libraryEntries && !hasAnySelectedLibraryType()) return false
        return libraryEntries ||
            categories ||
            appSettings ||
            extensionRepoSettings ||
            customButton ||
            sourceSettings ||
            achievements ||
            stats
    }

    companion object {
        val libraryOptions = persistentListOf(
            Entry(
                label = AYMR.strings.entries,
                getter = BackupOptions::libraryEntries,
                setter = { options, enabled -> options.copy(libraryEntries = enabled) },
            ),
            Entry(
                label = AYMR.strings.label_manga,
                getter = BackupOptions::backupManga,
                setter = { options, enabled -> options.copy(backupManga = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = AYMR.strings.label_anime,
                getter = BackupOptions::backupAnime,
                setter = { options, enabled -> options.copy(backupAnime = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = AYMR.strings.label_novel,
                getter = BackupOptions::backupNovel,
                setter = { options, enabled -> options.copy(backupNovel = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = AYMR.strings.chapters_episodes,
                getter = BackupOptions::chapters,
                setter = { options, enabled -> options.copy(chapters = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = MR.strings.track,
                getter = BackupOptions::tracking,
                setter = { options, enabled -> options.copy(tracking = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = MR.strings.history,
                getter = BackupOptions::history,
                setter = { options, enabled -> options.copy(history = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = MR.strings.categories,
                getter = BackupOptions::categories,
                setter = { options, enabled -> options.copy(categories = enabled) },
            ),
            Entry(
                label = AYMR.strings.non_library_settings,
                getter = BackupOptions::readEntries,
                setter = { options, enabled -> options.copy(readEntries = enabled) },
                enabled = { it.libraryEntries },
            ),
        )

        val settingsOptions = persistentListOf(
            Entry(
                label = MR.strings.app_settings,
                getter = BackupOptions::appSettings,
                setter = { options, enabled -> options.copy(appSettings = enabled) },
            ),
            Entry(
                label = MR.strings.extensionRepo_settings,
                getter = BackupOptions::extensionRepoSettings,
                setter = { options, enabled -> options.copy(extensionRepoSettings = enabled) },
            ),
            Entry(
                label = AYMR.strings.custom_button_settings,
                getter = BackupOptions::customButton,
                setter = { options, enabled -> options.copy(customButton = enabled) },
            ),
            Entry(
                label = MR.strings.source_settings,
                getter = BackupOptions::sourceSettings,
                setter = { options, enabled -> options.copy(sourceSettings = enabled) },
            ),
            Entry(
                label = MR.strings.private_settings,
                getter = BackupOptions::privateSettings,
                setter = { options, enabled -> options.copy(privateSettings = enabled) },
                enabled = { it.appSettings || it.sourceSettings },
            ),
        )

        val extensionOptions = persistentListOf(
            Entry(
                label = MR.strings.label_extensions,
                getter = BackupOptions::extensions,
                setter = { options, enabled -> options.copy(extensions = enabled) },
            ),
        )

        val achievementsOptions = persistentListOf(
            Entry(
                label = AYMR.strings.achievements,
                getter = BackupOptions::achievements,
                setter = { options, enabled -> options.copy(achievements = enabled) },
            ),
            Entry(
                label = AYMR.strings.stats,
                getter = BackupOptions::stats,
                setter = { options, enabled -> options.copy(stats = enabled) },
            ),
        )

        fun fromBooleanArray(array: BooleanArray) = BackupOptions(
            libraryEntries = array.getOrNull(0) ?: true,
            categories = array.getOrNull(1) ?: true,
            chapters = array.getOrNull(2) ?: true,
            tracking = array.getOrNull(3) ?: true,
            history = array.getOrNull(4) ?: true,
            readEntries = array.getOrNull(5) ?: true,
            appSettings = array.getOrNull(6) ?: true,
            extensionRepoSettings = array.getOrNull(7) ?: true,
            customButton = array.getOrNull(8) ?: true,
            sourceSettings = array.getOrNull(9) ?: true,
            privateSettings = array.getOrNull(10) ?: false,
            extensions = array.getOrNull(11) ?: false,
            achievements = array.getOrNull(12) ?: true,
            stats = array.getOrNull(13) ?: true,
            backupManga = array.getOrNull(14) ?: true,
            backupAnime = array.getOrNull(15) ?: true,
            backupNovel = array.getOrNull(16) ?: true,
        )
    }

    data class Entry(
        val label: StringResource,
        val getter: (BackupOptions) -> Boolean,
        val setter: (BackupOptions, Boolean) -> BackupOptions,
        val enabled: (BackupOptions) -> Boolean = { true },
    )
}
