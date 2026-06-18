package eu.kanade.tachiyomi.data.suggestions

import eu.kanade.domain.metadata.interactor.normalizeMetadataSearchQuery
import eu.kanade.domain.metadata.interactor.parseOriginalTitle
import tachiyomi.core.common.util.system.logcat

/**
 * Resolves title candidates for suggestion seed building and scores alias matches.
 *
 * Design rules:
 * - "one work, many candidate names, one typed result set"
 * - Cyrillic queries are kept as-is in candidates; Latin/Romaji variants from metadata are additive
 * - Normalization is consistent with [normalizeMetadataSearchQuery] (same suffix stripping, spacing, case)
 * - Scoring matches the metadata resolver contract: exact > prefix > contains > token overlap
 */
object SuggestionTitleResolver {

    /**
     * Build the full alias candidate list for a suggestion seed.
     *
     * Sources (in priority order):
     * 1. Raw entry title (Cyrillic or Latin)
     * 2. Parsed original title from description (e.g. "Original: Shingeki no Kyojin")
     * 3. Metadata primary search query
     * 4. Metadata alternative titles (romaji, english, native, synonyms)
     * 5. Normalized variants of each (suffix-stripped, spacing-collapsed)
     *
     * Cyrillic candidates are kept in the list – providers that know the work
     * under a Cyrillic alias will match them; providers that don't will fall through
     * to a Latin alias. No translation is performed.
     */
    fun parseSlugTitle(url: String): String? {
        val lastSegment = url.substringBefore("?").substringAfterLast("/").trim()
        if (lastSegment.isBlank()) return null
        val slug = if (lastSegment.contains("--")) {
            lastSegment.substringAfter("--")
        } else {
            lastSegment
        }
        if (slug.all { it.isDigit() }) return null

        return slug.replace("-", " ")
            .replace("_", " ")
            .trim()
            .ifBlank { null }
    }

    fun resolveCandidates(
        title: String,
        description: String?,
        url: String? = null,
        metadataAlternativeTitles: List<String> = emptyList(),
    ): List<String> = buildList {
        // 1. Raw entry title (may be Cyrillic)
        add(title)

        // 2. Parsed original title from description
        parseOriginalTitle(description)?.let { add(it) }

        // 3. Parsed English title from URL slug
        url?.let { parseSlugTitle(it)?.let { add(it) } }

        // 4 & 5. Metadata titles (primary + alternatives)
        addAll(metadataAlternativeTitles)
    }
        .flatMap { raw ->
            // For each raw title, also emit a normalized variant (suffix-stripped etc.)
            val normalized = normalizeMetadataSearchQuery(raw.trim())
            if (normalized != raw.trim()) listOf(raw.trim(), normalized) else listOf(raw.trim())
        }
        .filter { it.isNotBlank() }
        .distinct()
        .also { candidates ->
            logcat { "SuggestionTitleResolver: resolved ${candidates.size} candidates for '$title': $candidates" }
        }

    /**
     * Compute a similarity score between two normalized titles.
     *
     * Contract (aligned with MetadataResolver scoring):
     * - Exact match after case-folding       → 100
     * - One is a prefix of the other         → 75
     * - One contains the other               → 50
     * - Token overlap (Jaccard × 50)         → 0..49
     *
     * Cyrillic titles are handled naturally – Kotlin's [String.lowercase] is Unicode-aware.
     */
    fun scoreMatch(candidate: String, target: String): Int {
        val c = candidate.lowercase().trim()
        val t = target.lowercase().trim()
        if (c == t) return 100
        if (c.startsWith(t) || t.startsWith(c)) return 75
        if (c.contains(t) || t.contains(c)) return 50

        // Token overlap (Jaccard)
        val cTokens = c.split(Regex("\\s+")).filter { it.length > 1 }.toSet()
        val tTokens = t.split(Regex("\\s+")).filter { it.length > 1 }.toSet()
        if (cTokens.isEmpty() || tTokens.isEmpty()) return 0

        val intersection = cTokens.intersect(tTokens)
        val ratio = intersection.size.toDouble() / maxOf(cTokens.size, tTokens.size).toDouble()
        return (ratio * 50).toInt()
    }

