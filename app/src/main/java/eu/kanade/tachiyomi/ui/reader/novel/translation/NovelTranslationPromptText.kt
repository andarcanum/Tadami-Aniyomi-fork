package eu.kanade.tachiyomi.ui.reader.novel.translation

internal fun buildNovelTranslationUserPrompt(
    sourceLang: String,
    targetLang: String,
    taggedInput: String,
    family: NovelTranslationPromptFamily = resolveNovelTranslationPromptFamily(targetLang),
): String {
    return when (family) {
        NovelTranslationPromptFamily.RUSSIAN -> {
            "TRANSLATE from $sourceLang to $targetLang.\n" +
                "Inject soul into the text. Make the reader believe this was written by a Russian author.\n\n" +
                "Use popular genre terminology (Magic -> Магия, etc.). Make it sound like high-quality fiction.\n\n" +
                "1. Keep the XML structure exactly as is (<s i='...'>...</s>).\n" +
                "2. Return EXACTLY one <s i='N'>...</s> block for every input block and preserve every original i value.\n" +
                "3. Never merge, skip, renumber, duplicate, summarize, or reorder blocks. If a block is empty or untranslatable, copy it inside its original tag.\n" +
                "4. NO PREAMBLE. NO ANALYSIS TEXT. NO MARKDOWN HEADERS.\n" +
                "5. Start your response IMMEDIATELY with the first XML tag.\n\n" +
                "INPUT BLOCK:\n" +
                taggedInput
        }
        NovelTranslationPromptFamily.ENGLISH -> {
            "TRANSLATE from $sourceLang to $targetLang.\n" +
                "Inject soul into the text. Make the reader believe this was written by a native English author.\n\n" +
                "Use popular genre terminology and natural English wording where appropriate. " +
                "Make it sound like high-quality fiction.\n\n" +
                "1. Keep the XML structure exactly as is (<s i='...'>...</s>).\n" +
                "2. Return EXACTLY one <s i='N'>...</s> block for every input block and preserve every original i value.\n" +
                "3. Never merge, skip, renumber, duplicate, summarize, or reorder blocks. If a block is empty or untranslatable, copy it inside its original tag.\n" +
                "4. NO PREAMBLE. NO ANALYSIS TEXT. NO MARKDOWN HEADERS.\n" +
                "5. Start your response IMMEDIATELY with the first XML tag.\n\n" +
                "INPUT BLOCK:\n" +
                taggedInput
        }
    }
}
