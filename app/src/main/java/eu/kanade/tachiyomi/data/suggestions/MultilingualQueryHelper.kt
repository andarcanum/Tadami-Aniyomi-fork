package eu.kanade.tachiyomi.data.suggestions

import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationService
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object MultilingualQueryHelper {

    private val genreMap = mapOf(
        "action" to listOf("экшен", "боевик"),
        "adventure" to listOf("приключения"),
        "comedy" to listOf("комедия"),
        "drama" to listOf("драма"),
        "fantasy" to listOf("фэнтези"),
        "mystery" to listOf("детектив", "мистика", "загадка"),
        "psychological" to listOf("психология", "психологическое"),
        "romance" to listOf("романтика", "романтическое"),
        "sci-fi" to listOf("фантастика", "научная фантастика"),
        "science fiction" to listOf("фантастика", "научная фантастика"),
        "slice of life" to listOf("повседневность"),
        "supernatural" to listOf("сверхъестественное"),
        "thriller" to listOf("триллер"),
        "mecha" to listOf("меха"),
        "shounen" to listOf("сёнен", "для мальчиков", "сёнэн"),
        "shoujo" to listOf("сёдзё", "для девочек"),
        "seinen" to listOf("сэйнэн", "для мужчин"),
        "josei" to listOf("дзёсэй", "для женщин"),
        "historical" to listOf("исторический", "история"),
        "horror" to listOf("ужасы", "хоррор"),
        "isekai" to listOf("исекай", "попаданцы", "другой мир"),
        "magic" to listOf("магия", "волшебство"),
        "martial arts" to listOf("боевые искусства", "единоборства"),
        "school" to listOf("школа", "школьная жизнь"),
        "sports" to listOf("спорт"),
        "vampire" to listOf("вампиры"),
        "military" to listOf("военное", "милитари"),
        "harem" to listOf("гарем"),
        "demons" to listOf("демоны"),
        "game" to listOf("игры", "игровое", "виртуальный мир"),
        "music" to listOf("музыка"),
        "parody" to listOf("пародия"),
        "police" to listOf("полиция"),
        "space" to listOf("космос", "космическая фантастика"),
        "reincarnation" to listOf("реинкарнация", "перерождение"),
        "system" to listOf("система", "система rpg", "игровая система"),
        "cultivation" to listOf("культивация", "культивирование", "сянься", "уся"),
        "xianxia" to listOf("сянься", "культивирование", "китайское фэнтези"),
        "wuxia" to listOf("уся", "боевые искусства"),
        "litrpg" to listOf("литрпг", "rpg", "рпг"),
        "cyberpunk" to listOf("киберпанк"),
        "steampunk" to listOf("стимпанк"),
        "post-apocalyptic" to listOf("постапокалиптика", "постапокалипсис"),
        "dystopia" to listOf("антиутопия"),
        "virtual reality" to listOf("виртуальная реальность", "vr"),
        "time travel" to listOf("путешествие во времени", "петля времени"),
        "gamelit" to listOf("геймлит"),
        "magic academy" to listOf("академия магии", "магическая академия"),
        "dungeon" to listOf("подземелье", "зачистка подземелий"),
        "monsters" to listOf("монстры", "чудовища"),
        "gods" to listOf("боги", "божества"),
        "angels" to listOf("ангелы"),
        "dragons" to listOf("драконы"),
        "beastmen" to listOf("зверолюди", "оборотни"),
        "elves" to listOf("эльфы"),
        "dwarves" to listOf("гномы"),
        "demihumans" to listOf("полулюди"),
        "spirits" to listOf("духи", "призраки"),
        "necromancy" to listOf("некромантия"),
        "zombies" to listOf("зомби"),
        "undead" to listOf("нежить"),
        "dark fantasy" to listOf("тёмное фэнтези", "дарк фэнтези"),
        "heroic fantasy" to listOf("героическое фэнтези"),
        "high fantasy" to listOf("эпическое фэнтези", "высокое фэнтези"),
        "low fantasy" to listOf("низкое фэнтези"),
        "urban fantasy" to listOf("городское фэнтези"),
        "overpowered protagonist" to listOf("сильный гг", "имба гг", "всесильный главный герой"),
        "weak to strong" to listOf("от слабого к сильному", "становление героя"),
        "anti-hero" to listOf("антигерой"),
        "villainess" to listOf("злодейка"),
        "villain" to listOf("злодей"),
        "genius protagonist" to listOf("умный гг", "гениальный главный герой"),
        "smart mc" to listOf("умный гг", "хитрый гг"),
        "ruthless protagonist" to listOf("безжалостный гг"),
        "male protagonist" to listOf("гг мужчина", "мужской персонаж"),
        "female protagonist" to listOf("гг женщина", "женский персонаж"),
        "guilds" to listOf("гильдии"),
        "adventurers" to listOf("авантюристы", "приключенцы"),
        "aristocracy" to listOf("аристократия", "дворянство", "знать"),
        "royalty" to listOf("королевская семья", "монархия"),
        "politics" to listOf("политика", "политические интриги"),
        "wars" to listOf("война", "военные действия"),
        "kingdom building" to listOf("строительство королевства", "развитие территории"),
        "merchant" to listOf("торговля", "купцы"),
        "crafting" to listOf("крафт", "создание предметов"),
        "alchemist" to listOf("алхимия"),
        "farming" to listOf("фермерство", "сельское хозяйство"),
        "survival" to listOf("выживание"),
        "apocalypse" to listOf("апокалипсис"),
        "tragedy" to listOf("трагедия"),
        "betrayal" to listOf("предательство"),
        "revenge" to listOf("месть"),
        "friendship" to listOf("дружба"),
        "family" to listOf("семья"),
        "milf" to listOf("милфы", "милфа", "милф", "cougar"),
        "love triangle" to listOf("любовный треугольник"),
        "slow romance" to listOf("медленная романтика", "медленное развитие отношений"),
        "forced marriage" to listOf("брак по расчету", "вынужденный брак"),
        "ruling class" to listOf("правящий класс", "власть"),
        "noble" to listOf("благородные"),
        "slavery" to listOf("рабство"),
        "magic beasts" to listOf("волшебные звери", "магические звери"),
        "dungeon master" to listOf("хозяин подземелья"),
        "evolution" to listOf("эволюция", "развитие"),
        "summoning" to listOf("призыв"),
        "contract" to listOf("контракт", "договор"),
        "curse" to listOf("проклятие"),
        "prophecy" to listOf("пророчество"),
        "god-human relationship" to listOf("отношения богов и людей"),
        "mythology" to listOf("мифология"),
        "fairy tales" to listOf("сказки"),
        "gothic" to listOf("готика", "готический стиль"),
        "superpowers" to listOf("суперсила", "суперспособности", "супер сила"),
        "superheroes" to listOf("супергерои"),
        "aliens" to listOf("пришельцы", "инопланетяне"),
        "conspiracy" to listOf("заговор"),
        "crime" to listOf("преступление", "криминал"),
        "mafia" to listOf("мафия", "якудза", "банды"),
        "investigation" to listOf("расследование"),
        "urban legend" to listOf("городская легенда"),
        "yokai" to listOf("ёкаи", "японские демоны"),
        "shinto" to listOf("синтоизм"),
        "buddhism" to listOf("буддизм"),
        "cult" to listOf("культ", "секта"),
        "suspense" to listOf("саспенс"),
        "madness" to listOf("безумие"),
        "cruelty" to listOf("жестокость"),
        "blood" to listOf("кровь", "насилие"),
        "torture" to listOf("пытки"),
        "grimdark" to listOf("гримдарк"),
        "manipulative mc" to listOf("манипулятор гг"),
        "schemes" to listOf("интриги", "схемы"),
        "strategy" to listOf("стратегия", "тактика"),
        "military tactics" to listOf("военная тактика"),
        "warfare" to listOf("военное дело"),
        "space opera" to listOf("космическая опера"),
        "space travel" to listOf("космические путешествия"),
        "robots" to listOf("роботы", "андроиды"),
        "artificial intelligence" to listOf("искусственный интеллект", "ии"),
        "cyberspace" to listOf("киберпространство"),
        "hackers" to listOf("хакеры"),
        "virtual world" to listOf("виртуальный мир"),
        "game elements" to listOf("игровые элементы"),
        "stat points" to listOf("статы гг", "характеристики"),
        "skills" to listOf("навыки", "способности"),
        "classes" to listOf("классы"),
        "level up" to listOf("уровни", "повышение уровня"),
        "gacha" to listOf("гача"),
        "loot" to listOf("добыча", "лут"),
        "dungeon exploration" to listOf("исследование подземелий"),
        "monster girls" to listOf("девушки-монстры"),
        "beast girls" to listOf("зверодевочки"),
        "kemonomimi" to listOf("ушки", "хвост"),
        "furry" to listOf("фурри"),
        "maids" to listOf("горничные"),
        "butlers" to listOf("дворецкие"),
        "royals" to listOf("королевские особы"),
        "knights" to listOf("рыцари"),
        "mercenaries" to listOf("наемники"),
        "assassins" to listOf("убийцы", "ассасины"),
        "thieves" to listOf("воры"),
        "pirates" to listOf("пираты"),
        "ninjas" to listOf("ниндзя", "синоби"),
        "samurai" to listOf("самураи"),
        "wizards" to listOf("волшебники", "маги"),
        "clerics" to listOf("священники", "клирики"),
        "healer" to listOf("целитель", "лекарь"),
        "saints" to listOf("святые"),
        "devils" to listOf("дьяволы"),
        "demon lord" to listOf("повелитель демонов", "владыка демонов"),
        "goddesses" to listOf("богини"),
        "mythical creatures" to listOf("мифические существа"),
        "beasts" to listOf("звери"),
        "pets" to listOf("питомцы"),
        "taming" to listOf("приручение"),
        "summoned hero" to listOf("призванный герой"),
        "another world" to listOf("другой мир"),
        "parallel universe" to listOf("параллельный мир"),
        "reverse isekai" to listOf("обратный исекай"),
        "otome game" to listOf("отоме игра"),
        "novel world" to listOf("мир новеллы", "внутри новеллы"),
        "book world" to listOf("мир книги"),
        "comic world" to listOf("мир комикса", "мир манги"),
        "modern day" to listOf("современность"),
        "future" to listOf("будущее"),
        "past" to listOf("прошлое"),
        "alternate history" to listOf("альтернативная история"),
        "school life" to listOf("школьная жизнь"),
        "academy" to listOf("академия"),
        "university" to listOf("университет", "колледж"),
        "office" to listOf("офис", "офисная жизнь"),
        "workplace" to listOf("работа", "рабочее место"),
        "business" to listOf("бизнес"),
        "industry" to listOf("индустрия"),
        "wealth" to listOf("богатство"),
        "poverty" to listOf("бедность"),
        "slum" to listOf("трущобы"),
        "orphan" to listOf("сирота"),
        "adopted" to listOf("приемный"),
        "stepfamily" to listOf("мачеха", "отчим"),
        "siblings" to listOf("братья и сестры", "сиблинги"),
        "twins" to listOf("близнецы"),
        "childcare" to listOf("забота о детях", "воспитание детей"),
        "parenting" to listOf("родительство"),
        "marriage" to listOf("брак", "женитьба"),
        "divorce" to listOf("развод"),
        "widow" to listOf("вдова", "вдовец"),
        "secret identity" to listOf("скрытая личность", "тайная личность"),
        "disguise" to listOf("маскировка", "переодевание"),
        "gender bender" to listOf("смена пола", "гендерная интрига"),
        "crossdressing" to listOf("кроссдрессинг", "переодевание"),
        "yuri" to listOf("юри"),
        "yaoi" to listOf("яой"),
        "shounen-ai" to listOf("сёнэн-ай"),
        "shoujo-ai" to listOf("сёдзё-ай"),
        "reverse harem" to listOf("реверс-гарем", "обратный гарем"),
        "love interest" to listOf("любовный интерес"),
        "childhood friend" to listOf("друг детства"),
        "first love" to listOf("первая любовь"),
        "unrequited love" to listOf("безответная любовь"),
        "secret love" to listOf("тайная любовь"),
        "forbidden love" to listOf("запретная любовь"),
        "obsessive love" to listOf("одержимая любовь", "яндэре"),
        "tsundere" to listOf("цундэре"),
        "yandere" to listOf("яндэре"),
        "kuudere" to listOf("куудэре"),
        "dandere" to listOf("дандэре"),
        "genki" to listOf("генки"),
        "lolicon" to listOf("лоликон"),
        "shotacon" to listOf("шотакон"),
        "fujoshi" to listOf("фудзёси"),
        "otaku" to listOf("отаку"),
        "neet" to listOf("хикикомори", "нит"),
        "hikikomori" to listOf("хикикомори"),
    )

    fun containsCyrillic(text: String): Boolean {
        return text.any { it in '\u0400'..'\u04FF' }
    }

    fun containsLatin(text: String): Boolean {
        return text.any { it in 'a'..'z' || it in 'A'..'Z' }
    }

    private val translationService by lazy {
        Injekt.get<eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationService>()
    }

    suspend fun translate(text: String): String? {
        if (text.isBlank()) return null
        val targetLang = when {
            containsCyrillic(text) -> "en"
            containsLatin(text) -> "ru"
            else -> return null
        }
        return try {
            translationService.translateSingle(
                text = text,
                sourceLanguage = "auto",
                targetLanguage = targetLang,
            )
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[MultilingualQueryHelper] Translation failed for '$text': ${e.message}" }
            null
        }
    }

    /**
     * Translates a genre bidirectionally between Latin (English) and Cyrillic (Russian).
     * Returns a list of alternative translations.
     */
    fun getGenreTranslations(genre: String): List<String> {
        val cleaned = genre.trim().lowercase()
        val results = mutableListOf<String>()

        // 1. Check if the input genre is in the keys (English)
        val mappedCyrillic = genreMap[cleaned]
        if (mappedCyrillic != null) {
            results.addAll(mappedCyrillic)
        }

        // 2. Check if the input genre is in any of the values (Russian)
        for ((english, RussianList) in genreMap) {
            if (RussianList.any { it == cleaned }) {
                results.add(english)
                // Add other Cyrillic synonyms as well!
                results.addAll(RussianList.filter { it != cleaned })
            }
        }

        return results.distinct()
    }

    private val dictionaryPrimaryKeys: Map<String, String> by lazy {
        val parent = HashMap<String, String>()
        val isMapKey = HashSet<String>()
        fun find(x: String): String {
            var cur = parent.getOrPut(x) { x }
            while (cur != parent.getValue(cur)) {
                cur = parent.getValue(cur)
            }
            var node = x
            while (node != cur) {
                val next = parent.getValue(node)
                parent[node] = cur
                node = next
            }
            return cur
        }
        fun union(a: String, b: String) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }
        for ((key, aliases) in genreMap) {
            val normKey = eu.kanade.tachiyomi.data.discovery.GenreMatcher.normalize(key)
            if (normKey.isEmpty()) continue
            isMapKey += normKey
            find(normKey)
            for (alias in aliases) {
                val normAlias = eu.kanade.tachiyomi.data.discovery.GenreMatcher.normalize(alias)
                if (normAlias.isNotEmpty()) {
                    union(normKey, normAlias)
                }
            }
        }
        val components = parent.keys.groupBy { find(it) }
        buildMap {
            for (nodes in components.values) {
                val rep = nodes.filter { it in isMapKey }.minOrNull() ?: nodes.minOrNull() ?: continue
                for (node in nodes) {
                    put(node, rep)
                }
            }
        }
    }

    /**
     * Resolves a bilingual dictionary genre or any of its aliases onto a deterministic,
     * symmetric primary dictionary key (preferring declared English map keys over synonym values).
     */
    internal fun canonicalDictionaryKey(genre: String): String? {
        val norm = eu.kanade.tachiyomi.data.discovery.GenreMatcher.normalize(genre)
        if (norm.isEmpty()) return null
        return dictionaryPrimaryKeys[norm]
    }

    /**
     * Helper to get both original and translated variations of an author/artist name or genre.
     */
    suspend fun getMultilingualVariants(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val cleaned = text.trim()
        val variants = mutableListOf(cleaned)

        translate(cleaned)?.let { translated ->
            if (translated.isNotBlank() && !translated.equals(cleaned, ignoreCase = true)) {
                variants.add(translated)
            }
        }

        return variants.distinct()
    }
}
