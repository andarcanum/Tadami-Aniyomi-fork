package eu.kanade.tachiyomi.ui.reader.novel.translation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ChapterTranslationBackendTest {

    @Test
    fun `ML_KIT and GOOGLE have different fingerprints`() {
        val mlKitFingerprint = ChapterTranslationBackend.ML_KIT.fingerprint()
        val googleFingerprint = ChapterTranslationBackend.GOOGLE.fingerprint()

        assertNotEquals(mlKitFingerprint, googleFingerprint)
        assertEquals("ML_KIT", mlKitFingerprint)
        assertEquals("GOOGLE", googleFingerprint)
    }

    @Test
    fun `cache keys built with different backends cannot be the same`() {
        val cache = GoogleTranslationSessionCache()
        val chapterId = 123L
        val sourceLang = "en"
        val targetLang = "ja"

        val mlKitKey = cache.buildKey(chapterId, sourceLang, targetLang, ChapterTranslationBackend.ML_KIT)
        val googleKey = cache.buildKey(chapterId, sourceLang, targetLang, ChapterTranslationBackend.GOOGLE)

        assertNotEquals(mlKitKey, googleKey)
        assertEquals("123|en|ja|ML_KIT", mlKitKey)
        assertEquals("123|en|ja|GOOGLE", googleKey)
    }

    @Test
    fun `same parameters with same backend produce same key`() {
        val cache = GoogleTranslationSessionCache()
        val chapterId = 456L
        val sourceLang = "fr"
        val targetLang = "de"

        val key1 = cache.buildKey(chapterId, sourceLang, targetLang, ChapterTranslationBackend.ML_KIT)
        val key2 = cache.buildKey(chapterId, sourceLang, targetLang, ChapterTranslationBackend.ML_KIT)

        assertEquals(key1, key2)
    }
}
