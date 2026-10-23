package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
/**
 * Проверка эвристики матчинга жанров между пользовательским вводом (любой язык/форма)
 * и названиями жанров источников. Ключевое требование владельца: мусорный или
 * незнакомый жанр обязан быть инертным (не матчиться, не падать), а похожие,
 * но РАЗНЫЕ жанры — не склеиваться (роман ≠ романтика, music ≠ musical).
 */
class GenreMatcherTest {

    // ═══════════ Уровень 1: нормализация ═══════════

    @Test
    fun `нормализация схлопывает регистр, пунктуацию, диакритики и ё`() {
        GenreMatcher.normalize("  Action  ") shouldBe "action"
        GenreMatcher.normalize("Sci-Fi") shouldBe "sci fi"
        GenreMatcher.normalize("COMÉDIE") shouldBe "comedie"
        GenreMatcher.normalize("Seïnen") shouldBe "seinen"
        GenreMatcher.normalize("СЁНЕН") shouldBe "сенен" // ё→е; э не разлагается и остаётся
        GenreMatcher.normalize("Драма.") shouldBe "драма"
        GenreMatcher.normalize("Slice   of\tLife") shouldBe "slice of life"
    }

    @Test
    fun `нормализация мусора даёт пустую строку`() {
        GenreMatcher.normalize("") shouldBe ""
        GenreMatcher.normalize("   ") shouldBe ""
        GenreMatcher.normalize("!!!") shouldBe ""
        GenreMatcher.normalize("123") shouldBe "123"
    }

    @Test
    fun `транслитерация кириллицы в латиницу`() {
        GenreMatcher.translitCyrillicToLatin("меха") shouldBe "meha"
        GenreMatcher.translitCyrillicToLatin("хентай") shouldBe "hentay"
        GenreMatcher.translitCyrillicToLatin("фэнтези") shouldBe "fentezi"
        GenreMatcher.translitCyrillicToLatin("драма") shouldBe "drama"
        GenreMatcher.translitCyrillicToLatin("action") shouldBe "action"
    }

    // ═══════════ Уровень 2: формы ═══════════

    @Test
    fun `варианты включают нормализацию и обе романизации`() {
        GenreMatcher.variants("Экшен").let {
            it.shouldContain("экшен")
            it.shouldContain("ekshen")
        }
        GenreMatcher.variants("Меха").let {
            it.shouldContain("меха")
            it.shouldContain("meha")
            it.shouldContain("mekha")
        }
        GenreMatcher.variants("Action") shouldBe setOf("action")
    }

    @Test
    fun `варианты пустого и мусорного ввода пусты`() {
        GenreMatcher.variants("").shouldBeEmpty()
        GenreMatcher.variants("   ").shouldBeEmpty()
        GenreMatcher.variants("!!!").shouldBeEmpty()
        GenreMatcher.variants("🔥").shouldBeEmpty()
    }

    @Test
    fun `варианты сверхдлинного ввода пусты (защита от мусора)`() {
        GenreMatcher.variants("а".repeat(100)).shouldBeEmpty()
        GenreMatcher.variants("драма ".repeat(30)).shouldBeEmpty()
    }

    // ═══════════ Уровень 3: семантический мост онтологии ═══════════

    @Test
    fun `resolve возвращает канонические жанры онтологии`() {
        GenreMatcher.resolve("экшен") shouldBe setOf("action")
        GenreMatcher.resolve("космос") shouldBe setOf("space")
        GenreMatcher.resolve("Shounen") shouldBe setOf("shounen")
        GenreMatcher.resolve("мусор не жанр") shouldBe emptySet()
        GenreMatcher.resolve("") shouldBe emptySet()
    }

    @Test
    fun `онтология мостит языки через канонический жанр`() {
        // Итальянский ↔ русский → adventure
        GenreMatcher.matches("avventura", "приключения") shouldBe true
        // Арабский ↔ русский → action
        GenreMatcher.matches("حركة", "боевик") shouldBe true
        // Китайский ↔ русский → fantasy
        GenreMatcher.matches("奇幻小說", "фэнтези") shouldBe true
        // Английский синоним ↔ русский → thriller
        GenreMatcher.matches("suspense", "триллер") shouldBe true
    }

