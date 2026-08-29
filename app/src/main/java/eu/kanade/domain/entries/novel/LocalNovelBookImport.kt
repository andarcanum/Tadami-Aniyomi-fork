package eu.kanade.domain.entries.novel

/**
 * Pure helpers for the library "Import book" flow (books, text formats and archives
 * into localnovel/). Must stay in lockstep with LocalNovelFormats.SUPPORTED_EXTENSIONS.
 */
object LocalNovelBookImport {

    val SUPPORTED_EXTENSIONS: Set<String> = setOf(
        "epub",
        "fb2",
        "txt",
        "text",
        "md",
        "markdown",
        "html",
        "htm",
        "xhtml",
        "zip",
        "cbz",
        "rar",
        "cbr",
    )

    val PICKER_MIME_TYPES: Array<String> = arrayOf(
        "application/epub+zip",
        "application/x-fictionbook+xml",
        "application/xml",
        "text/xml",
        "text/plain",
        "text/markdown",
        "text/html",
        "application/zip",
        "application/x-zip-compressed",
        "application/vnd.rar",
        // Some OEM file managers only expose books under application/octet-stream.
        "application/octet-stream",
    )

    fun extensionOf(fileName: String): String {
        return fileName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
            .trim()
    }

    fun isSupportedImportFileName(fileName: String): Boolean {
        return extensionOf(fileName) in SUPPORTED_EXTENSIONS
    }

    fun titleFallbackFromFileName(fileName: String): String {
        val name = fileName.trim()
        if (name.isEmpty()) return "untitled"
        val ext = extensionOf(name)
        return if (ext in SUPPORTED_EXTENSIONS) {
            name.substringBeforeLast('.')
        } else {
            name
        }.ifBlank { "untitled" }
    }

    fun sanitizeFileName(displayName: String): String {
        return displayName.replace("""[/\\:*?"<>|]""".toRegex(), "_")
    }
}
