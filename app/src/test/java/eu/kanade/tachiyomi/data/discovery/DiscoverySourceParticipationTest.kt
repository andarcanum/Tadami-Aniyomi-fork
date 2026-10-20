package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import org.junit.Test

class DiscoverySourceParticipationTest {

    @Test
    fun `auto takes top3 of allowed`() {
        resolveSourceParticipation(
            mode = "auto",
            excludedIds = setOf(22L),
            weightOrder = listOf(11L, 22L, 33L, 44L),
            lastUsedId = 11L,
            fallbackId = -1L,
        ) shouldBe SourceParticipation(sourceIds = listOf(11L, 33L, 44L), primarySourceId = 11L)
    }

    @Test
    fun `manual allows all 15 in weight order and respects exclusions`() {
        val weightOrder = (1L..15L).toList()
        resolveSourceParticipation(
            mode = "manual",
            excludedIds = emptySet(),
            weightOrder = weightOrder,
            lastUsedId = -1L,
            fallbackId = -1L,
        ).sourceIds shouldBe (1L..15L).toList()

        resolveSourceParticipation(
            mode = "manual",
            excludedIds = setOf(2L, 5L, 10L),
            weightOrder = weightOrder,
            lastUsedId = -1L,
            fallbackId = -1L,
        ).sourceIds shouldBe weightOrder.filterNot { it in setOf(2L, 5L, 10L) }
    }

    @Test
    fun `excluded lastUsed falls back to first allowed`() {
        resolveSourceParticipation(
            mode = "auto",
            excludedIds = setOf(11L),
            weightOrder = listOf(11L, 22L),
            lastUsedId = 11L,
            fallbackId = 99L,
        ) shouldBe SourceParticipation(sourceIds = listOf(22L), primarySourceId = 22L)
    }

    @Test
    fun `all excluded yields empty and minus one`() {
        resolveSourceParticipation(
            mode = "manual",
            excludedIds = setOf(11L, 22L),
            weightOrder = listOf(11L, 22L),
            lastUsedId = 11L,
            fallbackId = 22L,
        ) shouldBe SourceParticipation(sourceIds = emptyList(), primarySourceId = -1L)
    }

    @Test
    fun `fallback used only when not excluded`() {
        resolveSourceParticipation(
            mode = "auto",
            excludedIds = setOf(99L),
            weightOrder = emptyList(),
            lastUsedId = -1L,
            fallbackId = 99L,
        ) shouldBe SourceParticipation(sourceIds = emptyList(), primarySourceId = -1L)
    }

    @Test
    fun `fallback used when lastUsed unset`() {
        resolveSourceParticipation(
            mode = "auto",
            excludedIds = emptySet(),
            weightOrder = emptyList(),
            lastUsedId = -1L,
            fallbackId = 55L,
        ) shouldBe SourceParticipation(sourceIds = emptyList(), primarySourceId = 55L)
    }

    @Test
    fun `stale excluded ids are ignored`() {
        // 999 — id удалённого плагина: на состав и primary не влияет.
        resolveSourceParticipation(
            mode = "auto",
            excludedIds = setOf(999L),
            weightOrder = listOf(11L, 22L),
            lastUsedId = 22L,
            fallbackId = -1L,
        ) shouldBe SourceParticipation(sourceIds = listOf(11L, 22L), primarySourceId = 22L)
    }

    @Test
    fun `key csv round trip and garbage tolerance`() {
        parseKeyCsv(formatKeyCsv(listOf("ext.b", "ext.a"))) shouldBe setOf("ext.b", "ext.a")
        parseKeyCsv("") shouldBe emptySet()
        parseKeyCsv("ext.x,, ,ext.y") shouldBe setOf("ext.x", "ext.y")
        formatKeyCsv(listOf("ext.b", "ext.a", "ext.b", " ")) shouldBe "ext.b,ext.a"
    }

    @Test
    fun `sources without plugin are dropped from plugin grouping`() {
        // −42 = встроенный OmniSource, 0 = локальный источник: не плагины, отбрасываются.
        groupSourcesByPlugin(setOf(-42L, 0L, 7L)) { id -> if (id == 7L) "ext.seven" else null } shouldBe
            mapOf("ext.seven" to listOf(7L))
        groupSourcesByPlugin(setOf(-42L, 0L)) { null } shouldBe emptyMap()
    }

    @Test
    fun `plugin grouping keeps language variants together`() {
        groupSourcesByPlugin(setOf(1L, 2L, 3L)) { id -> if (id == 3L) "ext.b" else "ext.a" } shouldBe
            mapOf("ext.a" to listOf(1L, 2L), "ext.b" to listOf(3L))
    }

    @Test
    fun `selectPluginRepresentative prefers active library source over preferred language fallback`() {
        val members = setOf(1L, 2L)
        val selected = selectPluginRepresentative(
            memberIds = members,
            langs = mapOf(1L to "ru", 2L to "en"),
            weights = mapOf(1L to 100, 2L to 0),
            appLang = "en",
        )
        selected shouldBe 1L
    }
}
