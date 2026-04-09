package eu.kanade.tachiyomi.ui.reader.novel.tts

object NovelTtsWordTokenizer : NovelTtsTokenizer {

    private val wordRegex = Regex("""[\p{L}\p{N}]+(?:['’\-][\p{L}\p{N}]+)*""")

    override fun tokenize(text: String): List<NovelTtsWordRange> {
        return wordRegex.findAll(text)
            .mapIndexed { index, matchResult ->
                NovelTtsWordRange(
                    wordIndex = index,
                    text = matchResult.value,
                    startChar = matchResult.range.first,
                    endChar = matchResult.range.last + 1,
                )
            }
            .toList()
    }
}
