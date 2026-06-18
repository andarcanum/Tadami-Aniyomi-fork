package eu.kanade.domain.entries.rating

import android.content.SharedPreferences
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.hours

class EntryRatingCacheTest {
    private lateinit var preferences: SharedPreferences

    @BeforeEach
    fun setUp() {
        preferences = inMemoryPreferences()
    }

    @Test
    fun `resolve returns fresh cached rating without running loader`() = runTest {
        val cache = EntryRatingCache(preferences)
        cache.put("manga", "Source", "https://example.org/title", 8.5f)

        val rating = cache.resolve("manga", "Source", "https://example.org/title") {
            error("loader should not run")
        }

        rating shouldBe 8.5f
    }

    @Test
    fun `resolve retries stale empty rating`() = runTest {
        var now = 0L
        val cache = EntryRatingCache(preferences) { now }
        val calls = AtomicInteger(0)

        cache.resolve("novel", "Source", "https://example.org/empty") {
            calls.incrementAndGet()
            null
        } shouldBe null

        now += 7.hours.inWholeMilliseconds

        cache.resolve("novel", "Source", "https://example.org/empty") {
            calls.incrementAndGet()
            7.4f
        } shouldBe 7.4f

        calls.get() shouldBe 2
    }

    @Test
    fun `resolve deduplicates concurrent loads for same key`() = runTest {
        val cache = EntryRatingCache(preferences)
        val calls = AtomicInteger(0)

        val ratings = coroutineScope {
            List(3) {
                async {
                    cache.resolve("anime", "Source", "https://example.org/title") {
                        calls.incrementAndGet()
                        delay(50)
                        9.1f
                    }
                }
            }.awaitAll()
        }

        ratings shouldBe listOf(9.1f, 9.1f, 9.1f)
        calls.get() shouldBe 1
    }

    @Test
    fun `cancelled waiter does not remove active in-flight load`() = runTest {
        val cache = EntryRatingCache(preferences)
        val calls = AtomicInteger(0)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Float?>()

        val first = async {
            cache.resolve("manga", "Source", "https://example.org/cancel") {
                calls.incrementAndGet()
                started.complete(Unit)
                finish.await()
            }
        }
        started.await()

        val cancelledWaiter = async {
            cache.resolve("manga", "Source", "https://example.org/cancel") {
                calls.incrementAndGet()
                1f
            }
        }
        delay(10)
        cancelledWaiter.cancelAndJoin()

        val third = async {
            cache.resolve("manga", "Source", "https://example.org/cancel") {
                calls.incrementAndGet()
                2f
            }
        }

        finish.complete(6.3f)

        first.await() shouldBe 6.3f
        third.await() shouldBe 6.3f
        calls.get() shouldBe 1
    }

    private fun inMemoryPreferences(): SharedPreferences {
        val values = mutableMapOf<String, String?>()
        val editor = mockk<SharedPreferences.Editor>()
        val preferences = mockk<SharedPreferences>()

        every { preferences.getString(any(), any()) } answers {
            values[firstArg()] ?: secondArg()
        }
        every { preferences.edit() } returns editor
        every { editor.putString(any(), any()) } answers {
            values[firstArg()] = secondArg()
            editor
        }
        every { editor.apply() } returns Unit
        every { editor.commit() } returns true
        every { editor.clear() } answers {
            values.clear()
            editor
        }
        return preferences
    }
}
