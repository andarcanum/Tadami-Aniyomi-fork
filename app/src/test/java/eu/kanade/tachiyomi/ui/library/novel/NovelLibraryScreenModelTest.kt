package eu.kanade.tachiyomi.ui.library.novel

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.library.novel.model.NovelLibrarySort
import tachiyomi.domain.library.service.LibraryPreferences

class NovelLibraryScreenModelTest {

    private lateinit var getLibraryNovel: GetLibraryNovel
    private lateinit var chapterRepository: NovelChapterRepository
    private lateinit var libraryFlow: MutableStateFlow<List<LibraryNovel>>
    private val activeScreenModels = mutableListOf<NovelLibraryScreenModel>()
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var basePreferences: BasePreferences
    private lateinit var libraryPreferences: LibraryPreferences

    @BeforeEach
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        getLibraryNovel = mockk()
        chapterRepository = mockk()
        libraryFlow = MutableStateFlow(emptyList())
        every { getLibraryNovel.subscribe() } returns libraryFlow
        coEvery { chapterRepository.getChapterByNovelId(any(), any()) } returns emptyList()
        val preferenceStore = FakePreferenceStore()
        basePreferences = BasePreferences(
            context = mockk<Context>(relaxed = true),
            preferenceStore = preferenceStore,
        )
        libraryPreferences = LibraryPreferences(preferenceStore)
    }

    @AfterEach
    fun tearDown() {
        activeScreenModels.forEach { it.onDispose() }
        testDispatcher.scheduler.advanceUntilIdle()
        activeScreenModels.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `filters library novels by search query`() = runTest(testDispatcher) {
        val first = libraryNovel(id = 1L, title = "First Novel")
        val second = libraryNovel(id = 2L, title = "Second Story")
        libraryFlow.value = listOf(first, second)

        val screenModel = trackedNovelLibraryScreenModel(
            getLibraryNovel = getLibraryNovel,
            chapterRepository = chapterRepository,
            basePreferences = basePreferences,
            libraryPreferences = libraryPreferences,
            hasDownloadedChapters = { false },
            downloadedIdsDispatcher = testDispatcher,
        )

        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.search("Second")
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.items.shouldContainExactly(second)
    }

    @Test
    fun `unread filter keeps only unread entries`() = runTest(testDispatcher) {
        val unread = libraryNovel(id = 1L, title = "Unread", total = 10L, read = 1L)
        val read = libraryNovel(id = 2L, title = "Read", total = 10L, read = 10L)
        libraryFlow.value = listOf(unread, read)

        val screenModel = trackedNovelLibraryScreenModel(
            getLibraryNovel = getLibraryNovel,
            chapterRepository = chapterRepository,
            basePreferences = basePreferences,
            libraryPreferences = libraryPreferences,
            hasDownloadedChapters = { false },
            downloadedIdsDispatcher = testDispatcher,
        )

        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.toggleUnreadFilter()
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.items.shouldContainExactly(unread)
        screenModel.state.value.hasActiveFilters shouldBe true
    }

    @Test
    fun `completed filter keeps only completed entries`() = runTest(testDispatcher) {
        val ongoing = libraryNovel(id = 1L, title = "Ongoing", status = SManga.ONGOING.toLong())
        val completed = libraryNovel(id = 2L, title = "Completed", status = SManga.COMPLETED.toLong())
        libraryFlow.value = listOf(ongoing, completed)

        val screenModel = trackedNovelLibraryScreenModel(
            getLibraryNovel = getLibraryNovel,
            chapterRepository = chapterRepository,
            basePreferences = basePreferences,
            libraryPreferences = libraryPreferences,
            hasDownloadedChapters = { false },
            downloadedIdsDispatcher = testDispatcher,
        )

        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.toggleCompletedFilter()
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.items.shouldContainExactly(completed)
    }

    @Test
    fun `downloaded filter keeps only downloaded entries`() = runTest(testDispatcher) {
        val downloaded = libraryNovel(id = 1L, title = "Downloaded")
        val notDownloaded = libraryNovel(id = 2L, title = "Not Downloaded")
        libraryFlow.value = listOf(downloaded, notDownloaded)

        val screenModel = trackedNovelLibraryScreenModel(
            getLibraryNovel = getLibraryNovel,
            chapterRepository = chapterRepository,
            basePreferences = basePreferences,
            libraryPreferences = libraryPreferences,
            hasDownloadedChapters = { it.id == downloaded.id },
            downloadedIdsDispatcher = testDispatcher,
        )

        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.toggleDownloadedFilter()
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.items.shouldContainExactly(downloaded)
        screenModel.state.value.hasActiveFilters shouldBe true
    }

    @Test
    fun `sort preference reorders entries`() = runTest(testDispatcher) {
        val older = libraryNovel(id = 1L, title = "Older", lastRead = 10L)
        val newer = libraryNovel(id = 2L, title = "Newer", lastRead = 50L)
        libraryFlow.value = listOf(older, newer)

        val screenModel = trackedNovelLibraryScreenModel(
            getLibraryNovel = getLibraryNovel,
            chapterRepository = chapterRepository,
            basePreferences = basePreferences,
            libraryPreferences = libraryPreferences,
            hasDownloadedChapters = { false },
            downloadedIdsDispatcher = testDispatcher,
        )

        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.setSort(
            NovelLibrarySort.Type.LastRead,
            NovelLibrarySort.Direction.Descending,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.items.shouldContainExactly(newer, older)
    }

    @Test
    fun `interval custom filter keeps only custom interval entries`() = runTest(testDispatcher) {
        val custom = libraryNovel(id = 1L, title = "Custom Interval", fetchInterval = -1)
        val regular = libraryNovel(id = 2L, title = "Regular Interval", fetchInterval = 0)
        libraryFlow.value = listOf(custom, regular)

        val screenModel = trackedNovelLibraryScreenModel(
            getLibraryNovel = getLibraryNovel,
            chapterRepository = chapterRepository,
            basePreferences = basePreferences,
            libraryPreferences = libraryPreferences,
            hasDownloadedChapters = { false },
            downloadedIdsDispatcher = testDispatcher,
        )

        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.toggleIntervalCustomFilter()
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.items.shouldContainExactly(custom)
        screenModel.state.value.hasActiveFilters shouldBe true
    }

    @Test
    fun `next unread follows reader order when source and chapter number order differ`() = runTest(testDispatcher) {
        val novel = Novel.create().copy(
            id = 10L,
            title = "Novel",
            source = 1L,
            chapterFlags = Novel.CHAPTER_SORTING_NUMBER or Novel.CHAPTER_SORT_DESC,
        )
        coEvery {
            chapterRepository.getChapterByNovelId(novelId = novel.id, applyScanlatorFilter = true)
        } returns listOf(
            novelChapter(
                id = 101L,
                novelId = novel.id,
                sourceOrder = 0L,
                chapterNumber = 100.0,
                read = true,
            ),
            novelChapter(
                id = 102L,
                novelId = novel.id,
                sourceOrder = 1L,
                chapterNumber = 1.0,
                read = false,
            ),
            novelChapter(
                id = 103L,
                novelId = novel.id,
                sourceOrder = 2L,
                chapterNumber = 2.0,
                read = false,
            ),
        )

        val screenModel = trackedNovelLibraryScreenModel(
            getLibraryNovel = getLibraryNovel,
            chapterRepository = chapterRepository,
            basePreferences = basePreferences,
            libraryPreferences = libraryPreferences,
            hasDownloadedChapters = { false },
            downloadedIdsDispatcher = testDispatcher,
        )

        screenModel.getNextUnreadChapter(novel)?.id shouldBe 102L
    }

    private fun trackedNovelLibraryScreenModel(
        getLibraryNovel: GetLibraryNovel,
        chapterRepository: NovelChapterRepository,
        basePreferences: BasePreferences,
        libraryPreferences: LibraryPreferences,
        hasDownloadedChapters: (Novel) -> Boolean,
        downloadedIdsDispatcher: TestDispatcher,
    ): NovelLibraryScreenModel {
        return NovelLibraryScreenModel(
            getLibraryNovel = getLibraryNovel,
            chapterRepository = chapterRepository,
            basePreferences = basePreferences,
            libraryPreferences = libraryPreferences,
            hasDownloadedChapters = hasDownloadedChapters,
            downloadedIdsDispatcher = downloadedIdsDispatcher,
        ).also(activeScreenModels::add)
    }

    private fun libraryNovel(
        id: Long,
        title: String,
        total: Long = 10L,
        read: Long = 1L,
        status: Long = 0L,
        lastRead: Long = 0L,
        fetchInterval: Int = 0,
    ): LibraryNovel {
        return LibraryNovel(
            novel = Novel.create().copy(
                id = id,
                title = title,
                url = "https://example.com/$id",
                source = 1L,
                favorite = true,
                status = status,
                fetchInterval = fetchInterval,
            ),
            category = 0L,
            totalChapters = total,
            readCount = read,
            bookmarkCount = 0L,
            latestUpload = 0L,
            chapterFetchedAt = 0L,
            lastRead = lastRead,
        )
    }

    private fun novelChapter(
        id: Long,
        novelId: Long,
        sourceOrder: Long,
        chapterNumber: Double,
        read: Boolean,
    ): NovelChapter {
        return NovelChapter.create().copy(
            id = id,
            novelId = novelId,
            sourceOrder = sourceOrder,
            chapterNumber = chapterNumber,
            read = read,
            url = "https://example.com/chapter/$id",
            name = "Chapter $id",
        )
    }

    private class FakePreferenceStore : PreferenceStore {
        private val strings = mutableMapOf<String, Preference<String>>()
        private val longs = mutableMapOf<String, Preference<Long>>()
        private val ints = mutableMapOf<String, Preference<Int>>()
        private val floats = mutableMapOf<String, Preference<Float>>()
        private val booleans = mutableMapOf<String, Preference<Boolean>>()
        private val stringSets = mutableMapOf<String, Preference<Set<String>>>()
        private val objects = mutableMapOf<String, Preference<Any>>()

        override fun getString(key: String, defaultValue: String): Preference<String> =
            strings.getOrPut(key) { FakePreference(key, defaultValue) }

        override fun getLong(key: String, defaultValue: Long): Preference<Long> =
            longs.getOrPut(key) { FakePreference(key, defaultValue) }

        override fun getInt(key: String, defaultValue: Int): Preference<Int> =
            ints.getOrPut(key) { FakePreference(key, defaultValue) }

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
            floats.getOrPut(key) { FakePreference(key, defaultValue) }

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
            booleans.getOrPut(key) { FakePreference(key, defaultValue) }

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
            stringSets.getOrPut(key) { FakePreference(key, defaultValue) }

        @Suppress("UNCHECKED_CAST")
        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> {
            return objects.getOrPut(key) { FakePreference(key, defaultValue as Any) } as Preference<T>
        }

        override fun getAll(): Map<String, *> {
            return emptyMap<String, Any>()
        }
    }

    private class FakePreference<T>(
        private val preferenceKey: String,
        defaultValue: T,
    ) : Preference<T> {
        private val state = MutableStateFlow(defaultValue)

        override fun key(): String = preferenceKey

        override fun get(): T = state.value

        override fun set(value: T) {
            state.value = value
        }

        override fun isSet(): Boolean = true

        override fun delete() = Unit

        override fun defaultValue(): T = state.value

        override fun changes(): Flow<T> = state

        override fun stateIn(scope: CoroutineScope) = state
    }
}
