package eu.kanade.domain.items.chapter.interactor

import eu.kanade.domain.entries.manga.interactor.GetExcludedScanlators
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.SChapter
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.ShouldUpdateDbChapter
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.model.ChapterUpdate
import tachiyomi.domain.items.chapter.repository.ChapterRepository
import tachiyomi.domain.library.service.LibraryPreferences

class SyncChaptersWithSourceTest {

    @Test
    fun `metadata refresh preserves user changes committed after the database snapshot`() = runTest {
        val fixture = Fixture(chapter())
        val snapshotRead = CompletableDeferred<Unit>()
        val resumeSync = CompletableDeferred<Unit>()
        fixture.afterSnapshot = {
            snapshotRead.complete(Unit)
            resumeSync.await()
        }
        val sourceChapter = sourceChapter().apply {
            name = "Chapter 2 revised"
            chapter_number = 2f
            scanlator = "New group"
            date_upload = 900L
        }

        val sync = async { fixture.interactor.await(listOf(sourceChapter), manga, source) }
        snapshotRead.await()
        fixture.stored = fixture.stored.copy(read = true, bookmark = true, lastPageRead = 37L, version = 8L)
        resumeSync.complete(Unit)
        sync.await() shouldBe emptyList()

        fixture.stored.read shouldBe true
        fixture.stored.bookmark shouldBe true
        fixture.stored.lastPageRead shouldBe 37L
        fixture.stored.version shouldBe 8L
        fixture.stored.dateFetch shouldBe 500L
        fixture.stored.name shouldBe "Chapter 2 revised"
        fixture.stored.chapterNumber shouldBe 2.0
        fixture.stored.scanlator shouldBe "New group"
        fixture.stored.dateUpload shouldBe 900L
        fixture.updates.single().assertSourceOnly()
    }

    @Test
    fun `source order refresh preserves a concurrent unread and progress reset`() = runTest {
        val fixture = Fixture(chapter().copy(read = true, bookmark = true, lastPageRead = 37L, sourceOrder = 4L))
        val snapshotRead = CompletableDeferred<Unit>()
        val resumeSync = CompletableDeferred<Unit>()
        fixture.afterSnapshot = {
            snapshotRead.complete(Unit)
            resumeSync.await()
        }

        val sync = async { fixture.interactor.await(listOf(sourceChapter()), manga, source) }
        snapshotRead.await()
        fixture.stored = fixture.stored.copy(read = false, bookmark = false, lastPageRead = 0L, version = 8L)
        resumeSync.complete(Unit)
        sync.await()

        fixture.stored.read shouldBe false
        fixture.stored.bookmark shouldBe false
        fixture.stored.lastPageRead shouldBe 0L
        fixture.stored.version shouldBe 8L
        fixture.stored.sourceOrder shouldBe 0L
        fixture.updates.single().assertSourceOnly()
    }

    @Test
    fun `memo only refresh keeps user state and persists rotated source context`() = runTest {
        val fixture = Fixture(chapter().copy(memo = memo("old")))
        val snapshotRead = CompletableDeferred<Unit>()
        val resumeSync = CompletableDeferred<Unit>()
        fixture.afterSnapshot = {
            snapshotRead.complete(Unit)
            resumeSync.await()
        }
        val sourceChapter = sourceChapter().apply { memo = memo("rotated") }

        val sync = async { fixture.interactor.await(listOf(sourceChapter), manga, source) }
        snapshotRead.await()
        fixture.stored = fixture.stored.copy(read = true, bookmark = true, lastPageRead = 37L, version = 8L)
        resumeSync.complete(Unit)
        sync.await()

        fixture.stored.memo shouldBe memo("rotated")
        fixture.stored.read shouldBe true
        fixture.stored.bookmark shouldBe true
        fixture.stored.lastPageRead shouldBe 37L
        fixture.stored.version shouldBe 8L
        fixture.updates.single().assertSourceOnly()
    }

    @Test
    fun `empty source memo does not replay older stored context during metadata refresh`() = runTest {
        val fixture = Fixture(chapter().copy(memo = memo("old")))
        val snapshotRead = CompletableDeferred<Unit>()
        val resumeSync = CompletableDeferred<Unit>()
        fixture.afterSnapshot = {
            snapshotRead.complete(Unit)
            resumeSync.await()
        }
        val sourceChapter = sourceChapter().apply { name = "Chapter 1 revised" }

        val sync = async { fixture.interactor.await(listOf(sourceChapter), manga, source) }
        snapshotRead.await()
        fixture.stored = fixture.stored.copy(memo = memo("newer"))
        resumeSync.complete(Unit)
        sync.await()

        fixture.stored.name shouldBe "Chapter 1 revised"
        fixture.stored.memo shouldBe memo("newer")
        fixture.updates.single().memo shouldBe null
    }

