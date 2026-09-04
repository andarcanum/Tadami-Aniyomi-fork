package eu.kanade.tachiyomi.ui.reader.novel

import android.app.Application
import eu.kanade.tachiyomi.data.translation.TranslationQueueManager
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationCacheEntry
import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationDiskCacheStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.fullType
import uy.kohesive.injekt.api.get
import java.io.File

class NovelTranslationControllerClearCacheTest {

    @TempDir
    lateinit var tempDir: File

    private fun ensureStoreDependencies() {
        runCatching { Injekt.get<Json>() }.getOrElse {
            Injekt.addSingleton(
                fullType<Json>(),
                Json {
                    encodeDefaults = true
                    ignoreUnknownKeys = true
                },
            )
        }
        runCatching { Injekt.get<Application>() }.getOrElse {
            val app = mockk<Application>(relaxed = true)
            every { app.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }
            every { app.codeCacheDir } returns File(tempDir, "code-cache").apply { mkdirs() }
            Injekt.addSingleton(fullType<Application>(), app)
        }
    }

    private fun createController(): NovelTranslationController {
        val settings = mockk<NovelReaderSettings>(relaxed = true)
        val host = mockk<NovelTranslationHost>(relaxed = true)
        every { host.translationScope } returns CoroutineScope(Dispatchers.Unconfined)
        every { host.translationReaderSettings() } returns settings
        every { host.translationActiveChapterId() } returns 7L

        return NovelTranslationController(
            host = host,
            application = mockk(relaxed = true),
            novelReaderPreferences = mockk<NovelReaderPreferences>(relaxed = true),
            googleTranslationService = mockk<GoogleTranslationService>(relaxed = true),
            translationQueueManager = mockk<TranslationQueueManager>(relaxed = true),
        )
    }

    @Test
    fun `kind-switch clear keeps the disk cache while explicit clear deletes it`() {
        ensureStoreDependencies()
        NovelReaderTranslationDiskCacheStore.clear()
        try {
            NovelReaderTranslationDiskCacheStore.put(
                GeminiTranslationCacheEntry(
                    chapterId = 7L,
                    translatedByIndex = mapOf(0 to "translated"),
                    model = "model",
                    sourceLang = "English",
                    targetLang = "Russian",
                    promptMode = GeminiPromptMode.ADULT_18,
                ),
            )
            val controller = createController()

            // Switching translation kind hides the Gemini side but must not delete the chapter's
            // disk cache: switching back should restore the already-paid translation.
            controller.clearGeminiTranslationForSwitch()
            NovelReaderTranslationDiskCacheStore.get(7L) shouldNotBe null

            // The explicit "clear cache" action still deletes it.
            controller.clearGeminiTranslation()
            NovelReaderTranslationDiskCacheStore.get(7L) shouldBe null
        } finally {
            NovelReaderTranslationDiskCacheStore.clear()
        }
    }
}
