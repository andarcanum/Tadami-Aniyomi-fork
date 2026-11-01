package tachiyomi.domain.reels.anime

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.reels.anime.interactor.ReelsRoundRobinScheduler
import tachiyomi.domain.reels.anime.model.ReelsCandidate
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinAnchor
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinCatalog
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinCatalogSource
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinEntry
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinFrame
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinNeed
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinSkip
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinSnapshot
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinSourceState
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinStop
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinTurn
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinValidation
import tachiyomi.domain.reels.anime.model.ReelsSessionKey
import java.util.Date

class ReelsRoundRobinSchedulerTest {

    @Test
    fun `three hundred sources get one slot each and the next item starts the next round`() {
        val keys = sourceKeys(300)
        val sources = keys.mapIndexed { index, key -> source(key, index.toLong(), title = "chat $key") }
        val entries = keys.flatMap { key ->
            listOf(
                entry(key, "$key-new", orderKey = 30),
                entry(key, "$key-anim", orderKey = 29, mediaKind = "animation"),
                entry(key, "$key-mid", orderKey = 20),
                entry(key, "$key-old", orderKey = 10),
            )
        }
        val reconciled = ReelsRoundRobinScheduler.reconcile(
            snapshot(sources = sources),
            catalogOf(sources),
        )

        reconciled.bootstrapComplete shouldBe true
        reconciled.frames.size shouldBe 2

        val eligible = entries.map { it.candidate.itemId }.toSet()
        val commit = ReelsRoundRobinScheduler.select(
            state = reconciled.copy(entries = entries),
            eligibleIds = eligible,
            nowMs = 0,
            limit = 301,
        )

        commit.page.size shouldBe 301
        val itemToSource = entries.associate { it.candidate.itemId to it.sourceKey }
        val firstRound = commit.page.take(300)
        firstRound.map { itemToSource.getValue(it.itemId) } shouldContainExactly keys
        firstRound.count { it.itemId == "${keys.first()}-new" } shouldBe 1
        commit.page[300].itemId shouldBe "${keys.first()}-anim"
        commit.snapshot.frames.size shouldBe 2
        commit.snapshot.frames.flatMap { it.turns.values }.forEach { turn ->
            ((turn.itemId != null) xor (turn.skip != null)) shouldBe true
        }

        listOf(12, 24).forEach { limit ->
            val cut = ReelsRoundRobinScheduler.select(
                state = reconciled.copy(entries = entries),
                eligibleIds = eligible,
                nowMs = 0,
                limit = limit,
            )
            cut.page.size shouldBe limit
            cut.page shouldBe commit.page.take(limit)
            cut.snapshot.frames.size shouldBe 2
        }

        val repeated = ReelsRoundRobinScheduler.select(
            state = reconciled.copy(entries = entries),
            eligibleIds = eligible,
            nowMs = 0,
            limit = 301,
        )
        repeated shouldBe commit
    }

    @Test
    fun `initial partial catalog grows the first round and a complete catalog enables the second frame`() {
        val initialKeys = sourceKeys(4)
        val initialSources = initialKeys.mapIndexed { index, key -> source(key, index.toLong(), title = "chat $key") }
        val partial = ReelsRoundRobinScheduler.reconcile(
            snapshot(),
            catalogOf(initialSources, complete = false),
        )

        partial.bootstrapComplete shouldBe false
        partial.frames.size shouldBe 1
        partial.frames.single().members shouldContainExactly initialKeys

        val allKeys = sourceKeys(300)
        val allSources = allKeys.mapIndexed { index, key -> source(key, index.toLong(), title = "chat $key") }
        val complete = ReelsRoundRobinScheduler.reconcile(partial, catalogOf(allSources))

        complete.bootstrapComplete shouldBe true
        complete.frames.size shouldBe 2
        complete.frames[0].members.size shouldBe 300
        complete.frames[0].members.take(4) shouldContainExactly initialKeys
        complete.frames[1].members.size shouldBe 300
        complete.sources.filter { it.key !in initialKeys }.all { it.joinRound == 0L } shouldBe true
    }

