package eu.kanade.tachiyomi.extension.novel.runtime

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Handle-based DOM store backed by Jsoup.
 *
 * Each parsed [Document] and every [Element] / [TextNode] selected from it
 * receives a stable integer handle.  The JavaScript cheerio module operates
 * exclusively on these handles, delegating all DOM traversal to the native
 * side where Jsoup preserves the full tree structure.
 *
 * Thread-safety: instances are **not** thread-safe.  Each [NovelJsRuntime]
 * owns its own store and all calls happen on the dedicated runtime thread.
 */
class NovelJsDomStore {

    private var nextHandle = 1
    private val nodes = LinkedHashMap<Int, Node>(256, 0.75f, true)

    // ------------------------------------------------------------------
    // Document lifecycle
    // ------------------------------------------------------------------

    /**
     * Parse [html] and return the handle of the full document root.
     *
     * Plugins (via cheerio) commonly read `<meta>` and embedded JSON state
     * from `<head>`. Returning `<body>` here hides those nodes and causes
     * silent data loss in parseNovel/chapters flows.
     */
    fun loadDocument(html: String): Int {
        val doc: Document = Jsoup.parse(html)
        return assignHandle(doc)
    }

    // ------------------------------------------------------------------
    // CSS selectors
    // ------------------------------------------------------------------

    fun select(handle: Int, selector: String): IntArray {
        val el = elementOrNull(handle) ?: return IntArray(0)
        return el.select(selector).map { assignHandle(it) }.toIntArray()
    }

    // ------------------------------------------------------------------
    // Tree traversal
    // ------------------------------------------------------------------

    /** Returns parent handle or -1. */
    fun parent(handle: Int): Int {
        val node = nodes[handle] ?: return -1
        val p = node.parent() ?: return -1
        if (p is Document) return -1
        // The root handle points at <body>; its Jsoup parent is <html> which
        // should be invisible to plugins.  Return -1 when the parent's own
        // parent is the Document (i.e. parent is <html>).
        if (p.parentNode() is Document) return -1
        return assignHandle(p)
    }

    fun children(handle: Int, selector: String?): IntArray {
        val el = elementOrNull(handle) ?: return IntArray(0)
        val kids = el.children()
        return if (selector.isNullOrBlank()) {
            kids.map { assignHandle(it) }.toIntArray()
        } else {
            kids.filter { it.`is`(selector) }.map { assignHandle(it) }.toIntArray()
        }
    }

    fun next(handle: Int, selector: String?): Int {
        val el = elementOrNull(handle) ?: return -1
        if (selector.isNullOrBlank()) {
            val n = el.nextElementSibling() ?: return -1
            return assignHandle(n)
        }
        var cur = el.nextElementSibling()
        while (cur != null) {
            if (cur.`is`(selector)) return assignHandle(cur)
            cur = cur.nextElementSibling()
        }
        return -1
    }

    fun nextAll(handle: Int, selector: String?): IntArray {
        val el = elementOrNull(handle) ?: return IntArray(0)
        val result = mutableListOf<Int>()
        var cur = el.nextElementSibling()
        while (cur != null) {
            if (selector.isNullOrBlank() || cur.`is`(selector)) {
                result += assignHandle(cur)
            }
            cur = cur.nextElementSibling()
        }
        return result.toIntArray()
    }

    fun prev(handle: Int, selector: String?): Int {
        val el = elementOrNull(handle) ?: return -1
        if (selector.isNullOrBlank()) {
            val p = el.previousElementSibling() ?: return -1
            return assignHandle(p)
        }
        var cur = el.previousElementSibling()
        while (cur != null) {
            if (cur.`is`(selector)) return assignHandle(cur)
            cur = cur.previousElementSibling()
        }
        return -1
    }

    fun prevAll(handle: Int, selector: String?): IntArray {
        val el = elementOrNull(handle) ?: return IntArray(0)
        val result = mutableListOf<Int>()
        var cur = el.previousElementSibling()
        while (cur != null) {
            if (selector.isNullOrBlank() || cur.`is`(selector)) {
                result += assignHandle(cur)
            }
            cur = cur.previousElementSibling()
        }
        return result.toIntArray()
    }

    fun siblings(handle: Int, selector: String?): IntArray {
        val el = elementOrNull(handle) ?: return IntArray(0)
        val parent = el.parent() ?: return IntArray(0)
        return parent.children()
            .filter { it !== el && (selector.isNullOrBlank() || it.`is`(selector)) }
            .map { assignHandle(it) }
            .toIntArray()
    }

    fun closest(handle: Int, selector: String): Int {
        var cur: Element? = elementOrNull(handle) ?: return -1
        while (cur != null && cur !is Document) {
            if (cur.`is`(selector)) return assignHandle(cur)
            val p = cur.parent()
            cur = if (p is Element && p !is Document) p else null
        }
        return -1
    }

