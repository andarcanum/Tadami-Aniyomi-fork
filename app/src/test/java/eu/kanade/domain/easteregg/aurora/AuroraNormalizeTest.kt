package eu.kanade.domain.easteregg.aurora

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Нормализация — единственный «контракт» между Kotlin и tools/aurora_forge.mjs.
 * Если этот тест падает — ваулт не откроется никогда.
 * ВАЖНО: здесь только нейтральные строки — ответов квеста в коде НЕТ.
 */
class AuroraNormalizeTest {

    @Test
    fun collapseWhitespaceAndTrim() {
        assertEquals("полярная ночь", AuroraVault.normalize("  Полярная   НОЧЬ "))
    }

    @Test
    fun yoMapsToYe() {
        assertEquals("зеленый луч", AuroraVault.normalize("Зелёный луч"))
    }

    @Test
    fun channelPrefixesSurvive() {
        assertEquals("sigil:1-2-3", AuroraVault.normalize("SIGIL:1-2-3"))
        assertEquals("категория:пример", AuroraVault.normalize("Категория:ПРИМЕР"))
    }
}