    @Test
    fun `out of order scan results keep member order and consumed candidates are skipped`() {
        val a = source("a", 0, title = "A")
        val b = source("b", 1, title = "B")
        val base = ReelsRoundRobinScheduler.reconcile(
            snapshot(sources = listOf(a, b)),
            catalogOf(listOf(a, b)),
        )
        val bEntries = listOf(entry("b", "b-new", orderKey = 20), entry("b", "b-old", orderKey = 10))

        val onlyB = ReelsRoundRobinScheduler.select(
            state = base.copy(entries = bEntries),
            eligibleIds = setOf("b-new", "b-old"),
            nowMs = 0,
            limit = 10,
        )

        onlyB.page.size shouldBe 0
        onlyB.stop shouldBe ReelsRoundRobinStop.NEED_INPUT
        onlyB.needs shouldContainExactly listOf(
            ReelsRoundRobinNeed.Scan(sourceKey = "a", checkpoint = null, refreshHead = true, maxCandidates = 10),
        )
        onlyB.snapshot.frames[0].turns["b"] shouldBe null
        onlyB.snapshot.entries.all { it.round == null } shouldBe true

        val ordered = ReelsRoundRobinScheduler.select(
            state = base.copy(entries = listOf(entry("a", "a-new", orderKey = 30)) + bEntries),
            eligibleIds = setOf("a-new", "b-new", "b-old"),
            nowMs = 0,
            limit = 10,
        )
        ordered.page.map { it.itemId } shouldContainExactly listOf("a-new", "b-new")

        val consumedOld = ReelsRoundRobinScheduler.select(
            state = base.copy(
                entries = listOf(
                    entry("a", "a-new", orderKey = 30),
                    entry("b", "b-old", orderKey = 10),
                    entry("b", "b-new", orderKey = 5),
                ),
            ),
            eligibleIds = setOf("a-new", "b-new"),
            nowMs = 0,
            limit = 10,
        )
        consumedOld.page.map { it.itemId } shouldContainExactly listOf("a-new", "b-new")
        consumedOld.snapshot.frames[0].turns["b"]?.itemId shouldBe "b-new"
        consumedOld.snapshot.entries.first { it.candidate.itemId == "b-old" }.round shouldBe null
    }

    @Test
    fun `deferred source gets an explicit retry skip and an untouched source is not marked done`() {
        val a = source("a", 0, title = "A")
        val b = source("b", 1, title = "B", retryAt = 1_000, retryReason = "timeout")
        val base = ReelsRoundRobinScheduler.reconcile(
            snapshot(sources = listOf(a, b)),
            catalogOf(listOf(a, b)),
        )

        val commit = ReelsRoundRobinScheduler.select(
            state = base.copy(entries = listOf(entry("a", "a1", orderKey = 10))),
            eligibleIds = setOf("a1"),
            nowMs = 0,
            limit = 10,
        )

        commit.page.map { it.itemId } shouldContainExactly listOf("a1")
        commit.snapshot.frames[0].turns["a"] shouldBe ReelsRoundRobinTurn(itemId = "a1", skip = null)
        commit.snapshot.frames[0].turns["b"] shouldBe
            ReelsRoundRobinTurn(itemId = null, skip = ReelsRoundRobinSkip.RETRY)
        commit.stop shouldBe ReelsRoundRobinStop.PAGE_READY

        val untouched = ReelsRoundRobinScheduler.select(
            state = base.copy(entries = emptyList()),
            eligibleIds = emptySet(),
            nowMs = 1_000_000,
            limit = 1,
        )
        untouched.page.size shouldBe 0
        untouched.stop shouldBe ReelsRoundRobinStop.NEED_INPUT
        untouched.snapshot.frames[0].turns shouldBe emptyMap()
    }

