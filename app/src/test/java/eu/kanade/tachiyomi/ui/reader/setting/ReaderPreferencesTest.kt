package eu.kanade.tachiyomi.ui.reader.setting

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class ReaderPreferencesTest {

    @Test
    fun `save long page position is enabled by default`() {
        val prefs = ReaderPreferences(FakePreferenceStore())

        prefs.saveLongPagePosition().get() shouldBe true
    }

    @Test
    fun `chapter long page progress can be stored and loaded`() {
        val prefs = ReaderPreferences(FakePreferenceStore())

        prefs.putLongPageProgressForChapter(chapterId = 42L, encodedProgress = 7_001_234_567L)

        prefs.getLongPageProgressForChapter(42L) shouldBe 7_001_234_567L
    }

    @Test
    fun `legacy import writes only when chapter cache is missing`() {
        val prefs = ReaderPreferences(FakePreferenceStore())

        prefs.importLongPageProgressFromLegacyIfMissing(chapterId = 7L, legacyProgress = 100L)
        prefs.putLongPageProgressForChapter(chapterId = 7L, encodedProgress = 200L)
        prefs.importLongPageProgressFromLegacyIfMissing(chapterId = 7L, legacyProgress = 300L)

        prefs.getLongPageProgressForChapter(7L) shouldBe 200L
    }

    @Test
    fun `chapter long page progress cache trims least recent entries`() {
        val prefs = ReaderPreferences(FakePreferenceStore())

        prefs.putLongPageProgressForChapter(chapterId = 1L, encodedProgress = 101L, maxEntries = 2)
        prefs.putLongPageProgressForChapter(chapterId = 2L, encodedProgress = 202L, maxEntries = 2)
        prefs.putLongPageProgressForChapter(chapterId = 3L, encodedProgress = 303L, maxEntries = 2)

        prefs.getLongPageProgressForChapter(1L).shouldBeNull()
        prefs.getLongPageProgressForChapter(2L) shouldBe 202L
        prefs.getLongPageProgressForChapter(3L) shouldBe 303L
    }

    @Test
    fun `chapter long page progress uses chapter key when provided`() {
        val prefs = ReaderPreferences(FakePreferenceStore())

        prefs.putLongPageProgressForChapter(chapterId = 42L, encodedProgress = 111L, chapterKey = "old-url")

        prefs.getLongPageProgressForChapter(42L, chapterKey = "old-url") shouldBe 111L
        prefs.getLongPageProgressForChapter(42L, chapterKey = "new-url").shouldBeNull()
    }

    @Test
    fun `legacy import respects chapter key`() {
        val prefs = ReaderPreferences(FakePreferenceStore())

        prefs.importLongPageProgressFromLegacyIfMissing(chapterId = 9L, legacyProgress = 777L, chapterKey = "url-a")

        prefs.getLongPageProgressForChapter(9L, chapterKey = "url-a") shouldBe 777L
        prefs.getLongPageProgressForChapter(9L, chapterKey = "url-b").shouldBeNull()
    }

    @Test
    fun `keyed lookup ignores unsigned legacy cache entry`() {
        val prefs = ReaderPreferences(FakePreferenceStore())

        prefs.putLongPageProgressForChapter(chapterId = 99L, encodedProgress = 555L)

        prefs.getLongPageProgressForChapter(99L, chapterKey = "chapter-url").shouldBeNull()
    }

    @Test
    fun `remove chapter long page progress removes keyed entry`() {
        val prefs = ReaderPreferences(FakePreferenceStore())

        prefs.putLongPageProgressForChapter(chapterId = 15L, encodedProgress = 100L, chapterKey = "a")
        prefs.removeLongPageProgressForChapter(chapterId = 15L, chapterKey = "b")
        prefs.getLongPageProgressForChapter(15L, chapterKey = "a") shouldBe 100L

        prefs.removeLongPageProgressForChapter(chapterId = 15L, chapterKey = "a")
        prefs.getLongPageProgressForChapter(15L, chapterKey = "a").shouldBeNull()
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

        override fun stateIn(scope: CoroutineScope) = state
    }
}
