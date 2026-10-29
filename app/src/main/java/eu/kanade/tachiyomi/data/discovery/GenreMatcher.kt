package eu.kanade.tachiyomi.data.discovery

import java.text.Normalizer
import kotlin.math.min

/**
 * Эвристика матчинга жанров между пользовательским вводом (любой язык/форма) и
 * названиями жанров конкретного плагина/трекера. Чистая и детерминированная:
 * без сети, без состояния, без исключений на мусорном вводе.
 *
 * Уровни сопоставления:
 *  1. **Семантический мост [GenreOntology]** — обе строки указывают на один
 *     канонический жанр (алиасы на ~20 языках из Wikidata/Shikimori/словаря):
 *     `حركة` и `боевик` — оба `action`.
 *  2. **Формы** — нормализация (регистр, пунктуация, диакритики, ё→е) +
 *     романизация кириллицы (мягкая `х→h, я→a` и строгая `х→kh, я→ya`) +
 *     мягкие формы окончаний (`драмы→драм`, `horrors→horror`).
 *  3. **Токены** — совпадение слова из многословного жанра (`жанр экшен` ↔ `экшен`).
 *  4. **Опечатки** — Левенштейн: ≤1 для длины ≥4, ≤2 для ≥6 (ловит `comédie↔comedy`).
 *
 * Устойчивость (требование владельца): пустой/мусорный/сверхдлинный/неизвестный
 * жанр инертен — не матчится и не бросает исключений; похожие, но РАЗНЫЕ жанры
 * не склеиваются (`роман ≠ романтика`, `music ≠ musical`, `mecha ≠ mechanical`).
 * Сетевой fallback перевода — [GenreTranslationFallback], ядро остаётся офлайн.
 */
object GenreMatcher {

    /** Жанры длиннее — мусор, игнорируются целиком. */
    private const val MAX_INPUT_LENGTH = 64
    private const val MIN_TYPO_LENGTH = 4
    private const val MIN_DOUBLE_TYPO_LENGTH = 6
    private const val MIN_TOKEN_LENGTH = 4