    @Test
    fun `словарные мосты RU-EN работают в обе стороны`() {
        GenreMatcher.matches("экшен", "Action") shouldBe true
        GenreMatcher.matches("Action", "боевик") shouldBe true
        GenreMatcher.matches("повседневность", "Slice of Life") shouldBe true
        GenreMatcher.matches("Psychological", "психологическое") shouldBe true
        GenreMatcher.matches("сэйнэн", "Seinen") shouldBe true
        GenreMatcher.matches("Shounen", "сёнен") shouldBe true
        GenreMatcher.matches("фантастика", "Sci-Fi") shouldBe true
    }

    @Test
    fun `ё-формы матчатся с е-формами и романизацией`() {
        GenreMatcher.matches("сёнен", "сонен") shouldBe true
        GenreMatcher.matches("сёнен", "senen") shouldBe true
        GenreMatcher.matches("shonen", "shounen") shouldBe true
        GenreMatcher.matches("сёнен", "Сёнэн") shouldBe true
    }

    @Test
    fun `диакритики источников матчатся с ascii-формами`() {
        GenreMatcher.matches("comédie", "comedy") shouldBe true
        GenreMatcher.matches("Comédie", "Комедия") shouldBe true
        GenreMatcher.matches("seïnen", "Seinen") shouldBe true
    }

    @Test
    fun `романизация кириллицы матчится с латиницей`() {
        GenreMatcher.matches("меха", "mekha") shouldBe true
        GenreMatcher.matches("meha", "Меха") shouldBe true
        GenreMatcher.matches("хентай", "hentay") shouldBe true
        GenreMatcher.matches("хентай", "hentai") shouldBe true
    }

    // ═══════════ Уровень 4: мягкие формы и токены ═══════════

    @Test
    fun `множественное число матчится с единственным`() {
        GenreMatcher.matches("драмы", "драма") shouldBe true
        GenreMatcher.matches("Драма", "драмы") shouldBe true
        GenreMatcher.matches("horrors", "horror") shouldBe true
        GenreMatcher.matches("Comedies", "comedy") shouldBe true
        GenreMatcher.matches("вампиры", "Vampire") shouldBe true
    }

    @Test
    fun `токены многословных жанров матчатся`() {
        GenreMatcher.matches("жанр экшен", "экшен") shouldBe true
        GenreMatcher.matches("science fiction", "фантастика") shouldBe true
        GenreMatcher.matches("Science Fiction", "Science") shouldBe true
        GenreMatcher.matches("боевые искусства", "martial arts") shouldBe true
    }

    // ═══════════ Уровень 5: опечатки ═══════════

    @Test
    fun `опечатки единичной буквы ловятся для длины от 4`() {
        GenreMatcher.matches("сихологическое", "психологическое") shouldBe true
        GenreMatcher.matches("actionn", "action") shouldBe true
        GenreMatcher.matches("drame", "drama") shouldBe true
        GenreMatcher.matches("seien", "seinen") shouldBe true
    }

    @Test
    fun `опечатки двух букв ловятся только для длинных слов`() {
        GenreMatcher.matches("фантастикаа", "фантастика") shouldBe true
        GenreMatcher.matches("sicológica", "psychological") shouldBe false // исп. без моста, дистанция велика
    }

    @Test
    fun `левенштейн считается корректно`() {
        GenreMatcher.levenshtein("action", "action") shouldBe 0
        GenreMatcher.levenshtein("drama", "drame") shouldBe 1
        GenreMatcher.levenshtein("shounen", "shoujo") shouldBe 3
        GenreMatcher.levenshtein("", "abc") shouldBe 3
    }

    // ═══════════ НЕГАТИВЫ: похожие, но РАЗНЫЕ жанры ═══════════

    @Test
    fun `роман не склеивается с романтикой`() {
        GenreMatcher.matches("роман", "романтика") shouldBe false
        GenreMatcher.matches("Романтика", "роман") shouldBe false
        GenreMatcher.matches("romance", "novel") shouldBe false
    }

    @Test
    fun `близкие, но разные жанры не матчатся`() {
        GenreMatcher.matches("music", "musical") shouldBe false
        GenreMatcher.matches("музыка", "музыкальный") shouldBe false
        GenreMatcher.matches("shoujo", "shounen") shouldBe false
        GenreMatcher.matches("сёдзё", "сёнен") shouldBe false
        GenreMatcher.matches("yuri", "yaoi") shouldBe false
        GenreMatcher.matches("фэнтези", "фантастика") shouldBe false
        GenreMatcher.matches("horror", "romance") shouldBe false
        GenreMatcher.matches("драма", "драматургия") shouldBe false
        GenreMatcher.matches("mecha", "mechanical") shouldBe false
    }

