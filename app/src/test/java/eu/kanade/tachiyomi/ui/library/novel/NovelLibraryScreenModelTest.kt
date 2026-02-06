package eu.kanade.tachiyomi.ui.library.novel

import tachiyomi.domain.entries.novel.interactor.GetLibraryNovel
import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.library.novel.LibraryNovel

class NovelLibraryScreenModelTest {

    private val getLibraryNovel: GetLibraryNovel = mockk()
    private val libraryFlow = MutableStateFlow<List<LibraryNovel>>(emptyList())
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { getLibraryNovel.subscribe() } returns libraryFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `filters library novels by search query`() = runTest {
        val first = libraryNovel(id = 1L, title = "First Novel")
        val second = libraryNovel(id = 2L, title = "Second Story")
        libraryFlow.value = listOf(first, second)

        val screenModel = NovelLibraryScreenModel(getLibraryNovel = getLibraryNovel)

        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.search("Second")
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.items.shouldContainExactly(second)
    }

    private fun libraryNovel(id: Long, title: String): LibraryNovel {
        return LibraryNovel(
            novel = Novel.create().copy(
                id = id,
                title = title,
                url = "https://example.com/$id",
                source = 1L,
                favorite = true,
            ),
            category = 0L,
            totalChapters = 10L,
            readCount = 1L,
            bookmarkCount = 0L,
            latestUpload = 0L,
            chapterFetchedAt = 0L,
            lastRead = 0L,
        )
    }
}
