package tachiyomi.core.common.util.lang

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SortUtilCollationKeyTest {

    @Test
    fun `precomputed collation keys produce the same order as compareToWithCollator`() {
        val sample = listOf(
            "Solo Leveling",
            "berserk",
            "Берсерк",
            "Атака титанов",
            "solo leveling",
            "100 Girlfriends",
            "Éclair",
            "eclair",
            "Ярость",
        )

        val sortedByCollator = sample.sortedWith { a, b -> a.compareToWithCollator(b) }
        val keys = sample.associateWith { it.toCollationKey() }
        val sortedByKeys = sample.sortedWith { a, b ->
            compareCollationKeys(keys[a], a, keys[b], b)
        }

        sortedByKeys shouldBe sortedByCollator
    }
}
