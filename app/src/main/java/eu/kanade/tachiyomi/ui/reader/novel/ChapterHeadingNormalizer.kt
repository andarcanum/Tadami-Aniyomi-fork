package eu.kanade.tachiyomi.ui.reader.novel

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.util.Locale

private val HEADING_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
private val BLOCK_CONTAINER_TAGS = setOf(
    "h1", "h2", "h3", "h4", "h5", "h6",
    "p", "div", "section", "article", "header", "main", "blockquote", "li", "figure",
)
private val MEDIA_TAGS = setOf("img", "svg", "picture", "video", "audio", "iframe", "hr", "table")

private const val MAX_CHAPTER_TITLE_CANDIDATE_CHARS = 160
private const val MAX_SUBTITLE_EXTRA_WORDS = 10

private val WHITESPACE_REGEX = Regex("\\s+")
private val PUNCTUATION_SEPARATOR_REGEX = Regex("[:\\-–—−―.,;!?«»\"'()\\[\\]{}…#*~_=|•№/\\\\]+")
private val LEADING_VOLUME_PREFIX_REGEX = Regex(
    "^(?:том|т|книга|кн|часть|ч|volume|vol|book|part|arc)\\s*\\d+[a-zа-я]?\\s+",
)
private val CHAPTER_WORD_ABBR_REGEX = Regex("\\b(?:гл|ch|chap)\\b")
private val CHAPTER_NUMBER_ONLY_REGEX = Regex(
    "^(?:глава|chapter|пролог|prologue|эпилог|epilogue|интерлюдия|interlude|экстра|extra)(?:\\s*\\d+[a-zа-я]?)?$",
)
private val LEADING_CHAPTER_LABEL_WITH_NUMBER_REGEX = Regex(
    "^(?:глава|chapter)\\s*\\d+[a-zа-я]?\\s+",
)
private val LEADING_CHAPTER_WORD_BEFORE_NUMBER_REGEX = Regex(
    "^(?:глава|chapter)\\s+(?=\\d)",
)

internal fun prependChapterHeadingIfMissing(
    rawHtml: String,
    chapterName: String?,
): String {
    val headingText = chapterName.orEmpty()
        .replace('\u00A0', ' ')
        .replace("\r", "")
        .trim()
    if (headingText.isBlank()) return rawHtml

    return runCatching {
        val document = Jsoup.parseBodyFragment(rawHtml)
        val body = document.body()
        val leadingBlocks = collectLeadingLeafBlocks(body, limit = 2)
        val firstBlock = leadingBlocks.getOrNull(0)
        val secondBlock = leadingBlocks.getOrNull(1)

        if (firstBlock != null && firstBlock.tagName().lowercase(Locale.US) in HEADING_TAGS) {
            deduplicateFollowingTitleBlock(
                headingElement = firstBlock,
                followingBlock = secondBlock,
                chapterName = headingText,
            )
            return body.html()
        }

        if (firstBlock != null && isChapterTitleMatch(firstBlock.text(), headingText)) {
            val preferredTitle = pickMoreSpecificChapterTitle(firstBlock.text(), headingText)
            val headingElement = Element("h1")
                .addClass("an-reader-chapter-title")
                .text(preferredTitle)
            firstBlock.replaceWith(headingElement)
            deduplicateFollowingTitleBlock(
                headingElement = headingElement,
                followingBlock = secondBlock,
                chapterName = headingText,
            )
            return body.html()
        }

        if (body.select("h1, h2, h3, h4, h5, h6").isNotEmpty()) {
            return body.html()
        }

        body.prependElement("h1")
            .addClass("an-reader-chapter-title")
            .text(headingText)
        body.html()
    }.getOrDefault(rawHtml)
}

internal fun isChapterTitleMatch(
    candidateText: String,
    referenceTitle: String,
    allowSubtitleOnly: Boolean = true,
): Boolean {
    val normalizedCandidate = normalizeTitleWhitespace(candidateText)
    val normalizedReference = normalizeTitleWhitespace(referenceTitle)
    if (normalizedCandidate.isBlank() || normalizedReference.isBlank()) return false
    if (normalizedCandidate == normalizedReference) return true
    if (normalizedCandidate.length > MAX_CHAPTER_TITLE_CANDIDATE_CHARS) return false

    val canonicalCandidate = canonicalizeChapterTitle(normalizedCandidate)
    val canonicalReference = canonicalizeChapterTitle(normalizedReference)
    if (canonicalCandidate.isBlank() || canonicalReference.isBlank()) return false
    if (canonicalCandidate == canonicalReference) return true

    val candidateNoVolume = stripLeadingVolumePrefix(canonicalCandidate)
    val referenceNoVolume = stripLeadingVolumePrefix(canonicalReference)
    if (candidateNoVolume.isNotBlank() && candidateNoVolume == referenceNoVolume) return true

    val candidateNoWord = LEADING_CHAPTER_WORD_BEFORE_NUMBER_REGEX.replaceFirst(candidateNoVolume, "")
    val referenceNoWord = LEADING_CHAPTER_WORD_BEFORE_NUMBER_REGEX.replaceFirst(referenceNoVolume, "")
    if (candidateNoWord.isNotBlank() && candidateNoWord == referenceNoWord) return true

    // Subtitle-only candidate when the reference includes a "Chapter N <subtitle>" prefix.
    if (allowSubtitleOnly) {
        val referenceSubtitle = LEADING_CHAPTER_LABEL_WITH_NUMBER_REGEX.replaceFirst(referenceNoVolume, "").trim()
        if (referenceSubtitle.length >= 3 && candidateNoVolume == referenceSubtitle) {
            return true
        }
    }

    // One side is "Chapter N" and the other side is "Chapter N <short subtitle>".
    if (isChapterNumberPrefixMatch(shorter = referenceNoVolume, longer = candidateNoVolume) ||
        isChapterNumberPrefixMatch(shorter = candidateNoVolume, longer = referenceNoVolume)
    ) {
        return true
    }

    return false
}

