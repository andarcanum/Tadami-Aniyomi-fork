package eu.kanade.presentation.library.novel

import tachiyomi.domain.library.novel.LibraryNovel

internal data class NovelLibraryBadgeState(
    val showDownloaded: Boolean,
    val unreadCount: Long?,
    val language: String?,
)

internal fun resolveNovelLibraryBadgeState(
    item: LibraryNovel,
    showDownloadBadge: Boolean,
    downloadedNovelIds: Set<Long>,
    showUnreadBadge: Boolean,
    showLanguageBadge: Boolean,
    sourceLanguage: String,
): NovelLibraryBadgeState {
    return NovelLibraryBadgeState(
        showDownloaded = showDownloadBadge && item.novel.id in downloadedNovelIds,
        unreadCount = item.unreadCount.takeIf { showUnreadBadge && it > 0L },
        language = sourceLanguage.takeIf { showLanguageBadge && it.isNotBlank() },
    )
}
