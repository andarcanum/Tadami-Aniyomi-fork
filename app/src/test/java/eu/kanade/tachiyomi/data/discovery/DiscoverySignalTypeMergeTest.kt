package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoverySignalType

class DiscoverySignalTypeMergeTest {

    @Test
    fun `like then consumed merges into consumed_liked`() {
        DiscoverySignalType.resolveEffectiveSignal(DiscoverySignalType.LIKE, DiscoverySignalType.CONSUMED)
            .shouldBe(DiscoverySignalType.CONSUMED_LIKED)
    }

    @Test
    fun `like after consumed is recorded as consumed_liked`() {
        DiscoverySignalType.resolveEffectiveSignal(DiscoverySignalType.CONSUMED, DiscoverySignalType.LIKE)
            .shouldBe(DiscoverySignalType.CONSUMED_LIKED)
    }

    @Test
    fun `weaker signal does not downgrade consumed_liked`() {
        listOf(DiscoverySignalType.LIKE, DiscoverySignalType.CONSUMED, DiscoverySignalType.CLICK).forEach { next ->
            DiscoverySignalType.resolveEffectiveSignal(DiscoverySignalType.CONSUMED_LIKED, next)
                .shouldBe(DiscoverySignalType.CONSUMED_LIKED)
        }
    }

    @Test
    fun `stronger signals still override`() {
        DiscoverySignalType.resolveEffectiveSignal(DiscoverySignalType.LIKE, DiscoverySignalType.ADD)
            .shouldBe(DiscoverySignalType.ADD)
        DiscoverySignalType.resolveEffectiveSignal(DiscoverySignalType.LIKE, DiscoverySignalType.HIDE)
            .shouldBe(DiscoverySignalType.HIDE)
    }

    @Test
    fun `add after consumed keeps exclusion as consumed_added`() {
        DiscoverySignalType.resolveEffectiveSignal(DiscoverySignalType.CONSUMED, DiscoverySignalType.ADD)
            .shouldBe(DiscoverySignalType.CONSUMED_ADDED)
        DiscoverySignalType.resolveEffectiveSignal(DiscoverySignalType.CONSUMED_LIKED, DiscoverySignalType.ADD)
            .shouldBe(DiscoverySignalType.CONSUMED_ADDED)
    }

    @Test
    fun `consumed after add merges into consumed_added`() {
        DiscoverySignalType.resolveEffectiveSignal(DiscoverySignalType.ADD, DiscoverySignalType.CONSUMED)
            .shouldBe(DiscoverySignalType.CONSUMED_ADDED)
    }

    @Test
    fun `weaker signals do not downgrade consumed_added and hide still wins`() {
        listOf(
            DiscoverySignalType.CLICK,
            DiscoverySignalType.CONSUMED,
            DiscoverySignalType.LIKE,
            DiscoverySignalType.ADD,
        )
            .forEach { next ->
                DiscoverySignalType.resolveEffectiveSignal(DiscoverySignalType.CONSUMED_ADDED, next)
                    .shouldBe(DiscoverySignalType.CONSUMED_ADDED)
            }
        DiscoverySignalType.resolveEffectiveSignal(DiscoverySignalType.CONSUMED_ADDED, DiscoverySignalType.HIDE)
            .shouldBe(DiscoverySignalType.HIDE)
    }

    @Test
    fun `consumed_added keeps add weight and excludes from feed`() {
        DiscoverySignalType.CONSUMED_ADDED.weight shouldBe 1.0
        DiscoverySignalType.CONSUMED_ADDED.excludesFromFeed shouldBe true
    }

    @Test
    fun `consumed_liked keeps like weight and excludes from feed`() {
        DiscoverySignalType.CONSUMED_LIKED.weight shouldBe 0.8
        DiscoverySignalType.CONSUMED_LIKED.excludesFromFeed shouldBe true
        DiscoverySignalType.CONSUMED.excludesFromFeed shouldBe true
        DiscoverySignalType.LIKE.excludesFromFeed shouldBe false
    }
}
