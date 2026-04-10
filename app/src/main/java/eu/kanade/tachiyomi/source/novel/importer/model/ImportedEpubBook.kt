package eu.kanade.tachiyomi.source.novel.importer.model

internal data class ImportedEpubBook(
    val title: String,
    val author: String? = null,
    val description: String? = null,
    val coverFileName: String? = null,
    val chapters: List<ImportedEpubChapter>,
    val assets: List<ImportedEpubAsset>,
)

internal data class ImportedEpubChapter(
    val title: String,
    val sourcePath: String,
)

internal data class ImportedEpubAsset(
    val sourcePath: String,
    val targetPath: String,
) {
    val targetFileName: String
        get() = targetPath.substringAfterLast('/')
}
