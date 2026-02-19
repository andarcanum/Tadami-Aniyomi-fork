package eu.kanade.presentation.entries.novel

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileDownloadOff
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material3.Icon
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.saket.swipe.SwipeAction
import tachiyomi.domain.library.service.LibraryPreferences

internal enum class NovelSwipeIcon {
    MarkRead,
    MarkUnread,
    AddBookmark,
    RemoveBookmark,
    Download,
    CancelDownload,
    DeleteDownload,
}

internal data class NovelSwipeActionSpec(
    val icon: NovelSwipeIcon,
    val isUndo: Boolean,
)

internal fun resolveNovelSwipeActionSpec(
    action: LibraryPreferences.NovelSwipeAction,
    read: Boolean,
    bookmark: Boolean,
    downloaded: Boolean,
    downloading: Boolean,
): NovelSwipeActionSpec? {
    return when (action) {
        LibraryPreferences.NovelSwipeAction.ToggleRead -> NovelSwipeActionSpec(
            icon = if (read) NovelSwipeIcon.MarkUnread else NovelSwipeIcon.MarkRead,
            isUndo = read,
        )
        LibraryPreferences.NovelSwipeAction.ToggleBookmark -> NovelSwipeActionSpec(
            icon = if (bookmark) NovelSwipeIcon.RemoveBookmark else NovelSwipeIcon.AddBookmark,
            isUndo = bookmark,
        )
        LibraryPreferences.NovelSwipeAction.Download -> NovelSwipeActionSpec(
            icon = when {
                downloading -> NovelSwipeIcon.CancelDownload
                downloaded -> NovelSwipeIcon.DeleteDownload
                else -> NovelSwipeIcon.Download
            },
            isUndo = false,
        )
        LibraryPreferences.NovelSwipeAction.Disabled -> null
    }
}

@Composable
internal fun novelSwipeAction(
    action: LibraryPreferences.NovelSwipeAction,
    read: Boolean,
    bookmark: Boolean,
    downloaded: Boolean,
    downloading: Boolean,
    background: Color,
    onSwipe: () -> Unit,
): SwipeAction? {
    val spec = resolveNovelSwipeActionSpec(
        action = action,
        read = read,
        bookmark = bookmark,
        downloaded = downloaded,
        downloading = downloading,
    ) ?: return null

    return SwipeAction(
        icon = {
            Icon(
                modifier = Modifier.padding(16.dp),
                imageVector = when (spec.icon) {
                    NovelSwipeIcon.MarkRead -> Icons.Outlined.Done
                    NovelSwipeIcon.MarkUnread -> Icons.Outlined.RemoveDone
                    NovelSwipeIcon.AddBookmark -> Icons.Outlined.BookmarkAdd
                    NovelSwipeIcon.RemoveBookmark -> Icons.Outlined.BookmarkRemove
                    NovelSwipeIcon.Download -> Icons.Outlined.Download
                    NovelSwipeIcon.CancelDownload -> Icons.Outlined.FileDownloadOff
                    NovelSwipeIcon.DeleteDownload -> Icons.Outlined.Delete
                },
                tint = contentColorFor(background),
                contentDescription = null,
            )
        },
        background = background,
        onSwipe = onSwipe,
        isUndo = spec.isUndo,
    )
}

internal val novelSwipeActionThreshold = 56.dp
