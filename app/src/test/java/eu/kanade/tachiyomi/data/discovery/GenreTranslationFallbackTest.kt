package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * M2: fallback перевода для жанров источников. Тестируется на фейковом
 * переводчике с ВЫДУМАННЫМИ названиями (реальные турецкие «Aksiyon»/«Macera»
 * онтология уже знает напрямую — Wikidata отдаёт tr-алиасы). Сеть в тестах
 * не участвует. Дисциплина: ошибка перевода, офлайн и бюджет не ломают выдачу.
 *
 * Все методы объявлены с `: Unit`: JUnit Jupiter не обнаруживает @Test-методы,
 * возвращающие значение, и такой тест молча не исполняется.
 */
class GenreTranslationFallbackTest {

    private val fallback = GenreTranslationFallback(
        translate = { name ->
            when (name) {
                "Jaqenai" -> "action"
                "Voltras" -> "adventure"
                else -> null
            }
        },
    )

    @Test
    fun `прямое совпадение не требует перевода дополнительных опций`(): Unit = runBlocking {
        val result = fallback.selectSourceGenres(
            userGenres = listOf("экшен"),
            sourceGenreNames = listOf("Action", "Jaqenai", "Drama"),
        )
        result.shouldContainExactly("Action")
    }

    @Test
    fun `полностью сопоставленные жанры не запускают сетевой fallback`(): Unit = runBlocking {
        val calls = mutableListOf<String>()
        val fb = GenreTranslationFallback(translate = { name ->
            calls += name
            if (name == "Voltras") "adventure" else null
        })
        val result = fb.selectSourceGenres(
            userGenres = listOf("приключения", "драма"),
            sourceGenreNames = listOf("Adventure", "Voltras", "Drama"),
        )
        result.shouldContainExactly("Adventure", "Drama")
        // Both constraints are already matched; extra options need no translation.
        calls.shouldBeEmpty()
    }

    @Test
    fun `перевод кэшируется — повторный вызов не ходит в сеть`(): Unit = runBlocking {
        val calls = mutableListOf<String>()
        val fb = GenreTranslationFallback(translate = { name ->
            calls += name
            if (name == "Jaqenai") "action" else null
        })
        fb.selectSourceGenres(listOf("экшен"), listOf("Jaqenai"))
        fb.selectSourceGenres(listOf("экшен"), listOf("Jaqenai"))
        calls.shouldContainExactly("Jaqenai")
    }

    @Test
    fun `ошибка переводчика не ломает выдачу`(): Unit = runBlocking {
        val fb = GenreTranslationFallback(translate = { throw IllegalStateException("offline") })
        val result = fb.selectSourceGenres(listOf("экшен"), listOf("Action", "Jaqenai"))
        result.shouldContainExactly("Action")
    }

    @Test
    fun `офлайн перевод — только прямой матчинг`(): Unit = runBlocking {
        val fb = GenreTranslationFallback(translate = { null })
        val result = fb.selectSourceGenres(listOf("экшен", "приключения"), listOf("Action", "Jaqenai"))
        result.shouldContainExactly("Action")
    }

    @Test
    fun `бюджет переводов ограничен лавиной экзотики`(): Unit = runBlocking {
        val calls = mutableListOf<String>()
        val fb = GenreTranslationFallback(
            translate = { name ->
                calls += name
                null
            },
            maxTranslationsPerCall = 3,
        )
        val exotic = (1..20).map { "Turkce$it" }
        val result = fb.selectSourceGenres(listOf("экшен"), exotic)
        result.shouldBeEmpty()
        calls.size shouldBe 3
    }

    @Test
    fun `пустые пользовательские жанры не запускают переводы`(): Unit = runBlocking {
        val calls = mutableListOf<String>()
        val fb = GenreTranslationFallback(translate = { name ->
            calls += name
            "action"
        })
        val result = fb.selectSourceGenres(listOf("  "), listOf("Jaqenai"))
        result.shouldBeEmpty()
        calls.shouldBeEmpty()
    }

    @Test
    fun `перевод — единственный путь к нестандартному чекбоксу`(): Unit = runBlocking {
        val result = fallback.selectSourceGenres(listOf("экшен"), listOf("Jaqenai"))
        result.shouldContainExactly("Jaqenai")
    }

    @Test
    fun `строгое совпадение выигрывает у более мягкой альтернативы`(): Unit = runBlocking {
        val result = fallback.selectSourceGenres(
            userGenres = listOf("Action"),
            sourceGenreNames = listOf("Action", "Action Adventure"),
        )
        // "Action Adventure" also matches softly; the reliable strict match must win alone.
        result.shouldContainExactly("Action")
    }

    @Test
    fun `однозначная мягкая альтернатива выбирается`(): Unit = runBlocking {
        val result = fallback.selectSourceGenres(
            userGenres = listOf("Action"),
            sourceGenreNames = listOf("Action Adventure"),
        )
        result.shouldContainExactly("Action Adventure")
    }

    @Test
    fun `ontology alias is a strict match and is not treated as an ambiguous alternative`(): Unit = runBlocking {
        val result = fallback.selectSourceGenres(
            userGenres = listOf("Action"),
            sourceGenreNames = listOf("Action Movie", "Action Series"),
        )
        // "action movie" is an ontology alias of action, so it is admitted
        // strictly; "Action Series" is only a soft token match next to it.
        result.shouldContainExactly("Action Movie")
    }

    @Test
    fun `неоднозначные мягкие альтернативы не угадываются`(): Unit = runBlocking {
        val result = fallback.selectSourceGenres(
            userGenres = listOf("Action"),
            sourceGenreNames = listOf("Action Series", "Action Comics"),
        )
        result.shouldBeEmpty()
    }
}
