package mihon.core.archive

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.Closeable
import java.io.InputStream

/**
 * Wrapper over ArchiveReader to load files in epub format.
 */
class EpubReader(private val reader: ArchiveReader) : Closeable by reader {

    /**
     * Path separator used by this epub.
     */
    private val pathSeparator = getPathSeparator()

    /**
     * Returns the path of the cover image.
     */
    fun getCoverImage(): String? {
        val ref = getPackageHref()
        val doc = getPackageDocument(ref)
        val basePath = getParentDirectory(ref)

        // EPUB 3
        val coverItem = doc.select("manifest > item[properties~=cover-image]").first()
        if (coverItem != null) {
            return resolveZipPath(basePath, coverItem.attr("href"))
        }

        // EPUB 2
        val coverMeta = doc.select("metadata > meta[name=cover]").first()
        if (coverMeta != null) {
            val coverId = coverMeta.attr("content")
            val item = doc.select("manifest > item[id=$coverId]").first()
            if (item != null) {
                return resolveZipPath(basePath, item.attr("href"))
            }
        }

        // Fallback: Check for cover.xhtml / titlepage.xhtml / cover.html in manifest
        val coverPageItem = doc.select(
            "manifest > item[href~=(?i)cover\\.(x)html|(?i)titlepage\\.(x)html]",
        ).first()
        if (coverPageItem != null) {
            val pagePath = resolveZipPath(basePath, coverPageItem.attr("href"))
            try {
                getInputStream(pagePath)?.use { stream ->
                    val pageDoc = Jsoup.parse(stream, null, "")
                    val img = pageDoc.select("img, image").first()
                    val src = img?.attr("src")?.takeIf { it.isNotEmpty() }
                        ?: img?.attr("xlink:href")
                    if (!src.isNullOrEmpty()) {
                        val pageBasePath = getParentDirectory(pagePath)
                        return resolveZipPath(pageBasePath, src)
                    }
                }
            } catch (_: Exception) {
                // Ignore parsing errors
            }
        }

        // Fallback: common cover filenames inside archive
        val commonPattern = Regex("(?i)(^|.*?/)(cover)\\.(jpg|jpeg|png|webp)$")
        val commonCandidates = reader.useEntries { entries ->
            entries.mapNotNull { entry ->
                val name = entry.name
                if (commonPattern.matches(name)) name else null
            }.toList()
        }
        if (commonCandidates.isNotEmpty()) {
            return commonCandidates.sortedBy { it.length }.first()
        }

        return null
    }

    /**
     * Returns an input stream for reading the contents of the specified zip file entry.
     */
    fun getInputStream(entryName: String): InputStream? {
        return reader.getInputStream(entryName)
    }

    /**
     * Returns the path of all the images found in the epub file.
     */
    fun getImagesFromPages(): List<String> {
        val ref = getPackageHref()
        val doc = getPackageDocument(ref)
        val pages = getPagesFromDocument(doc)
        return getImagesFromPages(pages, ref)
    }

    /**
     * Returns the path to the package document.
     */
    fun getPackageHref(): String {
        val meta = getInputStream(resolveZipPath("META-INF", "container.xml"))
        if (meta != null) {
            val metaDoc = meta.use { Jsoup.parse(it, null, "", Parser.xmlParser()) }
            val path = metaDoc.getElementsByTag("rootfile").first()?.attr("full-path")
            if (path != null) {
                return path
            }
        }
        return resolveZipPath("OEBPS", "content.opf")
    }

    /**
     * Returns the package document where all the files are listed.
     */
    fun getPackageDocument(ref: String): Document {
        return getInputStream(ref)!!.use { Jsoup.parse(it, null, "", Parser.xmlParser()) }
    }

    /**
     * Returns all the pages from the epub.
     */
    fun getPagesFromDocument(document: Document): List<String> {
        val pages = document.select("manifest > item")
            .filter { node -> "application/xhtml+xml" == node.attr("media-type") }
            .associateBy { it.attr("id") }

        val spine = document.select("spine > itemref").map { it.attr("idref") }
        return spine.mapNotNull { pages[it] }.map { it.attr("href") }
    }

