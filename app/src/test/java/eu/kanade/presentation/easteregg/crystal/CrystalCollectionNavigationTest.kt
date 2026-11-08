package eu.kanade.presentation.easteregg.crystal

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.presentation.more.settings.screen.SettingsTreasuryScreen
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.easteregg.crystal.CrystalEligibilityReason
import tachiyomi.domain.easteregg.crystal.CrystalMemorialState

class CrystalCollectionNavigationTest {
    private object Home : Screen {
        @Composable override fun Content() = Unit
    }
    private object Memorial : Screen {
        @Composable override fun Content() = Unit
    }

    @Test fun savedUnacknowledgedReceiptResumesCeremonyInsteadOfSkippingIt() {
        val receipt = CrystalMemorialState(CrystalEligibilityReason.TITLE, 1, grantedAt = 2, grantedXp = 999)
        assertFalse(receipt.replayFromCollection())
        assertTrue(receipt.copy(ceremonyAcknowledgedAt = 3).replayFromCollection())
        assertFalse(CrystalMemorialState(CrystalEligibilityReason.TITLE, 1).replayFromCollection())
    }

    @Test fun existingCollectionIsReusedAfterReplay() {
        assertEquals(
            CrystalCollectionDestination.EXISTING_COLLECTION,
            resolveCrystalCollectionDestination(
                listOf(Home, SettingsTreasuryScreen, CrystalCollectionScreen, Memorial),
            ),
        )
    }

    @Test fun mainTreasuryRemainsTheParentWhenCollectionIsFirstOpened() {
        assertEquals(
            CrystalCollectionDestination.EXISTING_TREASURY,
            resolveCrystalCollectionDestination(listOf(Home, SettingsTreasuryScreen, Memorial)),
        )
    }

    @Test fun automaticMemorialCreatesTheNormalTreasuryHierarchy() {
        assertEquals(
            CrystalCollectionDestination.NEW_TREASURY,
            resolveCrystalCollectionDestination(listOf(Home, Memorial)),
        )
    }
}
