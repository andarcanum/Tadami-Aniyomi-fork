package eu.kanade.tachiyomi.ui.library.manga

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MangaLibraryMigrationTest {

    @Test
    fun `single selected manga routes to config screen`() {
        resolveMangaLibraryMigrationRoute(listOf(42L)) shouldBe MangaLibraryMigrationRoute.Config(listOf(42L))
    }

    @Test
    fun `multiple selected manga routes to config screen`() {
        resolveMangaLibraryMigrationRoute(listOf(1L, 2L)) shouldBe MangaLibraryMigrationRoute.Config(listOf(1L, 2L))
    }

    @Test
    fun `empty selection routes to none`() {
        resolveMangaLibraryMigrationRoute(emptyList()) shouldBe MangaLibraryMigrationRoute.None
    }
}
