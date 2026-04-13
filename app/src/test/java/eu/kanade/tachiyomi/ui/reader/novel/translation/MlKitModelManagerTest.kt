package eu.kanade.tachiyomi.ui.reader.novel.translation

import com.google.mlkit.nl.translate.TranslateLanguage
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class MlKitModelManagerTest {

    private class FakeMlKitModelClient(
        initialDownloadedLanguageCodes: Set<String> = emptySet(),
    ) : MlKitModelClient {
        private val downloadedLanguageCodes = initialDownloadedLanguageCodes.toMutableSet()
        val downloadCalls = mutableListOf<String>()
        val deleteCalls = mutableListOf<String>()

        override suspend fun getDownloadedLanguageCodes(): Set<String> {
            return downloadedLanguageCodes.toSet()
        }

        override suspend fun download(languageCode: String) {
            downloadCalls += languageCode
            downloadedLanguageCodes += languageCode
        }

        override suspend fun delete(languageCode: String) {
            deleteCalls += languageCode
            downloadedLanguageCodes -= languageCode
        }
    }

    @Test
    fun `list models marks downloaded and built in languages`() = runTest {
        val client = FakeMlKitModelClient(
            initialDownloadedLanguageCodes = setOf(TranslateLanguage.JAPANESE),
        )
        val manager = MlKitModelManager(client)

        val models = manager.listModels(
            supportedLanguageCodes = listOf(
                TranslateLanguage.ENGLISH,
                TranslateLanguage.JAPANESE,
                TranslateLanguage.RUSSIAN,
            ),
            locale = Locale.ENGLISH,
        )

        models.map { it.languageCode } shouldBe listOf(
            TranslateLanguage.ENGLISH,
            TranslateLanguage.JAPANESE,
            TranslateLanguage.RUSSIAN,
        )
        models.single { it.languageCode == TranslateLanguage.ENGLISH }.isBuiltIn shouldBe true
        models.single { it.languageCode == TranslateLanguage.JAPANESE }.isDownloaded shouldBe true
        models.single { it.languageCode == TranslateLanguage.RUSSIAN }.isDownloaded shouldBe false
    }

    @Test
    fun `delete all skips built in english`() = runTest {
        val client = FakeMlKitModelClient(
            initialDownloadedLanguageCodes = setOf(
                TranslateLanguage.ENGLISH,
                TranslateLanguage.JAPANESE,
                TranslateLanguage.RUSSIAN,
            ),
        )
        val manager = MlKitModelManager(client)

        manager.deleteAll()

        client.deleteCalls.shouldContainExactly(
            TranslateLanguage.JAPANESE,
            TranslateLanguage.RUSSIAN,
        )
    }

    @Test
    fun `missing downloaded language codes only includes unloaded languages`() = runTest {
        val client = FakeMlKitModelClient(
            initialDownloadedLanguageCodes = setOf(TranslateLanguage.JAPANESE),
        )
        val manager = MlKitModelManager(client)

        manager.missingDownloadedLanguageCodes(
            setOf(
                TranslateLanguage.JAPANESE,
                TranslateLanguage.RUSSIAN,
                TranslateLanguage.ENGLISH,
            ),
        ) shouldBe setOf(TranslateLanguage.RUSSIAN)
    }
}
