package eu.kanade.presentation.easteregg.crystal

import org.junit.jupiter.api.Test
import kotlin.math.abs

class CrystalLivingCornerMotionTest {
    @Test
    fun initialFrameMatchesFixedV4GeometryAndRestingHeadScale() {
        val engine = CrystalLivingEngine()
        val frame = engine.snapshot()
        check(
            frame.toys == listOf(
                CrystalLivingToyState(CrystalLivingToyKind.YARN, 542f, 336f, 0f, 0f, 0f, 0f, 25f),
                CrystalLivingToyState(CrystalLivingToyKind.BALL, 251f, 353f, 0f, 0f, 0f, 0f, 17f),
            ),
        )
        check(frame.parts.keys == CrystalLivingPart.entries.toSet())
        check(frame.parts.getValue(CrystalLivingPart.HEAD).scaleX == 1.06f)
        check(frame.parts.getValue(CrystalLivingPart.HEAD).scaleY == 1.06f)
        check(frame.eyeClosure == 0f && frame.pupilRadius == 5.2f)
        check(!frame.playing && !frame.dragging && frame.contactCount == 0)
        check(frame == engine.snapshot())
        check(engine.hitToy(542f, 336f) == CrystalLivingToyKind.YARN)
        check(engine.hitToy(285f, 353f) == null)
        check(engine.hitToy(285f, 353f, 36f) == CrystalLivingToyKind.BALL)
    }

    @Test
    fun offeredToyFollows620MillisecondCubicApproachGoldenWithoutPrematureImpulse() {
        val engine = CrystalLivingEngine()
        check(engine.play())
        check(!engine.play() && !engine.pet())
        advanceFor(engine, 0.31f)
        val yarn = toy(engine, CrystalLivingToyKind.YARN)
        checkClose(yarn.x, 458.875f, 0.001f)
        checkClose(yarn.y, 338.625f, 0.001f)
        check(engine.snapshot().contactCount == 0)
        advanceFor(engine, 0.89f)
        check(engine.snapshot().contactCount == 0) // Contact window starts after approach + 620ms.
        advanceFor(engine, 2f)
        check(engine.snapshot().contactCount == 1)
        check(!engine.snapshot().playing) // Approach + 2450ms recovery have elapsed.
        advanceFor(engine, 1f)
        check(engine.snapshot().contactCount == 1)
    }

    @Test
    fun capturedToyDoesNotCountAsContactAndSuccessfulContactRetriesInsideTheWindow() {
        val engine = CrystalLivingEngine()
        check(engine.play())
        advanceFor(engine, 1.2f)
        val yarn = toy(engine, CrystalLivingToyKind.YARN)
        check(engine.beginDrag(yarn.kind, yarn.x, yarn.y))
        engine.dragTo(447f, 339f, 0.016f)
        advanceFor(engine, 0.3f)
        check(engine.snapshot().playing && engine.snapshot().dragging)
        check(engine.snapshot().contactCount == 0)
        engine.endDrag(cancelled = true)
        advanceFor(engine, 0.12f)
        check(engine.snapshot().contactCount == 1)
        advanceFor(engine, 2f)
        check(engine.snapshot().contactCount == 1)
    }

    @Test
    fun missedContactDoesNotFireAfterTheRetryWindow() {
        val engine = CrystalLivingEngine()
        check(engine.play())
        advanceFor(engine, 1.2f)
        val yarn = toy(engine, CrystalLivingToyKind.YARN)
        check(engine.beginDrag(yarn.kind, yarn.x, yarn.y))
        engine.dragTo(447f, 339f, 0.016f)
        advanceFor(engine, 0.8f)
        engine.endDrag(cancelled = true)
        advanceFor(engine, 1.2f)
        check(engine.snapshot().contactCount == 0)
        check(!engine.snapshot().playing)
    }

    @Test
    fun gazeAndSecondarySpringsMatchIndependentFixedV4Golden() {
        val engine = CrystalLivingEngine()
        engine.lookAt(0.6f, -0.2f)
        advanceFor(engine, 0.25f)
        val frame = engine.snapshot()
        checkClose(frame.gazeX, 0.495975257f)
        checkClose(frame.gazeY, -0.165325086f)
        val head = frame.parts.getValue(CrystalLivingPart.HEAD)
        checkClose(head.translationX, 1.289535667f)
        checkClose(head.translationY, -0.770940319f)
        checkClose(head.rotationDegrees, 0.650614862f)
        checkClose(frame.parts.getValue(CrystalLivingPart.TAIL).rotationDegrees, 0.867785489f)
        checkClose(frame.parts.getValue(CrystalLivingPart.CRYSTAL).rotationDegrees, 0.298291990f)
        check(frame.pupilRadius == 5.15f && frame.eyeClosure == 0f)
    }

