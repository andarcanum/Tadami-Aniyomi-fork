package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.database.models.manga.ChapterImpl
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class ReaderViewModelTest {

    @Test
    fun `unread chapters still restore saved progress`() {
        shouldRestoreSavedProgress(
            chapter = readerChapter(
                read = false,
                lastPageRead = 0L,
            ),
            preserveReadingPosition = false,
        ) shouldBe true
    }

    @Test
    fun `read chapters restore when saved progress exists`() {
        shouldRestoreSavedProgress(
            chapter = readerChapter(
                read = true,
                lastPageRead = 12L,
            ),
            preserveReadingPosition = false,
        ) shouldBe true
    }

    @Test
    fun `read chapters without saved progress only restore when preserve is enabled`() {
        shouldRestoreSavedProgress(
            chapter = readerChapter(
                read = true,
                lastPageRead = 0L,
            ),
            preserveReadingPosition = false,
        ) shouldBe false

        shouldRestoreSavedProgress(
            chapter = readerChapter(
                read = true,
                lastPageRead = 0L,
            ),
            preserveReadingPosition = true,
        ) shouldBe true
    }

    @Test
    fun `adjacent chapter switch flushes before restart`() {
        val events = mutableListOf<String>()

        prepareAdjacentChapterSwitch(
            flushReadTimer = { events += "flush" },
            restartReadTimer = { events += "restart" },
        )

        events shouldBe listOf("flush", "restart")
    }

    @Test
    fun `initial auto-scroll speed comes from saved preference`() {
        val readerPreferences = ReaderPreferences(
            MutablePreferenceStore(
                ints = mapOf("pref_auto_scroll_speed" to 73),
            ),
        )

        resolveInitialAutoScrollSpeed(readerPreferences) shouldBe 73
    }

    @Test
    fun `auto-scroll speed updates are persisted`() {
        val store = MutablePreferenceStore()
        val readerPreferences = ReaderPreferences(store)

        persistAutoScrollSpeed(readerPreferences, 88)

        store.getInt("pref_auto_scroll_speed", 50).get() shouldBe 88
    }

    @Test
    fun `preload buffer is 2 on metered connections regardless of speed`() {
        calculatePreloadBufferSize(averageSpeedSeconds = null, isMetered = true) shouldBe 2
        calculatePreloadBufferSize(averageSpeedSeconds = 2.0, isMetered = true) shouldBe 2
        calculatePreloadBufferSize(averageSpeedSeconds = 20.0, isMetered = true) shouldBe 2
    }

    @Test
    fun `preload buffer is 3 by default on unmetered connections`() {
        calculatePreloadBufferSize(averageSpeedSeconds = null, isMetered = false) shouldBe 3
    }

    @Test
    fun `preload buffer scales to 6 when reading speed is fast on unmetered connections`() {
        calculatePreloadBufferSize(averageSpeedSeconds = 3.9, isMetered = false) shouldBe 6
        calculatePreloadBufferSize(averageSpeedSeconds = 1.0, isMetered = false) shouldBe 6
    }

    @Test
    fun `preload buffer scales to 2 when reading speed is slow on unmetered connections`() {
        calculatePreloadBufferSize(averageSpeedSeconds = 16.0, isMetered = false) shouldBe 2
    }

    @Test
    fun `preload buffer is 3 for normal reading speed on unmetered connections`() {
        calculatePreloadBufferSize(averageSpeedSeconds = 5.0, isMetered = false) shouldBe 3
        calculatePreloadBufferSize(averageSpeedSeconds = 14.0, isMetered = false) shouldBe 3
    }

    private fun readerChapter(
        read: Boolean,
        lastPageRead: Long,
    ): ReaderChapter {
        return ReaderChapter(
            ChapterImpl().apply {
                id = 1L
                manga_id = 1L
                url = "chapter-1"
                name = "Chapter 1"
                this.read = read
                this.last_page_read = lastPageRead
            },
        )
    }

    private class MutablePreferenceStore(
        ints: Map<String, Int> = emptyMap(),
    ) : PreferenceStore {
        private val ints = ints.toMutableMap()

        override fun getString(key: String, defaultValue: String): Preference<String> {
            error("Not used in ReaderViewModelTest")
        }

        override fun getLong(key: String, defaultValue: Long): Preference<Long> {
            error("Not used in ReaderViewModelTest")
        }

        override fun getInt(key: String, defaultValue: Int): Preference<Int> {
            return MutablePreference(
                key = key,
                defaultValue = defaultValue,
                getter = { ints[key] ?: defaultValue },
                setter = { ints[key] = it },
            )
        }

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> {
            error("Not used in ReaderViewModelTest")
        }

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> {
            error("Not used in ReaderViewModelTest")
        }

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> {
            error("Not used in ReaderViewModelTest")
        }

        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> {
            error("Not used in ReaderViewModelTest")
        }

        override fun getAll(): Map<String, *> = ints
    }

    private class MutablePreference<T>(
        private val key: String,
        private val defaultValue: T,
        private val getter: () -> T,
        private val setter: (T) -> Unit,
    ) : Preference<T> {
        override fun key(): String = key

        override fun get(): T = getter()

        override fun set(value: T) {
            setter(value)
        }

        override fun isSet(): Boolean = getter() != defaultValue

        override fun delete() {
            setter(defaultValue)
        }

        override fun defaultValue(): T = defaultValue

        override fun changes() = flowOf(getter())

        override fun stateIn(scope: kotlinx.coroutines.CoroutineScope) =
            changes().stateIn(scope, SharingStarted.Eagerly, get())
    }
}
