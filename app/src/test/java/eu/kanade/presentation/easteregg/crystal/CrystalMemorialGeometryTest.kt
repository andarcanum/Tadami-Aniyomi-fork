package eu.kanade.presentation.easteregg.crystal

import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs

class CrystalMemorialGeometryTest {
    @Test
    fun preservesApprovedGeometryAndTimeline() {
        check(CrystalSceneData.face.size == 164)
        check(CrystalSceneData.roles.size == 82)
        check(CrystalSceneData.vertices.size == 42)
        check(CrystalSceneData.facets.size == 24)
        check(CrystalSceneData.groups.all { g -> g.indices.all { it in 0..81 } })
        check(CrystalSceneData.facets.all { f -> f.size == 3 && f.all { it in 0..13 } })
        check(CrystalTimeline.TOTAL_MS == 20600f)
    }

    @Test
    fun noseHasAnatomicalDepthInsteadOfFlatProjection() {
        val nose = CrystalSceneData.roles.indexOf("nose")
        check(abs(CrystalSceneGeometry.nodeDepth(nose) - 0.44f) < 0.00001f) {
            "Nose must retain the approved +0.44 Z depth"
        }
        val p = CrystalSceneGeometry.projectFace(nose, 404f, 860f)
        check(p.perspective > 1f)
        for (i in 0..81) {
            val q = CrystalSceneGeometry.projectFace(i, 360f, 640f, 0.25f, -0.33f)
            check(q.x.isFinite() && q.y.isFinite() && q.perspective.isFinite())
            check(q.x in 0f..360f && q.y in 0f..640f)
        }
    }

    @Test
    fun rotationPreservesLengthAndApprovedDirection() {
        val p = CrystalSceneGeometry.rotate(CrystalPoint3(1f, 0f, 0f), 0f, (PI / 2).toFloat())
        check(abs(p.x) < 0.00001f && abs(p.z + 1f) < 0.00001f)
    }

    @Test
    fun usesQuinticFrontierEaseAndClampsEndpoints() {
        check(CrystalTimeline.smooth(-1f) == 0f)
        check(CrystalTimeline.smooth(2f) == 1f)
        check(abs(CrystalTimeline.smooth(0.25f) - 0.103515625f) < 0.00001f)
    }

    @Test
    fun farewellFinishesBeforeTheSceneFinaleWithoutLingeringPartialOpacity() {
        check(CrystalTimeline.farewellOpacity(13_280f, false) == 0f)
        check(CrystalTimeline.farewellOpacity(14_715f, false) == 1f) {
            "The farewell must be fully visible once the last letter has completed"
        }
        check(CrystalTimeline.farewellOpacity(350f, true) == 0f)
        check(CrystalTimeline.farewellOpacity(775f, true) == 1f)
    }

    @Test
    fun farewellLettersRevealInOrderAndEveryLastLetterCompletesBeforeTheName() {
        for (reduced in listOf(false, true)) {
            val start = if (reduced) 350f else 13_280f
            val stagger = if (reduced) 45f else 145f
            val duration = if (reduced) 110f else 420f
            for (index in CrystalTimeline.FAREWELL.indices) {
                val birth = start + index * stagger
                check(CrystalTimeline.farewellLetterOpacity(birth, reduced, index) == 0f)
                check(
                    abs(CrystalTimeline.farewellLetterOpacity(birth + duration / 2, reduced, index) - 0.5f) < 0.00001f,
                )
                check(CrystalTimeline.farewellLetterOpacity(birth + duration, reduced, index) == 1f)
                if (index > 0) {
                    check(CrystalTimeline.farewellLetterOpacity(birth, reduced, index - 1) > 0f)
                }
            }
            val completed = start + 7 * stagger + duration
            check(CrystalTimeline.farewellNameOpacity(completed, reduced) == 0f)
            check(CrystalTimeline.farewellNameOpacity(completed + if (reduced) 180f else 550f, reduced) == 1f)
            check(CrystalTimeline.farewellLetterOpacity(CrystalTimeline.total(reduced), reduced, 7) == 1f)
        }
    }