    @Test
    fun `membership changes rebuild the prepared frame while new sources join the next round`() {
        val a = source("a", 0, title = "A")
        val b = source("b", 1, title = "B")
        val c = source("c", 2, title = "C")
        val d = source("d", 3, title = "D")
        val base = ReelsRoundRobinScheduler.reconcile(
            snapshot(sources = listOf(a, b, c)),
            catalogOf(listOf(a, b, c)),
        )
        val entries = listOf(
            entry("a", "a1", orderKey = 30),
            entry("b", "b1", orderKey = 30),
            entry("c", "c1", orderKey = 30),
            entry("a", "a2", orderKey = 20),
            entry("b", "b2", orderKey = 20),
            entry("c", "c2", orderKey = 20),
        )
        val reserveAll = ReelsRoundRobinScheduler.select(
            state = base.copy(entries = entries),
            eligibleIds = entries.map { it.candidate.itemId }.toSet(),
            nowMs = 0,
            limit = 6,
        )
        reserveAll.page.map { it.itemId } shouldContainExactly listOf("a1", "b1", "c1", "a2", "b2", "c2")

        val added = ReelsRoundRobinScheduler.reconcile(
            reserveAll.snapshot,
            catalogOf(listOf(a, b, c, d)),
        )
        added.sources.first { it.key == "d" }.joinRound shouldBe 1L
        added.frames[0].members shouldContainExactly listOf("a", "b", "c")
        added.frames[0].turns["a"] shouldBe ReelsRoundRobinTurn("a1", null)
        added.frames[0].turns["b"] shouldBe ReelsRoundRobinTurn("b1", null)
        added.frames[0].turns["c"] shouldBe ReelsRoundRobinTurn("c1", null)
        added.frames[1].members shouldContainExactly listOf("a", "b", "c", "d")
        added.frames[1].turns shouldBe emptyMap()
        added.entries.filter { it.round == 1L } shouldBe emptyList()

        val dEntry = entry("d", "d1", orderKey = 10)
        val afterD = ReelsRoundRobinScheduler.select(
            state = added.copy(entries = added.entries + dEntry),
            eligibleIds = entries.map { it.candidate.itemId }.toSet() + "d1",
            nowMs = 0,
            limit = 10,
        )
        afterD.page.map { it.itemId } shouldContainExactly listOf("a1", "b1", "c1", "a2", "b2", "c2", "d1")

        val renamedCatalog = catalogOf(
            listOf(a.copy(title = "Renamed A"), b, c, d),
        )
        val renamed = ReelsRoundRobinScheduler.reconcile(afterD.snapshot, renamedCatalog)
        renamed.sources.first { it.key == "a" }.title shouldBe "Renamed A"
        renamed.frames shouldBe afterD.snapshot.frames
        renamed.entries shouldBe afterD.snapshot.entries
        ReelsRoundRobinScheduler.reconcile(renamed, renamedCatalog) shouldBe renamed

        val removed = ReelsRoundRobinScheduler.reconcile(renamed, catalogOf(listOf(a, c, d)))
        removed.frames[0].members shouldContainExactly listOf("a", "b", "c")
        removed.frames[0].turns["b"]?.skip shouldBe ReelsRoundRobinSkip.REMOVED
        removed.frames[0].turns["a"] shouldBe ReelsRoundRobinTurn("a1", null)
        removed.frames[0].turns["c"] shouldBe ReelsRoundRobinTurn("c1", null)
        removed.frames[1].members shouldContainExactly listOf("a", "c", "d")
        removed.entries.first { it.candidate.itemId == "b1" }.round shouldBe null

        val excluded = ReelsRoundRobinScheduler.reconcile(
            removed,
            catalogOf(listOf(a, c.copy(excluded = true), d)),
        )
        excluded.frames[0].turns["c"]?.skip shouldBe ReelsRoundRobinSkip.HIDDEN
        excluded.frames[0].turns["a"] shouldBe ReelsRoundRobinTurn("a1", null)
        excluded.frames[1].members shouldContainExactly listOf("a", "d")
        excluded.entries.first { it.candidate.itemId == "c1" }.round shouldBe null
    }

    @Test
    fun `a reserved future item without payload becomes a revalidation need`() {
        val a = source("a", 0, title = "A")
        val b = source("b", 1, title = "B")
        val base = snapshot(
            sources = listOf(a, b),
            frames = listOf(
                frame(
                    number = 0,
                    members = listOf("a", "b"),
                    turns = mapOf(
                        "a" to ReelsRoundRobinTurn("a1", null),
                        "b" to ReelsRoundRobinTurn("b1", null),
                    ),
                ),
            ),
            entries = listOf(entry("a", "a1", orderKey = 10)),
            bootstrapComplete = true,
        )

        val commit = ReelsRoundRobinScheduler.select(
            state = base,
            eligibleIds = setOf("a1"),
            nowMs = 0,
            limit = 10,
        )

        commit.page.map { it.itemId } shouldContainExactly listOf("a1")
        commit.needs shouldContainExactly listOf(ReelsRoundRobinNeed.Revalidate("b1"))
        commit.snapshot.frames[0].turns["b"] shouldBe ReelsRoundRobinTurn("b1", null)
        commit.stop shouldBe ReelsRoundRobinStop.PAGE_READY
    }

