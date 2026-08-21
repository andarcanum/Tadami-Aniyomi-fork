package eu.kanade.presentation.reader.novel

import tachiyomi.domain.book.novel.model.NovelHighlight

/**
 * Builds the apply-payload for [buildApplyNovelHighlightsJs]: stored offsets are sent as-is;
 * blocks missing from the DOM are skipped silently by the JS side.
 */
internal fun buildNovelHighlightsPayloadJson(highlights: List<NovelHighlight>): String {
    val array = org.json.JSONArray()
    highlights.forEach { highlight ->
        val argb = highlight.colorArgb
        val color = String.format(
            java.util.Locale.US,
            "#%02X%02X%02X%02X",
            (argb shr 16) and 0xFF,
            (argb shr 8) and 0xFF,
            argb and 0xFF,
            (argb shr 24) and 0xFF,
        )
        array.put(
            org.json.JSONObject()
                .put("id", highlight.id)
                .put("domId", "${highlight.chapterId}:${highlight.blockIndex}")
                .put("start", highlight.charStart)
                .put("end", highlight.charEndExclusive)
                .put("color", color),
        )
    }
    return array.toString()
}

/** Reports the current selection's persistent block anchor alongside its screen rect. */
internal fun buildNovelHighlightAnchorJs(): String {
    return """
        (function() {
            const selection = window.getSelection ? window.getSelection() : null;
            if (!selection || selection.rangeCount === 0 || selection.isCollapsed) return null;
            const range = selection.getRangeAt(0);
            let el = range.startContainer;
            if (el.nodeType !== Node.ELEMENT_NODE) el = el.parentElement;
            while (el && !el.hasAttribute('data-an-b')) el = el.parentElement;
            if (!el) return null;
            let endEl = range.endContainer;
            if (endEl.nodeType !== Node.ELEMENT_NODE) endEl = endEl.parentElement;
            while (endEl && endEl !== el && !endEl.hasAttribute('data-an-b')) endEl = endEl.parentElement;
            // Only single-block selections get a persistent anchor; cross-block ones fall back.
            if (endEl !== el) return null;
            const pre = range.cloneRange();
            pre.selectNodeContents(el);
            pre.setEnd(range.startContainer, range.startOffset);
            const charStart = pre.toString().length;
            return {
                domId: el.getAttribute('data-an-b'),
                charStart: charStart,
                charEnd: charStart + range.toString().length,
            };
        })();
    """.trimIndent()
}

/**
 * Idempotently paints highlights inside `[data-an-b]` blocks. [payloadJson] is a JSON array of
 * `{id, domId, start, end, color}` with character offsets into the block's textContent.
 */
internal fun buildApplyNovelHighlightsJs(payloadJson: String): String {
    return """
        (function() {
            const payload = $payloadJson;
            document.querySelectorAll('span.an-hl[data-hl-id]').forEach((span) => {
                const parent = span.parentNode;
                while (span.firstChild) parent.insertBefore(span.firstChild, span);
                parent.removeChild(span);
                parent.normalize();
            });
            const wrapRange = (blockEl, start, end, id, color) => {
                const walker = document.createTreeWalker(blockEl, NodeFilter.SHOW_TEXT);
                let pos = 0;
                let node = walker.nextNode();
                const targets = [];
                while (node) {
                    const len = node.nodeValue.length;
                    if (pos + len > start && pos < end) {
                        targets.push({
                            node: node,
                            from: Math.max(0, start - pos),
                            to: Math.min(len, end - pos),
                        });
                    }
                    pos += len;
                    if (pos >= end) break;
                    node = walker.nextNode();
                }
                targets.forEach((target) => {
                    let textNode = target.node;
                    if (target.from > 0) {
                        textNode.splitText(target.from);
                        textNode = textNode.nextSibling;
                    }
                    if (target.to - target.from < textNode.nodeValue.length) {
                        textNode.splitText(target.to - target.from);
                    }
                    const span = document.createElement('span');
                    span.className = 'an-hl';
                    span.setAttribute('data-hl-id', String(id));
                    span.style.backgroundColor = color;
                    textNode.parentNode.insertBefore(span, textNode);
                    span.appendChild(textNode);
                });
            };
            payload.forEach((item) => {
                const blockEl = document.querySelector('[data-an-b="' + item.domId + '"]');
                if (!blockEl) return;
                wrapRange(blockEl, item.start, item.end, item.id, item.color);
            });
        })();
    """.trimIndent()
}

/** CSS marker for painted highlight spans; colors travel inline on each span. */
internal const val NOVEL_HIGHLIGHT_CSS_CLASS = "an-hl"

/** Click listener that reports taps on painted highlights back through the selection bridge. */
internal fun buildNovelHighlightClickJs(bridgeName: String): String {
    return """
        (function() {
            if (window.__an_highlight_click_bound__) return;
            window.__an_highlight_click_bound__ = true;
            document.addEventListener('click', (event) => {
                const bridge = window['$bridgeName'];
                if (!bridge) return;
                const target = event.target;
                const span = target && target.closest ? target.closest('span.an-hl[data-hl-id]') : null;
                if (!span) return;
                bridge.onHighlightClicked(span.getAttribute('data-hl-id'));
            }, true);
        })();
    """.trimIndent()
}
