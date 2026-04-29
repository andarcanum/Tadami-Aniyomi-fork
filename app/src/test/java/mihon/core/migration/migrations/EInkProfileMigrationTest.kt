package mihon.core.migration.migrations

import eu.kanade.domain.ui.model.EInkProfile
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.source.model.SourceType

class EInkProfileMigrationTest {

    @Test
    fun `legacy e ink toggle migrates to monochrome profile`() {
        val eInkProfilePref = InMemoryPreferenceStore.InMemoryPreference<EInkProfile>(
            "e_ink_profile",
            null,
            EInkProfile.OFF,
        )
        val legacyEInkModePref = InMemoryPreferenceStore.InMemoryPreference("e_ink_mode", true, false)

        migrateEInkProfile(eInkProfilePref, legacyEInkModePref)

        eInkProfilePref.get() shouldBe EInkProfile.MONOCHROME
    }

    @Test
    fun `existing e ink profile is preserved`() {
        val eInkProfilePref = InMemoryPreferenceStore.InMemoryPreference(
            "e_ink_profile",
            EInkProfile.COLOR,
            EInkProfile.OFF,
        )
        val legacyEInkModePref = InMemoryPreferenceStore.InMemoryPreference("e_ink_mode", true, false)

        migrateEInkProfile(eInkProfilePref, legacyEInkModePref)

        eInkProfilePref.get() shouldBe EInkProfile.COLOR
    }

    @Test
    fun `MoveLatestToFeedMigrationTest returns empty when anime and novel keys are absent`() {
        val entries = mapLegacyFeedEntries(
            mangaFeedSources = emptySet(),
            animeFeedSources = emptySet(),
            novelFeedSources = emptySet(),
            mangaResolve = { true },
            animeResolve = { true },
            novelResolve = { true },
        )

        entries shouldContainExactly emptyList()
    }

    @Test
    fun `MoveLatestToFeedMigrationTest skips stale and non numeric ids`() {
        val entries = mapLegacyFeedEntries(
            mangaFeedSources = setOf("1", "bad", "2"),
            animeFeedSources = setOf("10"),
            novelFeedSources = setOf("20"),
            mangaResolve = { id -> id == 1L },
            animeResolve = { false },
            novelResolve = { id -> id == 20L },
        )

        entries.map { it.sourceType to it.source } shouldContainExactly listOf(
            SourceType.MANGA to 1L,
            SourceType.NOVEL to 20L,
        )
    }
}