    /** Returns handles for all child nodes including [TextNode]s. */
    fun contents(handle: Int): IntArray {
        val el = elementOrNull(handle) ?: return IntArray(0)
        return el.childNodes().map { assignHandle(it) }.toIntArray()
    }

    // ------------------------------------------------------------------
    // Predicates
    // ------------------------------------------------------------------

    fun matches(handle: Int, selector: String): Boolean {
        val el = elementOrNull(handle) ?: return false
        return el.`is`(selector)
    }

    fun has(handle: Int, selector: String): Boolean {
        val el = elementOrNull(handle) ?: return false
        return el.select(selector).isNotEmpty()
    }

    fun not(handle: Int, selector: String): IntArray {
        // Operates on a single element; returns it if it does NOT match selector.
        val el = elementOrNull(handle) ?: return IntArray(0)
        return if (!el.`is`(selector)) intArrayOf(handle) else IntArray(0)
    }

    // ------------------------------------------------------------------
    // Content accessors
    // ------------------------------------------------------------------

    fun getHtml(handle: Int): String {
        val node = nodes[handle] ?: return ""
        return when (node) {
            is Element -> node.html()
            is TextNode -> node.wholeText
            else -> ""
        }
    }

    fun getOuterHtml(handle: Int): String {
        val node = nodes[handle] ?: return ""
        return when (node) {
            is Element -> node.outerHtml()
            is TextNode -> node.wholeText
            else -> ""
        }
    }

    fun getText(handle: Int): String {
        val node = nodes[handle] ?: return ""
        return when (node) {
            is Element -> node.text()
            is TextNode -> node.wholeText
            else -> ""
        }
    }

    fun getAttr(handle: Int, name: String): String? {
        val el = elementOrNull(handle) ?: return null
        if (!el.hasAttr(name)) return null
        return el.attr(name)
    }

    fun getAllAttrs(handle: Int): Map<String, String> {
        val el = elementOrNull(handle) ?: return emptyMap()
        return el.attributes().associate { it.key to it.value }
    }

    fun hasClass(handle: Int, className: String): Boolean {
        val el = elementOrNull(handle) ?: return false
        return el.hasClass(className)
    }

    fun getData(handle: Int, key: String): String? {
        return getAttr(handle, "data-$key")
    }

    fun getVal(handle: Int): String? {
        val el = elementOrNull(handle) ?: return null
        return el.`val`()
    }

    fun getTagName(handle: Int): String {
        val el = elementOrNull(handle) ?: return ""
        return el.tagName()
    }

    fun isTextNode(handle: Int): Boolean {
        return nodes[handle] is TextNode
    }

    // ------------------------------------------------------------------
    // Mutations
    // ------------------------------------------------------------------

    fun replaceWith(handle: Int, newHtml: String) {
        val node = nodes[handle] ?: return
        val parsed = Jsoup.parseBodyFragment(newHtml).body()?.childNodes() ?: return
        if (parsed.isEmpty()) {
            node.remove()
        } else {
            // Insert new nodes before, then remove original
            val parent = node.parentNode() ?: return
            val siblings = parent.childNodes()
            val idx = siblings.indexOf(node)
            if (idx < 0) return
            parsed.reversed().forEach { newNode ->
                node.before(newNode.clone())
            }
            node.remove()
        }
    }

    fun remove(handle: Int) {
        val node = nodes[handle] ?: return
        node.remove()
    }

    fun addClass(handle: Int, className: String) {
        val el = elementOrNull(handle) ?: return
        el.addClass(className)
    }

    fun removeClass(handle: Int, className: String) {
        val el = elementOrNull(handle) ?: return
        el.removeClass(className)
    }

    // ------------------------------------------------------------------
    // Handle management
    // ------------------------------------------------------------------

    fun release(handle: Int) {
        nodes.remove(handle)
    }

    fun releaseAll() {
        nodes.clear()
        nextHandle = 1
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun assignHandle(node: Node): Int {
        // Check if this exact node instance already has a handle.
        for ((h, n) in nodes) {
            if (n === node) return h
        }
        val h = nextHandle++
        nodes[h] = node
        evictIfNeeded()
        return h
    }

    private fun elementOrNull(handle: Int): Element? {
        return nodes[handle] as? Element
    }

    private fun evictIfNeeded() {
        if (nodes.size <= MAX_HANDLES) return
        // Remove oldest entries (LRU order from LinkedHashMap access-order).
        val iterator = nodes.iterator()
        val toRemove = nodes.size - MAX_HANDLES
        var removed = 0
        while (iterator.hasNext() && removed < toRemove) {
            iterator.next()
            iterator.remove()
            removed++
        }
    }

    companion object {
        private const val MAX_HANDLES = 50_000
    }
}
