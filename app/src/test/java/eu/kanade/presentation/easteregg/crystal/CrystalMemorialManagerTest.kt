package eu.kanade.presentation.easteregg.crystal

import eu.kanade.domain.easteregg.crystal.CrystalAppearancePreferences
import eu.kanade.domain.easteregg.crystal.CrystalCosmetic
import eu.kanade.domain.easteregg.crystal.CrystalMemorialManager
import eu.kanade.domain.ui.UserProfilePreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.easteregg.crystal.CrystalEligibilityReason
import tachiyomi.domain.easteregg.crystal.CrystalMemorialIds
import tachiyomi.domain.easteregg.crystal.CrystalMemorialRepository
import tachiyomi.domain.easteregg.crystal.CrystalMemorialState

class CrystalMemorialManagerTest {
    private val strings = mutableMapOf<String, InMemoryPreference<String>>()
    private val booleans = mutableMapOf<String, InMemoryPreference<Boolean>>()
    private val store = mockk<PreferenceStore> {
        every { getString(any(), any()) } answers
            { strings.getOrPut(firstArg()) { InMemoryPreference(firstArg(), null, secondArg()) } }
        every { getBoolean(any(), any()) } answers
            { booleans.getOrPut(firstArg()) { InMemoryPreference(firstArg(), null, secondArg()) } }
    }
    private val profile = UserProfilePreferences(store)
    private val appearance = CrystalAppearancePreferences(store)
    private val saved =
        MutableStateFlow(
            CrystalMemorialState(
                eligibilityReason = CrystalEligibilityReason.RELEASE,
                eligibleAt = 1,
                grantedAt = 2,
                grantedXp = 999,
            ),
        )
    private val repo = mockk<CrystalMemorialRepository>(relaxed = true) {
        every { observeState() } returns saved
        coEvery { getState() } answers { saved.value }
        coEvery { debugReset() } answers
            { CrystalMemorialState(generation = saved.value.generation + 1).also { saved.value = it } }
        coEvery { makeEligible(any()) } answers
            { saved.value.copy(eligibilityReason = firstArg(), eligibleAt = 3).also { saved.value = it } }
    }

    @Test fun nonDebugCannotResetTheReceipt() = runTest {
        val manager = CrystalMemorialManager(repo, appearance, profile, false)
        var denied = false
        try {
            manager.resetAndPrepareDebug()
        } catch (_: IllegalStateException) {
            denied = true
        }
        assertTrue(denied)
        coVerify(exactly = 0) { repo.debugReset() }
    }

    @Test fun resetDisablesOnlyMemorialCosmeticsAndImmediatelyAllowsRestart() = runTest {
        profile.avatarFrameStyle().set("crystal_point")
        profile.profileTitle().set(CrystalMemorialIds.TITLE)
        profile.homeBadgeStyle().set(CrystalMemorialIds.BADGE)
        appearance.pawsEnabled().set(true)
        appearance.silkEnabled().set(true)
        store.getString("app_theme", "DEFAULT").set("NORD")
        val state = CrystalMemorialManager(repo, appearance, profile, true).resetAndPrepareDebug()
        assertTrue(state.isEligible)
        assertEquals(CrystalEligibilityReason.DEBUG, state.eligibilityReason)
        assertEquals("none", profile.avatarFrameStyle().get())
        assertEquals("none", profile.profileTitle().get())
        assertEquals("none", profile.homeBadgeStyle().get())
        assertFalse(appearance.pawsEnabled().get())
        assertFalse(appearance.silkEnabled().get())
        assertEquals("NORD", store.getString("app_theme", "DEFAULT").get())
    }

    @Test fun resetPreservesAnUnrelatedEquippedFrameTitleAndBadge() = runTest {
        profile.avatarFrameStyle().set("trinity_orbit")
        profile.profileTitle().set("title_other")
        profile.homeBadgeStyle().set("other")
        CrystalMemorialManager(repo, appearance, profile, true).resetAndPrepareDebug()
        assertEquals("trinity_orbit", profile.avatarFrameStyle().get())
        assertEquals("title_other", profile.profileTitle().get())
        assertEquals("other", profile.homeBadgeStyle().get())
    }