    /**
     * Select the best query string from candidates to use as the primary search query
     * for a given provider. Prefers the normalized/Latin variant if available and the
     * Cyrillic original title is the same as [rawTitle]; otherwise keeps Cyrillic.
     *
     * This is a heuristic: external providers (AniList, MAL, MangaUpdates) tend to
     * search better with Latin-script titles when available.
     */
    fun selectBestQueryForProvider(candidates: List<String>, rawTitle: String): String {
        if (candidates.isEmpty()) return rawTitle
        // Prefer the first non-Cyrillic-only candidate if one exists
        val latinFirst = candidates.firstOrNull { c ->
            c.any { it.isLetter() && (it.code < 0x400 || it.code > 0x4FF) }
        }
        return latinFirst ?: candidates.first()
    }

    private val volumeChapterRegex =
        Regex(
            """(?i)\b(vol|volume|ch|chapter|season|part|book|tome|s\d+|том|часть|книга|глава|сезон|т)\b\s*\.?\s*\d+""",
        )
    private val nonAlphanumericRegex = Regex("""[^\p{L}\p{N}\s-]""")
    private val consecutiveSpacesRegex = Regex(" +")

    fun cleanTitle(title: String): String {
        val preTitle = title.lowercase()
        var cleanedTitle = removeTextInBrackets(preTitle)
        cleanedTitle = cleanedTitle.replace(volumeChapterRegex, " ")
        cleanedTitle = cleanedTitle.replace(nonAlphanumericRegex, " ")
        return cleanedTitle.trim().replace(consecutiveSpacesRegex, " ")
    }

    private fun removeTextInBrackets(text: String): String {
        var depth = 0
        return buildString {
            for (char in text) {
                when (char) {
                    '(', '[', '<', '{' -> depth++
                    ')', ']', '>', '}' -> if (depth > 0) depth--
                    else -> if (depth == 0) append(char)
                }
            }
        }
    }

    fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0) return 0.0
        if (len2 == 0) return 0.0

        val maxLen = maxOf(len1, len2)

        // Calculate Levenshtein Distance
        val dp = IntArray(len2 + 1) { it }
        for (i in 1..len1) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..len2) {
                val temp = dp[j]
                if (s1[i - 1] == s2[j - 1]) {
                    dp[j] = prev
                } else {
                    dp[j] = minOf(prev + 1, minOf(dp[j] + 1, dp[j - 1] + 1))
                }
                prev = temp
            }
        }
        val distance = dp[len2]
        val charSim = 1.0 - (distance.toDouble() / maxLen.toDouble())

        // Calculate Token Jaccard Similarity
        val tokens1 = s1.split(Regex("\\s+")).filter { it.length > 1 }.toSet()
        val tokens2 = s2.split(Regex("\\s+")).filter { it.length > 1 }.toSet()
        val jaccardSim = if (tokens1.isEmpty() || tokens2.isEmpty()) {
            0.0
        } else {
            val intersection = tokens1.intersect(tokens2).size
            val union = tokens1.union(tokens2).size
            intersection.toDouble() / union.toDouble()
        }

        return maxOf(charSim, jaccardSim)
    }

    fun normalizeEntryUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return url.trim()
            .substringBefore('#')
            .substringBefore('?')
            .trimEnd('/')
            .lowercase()
            .ifBlank { null }
    }

    fun isSameProviderEntry(item: SuggestionItem, entryUrl: String?): Boolean {
        val normalizedEntry = normalizeEntryUrl(entryUrl) ?: return false
        val normalizedProviderUrl = normalizeEntryUrl(item.providerUrl)
        if (normalizedProviderUrl == normalizedEntry) return true
        val providerSuffix = item.providerId?.substringAfter(':', missingDelimiterValue = item.providerId.orEmpty())
        return normalizeEntryUrl(providerSuffix) == normalizedEntry
    }

    fun isFranchiseDuplicate(titleA: String, titleB: String): Boolean {
        val cleanA = cleanTitle(titleA)
        val cleanB = cleanTitle(titleB)
        if (cleanA.isBlank() || cleanB.isBlank()) return false
        if (cleanA == cleanB) return true
        val similarity = calculateSimilarity(cleanA, cleanB)
        return similarity > 0.95
    }
}
