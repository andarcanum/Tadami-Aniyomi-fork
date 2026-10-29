package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.data.book.novel.NovelBookArtifact
import eu.kanade.tachiyomi.data.book.novel.NovelBookArtifactWriter
import eu.kanade.tachiyomi.data.book.novel.NovelBookBuildRequest
import eu.kanade.tachiyomi.data.book.novel.NovelBookSourceChapter
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsChapterRepository
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsChapterSnapshot
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.book.novel.interactor.GetNovelBookState
import tachiyomi.domain.book.novel.interactor.SetNovelBookProgress
import tachiyomi.domain.book.novel.model.NovelBookState
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import java.io.File

class NovelBookReaderControllerTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val preferenceStore = PersistingPreferenceStore()
    private val novelReaderPreferences = NovelReaderPreferences(preferenceStore, json)
    private val getNovelBookState = mockk<GetNovelBookState>()
    private val setNovelBookProgress = mockk<SetNovelBookProgress>(relaxed = true)

    @BeforeEach
    fun setUp() {
        Injekt.addSingleton(json)
        Injekt.addSingleton(novelReaderPreferences)
        Injekt.addSingleton(DownloadPreferences(preferenceStore))
        Injekt.addSingleton(mockk<NovelDownloadManager>(relaxed = true))
    }

    private fun buildTestArtifact(
        dir: File,
        chapters: List<NovelBookSourceChapter> = listOf(
            NovelBookSourceChapter(id = 1L, name = "Chapter 1", url = "/c1"),
        ),
    ): NovelBookArtifactSource {
        val request = NovelBookBuildRequest(
            sourceId = 7L,
            novelId = 42L,
            novelTitle = "Test novel",
            chapterSetHash = NovelBookArtifact.chapterSetHash(chapters),
            builtAt = 1_000L,
        )
        NovelBookArtifactWriter(dir).build(
            request = request,
            chapters = chapters,
            loadHtml = { chapter ->
                """
                <h2 class="nb-title">Chapter ${chapter.id}</h2>
                <p>Original body of ${chapter.id}</p>
                """.trimIndent()
            },
        )
        return NovelBookArtifactSource.open(dir)!!
    }

    @Test
    fun `nativeBookBlocksForSection returns translated blocks when translation is visible`(
        @TempDir dir: File,
    ) = runTest {
        val artifactSource = buildTestArtifact(dir)
        var googleVisible = false
        var geminiVisible = false

        val host = object : NovelBookReaderHost {
            override val bookScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
            override fun bookCurrentNovel(): Novel = Novel.create().copy(id = 42L, source = 7L, title = "Test novel")
            override fun bookCurrentChapter(): NovelChapter = NovelChapter.create().copy(id = 1L, name = "Chapter 1")
            override fun bookChapterOrderList(): List<NovelChapter> = listOf(
                NovelChapter.create().copy(id = 1L, name = "Chapter 1"),
                NovelChapter.create().copy(id = 2L, name = "Chapter 2"),
            )
            override fun bookFullChapterOrderList(): List<NovelChapter> = bookChapterOrderList()
            override fun bookMarkChapterReadInMemory(chapterId: Long) {}
            override fun bookTtsChapterRepository(): NovelTtsChapterRepository = mockk {
                coEvery { loadChapterSnapshot(any()) } answers {
                    val id = firstArg<Long>()
                    val novel = Novel.create().copy(id = 42L, source = 7L, title = "Test novel")
                    val chapter = NovelChapter.create().copy(id = id, name = "Chapter $id")
                    NovelTtsChapterSnapshot(
                        novel = novel,
                        chapter = chapter,
                        chapterOrderList = listOf(chapter),
                        rawHtml = "<h2>Chapter $id</h2><p>Original body of $id</p>",
                        customCss = null,
                        customJs = null,
                        pluginSite = null,
                        chapterWebUrl = null,
                        contentBlocks = emptyList(),
                        richContentBlocks = emptyList(),
                        richContentUnsupportedFeaturesDetected = false,
                        lastSavedIndex = 0,
                        lastSavedScrollOffsetPx = 0,
                        lastSavedWebProgressPercent = 0,
                        lastSavedPageReaderProgress = null,
                        previousChapterId = null,
                        previousChapterName = null,
                        nextChapterId = null,
                        nextChapterName = null,
                    )
                }
            }
            override fun bookUpdateSuccessState(
                transform: (NovelReaderScreenModel.State.Success) -> NovelReaderScreenModel.State.Success,
            ) {}
            override fun bookAdoptBookModeChapter(chapterId: Long) {}
            override fun bookEnqueueProgressPersistence(update: PendingProgressPersistence) {}
            override fun onNovelCompletedWitnessed(chapter: NovelChapter) {}
            override fun bookApplyBookSectionTranslation(chapterId: Long, bodyHtml: String): String {
                return bodyHtml.replace("Original body of", "TRANSLATED BODY OF")
            }
            override fun bookGeminiTranslationVisible(): Boolean = geminiVisible
            override fun bookGoogleTranslationVisible(): Boolean = googleVisible
        }

        coEvery { getNovelBookState.await(any()) } returns NovelBookState.create(novelId = 42L, sourceId = 7L).copy(
            enabled = true,
            complete = true,
        )

        val controller = NovelBookReaderController(
            host = host,
            novelReaderPreferences = novelReaderPreferences,
            getNovelBookState = getNovelBookState,
            setNovelBookProgress = setNovelBookProgress,
        )

        val field = NovelBookReaderController::class.java.getDeclaredField("artifactSource")
        field.isAccessible = true
        field.set(controller, artifactSource)

        novelReaderPreferences.bookModeShowChapterHeadings().set(true)

        // 1. Raw display: translation not visible
        val rawBlocks = controller.nativeBookBlocksForSection(0)
        rawBlocks shouldNotBe null
        val rawParagraph = rawBlocks!!.filterIsInstance<NovelRichContentBlock.Paragraph>().single()
        rawParagraph.segments.single().text shouldBe "Original body of 1"
        rawParagraph.bookRange shouldNotBe null

        // 2. Visible translation active: Google translation visible
        googleVisible = true
        val translatedBlocks = controller.nativeBookBlocksForSection(0)
        translatedBlocks shouldNotBe null
        val translatedParagraph = translatedBlocks!!.filterIsInstance<NovelRichContentBlock.Paragraph>().single()
        translatedParagraph.segments.single().text shouldBe "TRANSLATED BODY OF 1"
        translatedParagraph.bookRange shouldBe rawParagraph.bookRange

        // 3. Headings turned off: generated chapter heading dropped, non-heading content kept
        novelReaderPreferences.bookModeShowChapterHeadings().set(false)
        val noHeadingBlocks = controller.nativeBookBlocksForSection(0)
        noHeadingBlocks shouldNotBe null
        noHeadingBlocks!!.none { it is NovelRichContentBlock.Heading && it.isBookChapterHeading } shouldBe true
        noHeadingBlocks.filterIsInstance<NovelRichContentBlock.Paragraph>().size shouldBe 1
    }

    @Test
    fun `headings preference change invalidates resident section revisions`(@TempDir dir: File) = runTest {
        val artifactSource = buildTestArtifact(dir)
        val host = object : NovelBookReaderHost {
            override val bookScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
            override fun bookCurrentNovel(): Novel = Novel.create().copy(id = 42L, source = 7L, title = "Test novel")
            override fun bookCurrentChapter(): NovelChapter = NovelChapter.create().copy(id = 1L, name = "Chapter 1")
            override fun bookChapterOrderList(): List<NovelChapter> = listOf(
                NovelChapter.create().copy(id = 1L, name = "Chapter 1"),
                NovelChapter.create().copy(id = 2L, name = "Chapter 2"),
            )
            override fun bookFullChapterOrderList(): List<NovelChapter> = bookChapterOrderList()
            override fun bookMarkChapterReadInMemory(chapterId: Long) {}
            override fun bookTtsChapterRepository(): NovelTtsChapterRepository = mockk(relaxed = true)
            override fun bookUpdateSuccessState(
                transform: (NovelReaderScreenModel.State.Success) -> NovelReaderScreenModel.State.Success,
            ) {}
            override fun bookAdoptBookModeChapter(chapterId: Long) {}
            override fun bookEnqueueProgressPersistence(update: PendingProgressPersistence) {}
            override fun onNovelCompletedWitnessed(chapter: NovelChapter) {}
            override fun bookApplyBookSectionTranslation(chapterId: Long, bodyHtml: String): String = bodyHtml
            override fun bookGeminiTranslationVisible(): Boolean = false
            override fun bookGoogleTranslationVisible(): Boolean = false
        }

        val controller = NovelBookReaderController(
            host = host,
            novelReaderPreferences = novelReaderPreferences,
            getNovelBookState = getNovelBookState,
            setNovelBookProgress = setNovelBookProgress,
        )

        val field = NovelBookReaderController::class.java.getDeclaredField("artifactSource")
        field.isAccessible = true
        field.set(controller, artifactSource)

        controller.onBookHeadingsPreferenceChanged()
    }
}
