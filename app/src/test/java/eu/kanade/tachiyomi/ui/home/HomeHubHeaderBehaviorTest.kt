package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.achievement.model.ActivityType
import tachiyomi.domain.achievement.model.DayActivity
import java.time.LocalDate

class HomeHubHeaderBehaviorTest {

    @Test
    fun `resolveHomeHubScrollDirectionFromDelta returns down for negative delta`() {
        resolveHomeHubScrollDirectionFromDelta(deltaY = -5f) shouldBe HomeHubScrollDirection.Down
    }

    @Test
    fun `resolveHomeHubScrollDirectionFromDelta returns up for positive delta`() {
        resolveHomeHubScrollDirectionFromDelta(deltaY = 6f) shouldBe HomeHubScrollDirection.Up
    }

    @Test
    fun `resolveHomeHubScrollDirectionFromDelta returns idle for small delta`() {
        resolveHomeHubScrollDirectionFromDelta(deltaY = 0.2f) shouldBe HomeHubScrollDirection.Idle
    }

    @Test
    fun `resolveHomeHubScrollDirection returns down when offset increases`() {
        resolveHomeHubScrollDirection(
            previous = HomeHubScrollSnapshot(index = 0, offset = 10),
            current = HomeHubScrollSnapshot(index = 0, offset = 20),
        ) shouldBe HomeHubScrollDirection.Down
    }

    @Test
    fun `resolveHomeHubScrollDirection returns up when offset decreases`() {
        resolveHomeHubScrollDirection(
            previous = HomeHubScrollSnapshot(index = 0, offset = 20),
            current = HomeHubScrollSnapshot(index = 0, offset = 10),
        ) shouldBe HomeHubScrollDirection.Up
    }

    @Test
    fun `resolveHomeHubScrollDirection returns down when index increases`() {
        resolveHomeHubScrollDirection(
            previous = HomeHubScrollSnapshot(index = 0, offset = 50),
            current = HomeHubScrollSnapshot(index = 1, offset = 0),
        ) shouldBe HomeHubScrollDirection.Down
    }

    @Test
    fun `resolveHomeHubHeaderOffset increases offset when scrolling down`() {
        resolveHomeHubHeaderOffset(
            currentOffsetPx = 10f,
            deltaY = -12f,
            maxOffsetPx = 80f,
            isAtTop = false,
        ) shouldBe 22f
    }

    @Test
    fun `resolveHomeHubHeaderOffset decreases offset when scrolling up`() {
        resolveHomeHubHeaderOffset(
            currentOffsetPx = 30f,
            deltaY = 8f,
            maxOffsetPx = 80f,
            isAtTop = false,
        ) shouldBe 22f
    }

    @Test
    fun `resolveHomeHubHeaderOffset clamps result within range`() {
        resolveHomeHubHeaderOffset(
            currentOffsetPx = 70f,
            deltaY = -30f,
            maxOffsetPx = 80f,
            isAtTop = false,
        ) shouldBe 80f
    }

    @Test
    fun `resolveHomeHubHeaderOffset resets to zero at top`() {
        resolveHomeHubHeaderOffset(
            currentOffsetPx = 50f,
            deltaY = -20f,
            maxOffsetPx = 80f,
            isAtTop = true,
        ) shouldBe 0f
    }

    @Test
    fun `resolveHomeHubHeaderVisibility keeps header visible at top`() {
        resolveHomeHubHeaderVisibility(
            currentlyVisible = false,
            direction = HomeHubScrollDirection.Down,
            isAtTop = true,
        ) shouldBe true
    }

    @Test
    fun `resolveHomeHubHeaderVisibility hides header on downward scroll`() {
        resolveHomeHubHeaderVisibility(
            currentlyVisible = true,
            direction = HomeHubScrollDirection.Down,
            isAtTop = false,
        ) shouldBe false
    }

    @Test
    fun `resolveHomeHubHeaderVisibility shows header on upward scroll`() {
        resolveHomeHubHeaderVisibility(
            currentlyVisible = false,
            direction = HomeHubScrollDirection.Up,
            isAtTop = false,
        ) shouldBe true
    }

    @Test
    fun `resolveHomeHubHeaderVisibility preserves state when scroll idle`() {
        resolveHomeHubHeaderVisibility(
            currentlyVisible = false,
            direction = HomeHubScrollDirection.Idle,
            isAtTop = false,
        ) shouldBe false
    }

    @Test
    fun `shouldResetHomeHubScroll returns true on section change`() {
        shouldResetHomeHubScroll(previousPage = 0, currentPage = 1) shouldBe true
    }

    @Test
    fun `shouldResetHomeHubScroll returns false on same section`() {
        shouldResetHomeHubScroll(previousPage = 1, currentPage = 1) shouldBe false
    }

    @Test
    fun `calculateHomeOpenStreak counts today and yesterday when both active`() {
        val today = LocalDate.now()
        val activities = listOf(
            DayActivity(date = today.minusDays(2), level = 0, type = ActivityType.APP_OPEN),
            DayActivity(date = today.minusDays(1), level = 1, type = ActivityType.APP_OPEN),
            DayActivity(date = today, level = 1, type = ActivityType.APP_OPEN),
        )

        calculateHomeOpenStreak(activities) shouldBe 2
    }

    @Test
    fun `calculateHomeOpenStreak starts from yesterday when today has no activity`() {
        val today = LocalDate.now()
        val activities = listOf(
            DayActivity(date = today.minusDays(2), level = 0, type = ActivityType.APP_OPEN),
            DayActivity(date = today.minusDays(1), level = 1, type = ActivityType.APP_OPEN),
            DayActivity(date = today, level = 0, type = ActivityType.APP_OPEN),
        )

        calculateHomeOpenStreak(activities) shouldBe 1
    }

    @Test
    fun `calculateHomeOpenStreak returns zero when yesterday and today are inactive`() {
        val today = LocalDate.now()
        val activities = listOf(
            DayActivity(date = today.minusDays(1), level = 0, type = ActivityType.APP_OPEN),
            DayActivity(date = today, level = 0, type = ActivityType.APP_OPEN),
        )

        calculateHomeOpenStreak(activities) shouldBe 0
    }

    @Test
    fun `calculateHomeOpenStreak returns zero for empty input`() {
        calculateHomeOpenStreak(emptyList()) shouldBe 0
    }

    @Test
    fun `shouldShowNicknameEditHint returns true for default untouched nickname`() {
        shouldShowNicknameEditHint(
            currentName = "\u0417\u0440\u0438\u0442\u0435\u043b\u044c",
            isNameEdited = false,
        ) shouldBe true
    }

    @Test
    fun `shouldShowNicknameEditHint returns false after nickname change`() {
        shouldShowNicknameEditHint(
            currentName = "MyName",
            isNameEdited = true,
        ) shouldBe false
    }

    @Test
    fun `shouldShowNicknameEditHint returns false for custom name even without edit flag`() {
        shouldShowNicknameEditHint(
            currentName = "Custom",
            isNameEdited = false,
        ) shouldBe false
    }
}
