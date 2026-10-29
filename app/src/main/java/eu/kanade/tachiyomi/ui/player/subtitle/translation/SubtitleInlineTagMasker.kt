package eu.kanade.tachiyomi.ui.player.subtitle.translation

/**
 * Protects inline subtitle styling from being corrupted by machine/LLM translation.
 *
 * It replaces ASS override blocks ({\\an8}, {\\i1}) and HTML-like inline tags
 * (<i>, </i>, <b>, <font color="...">) with opaque sentinel placeholders, then
 * restores them after translation. [restore] returns null when the model dropped,
 * duplicated or mangled a placeholder so the caller can safely fall back to the
 * original line instead of emitting broken markup.
 */
object SubtitleInlineTagMasker {
    // Android's ICU regex engine rejects a literal '}' that is not escaped, unlike the desktop JVM.
    internal val assBlock = Regex("""\{[^}]*\}""")
    private val htmlTag = Regex("</?[a-zA-Z][^>]*>")

    // Word-joiner wrapped index. These code points are virtually never produced
    // or altered by translation engines, and survive round-trips intact.
    private const val OPEN = "\u2060\u2063"
    private const val CLOSE = "\u2063\u2060"

    data class Masked(val text: String, val tokens: List<String>) {
        val hasTokens: Boolean get() = tokens.isNotEmpty()
    }

    fun mask(text: String): Masked {
        val tokens = mutableListOf<String>()
        fun store(value: String): String {
            val index = tokens.size
            tokens += value
            return OPEN + index + CLOSE
        }
        val masked = text
            .replace(assBlock) { store(it.value) }
            .replace(htmlTag) { store(it.value) }
        return Masked(masked, tokens)
    }

    /** Restores tokens, or returns null if the placeholder set was damaged. */
    fun restore(translated: String, masked: Masked): String? {
        if (!masked.hasTokens) {
            return if (containsSentinel(translated)) null else translated
        }
        var result = translated
        masked.tokens.forEachIndexed { index, original ->
            val placeholder = OPEN + index + CLOSE
            if (!result.contains(placeholder)) return null
            result = result.replaceFirst(placeholder, original)
            if (result.contains(placeholder)) return null // duplicated placeholder
        }
        return if (containsSentinel(result)) null else result
    }

    /**
     * Removes any leftover sentinel placeholders from a string. Used as a soft
     * fallback when [restore] fails: we still emit the translated words (without
     * the styling) instead of throwing the whole translation away.
     */
    fun stripPlaceholders(value: String): String =
        value.replace(Regex("[\\u2060\\u2063][0-9]*[\\u2063\\u2060]?"), "")
            .replace("\u2060", "")
            .replace("\u2063", "")

    private fun containsSentinel(value: String): Boolean =
        value.contains('\u2060') || value.contains('\u2063')
}