    @Test
    fun `repair releases a future invalid slot and the same source is retried before the next round`() {
        val a = source("a", 0, title = "A")
        val b = source("b", 1, title = "B")
        val c = source("c", 2, title = "C")
        val base = ReelsRoundRobinScheduler.reconcile(
            snapshot(sources = listOf(a, b, c)),
            catalogOf(listOf(a, b, c)),
        )
        val entries = listOf(
            entry("a", "a1", orderKey = 30),
            entry("b", "b1", orderKey = 30),
            entry("c", "c1", orderKey = 30),
            entry("a", "a2", orderKey = 20),
            entry("b", "b2", orderKey = 20),
            entry("c", "c2", orderKey = 20),
        )
        val reserved = ReelsRoundRobinScheduler.select(
            state = base.copy(entries = entries),
            eligibleIds = entries.map { it.candidate.itemId }.toSet(),
            nowMs = 0,
            limit = 6,
        )
        val activated = ReelsRoundRobinScheduler.activate(reserved.snapshot, "a1")
        activated.anchor shouldBe ReelsRoundRobinAnchor(round = 0, sourceKey = "a", itemId = "a1")

        val repaired = ReelsRoundRobinScheduler.repair(activated, setOf("b1"))

        repaired.anchor shouldBe activated.anchor
        repaired.frames[0].turns["a"] shouldBe ReelsRoundRobinTurn("a1", null)
        repaired.frames[0].turns["b"] shouldBe null
        repaired.frames[0].turns["c"] shouldBe ReelsRoundRobinTurn("c1", null)
        repaired.frames[1].turns["b"] shouldBe null
        repaired.entries.first { it.candidate.itemId == "b1" }.let {
            it.round shouldBe null
            it.ordinal shouldBe null
            it.validation shouldBe ReelsRoundRobinValidation.UNKNOWN
        }
        repaired.entries.first { it.candidate.itemId == "b2" }.let {
            it.round shouldBe null
            it.ordinal shouldBe null
        }

        val replacement = entry("b", "b1b", orderKey = 15)
        val afterReplacement = ReelsRoundRobinScheduler.select(
            state = repaired.copy(entries = repaired.entries + replacement),
            eligibleIds = entries.map { it.candidate.itemId }.toSet() + "b1b" + "b1",
            nowMs = 0,
            limit = 10,
        )
        // The released later reservation is re-planned into B's unresolved round-0 turn before
        // the next round starts; the staged replacement fills the released round-1 slot.
        afterReplacement.page.map { it.itemId } shouldContainExactly
            listOf("b2", "c1", "a2", "b1b", "c2")

        ReelsRoundRobinScheduler.repair(activated, setOf("a1")) shouldBe activated
        val preparedRepair = ReelsRoundRobinScheduler.repair(activated, setOf("b2"))
        preparedRepair.frames[1].turns["b"] shouldBe null
        preparedRepair.frames[0].turns["b"] shouldBe ReelsRoundRobinTurn("b1", null)
        preparedRepair.entries.first { it.candidate.itemId == "b2" }.round shouldBe null
    }

    @Test
    fun `activation is monotonic and promotes the prepared frame without creating a third frame`() {
        val a = source("a", 0, title = "A")
        val b = source("b", 1, title = "B")
        val c = source("c", 2, title = "C")
        val base = ReelsRoundRobinScheduler.reconcile(
            snapshot(sources = listOf(a, b, c)),
            catalogOf(listOf(a, b, c)),
        )
        val entries = listOf(
            entry("a", "a1", orderKey = 30),
            entry("b", "b1", orderKey = 30),
            entry("c", "c1", orderKey = 30),
            entry("a", "a2", orderKey = 20),
            entry("b", "b2", orderKey = 20),
            entry("c", "c2", orderKey = 20),
        )
        val reserved = ReelsRoundRobinScheduler.select(
            state = base.copy(entries = entries),
            eligibleIds = entries.map { it.candidate.itemId }.toSet(),
            nowMs = 0,
            limit = 6,
        )

        val atA = ReelsRoundRobinScheduler.activate(reserved.snapshot, "a1")
        atA.anchor shouldBe ReelsRoundRobinAnchor(0, "a", "a1")
        ReelsRoundRobinScheduler.activate(atA, "a1") shouldBe atA

        val atB = ReelsRoundRobinScheduler.activate(atA, "b1")
        atB.anchor shouldBe ReelsRoundRobinAnchor(0, "b", "b1")
        ReelsRoundRobinScheduler.activate(atB, "a1") shouldBe atB

        val atC = ReelsRoundRobinScheduler.activate(atB, "c1")
        atC.anchor shouldBe ReelsRoundRobinAnchor(0, "c", "c1")
        val fling = ReelsRoundRobinScheduler.activate(reserved.snapshot, "c1")
        fling.anchor shouldBe ReelsRoundRobinAnchor(0, "c", "c1")

        val promoted = ReelsRoundRobinScheduler.activate(atC, "b2")
        promoted.anchor shouldBe ReelsRoundRobinAnchor(1, "b", "b2")
        promoted.frames.size shouldBe 1
        promoted.frames[0].number shouldBe 1
        promoted.frames[0].turns["a"] shouldBe ReelsRoundRobinTurn("a2", null)
        promoted.frames[0].turns["c"] shouldBe ReelsRoundRobinTurn("c2", null)
        ReelsRoundRobinScheduler.activate(promoted, "a2") shouldBe promoted
        ReelsRoundRobinScheduler.activate(promoted, "unknown") shouldBe promoted

        val next = ReelsRoundRobinScheduler.select(
            state = promoted,
            eligibleIds = entries.map { it.candidate.itemId }.toSet(),
            nowMs = 0,
            limit = 10,
        )
        next.page.map { it.itemId } shouldContainExactly listOf("c2")
        next.snapshot.frames.size shouldBe 2
        next.snapshot.frames.map { it.number } shouldContainExactly listOf(1L, 2L)
    }