    @Test
    fun holdRequiresAFullSecondAndClampsCancelledOrOvershotTime() {
        check(CrystalTimeline.holdProgress(-200f) == 0f)
        check(CrystalTimeline.holdProgress(0f) == 0f)
        check(CrystalTimeline.holdProgress(350f) == 0.35f)
        check(CrystalTimeline.holdProgress(999f) < 1f)
        check(CrystalTimeline.holdProgress(1000f) == 1f)
        check(CrystalTimeline.holdProgress(1500f) == 1f)
    }

    @Test
    fun interruptedKeyHoldsCannotCommitThePreviousAttemptEvenWhenEventsCoalesce() {
        val first = CrystalHoldInput().keyDown()
        val repeated = first.keyDown()
        check(repeated == first) { "Key-repeat must not restart an uninterrupted hold" }
        val second = repeated.keyUp().keyDown() // No frame/recomposition between these events.
        check(second.pressed)
        check(second.generation != first.generation)
        check(!second.canCommit(first.generation)) { "Two short holds must not add up to a full hold" }
        check(second.canCommit(second.generation))
        check(!second.keyUp().canCommit(second.generation))
        check(!second.cancel().canCommit(second.generation))
    }

    @Test
    fun interruptedTouchHoldsResetAndOldCleanupCannotReleaseANewContact() {
        val first = CrystalHoldInput().touchDown()
        val firstToken = requireNotNull(first.touchToken)
        val second = first.touchUp(firstToken).touchDown()
        check(second.pressed && second.generation != first.generation)
        check(!second.canCommit(first.generation))
        check(second.touchUp(firstToken) == second)
        val cancelled = second.cancel()
        check(!cancelled.pressed && !cancelled.canCommit(second.generation))
        val resumed = cancelled.touchDown()
        check(!resumed.canCommit(second.generation))
    }

    @Test
    fun fittedArtworkKeepsAllStarsInsideCompactAndLandscapePanels() {
        for ((width, height) in listOf(316f to 160f, 156f to 180f, 280f to 150f)) {
            for (index in 0..81) {
                val node = CrystalSceneGeometry.projectFace(index, width, height, 0.25f, -0.33f, fittedArtwork = true)
                check(node.x in 0f..width && node.y in 0f..height)
                check(node.perspective.isFinite())
            }
        }
    }

    @Test
    fun ceremonyBlendLiftsConstellationSmoothlyWithinViewportBounds() {
        val nose = CrystalSceneData.roles.indexOf("nose")
        val resting = CrystalSceneGeometry.projectFace(nose, 390f, 844f, ceremonyBlend = 0f)
        val lifted = CrystalSceneGeometry.projectFace(nose, 390f, 844f, ceremonyBlend = 1f)
        check(lifted.y < resting.y) { "Constellation should lift upward when 1D ceremony opens" }
        for (index in 0..81) {
            val node = CrystalSceneGeometry.projectFace(index, 390f, 844f, 0.2f, -0.25f, ceremonyBlend = 1f)
            check(node.x in 0f..390f && node.y in 0f..844f && node.perspective.isFinite())
        }
    }

    @Test
    fun backgroundAndFirstResumedFrameNeverAdvanceTheScene() {
        val c = CrystalFrameClock()
        c.advance(0L, true)
        c.advance(1_000_000_000L, true)
        c.advance(8_000_000_000L, false)
        c.advance(30_000_000_000L, true)
        c.advance(31_000_000_000L, true)
        check(c.elapsedMs == 2000f) { "Background must not complete memorial: elapsed=" + c.elapsedMs }
        c.seek(0f)
        c.advance(100_000_000_000L, true)
        check(c.elapsedMs == 0f)
    }
}