    /**
     * Returns all the images contained in every page from the epub.
     */
    private fun getImagesFromPages(pages: List<String>, packageHref: String): List<String> {
        val result = mutableListOf<String>()
        val basePath = getParentDirectory(packageHref)
        pages.forEach { page ->
            val entryPath = resolveZipPath(basePath, page)
            val document = getInputStream(entryPath)!!.use { Jsoup.parse(it, null, "") }
            val imageBasePath = getParentDirectory(entryPath)

            document.allElements.forEach {
                when (it.tagName()) {
                    "img" -> result.add(resolveZipPath(imageBasePath, it.attr("src")))
                    "image" -> {
                        val href = it.attr("xlink:href").takeIf { it.isNotBlank() }
                            ?: it.attr("href")
                        if (href.isNotBlank()) result.add(resolveZipPath(imageBasePath, href))
                    }
                }
            }
        }

        return result
    }

    /**
     * Returns the path separator used by the epub file.
     */
    private fun getPathSeparator(): String {
        val meta = getInputStream("META-INF\\container.xml")
        return if (meta != null) {
            meta.close()
            "\\"
        } else {
            "/"
        }
    }

    /**
     * Resolves a zip path from base and relative components and a path separator.
     */
    private fun resolveZipPath(basePath: String, relativePath: String): String {
        if (relativePath.startsWith("http://") || relativePath.startsWith("https://")) {
            return relativePath
        }

        val separator = pathSeparator
        val fullPath = if (relativePath.startsWith(separator)) {
            relativePath
        } else if (basePath.isEmpty()) {
            relativePath
        } else {
            "$basePath$separator$relativePath"
        }

        val segments = fullPath.split(separator)
        val resolved = mutableListOf<String>()

        for (segment in segments) {
            if (segment == "." || segment.isEmpty()) continue
            if (segment == "..") {
                if (resolved.isNotEmpty()) resolved.removeAt(resolved.lastIndex)
            } else {
                resolved.add(segment)
            }
        }

        return resolved.joinToString(separator)
    }

    /**
     * Gets the parent directory of a path.
     */
    private fun getParentDirectory(path: String): String {
        val separatorIndex = path.lastIndexOf(pathSeparator)
        return if (separatorIndex >= 0) {
            path.substring(0, separatorIndex)
        } else {
            ""
        }
    }

    /**
     * Returns the text content of all pages in the epub as HTML.
     */
    fun getTextContent(): String {
        val ref = getPackageHref()
        val doc = getPackageDocument(ref)
        val pages = getPagesFromDocument(doc)
        val basePath = getParentDirectory(ref)

        val content = StringBuilder()
        pages.forEach { page ->
            val entryPath = resolveZipPath(basePath, page)
            getInputStream(entryPath)?.use { inputStream ->
                val document = Jsoup.parse(inputStream, null, "")

                val imageBasePath = getParentDirectory(entryPath)
                document.select("img[src], image[xlink:href]").forEach { img ->
                    val src = if (img.hasAttr("src")) img.attr("src") else img.attr("xlink:href")
                    if (!src.startsWith("http") &&
                        !src.startsWith("data:") &&
                        !src.startsWith("tsundoku-novel-image://")
                    ) {
                        val imagePath = resolveZipPath(imageBasePath, src)
                        val novelUrl =
                            "tsundoku-novel-image://${java.net.URLEncoder.encode(imagePath, "UTF-8")}"
                        if (img.hasAttr("src")) {
                            img.attr("src", novelUrl)
                        } else {
                            img.attr("xlink:href", novelUrl)
                        }
                    }
                }

                document.body()?.let { body ->
                    content.append(body.html())
                    content.append("\n\n")
                }
            }
        }

        return content.toString()
    }

    /**
     * Data class representing an EPUB chapter/section from TOC.
     */
    data class EpubChapter(
        val title: String,
        val href: String,
        val order: Int,
    )