    @Test
    fun `previousKeys migration keeps one canonical slot order and played item ids`() {
        val old = source("chat-old", 0, title = "Old title")
        val b = source("b", 1, title = "B")
        val entries = listOf(
            entry("chat-old", "old-1", orderKey = 20),
            entry("chat-old", "old-2", orderKey = 10),
            entry("b", "b1", orderKey = 20),
            entry("b", "b2", orderKey = 10),
        )
        val base = snapshot(
            sources = listOf(old, b),
            frames = listOf(
                frame(
                    number = 0,
                    members = listOf("chat-old", "b"),
                    turns = mapOf(
                        "chat-old" to ReelsRoundRobinTurn("old-1", null),
                        "b" to ReelsRoundRobinTurn("b1", null),
                    ),
                ),
                frame(
                    number = 1,
                    members = listOf("chat-old", "b"),
                    turns = mapOf(
                        "chat-old" to ReelsRoundRobinTurn("old-2", null),
                        "b" to ReelsRoundRobinTurn("b2", null),
                    ),
                ),
            ),
            anchor = ReelsRoundRobinAnchor(0, "chat-old", "old-1"),
            entries = entries,
        )
        val migratedCatalog = ReelsRoundRobinCatalog(
            revision = "cat-migrated",
            complete = true,
            sources = listOf(
                ReelsRoundRobinCatalogSource(
                    key = "chat-new",
                    title = "New title",
                    author = null,
                    contentRevision = "content-2",
                    excluded = false,
                    previousKeys = listOf("chat-old"),
                ),
                ReelsRoundRobinCatalogSource(
                    key = "b",
                    title = "B",
                    author = null,
                    contentRevision = null,
                    excluded = false,
                    previousKeys = emptyList(),
                ),
            ),
        )

        val migrated = ReelsRoundRobinScheduler.reconcile(base, migratedCatalog)

        migrated.sources.map { it.key } shouldContainExactly listOf("chat-new", "b")
        migrated.sources.first { it.key == "chat-new" }.order shouldBe 0L
        migrated.sources.first { it.key == "chat-new" }.previousKeys.contains("chat-old") shouldBe true
        migrated.frames[0].members shouldContainExactly listOf("chat-new", "b")
        migrated.frames[0].members.distinct().size shouldBe migrated.frames[0].members.size
        migrated.frames[0].turns["chat-new"] shouldBe ReelsRoundRobinTurn("old-1", null)
        migrated.frames[1].turns["chat-new"] shouldBe ReelsRoundRobinTurn("old-2", null)
        migrated.anchor shouldBe ReelsRoundRobinAnchor(0, "chat-new", "old-1")
        migrated.entries.first { it.candidate.itemId == "old-1" }.sourceKey shouldBe "chat-new"
        migrated.entries.first { it.candidate.itemId == "old-2" }.sourceKey shouldBe "chat-new"
    }

