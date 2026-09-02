package eu.kanade.tachiyomi.ui.updates.novel

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.updates.novel.interactor.GetNovelUpdates

class NovelUpdatesScreenModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `lastUpdated reflects updated timestamp from preferences`() = runTest(testDispatcher) {
        val preferenceStore = FakePreferenceStore()
        val libraryPreferences = LibraryPreferences(preferenceStore)
        libraryPreferences.lastUpdatedTimestamp().set(12345L)

        val getUpdates = mockk<GetNovelUpdates>()
        every { getUpdates.subscribe(any()) } returns emptyFlow()
        val chapterRepository = mockk<NovelChapterRepository>(relaxed = true)

        val screenModel = NovelUpdatesScreenModel(
            getUpdates = getUpdates,
            libraryPreferences = libraryPreferences,
            chapterRepository = chapterRepository,
        )

        assertEquals(12345L, screenModel.lastUpdated)

        libraryPreferences.lastUpdatedTimestamp().set(67890L)
        testScheduler.advanceUntilIdle()

        assertEquals(67890L, screenModel.lastUpdated)
    }

    private class FakePreferenceStore : PreferenceStore {
        private val longs = mutableMapOf<String, Preference<Long>>()

        override fun getString(key: String, defaultValue: String): Preference<String> =
            throw UnsupportedOperationException()

        override fun getLong(key: String, defaultValue: Long): Preference<Long> =
            longs.getOrPut(key) { FakePreference(key, defaultValue) }

        override fun getInt(key: String, defaultValue: Int): Preference<Int> =
            throw UnsupportedOperationException()

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
            throw UnsupportedOperationException()

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
            throw UnsupportedOperationException()

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
            throw UnsupportedOperationException()

        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> = throw UnsupportedOperationException()

        override fun getAll(): Map<String, *> = emptyMap<String, Any>()
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

        override fun stateIn(scope: CoroutineScope): StateFlow<T> = state
    }
}
