package mihon.core.migration.migrations

import eu.kanade.domain.ui.model.EInkProfile
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

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
}
