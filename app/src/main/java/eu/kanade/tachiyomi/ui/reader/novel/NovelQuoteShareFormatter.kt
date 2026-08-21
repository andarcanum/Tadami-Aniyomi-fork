package eu.kanade.tachiyomi.ui.reader.novel

/**
 * Builds the shareable text of one highlight and of the whole quote collection.
 * Format (spec §7.5):
 *
 * «<quote>»
 * — <novel title>, <chapter name>
 * <note — only when present>
 */
object NovelQuoteShareFormatter {

    private const val ORNAMENT = "❦"

    fun formatQuote(
        quote: String,
        novelTitle: String,
        chapterName: String,
        note: String?,
    ): String {
        return buildString {
            append('«').append(quote).append('»').append('\n')
            append("— ").append(novelTitle).append(", ").append(chapterName)
            if (!note.isNullOrBlank()) {
                append('\n').append(note.trim())
            }
        }
    }

    fun formatDocument(
        novelTitle: String,
        quotes: List<Triple<String, String, String?>>,
    ): String {
        return buildString {
            append(novelTitle).append(" — Quotes")
            quotes.forEach { (quote, chapterName, note) ->
                append("\n\n").append(ORNAMENT).append("\n\n")
                // The document-level block keeps the source line to the chapter only; the novel
                // title already heads the document.
                append('«').append(quote).append('»').append('\n')
                append("— ").append(chapterName)
                if (!note.isNullOrBlank()) {
                    append('\n').append(note.trim())
                }
            }
        }
    }
}