    /**
     * Returns the table of contents (chapters) from the EPUB.
     * Tries EPUB 3 NAV first, then falls back to EPUB 2 NCX.
     */
    fun getTableOfContents(): List<EpubChapter> {
        val ref = getPackageHref()
        val doc = getPackageDocument(ref)
        val opfBasePath = getParentDirectory(ref)

        // Try EPUB 3 NAV first
        val navHref = doc.select("manifest > item[properties*=nav]").attr("href")
        if (navHref.isNotEmpty()) {
            val navChapters = parseEpub3Nav(resolveZipPath(opfBasePath, navHref), opfBasePath)
            if (navChapters.isNotEmpty()) return navChapters
        }

        // Fall back to EPUB 2 NCX
        val ncxHref = doc.select("manifest > item[media-type='application/x-dtbncx+xml']").attr("href")
        if (ncxHref.isNotEmpty()) {
            val ncxChapters = parseEpub2Ncx(resolveZipPath(opfBasePath, ncxHref), opfBasePath)
            if (ncxChapters.isNotEmpty()) return ncxChapters
        }

        // If no TOC found, create chapters from spine pages
        val pages = getPagesFromDocument(doc)
        return pages.mapIndexed { index, page ->
            EpubChapter(
                title = "Chapter ${index + 1}",
                href = page,
                order = index,
            )
        }
    }

    /**
     * Parse EPUB 3 NAV document for TOC.
     * NAV hrefs are relative to the NAV file location.
     */
    private fun parseEpub3Nav(navPath: String, opfBasePath: String): List<EpubChapter> {
        val chapters = mutableListOf<EpubChapter>()
        val navBasePath = getParentDirectory(navPath)
        getInputStream(navPath)?.use { inputStream ->
            val doc = Jsoup.parse(inputStream, null, "", Parser.xmlParser())
            val navElement = doc.selectFirst("nav[*|type=toc], nav#toc, nav[epub\\:type=toc]")
            navElement?.select("li a")?.forEachIndexed { index, element ->
                val title = element.text().trim()
                val href = element.attr("href").substringBefore("#")
                if (title.isNotEmpty() && href.isNotEmpty()) {
                    val resolvedHref = resolveZipPath(navBasePath, href)
                    chapters.add(EpubChapter(title, resolvedHref, index))
                }
            }
        }
        return chapters
    }

    /**
     * Parse EPUB 2 NCX document for TOC.
     * NCX src paths are relative to the OPF file location.
     */
    private fun parseEpub2Ncx(ncxPath: String, opfBasePath: String): List<EpubChapter> {
        val chapters = mutableListOf<EpubChapter>()
        getInputStream(ncxPath)?.use { inputStream ->
            val doc = Jsoup.parse(inputStream, null, "", Parser.xmlParser())
            doc.select("navPoint").forEachIndexed { index, navPoint ->
                val title = navPoint.selectFirst("navLabel > text")?.text()?.trim() ?: ""
                val href = navPoint.selectFirst("content")?.attr("src")?.substringBefore("#") ?: ""
                if (title.isNotEmpty() && href.isNotEmpty()) {
                    val resolvedHref = resolveZipPath(opfBasePath, href)
                    chapters.add(EpubChapter(title, resolvedHref, index))
                }
            }
        }
        return chapters
    }