    @Test
    fun bedForceMatchesIndependentFixedSubstepJavaScriptGoldenAndDoesNotRunAtZeroDt() {
        val engine = ballOnBed()
        val before = engine.snapshot()
        check(engine.advance(0f) == before)
        advanceFor(engine, 0.25f)
        val ball = toy(engine, CrystalLivingToyKind.BALL)
        checkClose(ball.x, 415.737633f, 0.001f)
        checkClose(ball.vx, 53.593417f, 0.001f)
        checkClose(ball.y, 338f)
        checkClose(ball.vy, 0f)
        checkClose(ball.angle - before.toys.first { it.kind == CrystalLivingToyKind.BALL }.angle, 26.078454f, 0.001f)
    }

    @Test
    fun physicsSpringsAndContactAreRefreshRateIndependentAt30And60And120Hz() {
        val samples = listOf(30, 60, 120).map { hz ->
            CrystalLivingEngine(42).also { engine ->
                check(engine.play())
                repeat(hz * 4) { engine.advance(1f / hz) }
            }.snapshot()
        }
        for (sample in samples) {
            check(sample.contactCount == 1 && !sample.playing)
            for ((index, value) in sample.toys.withIndex()) {
                val expected = samples.first().toys[index]
                checkClose(value.x, expected.x, 0.002f)
                checkClose(value.y, expected.y, 0.002f)
                checkClose(value.vx, expected.vx, 0.002f)
            }
            for (part in CrystalLivingPart.entries) {
                val value = sample.parts.getValue(part)
                val expected = samples.first().parts.getValue(part)
                checkClose(value.translationX, expected.translationX, 0.002f)
                checkClose(value.translationY, expected.translationY, 0.002f)
                checkClose(value.rotationDegrees, expected.rotationDegrees, 0.002f)
            }
        }
    }

    @Test
    fun draggingBallNearCushionTriggersUnguidedLeftPawPlayAndOneContact() {
        val engine = CrystalLivingEngine()
        check(engine.beginDrag(CrystalLivingToyKind.BALL, 251f, 353f))
        engine.dragTo(387f, 336f, 0.016f)
        advanceFor(engine, 0.1f)
        check(engine.snapshot().playing && engine.snapshot().contactCount == 0)
        engine.endDrag(cancelled = true)
        advanceFor(engine, 3f)
        check(engine.snapshot().contactCount == 1 && !engine.snapshot().playing)
        check(toy(engine, CrystalLivingToyKind.BALL).x < 387f)
        check(toy(engine, CrystalLivingToyKind.YARN).x == 542f)
    }

    @Test
    fun dragUsesPointerDeltasClampsBoundsAndLimitsReleaseVelocity() {
        val engine = CrystalLivingEngine()
        check(engine.beginDrag(CrystalLivingToyKind.BALL, 255f, 355f))
        check(!engine.beginDrag(CrystalLivingToyKind.YARN, 542f, 336f))
        check(!engine.play())
        engine.dragTo(275f, 365f, 0f)
        val dragged = toy(engine, CrystalLivingToyKind.BALL)
        checkClose(dragged.x, 271f)
        checkClose(dragged.y, 363f)
        check(dragged.vx == 280f && dragged.vy == 190f)
        engine.dragTo(1000f, -1000f, 0.016f)
        val clamped = toy(engine, CrystalLivingToyKind.BALL)
        check(clamped.x == 601f && clamped.y == 321f)
        check(abs(clamped.vx) <= 280f && abs(clamped.vy) <= 190f)
        engine.endDrag(cancelled = true)
        check(!engine.snapshot().dragging)
        check(toy(engine, CrystalLivingToyKind.BALL).vx == 0f)
        check(toy(engine, CrystalLivingToyKind.BALL).vy == 0f)
    }

    @Test
    fun pauseCancelsCapturedVelocityWithoutResettingToysOrActiveTime() {
        val engine = CrystalLivingEngine()
        advanceFor(engine, 1f)
        check(engine.beginDrag(CrystalLivingToyKind.YARN, 542f, 336f))
        engine.dragTo(590f, 350f, 0.02f)
        val before = engine.snapshot()
        engine.pause()
        val paused = engine.snapshot()
        check(paused.elapsedSeconds == before.elapsedSeconds)
        check(paused.toys.map { it.x to it.y } == before.toys.map { it.x to it.y })
        check(!paused.dragging && paused.toys.all { it.vx == 0f && it.vy == 0f })
        engine.dragTo(500f, 350f, 0.02f)
        check(engine.snapshot() == paused)
        checkClose(engine.advance(3600f).elapsedSeconds, paused.elapsedSeconds + 0.05f)
    }

    @Test
    fun lifecyclePausePreservesFreeMomentumAndGuidedApproachUntilActiveTimeResumes() {
        val moving = CrystalLivingEngine()
        check(moving.beginDrag(CrystalLivingToyKind.BALL, 251f, 353f))
        moving.dragTo(600f, 360f, 0.016f)
        moving.endDrag()
        val beforePause = moving.snapshot()
        check(beforePause.toys.first { it.kind == CrystalLivingToyKind.BALL }.vx != 0f)
        moving.pause()
        check(moving.snapshot() == beforePause)
        moving.setReduced(true)
        moving.setReduced(false)
        check(moving.snapshot().toys.all { it.vx == 0f && it.vy == 0f })

        val guided = CrystalLivingEngine()
        check(guided.play())
        advanceFor(guided, 0.25f)
        val offered = guided.snapshot()
        guided.pause()
        check(guided.snapshot() == offered)
        advanceFor(guided, 0.06f)
        checkClose(toy(guided, CrystalLivingToyKind.YARN).x, 458.875f, 0.001f)
        checkClose(toy(guided, CrystalLivingToyKind.YARN).y, 338.625f, 0.001f)
        check(guided.snapshot().playing && guided.snapshot().contactCount == 0)
    }

