package eu.kanade.tachiyomi.ui.browse.manga.migration.list.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import java.util.Locale

typealias SearchAction<T> = suspend (String) -> List<T>

abstract class BaseSmartSearchEngine<T>(
    private val extraSearchParams: String? = null,
    private val eligibleThreshold: Double = MIN_ELIGIBLE_THRESHOLD,
) {
    protected abstract fun getTitle(result: T): String

    protected suspend fun regularSearch(searchAction: SearchAction<T>, title: String): T? {
        return baseSearch(searchAction, listOf(title)) { similarity(title, getTitle(it)) }
    }

    protected suspend fun deepSearch(searchAction: SearchAction<T>, title: String): T? {
        val cleanedTitle = cleanDeepSearchTitle(title)
        val queries = getDeepSearchQueries(cleanedTitle)

        return baseSearch(searchAction, queries) {
            val cleanedResultTitle = cleanDeepSearchTitle(getTitle(it))
            similarity(cleanedTitle, cleanedResultTitle)
        }
    }

    private suspend fun baseSearch(
        searchAction: SearchAction<T>,
        queries: List<String>,
        calculateDistance: (T) -> Double,
    ): T? {
        val eligible = supervisorScope {
            queries.map { query ->
                async(Dispatchers.Default) {
                    val builtQuery = if (!extraSearchParams.isNullOrBlank()) {
                        "$query $extraSearchParams"
                    } else {
                        query
                    }

                    val candidates = searchAction(builtQuery)
                    candidates
                        .map {
                            val distance = if (queries.size > 1 || candidates.size > 1) {
                                calculateDistance(it)
                            } else {
                                1.0
                            }
                            SearchEntry(it, distance)
                        }
                        .filter { it.distance >= eligibleThreshold }
                }
            }.flatMap { it.await() }
        }

        return eligible.maxByOrNull { it.distance }?.entry
    }

    private fun similarity(a: String, b: String): Double {
        if (a.isBlank() || b.isBlank()) return 0.0
        val distance = levenshteinDistance(a.lowercase(Locale.getDefault()), b.lowercase(Locale.getDefault()))
        val maxLength = maxOf(a.length, b.length)
        return 1.0 - distance.toDouble() / maxLength.coerceAtLeast(1)
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)

        for (i in a.indices) {
            curr[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                curr[j + 1] = minOf(
                    curr[j] + 1,
                    prev[j + 1] + 1,
                    prev[j] + cost,
                )
            }
            prev.indices.forEach { prev[it] = curr[it] }
        }

        return prev[b.length]
    }

    private fun cleanDeepSearchTitle(title: String): String {
        val preTitle = title.lowercase(Locale.getDefault())

        var cleanedTitle = removeTextInBrackets(preTitle, true)
        if (cleanedTitle.length <= 5) {
            cleanedTitle = removeTextInBrackets(preTitle, false)
        }

        cleanedTitle = cleanedTitle.replace(chapterRefCyrillicRegexp, " ").trim()

        val cleanedTitleEng = cleanedTitle.replace(titleRegex, " ")
        cleanedTitle = if (cleanedTitleEng.length <= 5) {
            cleanedTitle.replace(titleCyrillicRegex, " ")
        } else {
            cleanedTitleEng
        }

        return cleanedTitle.trim().replace(" - ", " ").replace(consecutiveSpacesRegex, " ").trim()
    }

    private fun removeTextInBrackets(text: String, readForward: Boolean): String {
        val openingChars = if (readForward) "([<{" else ")]}>"
        val closingChars = if (readForward) ")]}>" else "([<{"
        var depth = 0

        return buildString {
            for (char in (if (readForward) text else text.reversed())) {
                when (char) {
                    in openingChars -> depth++
                    in closingChars -> if (depth > 0) depth--
                    else -> if (depth == 0) {
                        if (readForward) append(char) else insert(0, char)
                    }
                }
            }
        }
    }

    private fun getDeepSearchQueries(cleanedTitle: String): List<String> {
        val splitCleanedTitle = cleanedTitle.split(" ")
        val splitSortedByLargest = splitCleanedTitle.sortedByDescending { it.length }

        if (splitCleanedTitle.isEmpty()) return emptyList()

        val searchQueries = listOf(
            listOf(cleanedTitle),
            splitSortedByLargest.take(2),
            splitSortedByLargest.take(1),
            splitCleanedTitle.take(2),
            splitCleanedTitle.take(1),
        )

        return searchQueries.map { it.joinToString(" ").trim() }.distinct()
    }

    protected companion object {
        const val MIN_ELIGIBLE_THRESHOLD = 0.4

        private val titleRegex = Regex("[^a-zA-Z0-9- ]")
        private val titleCyrillicRegex = Regex("[^\\p{L}0-9- ]")
        private val consecutiveSpacesRegex = Regex(" +")
        private val chapterRefCyrillicRegexp = Regex("""((- часть|- глава) \d*)""")
    }
}

data class SearchEntry<T>(val entry: T, val distance: Double)
