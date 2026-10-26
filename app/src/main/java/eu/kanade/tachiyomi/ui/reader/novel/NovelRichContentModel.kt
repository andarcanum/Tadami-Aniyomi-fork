package eu.kanade.tachiyomi.ui.reader.novel

data class NovelRichContentParseResult(
    val blocks: List<NovelRichContentBlock>,
    val unsupportedFeaturesDetected: Boolean,
)

enum class NovelRichBlockTextAlign {
    LEFT,
    CENTER,
    JUSTIFY,
    RIGHT,
}

sealed interface NovelRichContentBlock {
    data class Paragraph(
        val segments: List<NovelRichTextSegment>,
        val textAlign: NovelRichBlockTextAlign? = null,
        val firstLineIndentEm: Float? = null,
        override val anchor: NovelBlockAnchor? = null,
        override val bookRange: NovelBookSourceRange? = null,
    ) : NovelRichContentBlock

    data class Heading(
        val level: Int,
        val segments: List<NovelRichTextSegment>,
        val textAlign: NovelRichBlockTextAlign? = null,
        override val anchor: NovelBlockAnchor? = null,
        override val bookRange: NovelBookSourceRange? = null,
        val isBookChapterHeading: Boolean = false,
    ) : NovelRichContentBlock

    data class BlockQuote(
        val segments: List<NovelRichTextSegment>,
        val textAlign: NovelRichBlockTextAlign? = null,
        override val anchor: NovelBlockAnchor? = null,
        override val bookRange: NovelBookSourceRange? = null,
    ) : NovelRichContentBlock

    data class HorizontalRule(
        override val anchor: NovelBlockAnchor? = null,
        override val bookRange: NovelBookSourceRange? = null,
    ) : NovelRichContentBlock

    data class Image(
        val url: String,
        val alt: String? = null,
        override val anchor: NovelBlockAnchor? = null,
        override val bookRange: NovelBookSourceRange? = null,
    ) : NovelRichContentBlock

    /**
     * Where this block sits in its chapter, when the markup announced it.
     *
     * Book mode stitches several chapters into one document, so a block's position inside the
     * rendered list says nothing about the chapter it belongs to. TTS addresses blocks by this pair
     * instead of by a text search, which is what makes follow-along work over a book.
     */
    val anchor: NovelBlockAnchor? get() = null

    /** Absolute source element span inside the book offset domain, when available. */
    val bookRange: NovelBookSourceRange? get() = null

    /** Returns a copy of this block carrying [anchor]. */
    fun withAnchor(anchor: NovelBlockAnchor): NovelRichContentBlock = when (this) {
        is Paragraph -> copy(anchor = anchor)
        is Heading -> copy(anchor = anchor)
        is BlockQuote -> copy(anchor = anchor)
        is HorizontalRule -> copy(anchor = anchor)
        is Image -> copy(anchor = anchor)
    }

    /** Returns a copy of this block carrying [range]. */
    fun withBookRange(range: NovelBookSourceRange): NovelRichContentBlock = when (this) {
        is Paragraph -> copy(bookRange = range)
        is Heading -> copy(bookRange = range)
        is BlockQuote -> copy(bookRange = range)
        is HorizontalRule -> copy(bookRange = range)
        is Image -> copy(bookRange = range)
    }
}

/**
 * Absolute element span of a block inside the book source text.
 */
data class NovelBookSourceRange(
    val charStart: Int,
    val charLength: Int,
) {
    init {
        require(charStart >= 0) { "charStart must be >= 0, was $charStart" }
        require(charLength > 0) { "charLength must be > 0, was $charLength" }
    }
}

/**
 * Stable address of a block inside its chapter.
 *
 * [blockIndex] is the index of the block in the chapter's parsed block stream, which is exactly the
 * index the TTS model reports as `sourceBlockIndex`. The same pair is written into the book DOM as
 * `data-an-b="<chapterId>:<blockIndex>"`, so the WebView renderer and the native renderer address
 * the same block by the same name.
 */
data class NovelBlockAnchor(
    val chapterId: Long,
    val blockIndex: Int,
) {
    /** The `data-an-b` value of this anchor. */
    val domId: String get() = "$chapterId:$blockIndex"

    companion object {
        /** Attribute the book DOM carries on every addressable block. */
        const val DOM_ATTRIBUTE = "data-an-b"

        /** Parses a `data-an-b` value, or returns null when it is missing or malformed. */
        fun parse(value: String?): NovelBlockAnchor? {
            if (value.isNullOrBlank()) return null
            val chapterId = value.substringBefore(':', "").toLongOrNull() ?: return null
            val blockIndex = value.substringAfter(':', "").toIntOrNull() ?: return null
            if (blockIndex < 0) return null
            return NovelBlockAnchor(chapterId = chapterId, blockIndex = blockIndex)
        }
    }
}

data class NovelRichTextSegment(
    val text: String,
    val style: NovelRichTextStyle = NovelRichTextStyle(),
    val linkUrl: String? = null,
)

data class NovelRichTextStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikeThrough: Boolean = false,
    val colorCss: String? = null,
    val backgroundColorCss: String? = null,
)
