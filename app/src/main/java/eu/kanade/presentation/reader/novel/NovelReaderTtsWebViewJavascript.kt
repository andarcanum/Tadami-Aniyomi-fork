package eu.kanade.presentation.reader.novel

internal fun buildWebReaderTtsSyncJavascript(
    snippet: String,
    progressPercent: Int,
): String {
    val safeSnippet = snippet.trim().take(180).escapeForJavascriptStringLiteral()
    return """
        (function() {
            const snippet = '$safeSnippet';
            const normalizedSnippet = (snippet || '').trim().toLowerCase();
            const percent = ${progressPercent.coerceIn(0, 100)};
            const highlightAttribute = 'data-an-tts-highlight';
            const totalScrollable = Math.max(
                0,
                document.documentElement.scrollHeight - window.innerHeight,
            );

            const clearHighlight = () => {
                const highlighted = document.querySelectorAll('[' + highlightAttribute + '="true"]');
                highlighted.forEach((element) => {
                    element.removeAttribute(highlightAttribute);
                    element.style.removeProperty('background-color');
                    element.style.removeProperty('border-radius');
                    element.style.removeProperty('box-shadow');
                    element.style.removeProperty('transition');
                });
            };

            const scrollToPercent = () => {
                const targetY = Math.round((percent / 100) * totalScrollable);
                window.scrollTo({ top: targetY, behavior: 'auto' });
                return 'fallback';
            };

            const resolveHighlightTarget = (element) => {
                if (!element) return null;
                return element.closest('p, div, blockquote, li, h1, h2, h3, h4, h5, h6, article, section') || element;
            };

            const applyHighlight = (element) => {
                const target = resolveHighlightTarget(element);
                if (!target) return;
                target.setAttribute(highlightAttribute, 'true');
                target.style.backgroundColor = 'rgba(255, 224, 130, 0.30)';
                target.style.borderRadius = '0.45em';
                target.style.boxShadow = '0 0 0 0.12em rgba(255, 224, 130, 0.18)';
                target.style.transition = 'background-color 120ms ease';
            };

            clearHighlight();

            if (!normalizedSnippet) {
                return scrollToPercent();
            }

            const walker = document.createTreeWalker(document.body || document.documentElement, NodeFilter.SHOW_TEXT);
            let node = walker.nextNode();
            while (node) {
                const text = (node.textContent || '').trim().toLowerCase();
                if (text && text.includes(normalizedSnippet)) {
                    const element = node.parentElement;
                    if (element) {
                        applyHighlight(element);
                        resolveHighlightTarget(element)?.scrollIntoView({ block: 'center', behavior: 'auto' });
                        return 'aligned';
                    }
                    break;
                }
                node = walker.nextNode();
            }

            return scrollToPercent();
        })();
    """.trimIndent()
}

private fun String.escapeForJavascriptStringLiteral(): String {
    return buildString(length + 8) {
        for (character in this@escapeForJavascriptStringLiteral) {
            when (character) {
                '\\' -> append("\\\\")
                '\'' -> append("\\'")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
    }
}
