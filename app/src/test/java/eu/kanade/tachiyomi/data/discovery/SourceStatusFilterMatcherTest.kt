package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus

/**
 * V4: эвристика статус-фильтров расширений. Требования: мультиязычные имена
 * фильтров/опций распознаются; «статус перевода» не принимается за статус выпуска;
 * одиночный Select при 2+ статусах инертен (best-effort); UNKNOWN-статус айтема не режется.
 */
class SourceStatusFilterMatcherTest {

    // ═══════════ Имя фильтра ═══════════

    @Test
    fun `имена статус-фильтров разных языков распознаются`() {
        listOf(
            "Status", "Статус", "Estado", "Statut", "Stato", "Durum",
            "Статус выхода", "状态", "状態", "상태", "Trạng thái",
        ).forEach { name ->
            SourceStatusFilterMatcher.isStatusFilterName(name) shouldBe true
        }
    }

    @Test
    fun `статус перевода не принимается за статус выпуска`() {
        SourceStatusFilterMatcher.isStatusFilterName("Статус перевода") shouldBe false
        SourceStatusFilterMatcher.isStatusFilterName("Translation status") shouldBe false
        SourceStatusFilterMatcher.isStatusFilterName("Estado de traducción") shouldBe false
    }

    @Test
    fun `несвязанные имена инертны`() {
        SourceStatusFilterMatcher.isStatusFilterName("Genre") shouldBe false
        SourceStatusFilterMatcher.isStatusFilterName("Жанры") shouldBe false
        SourceStatusFilterMatcher.isStatusFilterName("Sort by") shouldBe false
        SourceStatusFilterMatcher.isStatusFilterName("") shouldBe false
    }

    @Test
    fun `имена групп жанров и тегов разных языков распознаются`() {
        listOf(
            "Genre", "Genres", "Жанры", "Жанр", "Tags", "Теги", "Categories", "Категории", "Género", "ジャンル",
        ).forEach { name ->
            isGenreGroupName(name) shouldBe true
        }
    }

    // ═══════════ Статус опции ═══════════

    @Test
    fun `опции ongoing разных языков`() {
        listOf(
            "Ongoing", "Онгоинг", "Выходит", "En emisión", "En cours", "Devam ediyor",
            "Berlangsung", "連載中", "연재중", "Currently Airing",
        ).forEach { name ->
            SourceStatusFilterMatcher.statusOfOption(name) shouldBe DiscoveryReleaseStatus.ONGOING
        }
    }

    @Test
    fun `опции finished разных языков`() {
        listOf(
            "Completed", "Finished", "Завершён", "Завершен", "Finalizado", "Terminé",
            "完結", "완결", "Selesai", "Finished Airing",
        ).forEach { name ->
            SourceStatusFilterMatcher.statusOfOption(name) shouldBe DiscoveryReleaseStatus.FINISHED
        }
    }

    @Test
    fun `publishing finished не склеивается с ongoing`() {
        // Порядок таблиц: FINISHED раньше ONGOING, иначе «publishing» утащил бы в ongoing.
        SourceStatusFilterMatcher.statusOfOption("Publishing finished") shouldBe DiscoveryReleaseStatus.FINISHED
        SourceStatusFilterMatcher.statusOfOption("Currently publishing") shouldBe DiscoveryReleaseStatus.ONGOING
    }

    @Test
    fun `опции hiatus и anons`() {
        SourceStatusFilterMatcher.statusOfOption("On hiatus") shouldBe DiscoveryReleaseStatus.PAUSED
        SourceStatusFilterMatcher.statusOfOption("Приостановлен") shouldBe DiscoveryReleaseStatus.PAUSED
        SourceStatusFilterMatcher.statusOfOption("Discontinued") shouldBe DiscoveryReleaseStatus.PAUSED
        SourceStatusFilterMatcher.statusOfOption("Not yet released") shouldBe DiscoveryReleaseStatus.ANONS
        SourceStatusFilterMatcher.statusOfOption("Анонс") shouldBe DiscoveryReleaseStatus.ANONS
    }

    @Test
    fun `нейтральные и чужие опции инертны`() {
        SourceStatusFilterMatcher.statusOfOption("All") shouldBe null
        SourceStatusFilterMatcher.statusOfOption("Все") shouldBe null
        SourceStatusFilterMatcher.statusOfOption("Dropped") shouldBe null
        SourceStatusFilterMatcher.statusOfOption("Licensed") shouldBe null
        SourceStatusFilterMatcher.statusOfOption("") shouldBe null
    }

    // ═══════════ Select (одиночный) ═══════════

    @Test
    fun `select матчит индекс опции под один статус`() {
        val options = listOf("All", "Ongoing", "Completed", "Hiatus")
        SourceStatusFilterMatcher.matchSelectIndex(options, setOf(DiscoveryReleaseStatus.ONGOING)) shouldBe 1
        SourceStatusFilterMatcher.matchSelectIndex(options, setOf(DiscoveryReleaseStatus.FINISHED)) shouldBe 2
        SourceStatusFilterMatcher.matchSelectIndex(options, setOf(DiscoveryReleaseStatus.PAUSED)) shouldBe 3
    }

    @Test
    fun `select инертен при 2+ статусах и при отсутствии опции`() {
        val options = listOf("All", "Ongoing", "Completed")
        val multi = setOf(DiscoveryReleaseStatus.ONGOING, DiscoveryReleaseStatus.FINISHED)
        SourceStatusFilterMatcher.matchSelectIndex(options, multi) shouldBe null
        SourceStatusFilterMatcher.matchSelectIndex(options, setOf(DiscoveryReleaseStatus.ANONS)) shouldBe null
        SourceStatusFilterMatcher.matchSelectIndex(options, emptySet()) shouldBe null
    }