private fun deduplicateFollowingTitleBlock(
    headingElement: Element,
    followingBlock: Element?,
    chapterName: String,
) {
    if (followingBlock == null) return
    val headingText = normalizeTitleWhitespace(headingElement.text())
    val followingText = normalizeTitleWhitespace(followingBlock.text())
    if (followingText.isBlank()) return

    val canonicalHeading = stripLeadingVolumePrefix(canonicalizeChapterTitle(headingText))
    val canonicalChapter = stripLeadingVolumePrefix(canonicalizeChapterTitle(chapterName))
    val headingIsNumberOnly = CHAPTER_NUMBER_ONLY_REGEX.matches(canonicalHeading)
    val chapterSubtitle = LEADING_CHAPTER_LABEL_WITH_NUMBER_REGEX.replaceFirst(canonicalChapter, "").trim()
    val canonicalFollowing = stripLeadingVolumePrefix(canonicalizeChapterTitle(followingText))

    // Split heading: e.g. `<h1>Chapter 5</h1><p>Subtitle</p>` when chapterName is "Chapter 5: Subtitle".
    if (headingIsNumberOnly && chapterSubtitle.length >= 3 && canonicalFollowing == chapterSubtitle) {
        headingElement.text(chapterName)
        followingBlock.remove()
        return
    }

    // If the heading already carries the full title (not number-only), never strip a subtitle-only
    // opening paragraph (e.g. `<h1>Chapter 5: Silence</h1><p>Silence.</p>` could be story prose).
    val matchesHeading = headingText.isNotBlank() &&
        isChapterTitleMatch(followingText, headingText, allowSubtitleOnly = false)
    val matchesChapterName = isChapterTitleMatch(
        followingText,
        chapterName,
        allowSubtitleOnly = headingIsNumberOnly,
    )
    if (!matchesHeading && !matchesChapterName) return

    val preferredTitle = pickMoreSpecificChapterTitle(headingText, followingText)
    if (preferredTitle != headingText) {
        headingElement.text(preferredTitle)
    }
    followingBlock.remove()
}

private fun pickMoreSpecificChapterTitle(first: String, second: String): String {
    val cleanFirst = normalizeTitleWhitespace(first)
    val cleanSecond = normalizeTitleWhitespace(second)
    if (cleanFirst.isBlank()) return cleanSecond
    if (cleanSecond.isBlank()) return cleanFirst

    val canonicalFirst = canonicalizeChapterTitle(cleanFirst)
    val canonicalSecond = canonicalizeChapterTitle(cleanSecond)
    return when {
        canonicalSecond.length > canonicalFirst.length -> cleanSecond
        else -> cleanFirst
    }
}

private fun collectLeadingLeafBlocks(root: Element, limit: Int): List<Element> {
    val result = mutableListOf<Element>()
    var stopped = false

    fun visit(node: Node) {
        if (stopped || result.size >= limit) return
        when (node) {
            is TextNode -> {
                if (normalizeTitleWhitespace(node.wholeText).isNotBlank()) {
                    stopped = true
                }
            }
            is Element -> {
                val tag = node.tagName().lowercase(Locale.US)
                if (tag in MEDIA_TAGS) {
                    stopped = true
                    return
                }
                val hasMedia = node.select("img, svg, picture, video, audio, iframe, hr, table").isNotEmpty()
                val hasChildBlock = node.children().any { it.tagName().lowercase(Locale.US) in BLOCK_CONTAINER_TAGS }
                if (tag in BLOCK_CONTAINER_TAGS && !hasChildBlock) {
                    if (hasMedia) {
                        stopped = true
                        return
                    }
                    if (normalizeTitleWhitespace(node.text()).isNotBlank()) {
                        result.add(node)
                    }
                    return
                }
                for (child in node.childNodes()) {
                    visit(child)
                    if (stopped || result.size >= limit) return
                }
            }
        }
    }

    for (child in root.childNodes()) {
        visit(child)
        if (stopped || result.size >= limit) break
    }
    return result
}

private fun normalizeTitleWhitespace(text: String): String {
    return text
        .replace('\u00A0', ' ')
        .replace("\r", "")
        .replace(WHITESPACE_REGEX, " ")
        .trim()
}

private fun canonicalizeChapterTitle(normalizedText: String): String {
    val stripped = PUNCTUATION_SEPARATOR_REGEX
        .replace(normalizedText.lowercase(Locale.ROOT), " ")
        .replace(WHITESPACE_REGEX, " ")
        .trim()
    return CHAPTER_WORD_ABBR_REGEX.replace(stripped) { match ->
        when (match.value) {
            "гл" -> "глава"
            else -> "chapter"
        }
    }
}

private fun stripLeadingVolumePrefix(canonical: String): String {
    var current = canonical
    while (true) {
        val next = LEADING_VOLUME_PREFIX_REGEX.replaceFirst(current, "").trim()
        if (next == current || next.isBlank()) return current
        current = next
    }
}

private fun isChapterNumberPrefixMatch(shorter: String, longer: String): Boolean {
    if (shorter.isBlank() || longer.length <= shorter.length) return false
    if (!CHAPTER_NUMBER_ONLY_REGEX.matches(shorter)) return false
    if (!longer.startsWith("$shorter ")) return false
    if (longer.length > 120) return false
    val extraWords = longer.removePrefix(shorter).trim().split(' ').count { it.isNotBlank() }
    return extraWords in 1..MAX_SUBTITLE_EXTRA_WORDS
}
