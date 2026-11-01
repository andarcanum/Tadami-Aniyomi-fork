package tachiyomi.domain.reels.anime

import eu.kanade.tachiyomi.animesource.model.FeedVideoQualityVariant
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.reels.anime.interactor.ReelsQualitySelector
import tachiyomi.domain.reels.anime.model.ReelsQualityPreference

class ReelsQualitySelectorTest {

    private val source = variant("source", width = 1080, height = 1920, codec = "h264", isSource = true)
    private val hd720 = variant("alt:720h264", width = 720, height = 1280, codec = "h264")
    private val hd720hevc = variant("alt:720hevc", width = 720, height = 1280, codec = "h265")
    private val sd480 = variant("alt:480", width = 480, height = 854, codec = "h264")
    private val fullHd = variant("alt:1080", width = 1080, height = 1920, codec = "h264")

    @Test
    fun `global source without override selects source`() {
        val decision = select(
            global = ReelsQualityPreference.Source,
            variants = listOf(source, hd720),
        )

        decision.requested shouldBe ReelsQualityPreference.Source
        decision.variant shouldBe source
        decision.manual shouldBe false
        decision.fallbackToSource shouldBe false
    }

    @Test
    fun `manual resolution overrides global source`() {
        val decision = select(
            global = ReelsQualityPreference.Source,
            override = ReelsQualityPreference.Resolution(720),
            variants = listOf(source, hd720),
        )

        decision.requested shouldBe ReelsQualityPreference.Resolution(720)
        decision.variant shouldBe hd720
        decision.manual shouldBe true
        decision.fallbackToSource shouldBe false
    }

    @Test
    fun `manual resolution may be higher than the global preference`() {
        val decision = select(
            global = ReelsQualityPreference.Resolution(480),
            override = ReelsQualityPreference.Resolution(1080),
            variants = listOf(source, sd480, fullHd),
        )

        decision.variant shouldBe fullHd
        decision.manual shouldBe true
    }

    @Test
    fun `missing exact resolution falls back to source and never to a nearest one`() {
        val decision = select(
            global = ReelsQualityPreference.Resolution(720),
            variants = listOf(source, sd480),
        )

        decision.variant shouldBe source
        decision.fallbackToSource shouldBe true
    }

    @Test
    fun `data saver requests 480 but manual selection outranks it`() {
        val automatic = select(
            global = ReelsQualityPreference.Resolution(1080),
            forceSd = true,
            variants = listOf(source, sd480, fullHd),
        )
        automatic.requested shouldBe ReelsQualityPreference.Resolution(480)
        automatic.variant shouldBe sd480
        automatic.manual shouldBe false

        val manual = select(
            global = ReelsQualityPreference.Resolution(1080),
            override = ReelsQualityPreference.Resolution(1080),
            forceSd = true,
            variants = listOf(source, sd480, fullHd),
        )
        manual.requested shouldBe ReelsQualityPreference.Resolution(1080)
        manual.variant shouldBe fullHd
        manual.manual shouldBe true
    }

    @Test
    fun `data saver missing 480 falls back to source`() {
        val decision = select(
            global = ReelsQualityPreference.Resolution(1080),
            forceSd = true,
            variants = listOf(source, fullHd),
        )

        decision.requested shouldBe ReelsQualityPreference.Resolution(480)
        decision.variant shouldBe source
        decision.fallbackToSource shouldBe true
    }

    @Test
    fun `vertical dimensions use the short edge`() {
        val vertical = variant("alt:vertical", width = 1080, height = 1920, codec = "h264")
        val decision = select(
            global = ReelsQualityPreference.Resolution(1080),
            variants = listOf(source, vertical),
        )

        decision.variant shouldBe vertical
    }

    @Test
    fun `compatible codec preference is h264 then h265 then av1`() {
        val av1 = variant("alt:720av1", width = 720, height = 1280, codec = "av01")
        val compatible = select(
            global = ReelsQualityPreference.Resolution(720),
            variants = listOf(source, av1, hd720hevc, hd720),
            compatibleIds = setOf(av1.id, hd720hevc.id, hd720.id),
        )
        compatible.variant shouldBe hd720

        val withoutH264 = select(
            global = ReelsQualityPreference.Resolution(720),
            variants = listOf(source, av1, hd720hevc, hd720),
            compatibleIds = setOf(av1.id, hd720hevc.id),
        )
        withoutH264.variant shouldBe hd720hevc

        val onlyAv1 = select(
            global = ReelsQualityPreference.Resolution(720),
            variants = listOf(source, av1, hd720hevc, hd720),
            compatibleIds = setOf(av1.id),
        )
        onlyAv1.variant shouldBe av1
    }

    @Test
    fun `incompatible exact variant falls back to source instead of a fake selection`() {
        val decision = select(
            global = ReelsQualityPreference.Resolution(720),
            variants = listOf(source, hd720),
            compatibleIds = emptySet(),
        )

        decision.variant shouldBe source
        decision.fallbackToSource shouldBe true
    }

    @Test
    fun `equal codecs prefer the smaller known size then stable id`() {
        val smaller = variant("alt:b", width = 720, height = 1280, codec = "h264", size = 10_000_000L)
        val larger = variant("alt:a", width = 720, height = 1280, codec = "h264", size = 20_000_000L)
        val unknown = variant("alt:c", width = 720, height = 1280, codec = "h264", size = null)

        val decision = select(
            global = ReelsQualityPreference.Resolution(720),
            variants = listOf(source, larger, unknown, smaller),
        )

        decision.variant shouldBe smaller
    }

    @Test
    fun `invalid stored global value and serialization roundtrip`() {
        ReelsQualitySelector.parseGlobal("invalid") shouldBe ReelsQualityPreference.Source
        ReelsQualitySelector.parseGlobal(null) shouldBe ReelsQualityPreference.Source
        ReelsQualitySelector.parseGlobal("720") shouldBe ReelsQualityPreference.Resolution(720)
        ReelsQualitySelector.parseGlobal("SOURCE") shouldBe ReelsQualityPreference.Source

        ReelsQualityPreference.Resolution(1080).let { preference ->
            ReelsQualitySelector.parseGlobal(ReelsQualitySelector.serializeGlobal(preference)) shouldBe preference
        }
        ReelsQualitySelector.serializeGlobal(ReelsQualityPreference.Source) shouldBe "source"
    }

    private fun select(
        global: ReelsQualityPreference,
        override: ReelsQualityPreference? = null,
        forceSd: Boolean = false,
        variants: List<FeedVideoQualityVariant>,
        compatibleIds: Set<String> = variants.map { it.id }.toSet(),
    ) = ReelsQualitySelector.select(
        global = global,
        override = override,
        forceSd = forceSd,
        variants = variants,
        compatibleIds = compatibleIds,
    )

    private fun variant(
        id: String,
        width: Int?,
        height: Int?,
        codec: String?,
        size: Long? = 1_000L,
        isSource: Boolean = false,
    ) = FeedVideoQualityVariant(
        id = id,
        width = width,
        height = height,
        codec = codec,
        sizeBytes = size,
        isSource = isSource,
    )
}
