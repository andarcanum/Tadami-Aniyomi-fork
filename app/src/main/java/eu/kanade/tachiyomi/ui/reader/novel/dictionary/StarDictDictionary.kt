package eu.kanade.tachiyomi.ui.reader.novel.dictionary

import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import java.io.File
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class StarDictInfo(
    val bookname: String,
    val wordcount: Int,
    val synwordcount: Int = 0,
    val idxoffsetbits: Int = 32,
    val sametypesequence: String? = null,
    val version: String = "2.4.2",
    val author: String? = null,
    val description: String? = null,
)

data class StarDictArticle(
    val headword: String,
    val definitionsHtml: String,
)

/**
 * Minimal StarDict dictionary reader.
 *
 * Supports the standard StarDict on-disk layout:
 *  - `<name>.ifo`  — metadata (versions 2.4.2 / 3.0.0)
 *  - `<name>.idx`  — sorted word index (32-bit or 64-bit article offsets)
 *  - `<name>.syn`  — optional synonym index
 *  - `<name>.dict` — article data (`.dict.dz`/`.idx.gz` archives are decompressed at import time)
 *
 * Memory model: the `.idx`/`.syn` files are memory-mapped (off the Java heap) and only a
 * compact `IntArray` of entry positions is kept on the heap (~4 bytes per entry). Lookups
 * use binary search over the mapped bytes, relying on the StarDict sort contract
 * (`g_ascii_strcasecmp`, i.e. ASCII-case-insensitive byte order). This keeps even huge
 * dictionaries (millions of synonyms) at a few MB of heap instead of hundreds of MB.
 *
 * Article bodies are read lazily from the `.dict` file on lookup. Nothing is bundled with
 * the app — dictionaries are always imported by the user.
 */
