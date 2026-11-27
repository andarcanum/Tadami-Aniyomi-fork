package eu.kanade.tachiyomi.ui.reader.viewer

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class ReaderRenderEffectsTest {

    @AfterEach
    fun tearDown() {
        ReaderRenderEffects.clear()
    }

    @Test
    fun `fling pause keeps levels effect active when full chain includes heavy shaders`() {
        val full = Any()
        val levels = Any()
        ReaderRenderEffects.setEffectsForTest(configured = full, fling = levels)

        ReaderRenderEffects.hasDistinctFlingEffect() shouldBe true
        ReaderRenderEffects.currentEffect shouldBeSameInstanceAs full

        ReaderRenderEffects.setFlingPausedForTest(true)
        ReaderRenderEffects.currentEffect shouldBeSameInstanceAs levels

        ReaderRenderEffects.setFlingPausedForTest(false)
        ReaderRenderEffects.currentEffect shouldBeSameInstanceAs full
    }

    @Test
    fun `levels only configuration has no distinct fling effect and stays active on fling`() {
        val levels = Any()
        ReaderRenderEffects.setEffectsForTest(configured = levels, fling = levels)

        ReaderRenderEffects.hasDistinctFlingEffect() shouldBe false
        ReaderRenderEffects.setFlingPausedForTest(true)
        ReaderRenderEffects.currentEffect shouldBeSameInstanceAs levels
    }

    @Test
    fun `heavy shader only configuration pauses to null on fling and restores afterwards`() {
        val heavy = Any()
        ReaderRenderEffects.setEffectsForTest(configured = heavy, fling = null)

        ReaderRenderEffects.hasDistinctFlingEffect() shouldBe true
        ReaderRenderEffects.setFlingPausedForTest(true)
        ReaderRenderEffects.currentEffect.shouldBeNull()

        ReaderRenderEffects.setFlingPausedForTest(false)
        ReaderRenderEffects.currentEffect shouldBeSameInstanceAs heavy
    }

    @Test
    fun `clear resets configured fling and pause state`() {
        val full = Any()
        val levels = Any()
        ReaderRenderEffects.setEffectsForTest(configured = full, fling = levels)
        ReaderRenderEffects.setFlingPausedForTest(true)

        ReaderRenderEffects.clear()

        ReaderRenderEffects.hasDistinctFlingEffect() shouldBe false
        ReaderRenderEffects.currentEffect.shouldBeNull()
    }
}
