package tachiyomi.domain.easteregg.crystal

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CrystalMemorialReleasePolicyTest {

    private val releaseCodes = setOf(100, 105)
    private val eligible = CrystalMemorialState(
        eligibilityReason = CrystalEligibilityReason.RELEASE,
        eligibleAt = 1L,
    )

    @Test
    fun `exact configured release becomes eligible without title progress`() {
        for (version in releaseCodes) {
            decision(version).shouldMakeReleaseEligible shouldBe true
            decision(version).mayUseTitleUnlock shouldBe false
            decision(version).shouldOfferPending shouldBe false
        }
    }

    @Test
    fun `skipping release never grants eligibility on a later version`() {
        for (version in listOf(101, 106, 1000)) {
            decision(version).shouldMakeReleaseEligible shouldBe false
            decision(version).mayUseTitleUnlock shouldBe true
            decision(version).shouldOfferPending shouldBe false
        }
    }

    @Test
    fun `versions before first configured release have no automatic route`() {
        decision(99) shouldBe CrystalReleaseDecision(false, false, false)
    }

    @Test
    fun `persisted eligibility offers on release and later without becoming eligible again`() {
        for (version in listOf(100, 101, 106)) {
            decision(version, eligible) shouldBe CrystalReleaseDecision(false, false, true)
        }
    }

    @Test
    fun `title and restored eligibility can offer later`() {
        for (reason in listOf(CrystalEligibilityReason.TITLE, CrystalEligibilityReason.RESTORED)) {
            decision(106, eligible.copy(eligibilityReason = reason)).shouldOfferPending shouldBe true
        }
    }

    @Test
    fun `consumed automatic presentation never repeats but eligibility remains`() {
        val consumed = eligible.copy(automaticPresentationConsumedAt = 2L)
        decision(106, consumed) shouldBe CrystalReleaseDecision(false, false, false)
        consumed.isEligible shouldBe true
    }

    @Test
    fun `already granted state never automatically offers even with pending ceremony`() {
        val granted = eligible.copy(grantedAt = 2L, grantedXp = CrystalMemorialIds.XP)
        granted.hasPendingCeremony shouldBe true
        for (version in listOf(100, 106)) {
            decision(version, granted) shouldBe CrystalReleaseDecision(false, false, false)
        }
    }

    @Test
    fun `a partial grant receipt also fails closed`() {
        decision(106, eligible.copy(grantedAt = 2L)) shouldBe CrystalReleaseDecision(false, false, false)
    }

    @Test
    fun `empty release configuration disables all production routes`() {
        for (state in listOf(CrystalMemorialState(), eligible)) {
            CrystalReleasePolicy.evaluate(100, true, emptySet(), state) shouldBe
                CrystalReleaseDecision(false, false, false)
        }
    }

    @Test
    fun `preview benchmark and debug builds cannot trigger production routes`() {
        for (version in listOf(100, 106)) {
            for (state in listOf(CrystalMemorialState(), eligible)) {
                CrystalReleasePolicy.evaluate(version, false, releaseCodes, state) shouldBe
                    CrystalReleaseDecision(false, false, false)
            }
        }
    }

    @Test
    fun `debug eligibility is not a production automatic offer`() {
        decision(106, eligible.copy(eligibilityReason = CrystalEligibilityReason.DEBUG)) shouldBe
            CrystalReleaseDecision(false, false, false)
    }

    @Test
    fun `incomplete eligibility is not offered`() {
        decision(106, eligible.copy(eligibleAt = null)).shouldOfferPending shouldBe false
        decision(106, eligible.copy(eligibilityReason = null)).shouldOfferPending shouldBe false
    }

    private fun decision(version: Int, state: CrystalMemorialState = CrystalMemorialState()) =
        CrystalReleasePolicy.evaluate(version, true, releaseCodes, state)
}