    /** Мягкая романизация: общепринятые формы вроде `meha`, `hentay`, `drama`. */
    private val SOFT_CYR_TO_LAT = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ж' to "zh",
        'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
        'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f",
        'х' to "h", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch", 'ъ' to "",
        'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "a",
    )

    /** Строгая романизация: ГОСТ-подобные формы `mekha`, `komediya` — вторая опора. */
    private val STRICT_CYR_TO_LAT = SOFT_CYR_TO_LAT.toMutableMap().apply {
        put('х', "kh")
        put('я', "ya")
    }

    /**
     * Окончания мягких форм: латиница только `s`/`es` (у `e` снятие опасно:
     * `romance→romanc` ложно склеилось бы с `roman`), кириллица — базовые падежи.
     * Суффикс должен целиком входить в список — «хвосты» вроде `тика` (роман-тика)
     * не проходят, что и защищает от склейки похожих жанров.
     */
    private val STRIP_ENDINGS =
        listOf("es", "s", "ая", "ое", "ие", "ий", "ый", "ой", "а", "ы", "и", "е", "у", "о", "я", "ь")

    /** Нормализация: регистр, диакритики, ё→е, пунктуация→пробел, схлопывание пробелов. */
    fun normalize(raw: String): String {
        if (raw.isBlank()) return ""
        val decomposed = Normalizer.normalize(raw.trim().lowercase(), Normalizer.Form.NFD)
        val builder = StringBuilder(decomposed.length)
        for (ch in decomposed) {
            when {
                Character.getType(ch) == Character.NON_SPACING_MARK.toInt() -> Unit // диакритика
                ch.isLetterOrDigit() -> builder.append(ch)
                else -> builder.append(' ')
            }
        }
        return builder.toString().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** Мягкая романизация кириллицы (ожидает нормализованную строку). */
    fun translitCyrillicToLatin(normalized: String): String = translit(normalized, SOFT_CYR_TO_LAT)

    private fun translit(s: String, table: Map<Char, String>): String =
        buildString(s.length) { s.forEach { append(table[it] ?: it.toString()) } }

    /**
     * Формы жанра: нормализация + обе романизации. Пусто для пустого/мусорного/
     * сверхдлинного ввода — дальше все уровни инертны.
     */
    fun variants(genre: String): Set<String> {
        val raw = genre.trim()
        if (raw.isEmpty() || raw.length > MAX_INPUT_LENGTH) return emptySet()
        val norm = normalize(raw)
        if (norm.isEmpty()) return emptySet()
        val out = linkedSetOf(norm)
        if (norm.any { it in 'а'..'я' }) {
            out += translit(norm, SOFT_CYR_TO_LAT)
            out += translit(norm, STRICT_CYR_TO_LAT)
        }
        return out
    }

    /** Канонические жанры [GenreOntology], которые обозначает строка. */
    fun resolve(genre: String): Set<String> {
        val pool = matchPool(genre)
        if (pool.isEmpty()) return emptySet()
        val out = mutableSetOf<String>()
        for (form in pool) {
            GenreOntology.aliasIndex[form]?.let(out::addAll)
        }
        return out
    }

    /** Классический Левенштейн (DP, O(n·m)); входы уже ≤64 символов. */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(prev[j] + 1, min(cur[j - 1] + 1, prev[j - 1] + cost))
            }
            prev = cur.copyOf()
        }
        return prev[b.length]
    }

    /**
     * Матчится ли пользовательский жанр с жанром источника. Никогда не бросает
     * исключений; неизвестные и мусорные жанры возвращают false.
     */
    fun matches(userGenre: String, sourceGenre: String): Boolean {
        val poolA = matchPool(userGenre)
        val poolB = matchPool(sourceGenre)
        if (poolA.isEmpty() || poolB.isEmpty()) return false
        // 1. Семантический мост онтологии: любая пара языков через канонический жанр.
        val canonA = canonicalsOf(poolA)
        val canonB = canonicalsOf(poolB)
        if (canonA.isNotEmpty() && canonB.isNotEmpty() && canonA.intersect(canonB).isNotEmpty()) return true
        // 2. Прямое пересечение форм (нормализация, романизации, мягкие окончания).
        if (poolA.intersect(poolB).isNotEmpty()) return true
        // 3. Токены многословных жанров: «жанр экшен» ↔ «экшен», «science fiction» ↔ «science».
        val tokensA = poolA.flatMap { it.split(' ') }.filter { it.length >= MIN_TOKEN_LENGTH }
        val tokensB = poolB.flatMap { it.split(' ') }.filter { it.length >= MIN_TOKEN_LENGTH }
        if (tokensA.isNotEmpty() && tokensA.toSet().intersect(tokensB.toSet()).isNotEmpty()) return true
        // 4. Опечатки по всем парам форм.
        for (a in poolA) {
            for (b in poolB) {
                if (levenshteinMatches(a, b)) return true
            }
        }
        return false
    }

    /**
     * Строгое сопоставление жанров для ограничений (blacklist, ignored, required):
     * 1. Семантическое равенство через канонический жанр [GenreOntology].
     * 2. Точное совпадение нормализованных форм/романизаций и основ (без Левенштейна и разбивки на токены).
     */
    fun matchesConstraint(constraintGenre: String, itemGenre: String): Boolean {
        val poolA = matchPool(constraintGenre)
        val poolB = matchPool(itemGenre)
        if (poolA.isEmpty() || poolB.isEmpty()) return false

        // 1. Семантический мост онтологии: совпадение канонических категорий
        val canonA = canonicalsOf(poolA)
        val canonB = canonicalsOf(poolB)
        if (canonA.isNotEmpty() && canonB.isNotEmpty() && canonA.intersect(canonB).isNotEmpty()) return true

        // 2. Прямое пересечение нормализованных форм (включая романизации и мягкие основы)
        if (poolA.intersect(poolB).isNotEmpty()) return true

        return false
    }

    /** Нестрогий/поисковый матчинг (алиас к [matches]). */
    fun matchesSearch(userGenre: String, sourceGenre: String): Boolean = matches(userGenre, sourceGenre)

    /**
     * Какие чекбоксы жанров источника проставить под список пользовательских жанров.
     * Прямая замена строгого сравнения в фильтрах каталога: мусор с любой стороны
     * просто не попадает в результат. Сетевой fallback — [GenreTranslationFallback].
     */
    fun selectSourceGenres(userGenres: List<String>, sourceGenreNames: List<String>): List<String> {
        val wanted = userGenres.filter { it.isNotBlank() }.take(32)
        if (wanted.isEmpty()) return emptyList()
        return sourceGenreNames.filter { name ->
            name.isNotBlank() && name.length <= MAX_INPUT_LENGTH && wanted.any { matches(it, name) }
        }
    }

    /** Пул форм для сравнения: варианты + мягкие формы окончаний. */
    private fun matchPool(genre: String): Set<String> {
        val base = variants(genre)
        if (base.isEmpty()) return emptySet()
        val out = LinkedHashSet(base)
        base.forEach { out.addAll(softStems(it)) }
        return out
    }

    /** Мягкие формы: снятие whitelisted-окончаний, только одиночные слова. */
    private fun softStems(normalized: String): List<String> {
        if (' ' in normalized) return emptyList()
        val out = mutableListOf<String>()
        for (ending in STRIP_ENDINGS) {
            if (normalized.endsWith(ending) && normalized.length - ending.length >= 3) {
                out += normalized.dropLast(ending.length)
            }
        }
        return out
    }

    private fun canonicalsOf(pool: Set<String>): Set<String> {
        val out = mutableSetOf<String>()
        for (form in pool) {
            GenreOntology.aliasIndex[form]?.let(out::addAll)
        }
        return out
    }

    private fun levenshteinMatches(a: String, b: String): Boolean {
        val n = min(a.length, b.length)
        if (n < MIN_TYPO_LENGTH) return false
        val distance = levenshtein(a, b)
        return distance <= 1 || (n >= MIN_DOUBLE_TYPO_LENGTH && distance <= 2)
    }
}