    @Test
    fun `content keys dedup only against reserved and still available representations`() {
        val a = source("a", 0, title = "A")
        val b = source("b", 1, title = "B")
        val base = ReelsRoundRobinScheduler.reconcile(
            snapshot(sources = listOf(a, b)),
            catalogOf(listOf(a, b)),
        )
        val sharedEntries = listOf(
            entry("a", "a1", orderKey = 10, contentKey = "shared"),
            entry("b", "b1", orderKey = 10, contentKey = "shared"),
        )

        val first = ReelsRoundRobinScheduler.select(
            state = base.copy(entries = sharedEntries),
            eligibleIds = setOf("a1", "b1"),
            nowMs = 0,
            limit = 10,
        )
        first.page.map { it.itemId } shouldContainExactly listOf("a1")
        first.snapshot.frames[0].turns["b"] shouldBe null
        first.needs.filterIsInstance<ReelsRoundRobinNeed.Scan>()
            .map { it.sourceKey } shouldContainExactly listOf("b")

        val released = snapshot(
            sources = listOf(a, b),
            frames = listOf(
                frame(
                    number = 0,
                    members = listOf("a", "b"),
                    turns = mapOf("a" to ReelsRoundRobinTurn("a1", null)),
                ),
            ),
            anchor = ReelsRoundRobinAnchor(0, "a", "a1"),
            entries = sharedEntries,
        )
        val second = ReelsRoundRobinScheduler.select(
            state = released,
            eligibleIds = setOf("b1"),
            nowMs = 0,
            limit = 10,
        )
        second.page.map { it.itemId } shouldContainExactly listOf("b1")
    }

    @Test
    fun `prepared frame records filter budget and empty skips instead of soft locking`() {
        val a = source("a", 0, title = "A", checkpoint = "cp-a")
        val c = source("c", 1, title = "C", historyExhausted = true)
        val b = source("b", 2, title = "B")
        val base = snapshot(
            sources = listOf(a, c, b),
            frames = listOf(
                frame(
                    number = 0,
                    members = listOf("a", "c", "b"),
                    turns = mapOf(
                        "a" to ReelsRoundRobinTurn(null, ReelsRoundRobinSkip.FILTER_BUDGET),
                        "c" to ReelsRoundRobinTurn(null, ReelsRoundRobinSkip.EMPTY),
                        "b" to ReelsRoundRobinTurn("b1", null),
                    ),
                ),
                frame(number = 1, members = listOf("a", "c", "b")),
            ),
            anchor = ReelsRoundRobinAnchor(0, "b", "b1"),
            entries = listOf(
                entry("a", "a1", orderKey = 10, validation = ReelsRoundRobinValidation.FILTERED),
                entry("b", "b1", orderKey = 20),
                entry("b", "b2", orderKey = 10),
            ),
            bootstrapComplete = true,
        )

        val commit = ReelsRoundRobinScheduler.select(
            state = base,
            eligibleIds = setOf("b1", "b2"),
            nowMs = 0,
            limit = 10,
        )

        commit.page.map { it.itemId } shouldContainExactly listOf("b2")
        commit.snapshot.frames[1].turns["a"] shouldBe
            ReelsRoundRobinTurn(null, ReelsRoundRobinSkip.FILTER_BUDGET)
        commit.snapshot.frames[1].turns["c"] shouldBe
            ReelsRoundRobinTurn(null, ReelsRoundRobinSkip.EMPTY)
        commit.stop shouldBe ReelsRoundRobinStop.RESERVATION_HORIZON
    }

    @Test
    fun `prepared retry and scheduled head check keep their turns unresolved`() {
        val retrying = source("a", 0, title = "A", retryAt = 5_000, retryReason = "timeout")
        val b = source("b", 1, title = "B")
        val retryState = snapshot(
            sources = listOf(retrying, b),
            frames = listOf(
                frame(0, listOf("a", "b"), mapOf("b" to ReelsRoundRobinTurn("b1", null))),
                frame(1, listOf("a", "b")),
            ),
            anchor = ReelsRoundRobinAnchor(0, "b", "b1"),
            entries = listOf(entry("b", "b1", orderKey = 20), entry("b", "b2", orderKey = 10)),
            bootstrapComplete = true,
        )
        val waiting = ReelsRoundRobinScheduler.select(
            state = retryState,
            eligibleIds = setOf("b1", "b2"),
            nowMs = 1_000,
            limit = 10,
        )
        waiting.page shouldBe emptyList()
        waiting.snapshot.frames[1].turns shouldBe emptyMap()
        waiting.stop shouldBe ReelsRoundRobinStop.WAIT_RETRY

        val scheduled = source(
            "a",
            0,
            title = "A",
            checkpoint = "cp-a",
            historyExhausted = true,
            nextHeadCheckAt = 10_000,
        )
        val deferred = ReelsRoundRobinScheduler.select(
            state = retryState.copy(sources = listOf(scheduled, b)),
            eligibleIds = setOf("b1", "b2"),
            nowMs = 1_000,
            limit = 10,
        )
        deferred.page shouldBe emptyList()
        deferred.needs shouldBe emptyList()
        deferred.snapshot.frames[1].turns shouldBe emptyMap()
    }

