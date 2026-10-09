package tachiyomi.data.discovery

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import data.History
import data.Mangas
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter

/**
 * Backfill после апгрейда: SOURCE-строки кэша до миграции 58 не имеют привязки
 * (source_id NULL) — на них direct open деградирует в шторку. Запрос обязан находить
 * ровно такие строки (и только их), чтобы Home мог запросить разовую перегенерацию ленты.
 */
class DiscoveryUnboundBindingQueryTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    private val db = Database(
        driver = driver,
        chaptersAdapter = data.Chapters.Adapter(memoAdapter = MemoColumnAdapter),
        historyAdapter = History.Adapter(last_readAdapter = DateColumnAdapter),
        mangasAdapter = Mangas.Adapter(
            memoAdapter = MemoColumnAdapter,
            genreAdapter = StringListColumnAdapter,
            custom_genreAdapter = StringListColumnAdapter,
            update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
        ),
    ).also { Database.Schema.create(driver) }

    @AfterEach
    fun close() = driver.close()

    private fun insert(rowType: String, sourceId: Long?) {
        val key = "t-$rowType-$sourceId"
        db.discovery_suggestionsQueries.insert(
            media_type = "novel",
            row_type = rowType,
            title = key,
            clean_title = key,
            cover_url = null,
            reason = null,
            seed_title = null,
            provider = "p",
            score = 0.0,
            position = 0,
            created_at = 1L,
            source_id = sourceId,
            source_url = if (sourceId != null) "/u" else null,
            genres = null,
        )
    }

    @Test
    fun `counts only source rows without binding`() {
        db.discovery_suggestionsQueries.countUnboundSourceRows().executeAsOne() shouldBe 0L
        insert("source", null)
        db.discovery_suggestionsQueries.countUnboundSourceRows().executeAsOne() shouldBe 1L
        // Привязанный SOURCE и непривязанные других рядов не влияют на счётчик.
        insert("source", 7L)
        insert("like", null)
        insert("trend", null)
        insert("taste", null)
        db.discovery_suggestionsQueries.countUnboundSourceRows().executeAsOne() shouldBe 1L
    }
}
