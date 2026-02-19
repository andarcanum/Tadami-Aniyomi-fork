package eu.kanade.tachiyomi.ui.browse.novel.migration

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.i18n.MR

data class NovelMigrationFlag(
    val flag: Int,
    val isDefaultSelected: Boolean,
    val titleId: StringResource,
) {
    companion object {
        fun create(flag: Int, defaultSelectionMap: Int, titleId: StringResource): NovelMigrationFlag {
            return NovelMigrationFlag(
                flag = flag,
                isDefaultSelected = defaultSelectionMap and flag != 0,
                titleId = titleId,
            )
        }
    }
}

object NovelMigrationFlags {

    private const val CHAPTERS = 0b00001
    private const val CATEGORIES = 0b00010
    private const val DELETE_DOWNLOADED = 0b00100

    private val downloadManager = NovelDownloadManager()

    fun hasChapters(value: Int): Boolean {
        return value and CHAPTERS != 0
    }

    fun hasCategories(value: Int): Boolean {
        return value and CATEGORIES != 0
    }

    fun hasDeleteDownloaded(value: Int): Boolean {
        return value and DELETE_DOWNLOADED != 0
    }

    fun getFlags(novel: Novel?, defaultSelectedBitMap: Int): List<NovelMigrationFlag> {
        val flags = mutableListOf<NovelMigrationFlag>()
        flags += NovelMigrationFlag.create(CHAPTERS, defaultSelectedBitMap, MR.strings.chapters)
        flags += NovelMigrationFlag.create(CATEGORIES, defaultSelectedBitMap, MR.strings.categories)

        if (novel != null && downloadManager.getDownloadCount(novel) > 0) {
            flags += NovelMigrationFlag.create(
                DELETE_DOWNLOADED,
                defaultSelectedBitMap,
                MR.strings.delete_downloaded,
            )
        }

        return flags
    }

    fun getSelectedFlagsBitMap(
        selectedFlags: List<Boolean>,
        flags: List<NovelMigrationFlag>,
    ): Int {
        return selectedFlags
            .zip(flags)
            .filter { (isSelected, _) -> isSelected }
            .map { (_, flag) -> flag.flag }
            .reduceOrNull { acc, mask -> acc or mask } ?: 0
    }
}