    /**
     * Returns the text content of a specific chapter/page from the EPUB.
     * @param chapterHref The relative path to the chapter within the EPUB
     */
    fun getChapterContent(chapterHref: String): String {
        val sanitizedHref = chapterHref.substringBefore("#")

        val ref = getPackageHref()
        val basePath = getParentDirectory(ref)
        val resolvedFromPackage = resolveZipPath(basePath, sanitizedHref)

        val entryPath = when {
            getInputStream(sanitizedHref) != null -> sanitizedHref
            else -> resolvedFromPackage
        }

        return getInputStream(entryPath)?.use { inputStream ->
            val document = Jsoup.parse(inputStream, null, "", Parser.xmlParser())

            val imageBasePath = getParentDirectory(entryPath)
            document.select("img, image").forEach { img ->
                val rawSrc = when {
                    img.hasAttr("src") -> img.attr("src")
                    img.hasAttr("xlink:href") -> img.attr("xlink:href")
                    img.hasAttr("href") -> img.attr("href")
                    else -> return@forEach
                }

                val src = rawSrc.substringBefore("#").trim()
                if (src.isBlank() ||
                    src.startsWith("http") ||
                    src.startsWith("//") ||
                    src.startsWith("data:") ||
                    src.startsWith("tsundoku-novel-image://")
                ) {
                    return@forEach
                }

                val imagePath = resolveZipPath(imageBasePath, src)
                val novelUrl =
                    "tsundoku-novel-image://${java.net.URLEncoder.encode(imagePath, "UTF-8")}"
                when {
                    img.hasAttr("src") -> img.attr("src", novelUrl)
                    img.hasAttr("xlink:href") -> img.attr("xlink:href", novelUrl)
                    else -> img.attr("href", novelUrl)
                }
            }

            // Convert simple SVG image containers to regular <img> tags.
            document.select("svg").forEach { svg ->
                val svgImage = svg.select("image")
                    .firstOrNull { it.hasAttr("xlink:href") || it.hasAttr("href") } ?: return@forEach
                val imageSrc = when {
                    svgImage.hasAttr("xlink:href") -> svgImage.attr("xlink:href")
                    svgImage.hasAttr("href") -> svgImage.attr("href")
                    else -> ""
                }
                if (imageSrc.isBlank()) return@forEach

                val imgElement = Element("img")
                imgElement.attr("src", imageSrc)
                imgElement.attr("style", "max-width:100%;height:auto;")
                svg.replaceWith(imgElement)
            }

            // Inline EPUB CSS
            document.select("link[rel=stylesheet]").forEach { link ->
                val href = link.attr("href")
                if (href.isNotBlank()) {
                    val cssPath = resolveZipPath(imageBasePath, href)
                    try {
                        getInputStream(cssPath)?.use { stream ->
                            var cssText = stream.reader().readText()

                            val urlRegex = Regex("""url\(['"]?(.*?)['"]?\)""")
                            cssText = urlRegex.replace(cssText) { match ->
                                val assetUrl = match.groupValues[1]
                                if (assetUrl.startsWith("data:") || assetUrl.startsWith("http")) {
                                    return@replace match.value
                                }

                                val cssDir = getParentDirectory(cssPath)
                                val assetPath = resolveZipPath(
                                    cssDir,
                                    assetUrl.substringBefore("?").substringBefore("#"),
                                )
                                try {
                                    getInputStream(assetPath)?.use { assetStream ->
                                        val bytes = assetStream.readBytes()
                                        val base64 = java.util.Base64.getEncoder()
                                            .encodeToString(bytes)
                                        val mimeType = when (
                                            assetPath.substringAfterLast('.', "").lowercase()
                                        ) {
                                            "png" -> "image/png"
                                            "jpg", "jpeg" -> "image/jpeg"
                                            "gif" -> "image/gif"
                                            "svg" -> "image/svg+xml"
                                            "ttf" -> "font/ttf"
                                            "otf" -> "font/otf"
                                            "woff" -> "font/woff"
                                            "woff2" -> "font/woff2"
                                            else -> "application/octet-stream"
                                        }
                                        "url('data:$mimeType;base64,$base64')"
                                    } ?: match.value
                                } catch (_: Exception) {
                                    match.value
                                }
                            }

                            val style = Element("style")
                            style.attr("data-epub-css", "true")
                            style.attr("data-file", href.substringAfterLast('/'))
                            style.text(cssText)
                            link.replaceWith(style)
                        }
                    } catch (_: Exception) {
                        // Ignore CSS inlining failures
                    }
                }
            }

            // Inline EPUB JS
            document.select("script[src]").forEach { script ->
                val src = script.attr("src")
                if (src.isNotBlank() && !src.startsWith("http") && !src.startsWith("//")) {
                    val jsPath = resolveZipPath(imageBasePath, src)
                    try {
                        getInputStream(jsPath)?.use { stream ->
                            val jsText = stream.reader().readText()
                            val inlineScript = Element("script")
                            inlineScript.attr("data-epub-js", "true")
                            inlineScript.attr("data-file", src.substringAfterLast('/'))
                            inlineScript.appendChild(org.jsoup.nodes.DataNode(jsText))
                            script.replaceWith(inlineScript)
                        }
                    } catch (_: Exception) {
                        // Ignore JS inlining failures
                    }
                }
            }

            // Remove title to prevent bleeding into text viewers
            document.getElementsByTag("title").remove()

            document.outerHtml()
        } ?: ""
    }
}