    // ═══════════ Устойчивость к мусорному вводу ═══════════

    @Test
    fun `мусорный пользовательский ввод инертен и не падает`() {
        GenreMatcher.matches("", "Action") shouldBe false
        GenreMatcher.matches("   ", "Action") shouldBe false
        GenreMatcher.matches("!!!", "Драма") shouldBe false
        GenreMatcher.matches("🔥", "экшен") shouldBe false
        GenreMatcher.matches("123", "фантастика") shouldBe false
        GenreMatcher.matches("а".repeat(100), "драма") shouldBe false
        GenreMatcher.matches("драма", "") shouldBe false
        GenreMatcher.matches("драма", "🔥🔥") shouldBe false
    }

    @Test
    fun `неизвестный жанр просто не матчится — не падает и не блокирует`() {
        GenreMatcher.matches("кулонное искушение", "Action") shouldBe false
        GenreMatcher.matches("garmonbozia", "Драма") shouldBe false
    }

    // ═══════════ Пакетный выбор чекбоксов источника ═══════════

    @Test
    fun `selectSourceGenres выбирает совпавшие чекбоксы источника`() {
        val sourceGenres = listOf("Action", "Комедия", "Romance", "Horror", "Sci-Fi")
        GenreMatcher.selectSourceGenres(listOf("экшен", "драма"), sourceGenres) shouldBe listOf("Action")
        GenreMatcher.selectSourceGenres(listOf("ужасы", "фантастика"), sourceGenres) shouldBe listOf("Horror", "Sci-Fi")
        GenreMatcher.selectSourceGenres(listOf("Comedy"), sourceGenres) shouldBe listOf("Комедия")
    }

    @Test
    fun `selectSourceGenres переживает мусор с обеих сторон`() {
        GenreMatcher.selectSourceGenres(listOf("", "  ", "🔥", "123"), listOf("Action", "Драма")).shouldBeEmpty()
        GenreMatcher.selectSourceGenres(listOf("экшен"), listOf("", "  ", "!!!")).shouldBeEmpty()
        GenreMatcher.selectSourceGenres(listOf("драма"), listOf("🔥", "123", "   ")).shouldBeEmpty()
    }

    @Test
    fun `selectSourceGenres не склеивает похожие жанры источника`() {
        val sourceGenres = listOf("Romance", "Novel", "Music", "Musical")
        GenreMatcher.selectSourceGenres(listOf("музыка"), sourceGenres) shouldBe listOf("Music")
    }

    @Test
    fun `selectSourceGenres устойчив к очень длинным спискам`() {
        val user = (1..32).map { "жанр$it" }
        val source = (1..200).map { "Genre$it" }
        // не падает, ничего осмысленного не находит
        GenreMatcher.selectSourceGenres(user, source).shouldBeEmpty()
    }

    @Test
    fun `resolveUserGenreInput санитизирует запятые чтобы не ломать CSV`() {
        // Канонический резолв не меняется.
        resolveUserGenreInput("экшен") shouldBe ("action" to true)
        // Запятая (разделитель CSV) не должна пережить ввод: иначе raw-строка
        // распадётся на два битых элемента при следующем чтении префа.
        val (resolved, _) = resolveUserGenreInput("бытовое фэнтези, драма")
        (resolved.contains(",")) shouldBe false
    }

    // ═══════════ G08: Строгое ограничение (matchesConstraint) vs Поиск (matchesSearch) ═══════════

    @Test
    fun `matchesConstraint сопоставляет онтологические категории и точные формы`() {
        GenreMatcher.matchesConstraint("экшен", "Action") shouldBe true
        GenreMatcher.matchesConstraint("боевик", "Action") shouldBe true
        GenreMatcher.matchesConstraint("приключения", "Adventure") shouldBe true
        GenreMatcher.matchesConstraint("horror", "ужасы") shouldBe true
    }

    @Test
    fun `matchesConstraint исключает нестрогую токенизацию и опечатки Левенштейна`() {
        GenreMatcher.matchesConstraint("actoin", "Action") shouldBe false
        GenreMatcher.matchesConstraint("драма", "мелодрама") shouldBe false
        GenreMatcher.matchesConstraint("romance", "romantic comedy") shouldBe false
        GenreMatcher.matchesSearch("боевик", "Action") shouldBe true
    }
}
