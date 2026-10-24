package eu.kanade.tachiyomi.ui.reels.player

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class ReelsCacheOwnerTest {

    @Test
    fun `concurrent cold access creates exactly one instance`() {
        val owner = ReelsCacheOwner<Any>()
        val factoryCalls = AtomicInteger()
        val firstFactoryEntered = CountDownLatch(1)
        val secondCalling = CountDownLatch(1)
        val releaseFactory = CountDownLatch(1)
        val start = CountDownLatch(1)
        val factory: () -> Any = {
            if (factoryCalls.incrementAndGet() == 1) {
                firstFactoryEntered.countDown()
                check(releaseFactory.await(5, TimeUnit.SECONDS))
            }
            Any()
        }
        val first = AtomicReference<Any>()
        val second = AtomicReference<Any>()

        val firstThread = thread(start = true) {
            start.await()
            first.set(owner.getOrCreate(factory))
        }
        val secondThread = thread(start = true) {
            start.await()
            secondCalling.countDown()
            second.set(owner.getOrCreate(factory))
        }

        start.countDown()
        firstFactoryEntered.await(5, TimeUnit.SECONDS) shouldBe true
        secondCalling.await(5, TimeUnit.SECONDS) shouldBe true
        releaseFactory.countDown()
        firstThread.join(5_000)
        secondThread.join(5_000)

        factoryCalls.get() shouldBe 1
        (first.get() === second.get()) shouldBe true
    }

    @Test
    fun `clear-if-absent does not run during first initialization`() {
        val owner = ReelsCacheOwner<Any>()
        val factoryEntered = CountDownLatch(1)
        val releaseFactory = CountDownLatch(1)
        val clearAttempted = CountDownLatch(1)
        val clearRan = AtomicBoolean(false)
        val cache = Any()

        val initThread = thread(start = true) {
            owner.getOrCreate {
                factoryEntered.countDown()
                check(releaseFactory.await(5, TimeUnit.SECONDS))
                cache
            }
        }
        factoryEntered.await(5, TimeUnit.SECONDS) shouldBe true

        val clearThread = thread(start = true) {
            clearAttempted.countDown()
            owner.clearIfAbsent {
                clearRan.set(true)
                0L
            }
        }
        clearAttempted.await(5, TimeUnit.SECONDS) shouldBe true
        releaseFactory.countDown()
        initThread.join(5_000)
        clearThread.join(5_000)

        clearRan.get() shouldBe false
        (owner.current() === cache) shouldBe true
    }

    @Test
    fun `clear-if-absent runs while no cache exists`() {
        val owner = ReelsCacheOwner<Any>()
        val ran = AtomicBoolean(false)

        owner.clearIfAbsent {
            ran.set(true)
            42L
        } shouldBe 42L

        ran.get() shouldBe true
        owner.current() shouldBe null
    }
}
