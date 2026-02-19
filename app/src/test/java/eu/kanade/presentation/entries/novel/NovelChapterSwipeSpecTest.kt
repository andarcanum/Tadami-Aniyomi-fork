package eu.kanade.presentation.entries.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.service.LibraryPreferences

class NovelChapterSwipeSpecTest {

    @Test
    fun `toggle read swipe uses mark-read icon for unread chapter`() {
        resolveNovelSwipeActionSpec(
            action = LibraryPreferences.NovelSwipeAction.ToggleRead,
            read = false,
            bookmark = false,
            downloaded = false,
            downloading = false,
        ) shouldBe NovelSwipeActionSpec(
            icon = NovelSwipeIcon.MarkRead,
            isUndo = false,
        )
    }

    @Test
    fun `toggle read swipe uses mark-unread icon for read chapter`() {
        resolveNovelSwipeActionSpec(
            action = LibraryPreferences.NovelSwipeAction.ToggleRead,
            read = true,
            bookmark = false,
            downloaded = false,
            downloading = false,
        ) shouldBe NovelSwipeActionSpec(
            icon = NovelSwipeIcon.MarkUnread,
            isUndo = true,
        )
    }

    @Test
    fun `toggle bookmark swipe uses remove-bookmark icon for bookmarked chapter`() {
        resolveNovelSwipeActionSpec(
            action = LibraryPreferences.NovelSwipeAction.ToggleBookmark,
            read = false,
            bookmark = true,
            downloaded = false,
            downloading = false,
        ) shouldBe NovelSwipeActionSpec(
            icon = NovelSwipeIcon.RemoveBookmark,
            isUndo = true,
        )
    }

    @Test
    fun `download swipe uses download icon when chapter is not downloaded`() {
        resolveNovelSwipeActionSpec(
            action = LibraryPreferences.NovelSwipeAction.Download,
            read = false,
            bookmark = false,
            downloaded = false,
            downloading = false,
        ) shouldBe NovelSwipeActionSpec(
            icon = NovelSwipeIcon.Download,
            isUndo = false,
        )
    }

    @Test
    fun `download swipe uses cancel icon while chapter is downloading`() {
        resolveNovelSwipeActionSpec(
            action = LibraryPreferences.NovelSwipeAction.Download,
            read = false,
            bookmark = false,
            downloaded = false,
            downloading = true,
        ) shouldBe NovelSwipeActionSpec(
            icon = NovelSwipeIcon.CancelDownload,
            isUndo = false,
        )
    }

    @Test
    fun `download swipe uses delete icon when chapter is downloaded`() {
        resolveNovelSwipeActionSpec(
            action = LibraryPreferences.NovelSwipeAction.Download,
            read = false,
            bookmark = false,
            downloaded = true,
            downloading = false,
        ) shouldBe NovelSwipeActionSpec(
            icon = NovelSwipeIcon.DeleteDownload,
            isUndo = false,
        )
    }

    @Test
    fun `disabled swipe action produces no swipe spec`() {
        resolveNovelSwipeActionSpec(
            action = LibraryPreferences.NovelSwipeAction.Disabled,
            read = false,
            bookmark = false,
            downloaded = false,
            downloading = false,
        ) shouldBe null
    }
}