    @Test
    fun `readded chapter retains existing read bookmark and fetch date transfer`() = runTest {
        val fixture = Fixture(chapter().copy(read = true, bookmark = true, lastPageRead = 37L))
        val replacement = sourceChapter().apply {
            url = "/chapter-new"
            memo = memo("new-url")
        }

        fixture.interactor.await(listOf(replacement), manga, source) shouldBe emptyList()

        fixture.removedIds shouldBe listOf(1L)
        fixture.added.single().read shouldBe true
        fixture.added.single().bookmark shouldBe true
        fixture.added.single().dateFetch shouldBe 500L
        fixture.added.single().memo shouldBe memo("new-url")
        // Manga replacement currently transfers flags, not the previous URL's page position.
        fixture.added.single().lastPageRead shouldBe 0L
        fixture.updates shouldBe emptyList()
    }

    @Test
    fun `unchanged source chapter does not write a database update`() = runTest {
        val fixture = Fixture(chapter())

        fixture.interactor.await(listOf(sourceChapter()), manga, source) shouldBe emptyList()

        fixture.updates shouldBe emptyList()
        fixture.added shouldBe emptyList()
        fixture.removedIds shouldBe emptyList()
    }

    private fun ChapterUpdate.assertSourceOnly() {
        read shouldBe null
        bookmark shouldBe null
        lastPageRead shouldBe null
        version shouldBe null
        dateFetch shouldBe null
        mangaId shouldBe null
        url shouldBe null
    }

    private class Fixture(initial: Chapter) {
        var stored = initial
        var afterSnapshot: suspend () -> Unit = {}
        val updates = mutableListOf<ChapterUpdate>()
        val added = mutableListOf<Chapter>()
        val removedIds = mutableListOf<Long>()
        private val repository = mockk<ChapterRepository>()
        val interactor: SyncChaptersWithSource

        init {
            coEvery { repository.getChapterByMangaId(any(), any()) } coAnswers {
                val snapshot = listOf(stored)
                afterSnapshot()
                snapshot
            }
            coEvery { repository.updateAllChapters(any()) } coAnswers {
                val patches = firstArg<List<ChapterUpdate>>()
                updates.addAll(patches)
                // Model chapters.sq coalesce semantics at the repository boundary. SQL triggers
                // are not executed here; assertSourceOnly also checks that version is omitted.
                patches.forEach { patch ->
                    stored = stored.copy(
                        mangaId = patch.mangaId ?: stored.mangaId,
                        read = patch.read ?: stored.read,
                        bookmark = patch.bookmark ?: stored.bookmark,
                        lastPageRead = patch.lastPageRead ?: stored.lastPageRead,
                        dateFetch = patch.dateFetch ?: stored.dateFetch,
                        sourceOrder = patch.sourceOrder ?: stored.sourceOrder,
                        url = patch.url ?: stored.url,
                        name = patch.name ?: stored.name,
                        dateUpload = patch.dateUpload ?: stored.dateUpload,
                        chapterNumber = patch.chapterNumber ?: stored.chapterNumber,
                        scanlator = patch.scanlator ?: stored.scanlator,
                        version = patch.version ?: stored.version,
                        memo = patch.memo ?: stored.memo,
                    )
                }
            }
            coEvery { repository.removeChaptersWithIds(any()) } coAnswers {
                removedIds.addAll(firstArg())
                Unit
            }
            coEvery { repository.addAllChapters(any()) } coAnswers {
                firstArg<List<Chapter>>().also { added.addAll(it) }
            }
            val preferences = mockk<LibraryPreferences> {
                every { markDuplicateReadChapterAsRead().get() } returns emptySet()
            }
            val excludedScanlators = mockk<GetExcludedScanlators> {
                coEvery { await(any()) } returns emptySet()
            }
            interactor = SyncChaptersWithSource(
                downloadManager = mockk(relaxed = true),
                downloadProvider = mockk { every { isChapterDirNameChanged(any(), any()) } returns false },
                chapterRepository = repository,
                shouldUpdateDbChapter = ShouldUpdateDbChapter(),
                updateManga = mockk(relaxed = true),
                updateChapter = UpdateChapter(repository),
                getChaptersByMangaId = GetChaptersByMangaId(repository),
                getExcludedScanlators = excludedScanlators,
                libraryPreferences = preferences,
            )
        }
    }

    private companion object {
        val manga = Manga.create().copy(id = 10L, title = "Manga", source = 1L)
        val source = object : MangaSource {
            override val id = 1L
            override val name = "Test"
        }

        fun chapter() = Chapter.create().copy(
            id = 1L,
            mangaId = 10L,
            url = "/chapter-1",
            name = "Chapter 1",
            chapterNumber = 1.0,
            dateUpload = 100L,
            dateFetch = 500L,
            version = 3L,
        )

        fun sourceChapter() = SChapter.create().apply {
            url = "/chapter-1"
            name = "Chapter 1"
            chapter_number = 1f
            date_upload = 100L
        }

        fun memo(slug: String) = JsonObject(mapOf("slug" to JsonPrimitive(slug)))
    }
}