    @Test
    fun `filtered only source requests a head check when due or without a checkpoint`() {
        val a = source("a", 0, title = "A")
        val b = source("b", 1, title = "B")
        val base = ReelsRoundRobinScheduler.reconcile(
            snapshot(sources = listOf(a, b)),
            catalogOf(listOf(a, b)),
        )
        val entries = listOf(
            entry("a", "a1", orderKey = 10, validation = ReelsRoundRobinValidation.FILTERED),
            entry("b", "b1", orderKey = 10),
        )

        val noCheckpoint = ReelsRoundRobinScheduler.select(
            state = base.copy(entries = entries),
            eligibleIds = setOf("b1"),
            nowMs = 0,
            limit = 10,
        )
        noCheckpoint.page shouldBe emptyList()
        noCheckpoint.needs shouldContainExactly listOf(
            ReelsRoundRobinNeed.Scan(sourceKey = "a", checkpoint = null, refreshHead = true, maxCandidates = 10),
        )
        noCheckpoint.snapshot.frames[0].turns["a"] shouldBe null

        val due = ReelsRoundRobinScheduler.select(
            state = base.copy(
                sources = listOf(a.copy(checkpoint = "cp-a", nextHeadCheckAt = 1_000), b),
                entries = entries,
            ),
            eligibleIds = setOf("b1"),
            nowMs = 1_000,
            limit = 10,
        )
        due.needs shouldContainExactly listOf(
            ReelsRoundRobinNeed.Scan(sourceKey = "a", checkpoint = "cp-a", refreshHead = true, maxCandidates = 10),
        )

        val notDue = ReelsRoundRobinScheduler.select(
            state = base.copy(
                sources = listOf(a.copy(checkpoint = "cp-a", nextHeadCheckAt = 5_000), b),
                entries = entries,
            ),
            eligibleIds = setOf("b1"),
            nowMs = 0,
            limit = 10,
        )
        notDue.snapshot.frames[0].turns["a"] shouldBe
            ReelsRoundRobinTurn(null, ReelsRoundRobinSkip.FILTER_BUDGET)
        notDue.page.map { it.itemId } shouldContainExactly listOf("b1")
    }

    @Test
    fun `partial catalog does not remove a known member and keeps its reserved slot`() {
        val a = source("a", 0, title = "A")
        val b = source("b", 1, title = "B")
        val base = ReelsRoundRobinScheduler.reconcile(
            snapshot(sources = listOf(a, b)),
            catalogOf(listOf(a, b)),
        )
        val reserved = ReelsRoundRobinScheduler.select(
            state = base.copy(
                entries = listOf(
                    entry("a", "a1", orderKey = 20),
                    entry("b", "b1", orderKey = 20),
                ),
            ),
            eligibleIds = setOf("a1", "b1"),
            nowMs = 0,
            limit = 10,
        )
        reserved.page.map { it.itemId } shouldContainExactly listOf("a1", "b1")

        val partial = ReelsRoundRobinScheduler.reconcile(
            reserved.snapshot,
            catalogOf(listOf(a), complete = false),
        )

        partial.sources.first { it.key == "b" }.present shouldBe true
        partial.frames.size shouldBe 2
        partial.frames[0].turns["b"] shouldBe ReelsRoundRobinTurn("b1", null)
        partial.frames[1].members shouldContainExactly listOf("a", "b")
        partial.entries.first { it.candidate.itemId == "b1" }.round shouldBe 0L

        // Only a complete snapshot is authoritative enough to drop membership.
        val complete = ReelsRoundRobinScheduler.reconcile(
            partial,
            catalogOf(listOf(a), complete = true),
        )
        complete.sources.first { it.key == "b" }.present shouldBe false
        complete.frames[0].turns["b"]?.skip shouldBe ReelsRoundRobinSkip.REMOVED
        complete.frames[1].members shouldContainExactly listOf("a")
    }