    @Test
    fun reducedMotionKeepsPositionButRestsEyesAndBlocksInteractionsAndCatchup() {
        val engine = CrystalLivingEngine()
        check(engine.pet())
        advanceFor(engine, 0.3f)
        check(engine.snapshot().happiness > 0.8f && engine.snapshot().eyeClosure > 0.7f)
        val lively = engine.snapshot()
        val resting = engine.restingSnapshot()
        check(engine.snapshot() == lively) // Static preview is a nonmutating view.
        check(resting.eyeClosure == 0f && resting.happiness == 0f && resting.pupilRadius == 5.2f)
        engine.setReduced(true)
        val reduced = engine.snapshot()
        check(reduced.toys.map { it.x to it.y } == lively.toys.map { it.x to it.y })
        check(engine.advance(20f) == reduced)
        check(!engine.play() && !engine.pet() && !engine.activateProp(CrystalLivingPart.CRYSTAL))
        check(!engine.beginDrag(CrystalLivingToyKind.YARN, 542f, 336f))
        engine.setReduced(false)
        checkClose(engine.advance(0.01f).elapsedSeconds, reduced.elapsedSeconds + 0.01f)
        engine.reset()
        check(engine.snapshot() == CrystalLivingEngine().snapshot())
    }

    @Test
    fun invalidTimingOrCoordinatesCannotPoisonThePureEngine() {
        val engine = CrystalLivingEngine()
        val initial = engine.snapshot()
        for (dt in listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            check(engine.advance(dt) == initial)
        }
        engine.lookAt(Float.NaN, 1f)
        check(engine.hitToy(Float.NaN, 336f) == null)
        check(!engine.beginDrag(CrystalLivingToyKind.YARN, Float.POSITIVE_INFINITY, 0f))
        check(engine.beginDrag(CrystalLivingToyKind.YARN, 542f, 336f))
        val captured = engine.snapshot()
        engine.dragTo(Float.NaN, 336f, 0.01f)
        check(engine.snapshot() == captured)
        engine.endDrag(cancelled = true)
        advanceFor(engine, 5f)
        check(engine.snapshot().toys.all { it.x.isFinite() && it.vx.isFinite() && it.angle.isFinite() })
    }

    @Test
    fun propsAndAmbientSchedulesAreDeterministicActiveTimeOnly() {
        val first = CrystalLivingEngine(7)
        val second = CrystalLivingEngine(7)
        for (part in listOf(CrystalLivingPart.CRYSTAL, CrystalLivingPart.FEATHER, CrystalLivingPart.POM)) {
            check(first.activateProp(part) && second.activateProp(part))
        }
        check(!first.activateProp(CrystalLivingPart.HEAD))
        advanceFor(first, 0.1f)
        advanceFor(second, 0.1f)
        check(first.snapshot() == second.snapshot())
        for (part in listOf(CrystalLivingPart.CRYSTAL, CrystalLivingPart.FEATHER, CrystalLivingPart.POM)) {
            check(abs(first.snapshot().parts.getValue(part).rotationDegrees) > 0.5f)
        }
        advanceFor(first, 7.9f)
        second.pause()
        checkClose(second.snapshot().elapsedSeconds, 0.1f)
        // Explicit reset restarts the seeded ambient schedule; pause itself does not advance time.
        first.reset()
        second.reset()
        advanceFor(first, 8f)
        advanceFor(second, 8f)
        check(first.snapshot() == second.snapshot())
    }

    private fun ballOnBed(): CrystalLivingEngine = CrystalLivingEngine().also {
        check(it.beginDrag(CrystalLivingToyKind.BALL, 251f, 353f))
        it.dragTo(408f, 338f, 0.016f)
        it.endDrag(cancelled = true)
        // Drag rotation is intentionally retained; explicit reset is the only positional reset.
    }

    private fun toy(engine: CrystalLivingEngine, kind: CrystalLivingToyKind) =
        engine.snapshot().toys.first { it.kind == kind }

    private fun advanceFor(engine: CrystalLivingEngine, seconds: Float) {
        val steps = (seconds * 120f).toInt()
        repeat(steps) { engine.advance(1f / 120f) }
        val remainder = seconds - steps / 120f
        if (remainder > 0.000001f) engine.advance(remainder)
    }

    private fun checkClose(actual: Float, expected: Float, tolerance: Float = 0.0001f) {
        check(abs(actual - expected) <= tolerance) { "Expected $expected, got $actual" }
    }
}
