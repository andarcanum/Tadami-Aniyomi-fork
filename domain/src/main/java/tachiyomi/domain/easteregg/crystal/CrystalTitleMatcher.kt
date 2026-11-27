package tachiyomi.domain.easteregg.crystal

import java.text.Normalizer
import java.util.Locale

enum class CrystalWork { SAKURASOU, ROOMMATE_CAT }

enum class CrystalMedia { ANIME, MANGA, NOVEL }

data class CrystalAdaptation(val work: CrystalWork, val media: CrystalMedia)

/** Trusted catalog identity, not a scraped title or user label. Catalog keys are exact, e.g. "anilist". */
data class CrystalCatalogBinding(val catalog: String, val media: CrystalMedia, val id: Long)

/** Only raw source metadata belongs here. A user's custom title must never be passed as an alias. */
data class CrystalTitleInput(
    val media: CrystalMedia,
    val sourceTitle: String,
    val alternativeTitles: List<String> = emptyList(),
    val trustedBindings: List<CrystalCatalogBinding> = emptyList(),
)

enum class CrystalTitleMatchReason {
    MATCHED_CATALOG,
    MATCHED_ALIAS,
    NO_MATCH,
    CONFLICTING_BINDING,
    AMBIGUOUS_TITLE,
    UNSUPPORTED_MEDIA,
}

data class CrystalTitleMatch(
    val adaptation: CrystalAdaptation?,
    val reason: CrystalTitleMatchReason,
    val matchedTitle: String? = null,
)

/**
 * Exact reviewed aliases after NFKC, Locale.ROOT case folding, ё/е and punctuation/whitespace folding.
 * No substring, transliteration or fuzzy matching. Identity always includes the adaptation's medium.
 *
 * [catalogMappings] extends/overrides the two verified anime AniList mappings. Manga/novel IDs and
 * other catalogs are not verified here and deliberately absent. Callers may inject reviewed mappings;
 * an explicit null means a reviewed non-target identity and rejects aliases. An absent key is unknown,
 * not evidence of a conflict. Conflicting known bindings fail closed rather than silently reassigning
 * the work. Injected mappings cannot enable the unsupported Roommate novel adaptation.
 */
class CrystalTitleMatcher(catalogMappings: Map<CrystalCatalogBinding, CrystalWork?> = emptyMap()) {

    private val catalogMappings = VERIFIED_CATALOG_MAPPINGS + catalogMappings

    fun match(input: CrystalTitleInput): CrystalTitleMatch {
        val titleMatches = (listOf(input.sourceTitle) + input.alternativeTitles).mapNotNull { title ->
            ALIASES[normalize(title)]?.let { title to it }
        }
        val aliasWorks = titleMatches.map { it.second }.toSet()
        val knownBindings = input.trustedBindings.filter { catalogMappings.containsKey(it) }
        val boundWorks = knownBindings.mapNotNull { catalogMappings[it] }.toSet()
        val boundWork = boundWorks.singleOrNull()

        if (knownBindings.any { it.media != input.media || catalogMappings[it] == null } ||
            boundWorks.size > 1 || (boundWork != null && aliasWorks.any { it != boundWork })
        ) {
            return CrystalTitleMatch(null, CrystalTitleMatchReason.CONFLICTING_BINDING)
        }
        if (aliasWorks.size > 1) {
            return CrystalTitleMatch(null, CrystalTitleMatchReason.AMBIGUOUS_TITLE)
        }
        val work = boundWork ?: aliasWorks.singleOrNull()
            ?: return CrystalTitleMatch(null, CrystalTitleMatchReason.NO_MATCH)
        val matchedTitle = titleMatches.firstOrNull { it.second == work }?.first
        if (work == CrystalWork.ROOMMATE_CAT && input.media == CrystalMedia.NOVEL) {
            return CrystalTitleMatch(null, CrystalTitleMatchReason.UNSUPPORTED_MEDIA, matchedTitle)
        }
        return CrystalTitleMatch(
            adaptation = CrystalAdaptation(work, input.media),
            reason = if (boundWork != null) {
                CrystalTitleMatchReason.MATCHED_CATALOG
            } else {
                CrystalTitleMatchReason.MATCHED_ALIAS
            },
            matchedTitle = matchedTitle,
        )
    }

    private companion object {
        val VERIFIED_CATALOG_MAPPINGS: Map<CrystalCatalogBinding, CrystalWork?> = mapOf(
            CrystalCatalogBinding("anilist", CrystalMedia.ANIME, 13759L) to CrystalWork.SAKURASOU,
            CrystalCatalogBinding("anilist", CrystalMedia.ANIME, 103874L) to CrystalWork.ROOMMATE_CAT,
        )
        val ALIASES = mapOf(
            CrystalWork.SAKURASOU to listOf(
                "Кошечка из Сакурасо",
                "Sakurasou no Pet na Kanojo",
                "Sakura-sou no Pet na Kanojo",
                "The Pet Girl of Sakurasou",
                "さくら荘のペットな彼女",
                "樱花庄的宠物女孩",
            ),
            CrystalWork.ROOMMATE_CAT to listOf(
                "My Roommate is a Cat",
                "Doukyonin wa Hiza, Tokidoki, Atama no Ue.",
                "同居人はひざ、時々、頭のうえ。",
                "Домашний питомец, иногда сидящий на моей голове",
            ),
        ).flatMap { (work, aliases) -> aliases.map { normalize(it) to work } }.toMap()

        fun normalize(title: String): String {
            val normalized = Normalizer.normalize(title, Normalizer.Form.NFKC).lowercase(Locale.ROOT).replace('ё', 'е')
            return buildString {
                var index = 0
                while (index < normalized.length) {
                    val point = normalized.codePointAt(index)
                    val punctuation = when (Character.getType(point)) {
                        Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
                        Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
                        Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
                        Character.OTHER_PUNCTUATION.toInt(),
                        -> true
                        else -> false
                    }
                    // Android ICU rejects Java's inline (?U) flag. Code points also preserve surrogate pairs.
                    val whitespace = Character.isWhitespace(point) || Character.isSpaceChar(point) || point == 0x85
                    if (!punctuation && !whitespace) appendCodePoint(point)
                    index += Character.charCount(point)
                }
            }
        }
    }
}