    @Test fun ownerCanEquipButRemovingDoesNotClearAnotherFrame() = runTest {
        val manager = CrystalMemorialManager(repo, appearance, profile, true)
        assertTrue(manager.setEquipped(CrystalCosmetic.FRAME, true, "crystal_thread"))
        assertEquals("crystal_thread", profile.avatarFrameStyle().get())
        profile.avatarFrameStyle().set("other")
        assertTrue(manager.setEquipped(CrystalCosmetic.FRAME, false))
        assertEquals("other", profile.avatarFrameStyle().get())
    }

    @Test fun catalogueHasSixRewardsAndVariantsAreNotExtraRewards() {
        assertEquals(CrystalMemorialIds.REWARDS, CrystalItem.entries.map { it.unlockId }.toSet())
        assertEquals(6, CrystalItem.entries.size)
        assertEquals(3, CrystalFrameVariant.entries.size)
        assertEquals(3, CrystalPalette.entries.size)
    }

    @Test fun silkAndPawsNeverReplaceGlobalThemeOrSpecialBackground() = runTest {
        store.getString("app_theme", "DEFAULT").set("NORD")
        store.getString("special_background_style", "none").set("deep_space_archive")
        val manager = CrystalMemorialManager(repo, appearance, profile, true)
        assertTrue(manager.setEquipped(CrystalCosmetic.SILK, true, "pearl"))
        assertTrue(manager.setEquipped(CrystalCosmetic.PAWS, true))
        assertTrue(appearance.pawsEnabled().get())
        assertEquals("pearl", appearance.silkVariant().get())
        assertEquals("NORD", store.getString("app_theme", "DEFAULT").get())
        assertEquals("deep_space_archive", store.getString("special_background_style", "none").get())
    }

    @Test fun invalidVariantDoesNotOverwriteCurrentFrame() = runTest {
        profile.avatarFrameStyle().set("crystal_point")
        assertFalse(
            CrystalMemorialManager(repo, appearance, profile, true).setEquipped(CrystalCosmetic.FRAME, true, "unknown"),
        )
        assertEquals("crystal_point", profile.avatarFrameStyle().get())
    }

    @Test fun pawsRemainIndependentAcrossStandardInkAndOtherBackgrounds() = runTest {
        val manager = CrystalMemorialManager(repo, appearance, profile, true)
        for (base in listOf("none", "ink_water", "deep_space_archive")) {
            for (baseAnimated in listOf(false, true)) {
                store.getString("special_background_style", "none").set(base)
                store.getBoolean("animated_aurora_background", true).set(baseAnimated)
                assertTrue(manager.setEquipped(CrystalCosmetic.PAWS, true))
                assertTrue(appearance.pawsEnabled().get())
                assertEquals(base, store.getString("special_background_style", "none").get())
                assertEquals(baseAnimated, store.getBoolean("animated_aurora_background", true).get())
                assertTrue(manager.setEquipped(CrystalCosmetic.PAWS, false))
                assertFalse(appearance.pawsEnabled().get())
                assertEquals(base, store.getString("special_background_style", "none").get())
            }
        }
    }

    @Test fun replayNeverInvokesTheGrantOperation() = runTest {
        val manager = CrystalMemorialManager(repo, appearance, profile, true)
        manager.completeSession(saved.value.generation, replay = true)
        coVerify(exactly = 0) { repo.completeMemorial(any()) }
        coVerify(exactly = 0) { repo.acknowledgeCeremony() }
    }

    @Test fun ownershipIsRequiredEvenWhenPreferencesOrDebugBypassSayOtherwise() = runTest {
        saved.value = CrystalMemorialState()
        assertFalse(CrystalMemorialManager(repo, appearance, profile, true).setEquipped(CrystalCosmetic.PAWS, true))
        assertFalse(appearance.pawsEnabled().get())
    }
}