    @Test
    fun `a fully filtered page becomes a filter budget skip and waits for its head check`() {
        val a = source("a", 0, title = "A", checkpoint = "cp-a", nextHeadCheckAt = 100_000)
        val b = source("b", 1, title = "B", historyExhausted = true)
        val base = ReelsRoundRobinScheduler.reconcile(
            snapshot(sources = listOf(a, b)),
            catalogOf(listOf(a, b)),
        )
        val filtered = base.copy(
            sources = listOf(a.copy(fruitlessScan = true), b),
            entries = listOf(entry("b", "b1", orderKey = 20)),
        )

        val commit = ReelsRoundRobinScheduler.select(
            state = filtered,
            eligibleIds = setOf("b1"),
            nowMs = 1_000,
            limit = 10,
        )

        commit.page.map { it.itemId } shouldContainExactly listOf("b1")
        commit.snapshot.frames[0].turns["a"]?.skip shouldBe ReelsRoundRobinSkip.FILTER_BUDGET
        commit.needs shouldBe emptyList()

        val due = ReelsRoundRobinScheduler.select(
            state = commit.snapshot,
            eligibleIds = setOf("b1"),
            nowMs = 100_000,
            limit = 10,
        )
        due.needs shouldContainExactly listOf(
            ReelsRoundRobinNeed.Scan(
                sourceKey = "a",
                checkpoint = "cp-a",
                refreshHead = true,
                maxCandidates = 10,
            ),
        )
        // The expired budget skip is cleared so the scan result can fill the slot again.
        due.snapshot.frames.first().turns["a"] shouldBe null
    }

    private fun sourceKeys(count: Int): List<String> = (0 until count).map { "s${it.toString().padStart(3, '0')}" }

    private fun source(
        key: String,
        order: Long,
        title: String = key,
        previousKeys: Set<String> = emptySet(),
        retryAt: Long? = null,
        retryReason: String? = null,
        checkpoint: String? = null,
        historyExhausted: Boolean = false,
        nextHeadCheckAt: Long? = null,
    ): ReelsRoundRobinSourceState = ReelsRoundRobinSourceState(
        key = key,
        order = order,
        title = title,
        author = null,
        knownAuthors = emptySet(),
        previousKeys = previousKeys,
        present = true,
        excluded = false,
        joinRound = 0,
        contentRevision = null,
        checkpoint = checkpoint,
        checkpointContext = null,
        historyExhausted = historyExhausted,
        retryAt = retryAt,
        retryReason = retryReason,
        nextHeadCheckAt = nextHeadCheckAt,
    )

    private fun entry(
        sourceKey: String,
        itemId: String,
        orderKey: Long,
        contentKey: String? = "content-$itemId",
        validation: ReelsRoundRobinValidation = ReelsRoundRobinValidation.ELIGIBLE,
        mediaKind: String = "video",
    ): ReelsRoundRobinEntry = ReelsRoundRobinEntry(
        candidate = ReelsCandidate(
            accountScopeId = "account",
            sourceId = 1L,
            itemId = itemId,
            title = itemId,
            author = "author-$sourceKey",
            videoUrl = "https://example.invalid/$itemId",
            videoUrlHd = null,
            posterUrl = null,
            posterUrlVertical = null,
            webUrl = null,
            durationSec = 10.0,
            hasAudio = true,
            createdAtEpochSec = 1L,
            discoveredAt = Date(0),
            tagsJson = "[\"$mediaKind\"]",
        ),
        sourceKey = sourceKey,
        orderKey = orderKey,
        contentKey = contentKey,
        checkedContext = null,
        validation = validation,
        round = null,
        ordinal = null,
    )

    private fun frame(
        number: Long,
        members: List<String>,
        turns: Map<String, ReelsRoundRobinTurn> = emptyMap(),
    ): ReelsRoundRobinFrame = ReelsRoundRobinFrame(number, members, turns)

    private fun snapshot(
        sources: List<ReelsRoundRobinSourceState> = emptyList(),
        frames: List<ReelsRoundRobinFrame> = emptyList(),
        anchor: ReelsRoundRobinAnchor? = null,
        entries: List<ReelsRoundRobinEntry> = emptyList(),
        bootstrapComplete: Boolean = false,
        catalogRevision: String? = null,
    ): ReelsRoundRobinSnapshot = ReelsRoundRobinSnapshot(
        key = ReelsSessionKey(accountScopeId = "account", sourceId = 1L, contextKey = "context"),
        revision = 0,
        unseenOnly = false,
        bootstrapComplete = bootstrapComplete,
        catalogRevision = catalogRevision,
        sources = sources,
        frames = frames,
        anchor = anchor,
        entries = entries,
        unmappedItemIds = emptyList(),
    )

    private fun catalogOf(
        sources: List<ReelsRoundRobinSourceState>,
        complete: Boolean = true,
        revision: String = "cat-${sources.size}",
    ): ReelsRoundRobinCatalog = ReelsRoundRobinCatalog(
        revision = revision,
        complete = complete,
        sources = sources.map { source ->
            ReelsRoundRobinCatalogSource(
                key = source.key,
                title = source.title,
                author = source.author,
                contentRevision = source.contentRevision,
                excluded = source.excluded,
                previousKeys = source.previousKeys.toList(),
            )
        },
    )
}
