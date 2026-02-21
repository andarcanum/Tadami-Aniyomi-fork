package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.util.Calendar

class GreetingProviderTest {

    @Test
    fun `selectGreetingForContext returns first time scenario with top priority`() {
        val context = GreetingProvider.GreetingContext(
            nowMillis = 1_700_000_000_000L,
            hourOfDay = 10,
            dayOfWeek = Calendar.MONDAY,
            lastOpenedTime = 0L,
            isFirstTime = true,
            totalLaunches = 0L,
        )

        val selection = GreetingProvider.selectGreetingForContext(context)

        selection.scenarioId shouldBe "first_time"
    }

    @Test
    fun `selectGreetingForContext returns long absence scenario over weekend and milestones`() {
        val now = 1_700_000_000_000L
        val eightDaysMs = 8L * 24 * 60 * 60 * 1000
        val context = GreetingProvider.GreetingContext(
            nowMillis = now,
            hourOfDay = 19,
            dayOfWeek = Calendar.SATURDAY,
            lastOpenedTime = now - eightDaysMs,
            achievementCount = 99,
            episodesWatched = 1200,
            librarySize = 250,
            currentStreak = 30,
            isFirstTime = false,
            totalLaunches = 120L,
        )

        val selection = GreetingProvider.selectGreetingForContext(context)

        selection.scenarioId shouldBe "absence_long"
    }

    @Test
    fun `selectGreetingForContext picks frequent user teasing when user opens app constantly`() {
        val now = 1_700_000_000_000L
        val context = GreetingProvider.GreetingContext(
            nowMillis = now,
            hourOfDay = 21,
            dayOfWeek = Calendar.WEDNESDAY,
            lastOpenedTime = now - (6L * 60 * 60 * 1000),
            isFirstTime = false,
            totalLaunches = 60L,
        )

        val selection = GreetingProvider.selectGreetingForContext(context)

        selection.scenarioId shouldBe "frequent_tease"
    }

    @Test
    fun `selectGreetingForContext avoids repeating greetings from recent history`() {
        val now = 1_700_000_000_000L
        val baseContext = GreetingProvider.GreetingContext(
            nowMillis = now,
            hourOfDay = 20,
            dayOfWeek = Calendar.THURSDAY,
            lastOpenedTime = now - (2L * 60 * 60 * 1000),
            isFirstTime = false,
            totalLaunches = 80L,
        )

        val first = GreetingProvider.selectGreetingForContext(baseContext)

        val second = GreetingProvider.selectGreetingForContext(
            context = baseContext.copy(totalLaunches = 81L),
            recentGreetingIds = listOf(first.greetingId),
        )

        val third = GreetingProvider.selectGreetingForContext(
            context = baseContext.copy(totalLaunches = 82L),
            recentGreetingIds = listOf(second.greetingId, first.greetingId),
        )

        second.greetingId shouldNotBe first.greetingId
        third.greetingId shouldNotBe first.greetingId
        third.greetingId shouldNotBe second.greetingId
    }

    @Test
    fun `selectGreetingForContext should never use sunday weekend greeting on saturday`() {
        val baseNow = 1_700_000_000_000L
        val weekendGreetingIds = mutableSetOf<String>()

        repeat(240) { step ->
            val context = GreetingProvider.GreetingContext(
                nowMillis = baseNow + step * 3_600_000L,
                hourOfDay = 18,
                dayOfWeek = Calendar.SATURDAY,
                lastOpenedTime = baseNow - (6L * 60 * 60 * 1000),
                isFirstTime = false,
                totalLaunches = 3L,
            )

            val selection = GreetingProvider.selectGreetingForContext(context)
            if (selection.scenarioId == "weekend") {
                weekendGreetingIds += selection.greetingId
            }
        }

        weekendGreetingIds.isNotEmpty() shouldBe true
        weekendGreetingIds.contains("sunday_marathon") shouldBe false
    }

    @Test
    fun `selectGreetingForContext should never use saturday weekend greeting on sunday`() {
        val baseNow = 1_700_000_000_000L
        val weekendGreetingIds = mutableSetOf<String>()

        repeat(240) { step ->
            val context = GreetingProvider.GreetingContext(
                nowMillis = baseNow + step * 3_600_000L,
                hourOfDay = 18,
                dayOfWeek = Calendar.SUNDAY,
                lastOpenedTime = baseNow - (6L * 60 * 60 * 1000),
                isFirstTime = false,
                totalLaunches = 3L,
            )

            val selection = GreetingProvider.selectGreetingForContext(context)
            if (selection.scenarioId == "weekend") {
                weekendGreetingIds += selection.greetingId
            }
        }

        weekendGreetingIds.isNotEmpty() shouldBe true
        weekendGreetingIds.contains("saturday_perfect") shouldBe false
    }
}
