package tachiyomi.data.novel

import app.cash.sqldelight.db.SqlDriver
import datanovel.Novel_history
import datanovel.Novels
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.novel.data.NovelDatabase

fun createTestNovelDatabase(driver: SqlDriver): NovelDatabase {
    NovelDatabase.Schema.create(driver)
    return NovelDatabase(
        driver = driver,
        novel_historyAdapter = Novel_history.Adapter(
            last_readAdapter = DateColumnAdapter,
        ),
        novelsAdapter = Novels.Adapter(
            genreAdapter = StringListColumnAdapter,
            update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
        ),
    )
}