class StarDictDictionary private constructor(
    val id: String,
    val info: StarDictInfo,
    private val dictFile: File,
    private val offsetBytes: Int,
    private val idx: SortedIndex,
    private val syn: SortedIndex?,
) {

    val wordCount: Int get() = idx.size

    /**
     * Looks up [rawTerm] with a few forgiving fallbacks (trimmed punctuation, lowercase,
     * decapitalized/capitalized first letter). Returns at most [maxArticles] rendered articles.
     */
    fun lookup(rawTerm: String, maxArticles: Int = MAX_ARTICLES): List<StarDictArticle> {
        val term = rawTerm.trim()
        if (term.isEmpty() || term.length > MAX_TERM_LENGTH) return emptyList()

        val stripped = term.trim { !it.isLetterOrDigit() }
        val candidates = LinkedHashSet<String>()
        candidates += term
        if (stripped.isNotEmpty()) candidates += stripped
        candidates += term.lowercase()
        if (stripped.isNotEmpty()) {
            candidates += stripped.lowercase()
            candidates += stripped.replaceFirstChar { it.lowercase() }
            candidates += stripped.lowercase().replaceFirstChar { it.uppercase() }
        }

        val articles = ArrayList<StarDictArticle>(maxArticles)
        val seenOffsets = HashSet<Long>()
        for (candidate in candidates) {
            if (candidate.isEmpty()) continue
            val query = candidate.toByteArray(Charsets.UTF_8)
            collectFromIdx(query, articles, seenOffsets, maxArticles)
            if (articles.size >= maxArticles) return articles
            collectFromSyn(query, articles, seenOffsets, maxArticles)
            if (articles.size >= maxArticles) return articles
        }
        return articles
    }

    private fun collectFromIdx(
        query: ByteArray,
        out: MutableList<StarDictArticle>,
        seenOffsets: MutableSet<Long>,
        maxArticles: Int,
    ) {
        var i = idx.firstMatch(query)
        if (i < 0) return
        var scanned = 0
        while (i < idx.size && scanned < MAX_EQUAL_SCAN && idx.compareAsciiFold(query, i) == 0) {
            appendEntry(i, out, seenOffsets)
            if (out.size >= maxArticles) return
            i++
            scanned++
        }
    }

    private fun collectFromSyn(
        query: ByteArray,
        out: MutableList<StarDictArticle>,
        seenOffsets: MutableSet<Long>,
        maxArticles: Int,
    ) {
        val syn = syn ?: return
        var i = syn.firstMatch(query)
        if (i < 0) return
        var scanned = 0
        while (i < syn.size && scanned < MAX_EQUAL_SCAN && syn.compareAsciiFold(query, i) == 0) {
            val target = syn.readUInt32(syn.payloadPosition(i)).toInt()
            if (target in 0 until idx.size) {
                appendEntry(target, out, seenOffsets)
                if (out.size >= maxArticles) return
            }
            i++
            scanned++
        }
    }

    private fun appendEntry(
        entryIndex: Int,
        out: MutableList<StarDictArticle>,
        seenOffsets: MutableSet<Long>,
    ) {
        val payload = idx.payloadPosition(entryIndex)
        val offset = if (offsetBytes == 8) idx.readUInt64(payload) else idx.readUInt32(payload)
        val size = idx.readUInt32(payload + offsetBytes).toInt()
        if (!seenOffsets.add(offset)) return
        val html = runCatching { readArticle(offset, size) }.getOrNull() ?: return
        if (html.isBlank()) return
        out += StarDictArticle(headword = idx.wordAt(entryIndex), definitionsHtml = html)
    }

    private fun readArticle(offset: Long, size: Int): String {
        if (offset < 0 || size <= 0 || size > MAX_ARTICLE_BYTES) return ""
        val buffer = ByteArray(size)
        RandomAccessFile(dictFile, "r").use { raf ->
            raf.seek(offset)
            raf.readFully(buffer)
        }
        return renderFields(buffer)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Article rendering (StarDict field types → sanitized HTML)
    // ─────────────────────────────────────────────────────────────────────────

    private fun renderFields(data: ByteArray): String {
        val sb = StringBuilder()
        val sequence = info.sametypesequence
        var pos = 0
        if (!sequence.isNullOrEmpty()) {
            for ((i, type) in sequence.withIndex()) {
                if (pos >= data.size) break
                val isLast = i == sequence.length - 1
                if (type.isUpperCase()) {
                    // Uppercase types: 32-bit size prefix, omitted for the last field.
                    if (isLast) {
                        appendField(sb, type, data.copyOfRange(pos, data.size))
                        pos = data.size
                    } else {
                        if (pos + 4 > data.size) break
                        val size = readUInt32(data, pos).toInt()
                        val end = (pos + 4 + size).coerceAtMost(data.size)
                        appendField(sb, type, data.copyOfRange(pos + 4, end))
                        pos = end
                    }
                } else {
                    // Lowercase types: NUL-terminated string, terminator omitted for the last field.
                    val end = if (isLast) data.size else indexOfNul(data, pos)
                    appendField(sb, type, data.copyOfRange(pos, end))
                    pos = if (isLast || end >= data.size) data.size else end + 1
                }
            }
        } else {
            // Without sametypesequence every field is prefixed with its type character.
            while (pos < data.size) {
                val type = data[pos].toInt().toChar()
                pos++
                if (type.isUpperCase()) {
                    if (pos + 4 > data.size) break
                    val size = readUInt32(data, pos).toInt()
                    pos += 4
                    val end = (pos + size).coerceAtMost(data.size)
                    appendField(sb, type, data.copyOfRange(pos, end))
                    pos = end
                } else {
                    val end = indexOfNul(data, pos)
                    appendField(sb, type, data.copyOfRange(pos, end))
                    pos = if (end >= data.size) data.size else end + 1
                }
            }
        }
        return sb.toString().trim()
    }

    private fun appendField(sb: StringBuilder, type: Char, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        when (type) {
            // Plain text variants (meaning, wiki/plain text, KingSoft, WordNet, tree)
            'm', 'l', 'y', 'k', 'w', 'n' -> {
                val text = decodeUtf8(bytes).trim()
                if (text.isNotEmpty()) {
                    sb.append("<p>").append(escapeHtml(text).replace("\n", "<br>")).append("</p>")
                }
            }
            // Pronunciation / phonetics
            't' -> {
                val text = decodeUtf8(bytes).trim()
                if (text.isNotEmpty()) {
                    sb.append("<p><i>[").append(escapeHtml(text)).append("]</i></p>")
                }
            }
            // Pango markup — close enough to HTML for a sanitizer pass.
            'g' -> sb.append(sanitizeHtml(decodeUtf8(bytes)))
            // XDXF markup — convert well-known tags to HTML first, then sanitize.
            'x' -> sb.append(sanitizeHtml(xdxfToHtml(decodeUtf8(bytes))))
            // HTML
            'h' -> sb.append(sanitizeHtml(decodeUtf8(bytes)))
            // 'r' (resource file list) and uppercase media types (W/P/X) are not supported offline.
            else -> Unit
        }
    }

    private fun decodeUtf8(bytes: ByteArray): String = String(bytes, Charsets.UTF_8)

    private fun xdxfToHtml(source: String): String {
        return source
            .replace(Regex("<k>(.*?)</k>", RegexOption.DOT_MATCHES_ALL), "<b>$1</b><br>")
            .replace(Regex("<(/?)ex>"), "<$1i>")
            .replace(Regex("<(/?)abr>"), "<$1i>")
            .replace(Regex("<(/?)tr>"), "<$1i>")
            .replace(Regex("<kref>(.*?)</kref>", RegexOption.DOT_MATCHES_ALL), "<u>$1</u>")
            .replace(Regex("<c(?:\\s+c=\"[^\"]*\")?>"), "<span>")
            .replace("</c>", "</span>")
            .replace(Regex("<rref>.*?</rref>", RegexOption.DOT_MATCHES_ALL), "")
    }

    /**
     * A NUL-terminated, StarDict-sorted (ASCII-case-insensitive) word list backed by a
     * read-only memory-mapped buffer. Only entry start positions live on the Java heap.
     * All buffer reads are absolute (no shared mutable position), so concurrent lookups
     * are safe.
     */
    private class SortedIndex(
        private val buffer: MappedByteBuffer,
        private val positions: IntArray,
    ) {
        val size: Int get() = positions.size

        private fun wordEnd(i: Int): Int {
            var p = positions[i]
            while (buffer.get(p) != 0.toByte()) p++
            return p
        }

        fun payloadPosition(i: Int): Int = wordEnd(i) + 1

        fun wordAt(i: Int): String {
            val start = positions[i]
            val end = wordEnd(i)
            val bytes = ByteArray(end - start)
            for (k in bytes.indices) bytes[k] = buffer.get(start + k)
            return String(bytes, Charsets.UTF_8)
        }

        /**
         * ASCII-case-insensitive comparison of [query] against entry [i]'s word bytes,
         * mirroring glib's `g_ascii_strcasecmp` used by StarDict to sort `.idx`/`.syn`.
         * Returns <0 / 0 / >0 when the query sorts before / equals / sorts after the entry.
         */
        fun compareAsciiFold(query: ByteArray, i: Int): Int {
            var p = positions[i]
            var q = 0
            while (true) {
                val entryByte = buffer.get(p)
                if (q == query.size) return if (entryByte == 0.toByte()) 0 else -1
                if (entryByte == 0.toByte()) return 1
                val qc = foldAscii(query[q])
                val ec = foldAscii(entryByte)
                if (qc != ec) return qc - ec
                p++
                q++
            }
        }

        /** Binary search for the first entry that fold-equals [query]; -1 when absent. */
        fun firstMatch(query: ByteArray): Int {
            var lo = 0
            var hi = size - 1
            var result = -1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                val cmp = compareAsciiFold(query, mid)
                when {
                    cmp == 0 -> {
                        result = mid
                        hi = mid - 1
                    }
                    cmp < 0 -> hi = mid - 1
                    else -> lo = mid + 1
                }
            }
            return result
        }

        fun readUInt32(p: Int): Long {
            return ((buffer.get(p).toLong() and 0xFF) shl 24) or
                ((buffer.get(p + 1).toLong() and 0xFF) shl 16) or
                ((buffer.get(p + 2).toLong() and 0xFF) shl 8) or
                (buffer.get(p + 3).toLong() and 0xFF)
        }

        fun readUInt64(p: Int): Long {
            var value = 0L
            for (k in 0 until 8) {
                value = (value shl 8) or (buffer.get(p + k).toLong() and 0xFF)
            }
            return value
        }

        private fun foldAscii(b: Byte): Int {
            val v = b.toInt() and 0xFF
            return if (v in 'A'.code..'Z'.code) v + 32 else v
        }
    }

    companion object Loader {
        private const val MAX_ARTICLE_BYTES = 1 shl 20 // 1 MiB per article
        private const val MAX_ARTICLES = 3
        private const val MAX_TERM_LENGTH = 256
        private const val MAX_EQUAL_SCAN = 32 // duplicate headwords scanned per candidate
        private const val MAX_INDEX_BYTES = 1L shl 30 // 1 GiB per index file
        private const val MAX_ENTRIES = 20_000_000

        private val SAFE_HTML: Safelist = Safelist.relaxed()
            .removeTags("img")
            .addTags("span", "font", "hr")

        private fun sanitizeHtml(html: String): String = Jsoup.clean(html, SAFE_HTML)

        private fun escapeHtml(text: String): String = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        private fun indexOfNul(data: ByteArray, from: Int): Int {
            var i = from
            while (i < data.size && data[i] != 0.toByte()) i++
            return i
        }

        private fun readUInt32(data: ByteArray, pos: Int): Long {
            return ((data[pos].toLong() and 0xFF) shl 24) or
                ((data[pos + 1].toLong() and 0xFF) shl 16) or
                ((data[pos + 2].toLong() and 0xFF) shl 8) or
                (data[pos + 3].toLong() and 0xFF)
        }

        private fun readUInt64(data: ByteArray, pos: Int): Long {
            var value = 0L
            for (i in 0 until 8) {
                value = (value shl 8) or (data[pos + i].toLong() and 0xFF)
            }
            return value
        }

        /** Parses a StarDict `.ifo` file; returns null when the file is not a valid ifo. */
        fun parseIfo(file: File): StarDictInfo? {
            val lines = runCatching { file.readLines(Charsets.UTF_8) }.getOrNull() ?: return null
            if (lines.isEmpty() || !lines.first().contains("StarDict's dict ifo file")) return null
            val values = lines.drop(1).mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) {
                    null
                } else {
                    line.substring(0, separator).trim() to line.substring(separator + 1).trim()
                }
            }.toMap()
            val bookname = values["bookname"]?.takeIf { it.isNotEmpty() } ?: return null
            val wordcount = values["wordcount"]?.toIntOrNull() ?: return null
            return StarDictInfo(
                bookname = bookname,
                wordcount = wordcount,
                synwordcount = values["synwordcount"]?.toIntOrNull() ?: 0,
                idxoffsetbits = values["idxoffsetbits"]?.toIntOrNull() ?: 32,
                sametypesequence = values["sametypesequence"]?.takeIf { it.isNotEmpty() },
                version = values["version"] ?: "2.4.2",
                author = values["author"],
                description = values["description"],
            )
        }

        /**
         * Loads a dictionary from [dir], which must contain `<base>.ifo`, `<base>.idx`
         * and `<base>.dict` (already decompressed). The index files are memory-mapped;
         * only compact position arrays are allocated on the heap.
         */
        fun load(id: String, dir: File): StarDictDictionary {
            val ifoFile = dir.listFiles()?.firstOrNull { it.isFile && it.extension.equals("ifo", true) }
                ?: error("Missing .ifo file in ${dir.name}")
            val info = parseIfo(ifoFile) ?: error("Invalid .ifo file: ${ifoFile.name}")
            val base = ifoFile.name.removeSuffix(".ifo")
            val idxFile = File(dir, "$base.idx")
            val dictFile = File(dir, "$base.dict")
            check(idxFile.isFile) { "Missing .idx file for $base" }
            check(dictFile.isFile) { "Missing .dict file for $base" }
            check(info.idxoffsetbits == 32 || info.idxoffsetbits == 64) {
                "Unsupported idxoffsetbits=${info.idxoffsetbits}"
            }

            val offsetBytes = info.idxoffsetbits / 8
            val idxBuffer = mapReadOnly(idxFile)
            val idxPositions = scanEntryPositions(
                buffer = idxBuffer,
                payloadBytes = offsetBytes + 4,
                expectedCount = info.wordcount,
            )
            check(idxPositions.isNotEmpty()) { "Empty .idx index for $base" }

            // Optional synonym index (.syn) maps alternative spellings to idx entries.
            val synFile = File(dir, "$base.syn")
            val syn = if (synFile.isFile) {
                runCatching {
                    val synBuffer = mapReadOnly(synFile)
                    val synPositions = scanEntryPositions(
                        buffer = synBuffer,
                        payloadBytes = 4,
                        expectedCount = info.synwordcount,
                    )
                    SortedIndex(synBuffer, synPositions).takeIf { it.size > 0 }
                }.getOrNull()
            } else {
                null
            }

            return StarDictDictionary(
                id = id,
                info = info,
                dictFile = dictFile,
                offsetBytes = offsetBytes,
                idx = SortedIndex(idxBuffer, idxPositions),
                syn = syn,
            )
        }

        private fun mapReadOnly(file: File): MappedByteBuffer {
            val length = file.length()
            check(length in 1..MAX_INDEX_BYTES) { "Unsupported index file size: ${file.name}" }
            return RandomAccessFile(file, "r").use { raf ->
                raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, length)
            }
        }

        /** Scans NUL-terminated entries and returns the start position of each word. */
        private fun scanEntryPositions(
            buffer: MappedByteBuffer,
            payloadBytes: Int,
            expectedCount: Int,
        ): IntArray {
            val limit = buffer.capacity()
            var positions = IntArray(expectedCount.coerceIn(16, MAX_ENTRIES))
            var count = 0
            var p = 0
            while (p < limit) {
                val start = p
                while (p < limit && buffer.get(p) != 0.toByte()) p++
                if (p >= limit) break
                val wordLength = p - start
                p++ // NUL terminator
                if (p + payloadBytes > limit) break
                if (wordLength > 0) {
                    if (count == MAX_ENTRIES) break
                    if (count == positions.size) {
                        positions = positions.copyOf((positions.size * 2).coerceAtMost(MAX_ENTRIES))
                    }
                    positions[count++] = start
                }
                p += payloadBytes
            }
            return if (count == positions.size) positions else positions.copyOf(count)
        }
    }
}