    // ═══════════ Group (чекбоксы/tri-state) ═══════════

    @Test
    fun `group матчит несколько опций под выбор`() {
        val options = listOf("Ongoing", "Completed", "Hiatus", "Dropped")
        val matched = SourceStatusFilterMatcher.matchOptionNames(
            options,
            setOf(DiscoveryReleaseStatus.ONGOING, DiscoveryReleaseStatus.FINISHED),
        )
        matched shouldBe setOf("Ongoing", "Completed")
    }

    @Test
    fun `group без совпадений даёт пустое множество`() {
        SourceStatusFilterMatcher.matchOptionNames(
            listOf("All", "Dropped"),
            setOf(DiscoveryReleaseStatus.ONGOING),
        ) shouldBe emptySet()
    }

    // ═══════════ Пост-фильтр latest ═══════════

    @Test
    fun `пост-фильтр режет известный статус вне выбора`() {
        val selected = setOf(DiscoveryReleaseStatus.FINISHED)
        // SManga: ONGOING=1, COMPLETED=2, LICENSED=3, PUBLISHING_FINISHED=4, CANCELLED=5, ON_HIATUS=6.
        SourceStatusFilterMatcher.entryPasses(1, selected) shouldBe false
        SourceStatusFilterMatcher.entryPasses(2, selected) shouldBe true
        SourceStatusFilterMatcher.entryPasses(4, selected) shouldBe true
        SourceStatusFilterMatcher.entryPasses(6, selected) shouldBe false
    }

    @Test
    fun `пост-фильтр не режет UNKNOWN и LICENSED и выключен без выбора`() {
        val selected = setOf(DiscoveryReleaseStatus.ONGOING)
        SourceStatusFilterMatcher.entryPasses(0, selected) shouldBe true
        SourceStatusFilterMatcher.entryPasses(3, selected) shouldBe true
        SourceStatusFilterMatcher.entryPasses(0, emptySet()) shouldBe true
        SourceStatusFilterMatcher.entryPasses(2, emptySet()) shouldBe true
    }

    // ═══════════ Сырые статусы провайдеров (LIKE-ряд) ═══════════

    @Test
    fun `сырые статусы провайдеров распознаются с нормализацией`() {
        // AniList GraphQL (верхний регистр, подчёркивания)…
        SourceStatusFilterMatcher.statusOfRaw("RELEASING") shouldBe DiscoveryReleaseStatus.ONGOING
        SourceStatusFilterMatcher.statusOfRaw("FINISHED") shouldBe DiscoveryReleaseStatus.FINISHED
        SourceStatusFilterMatcher.statusOfRaw("NOT_YET_RELEASED") shouldBe DiscoveryReleaseStatus.ANONS
        SourceStatusFilterMatcher.statusOfRaw("HIATUS") shouldBe DiscoveryReleaseStatus.PAUSED
        SourceStatusFilterMatcher.statusOfRaw("CANCELLED") shouldBe DiscoveryReleaseStatus.PAUSED
        // Shikimori… MAL/Jikan-формы…
        SourceStatusFilterMatcher.statusOfRaw("released") shouldBe DiscoveryReleaseStatus.FINISHED
        SourceStatusFilterMatcher.statusOfRaw("ongoing") shouldBe DiscoveryReleaseStatus.ONGOING
        SourceStatusFilterMatcher.statusOfRaw("Currently Airing") shouldBe DiscoveryReleaseStatus.ONGOING
        SourceStatusFilterMatcher.statusOfRaw("Finished Airing") shouldBe DiscoveryReleaseStatus.FINISHED
        SourceStatusFilterMatcher.statusOfRaw(null) shouldBe null
        SourceStatusFilterMatcher.statusOfRaw("") shouldBe null
        SourceStatusFilterMatcher.statusOfRaw("??") shouldBe null
    }

    @Test
    fun `строгий проход без статуса не проходит при активном фильтре`() {
        val finishedOnly = setOf(DiscoveryReleaseStatus.FINISHED)
        // Фильтр выключен — всё проходит, включая без статуса.
        SourceStatusFilterMatcher.rawStatusPasses(null, emptySet()) shouldBe true
        SourceStatusFilterMatcher.rawStatusPasses("RELEASING", emptySet()) shouldBe true
        // Фильтр активен: соответствие проходит, несовпадение и «нет статуса» — нет
        // (MAL/MU/NU similar не несут статус → не просачиваются в ряд «Похоже»).
        SourceStatusFilterMatcher.rawStatusPasses("FINISHED", finishedOnly) shouldBe true
        SourceStatusFilterMatcher.rawStatusPasses("released", finishedOnly) shouldBe true
        SourceStatusFilterMatcher.rawStatusPasses("RELEASING", finishedOnly) shouldBe false
        SourceStatusFilterMatcher.rawStatusPasses(null, finishedOnly) shouldBe false
        SourceStatusFilterMatcher.rawStatusPasses("garbage", finishedOnly) shouldBe false
    }

    @Test
    fun `filterByRawStatus режет список рекомендаций`() {
        val items = listOf("finished-rec" to "FINISHED", "ongoing-rec" to "RELEASING", "no-status" to null)
        val kept = SourceStatusFilterMatcher.filterByRawStatus(items, setOf(DiscoveryReleaseStatus.FINISHED)) {
            it.second
        }
        kept.map { it.first } shouldBe listOf("finished-rec")
        // Без фильтра — всё.
        val all = SourceStatusFilterMatcher.filterByRawStatus(items, emptySet()) { it.second }
        all.size shouldBe 3
    }
}
