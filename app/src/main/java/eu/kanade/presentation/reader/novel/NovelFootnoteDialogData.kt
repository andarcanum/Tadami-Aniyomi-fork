package eu.kanade.presentation.reader.novel

internal data class NovelFootnoteDialogData(
    val anchorId: String,
    val titleNumber: String?,
    val bodyText: String,
    val resolvedExternalUrl: String?,
    val found: Boolean,
)
