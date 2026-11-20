package eu.kanade.tachiyomi.ui.player

import eu.kanade.domain.metadata.interactor.parseOriginalTitle
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class MpvStartupOptionsTest {

    @Test
    fun `zero or negative resume position maps to mpv start none to avoid initial seek flush`() {
        MpvStartupOptions.formatStartArgumentFromMillis(0L) shouldBe "none"
        MpvStartupOptions.formatStartArgumentFromMillis(-500L) shouldBe "none"
        MpvStartupOptions.formatStartArgumentFromSeconds(0) shouldBe "none"
        MpvStartupOptions.formatStartArgumentFromSeconds(-1) shouldBe "none"
    }

    @Test
    fun `positive resume position formats seconds for mpv start option`() {
        MpvStartupOptions.formatStartArgumentFromMillis(12_500L) shouldBe "12.5"
        MpvStartupOptions.formatStartArgumentFromSeconds(42) shouldBe "42"
    }

    @Test
    fun `startup network and demuxer options optimize stream launch`() {
        MpvStartupOptions.STREAM_LAVF_OPTIONS shouldContain "http_persistent=1"
        MpvStartupOptions.STREAM_LAVF_OPTIONS shouldContain "reconnect=1"
        MpvStartupOptions.CACHE_PAUSE_WAIT_SECONDS.toDouble() shouldBe 0.3
        MpvStartupOptions.DEMUXER_LAVF_PROBESIZE_BYTES.toLong() shouldBe 1_048_576L
        MpvStartupOptions.DEMUXER_LAVF_ANALYZE_DURATION_SECONDS.toDouble() shouldBe 1.5
    }

    @Test
    fun `parseOriginalTitle rejects pure Russian punctuation remnants and stray acronyms`() {
        val pureRussian = "Давным-давно, в далёком королевстве, жил юноша. Он мечтал о подвигах, славе и свободе."
        parseOriginalTitle(pureRussian) shouldBe null

        val russianWithStrayToken =
            "Однажды обычный школьник попадает в мир MMORPG, где ему предстоит выживать и спасать своих друзей."
        parseOriginalTitle(russianWithStrayToken) shouldBe null
    }

    @Test
    fun `parseOriginalTitle extracts explicit labeled and standalone Latin titles`() {
        parseOriginalTitle("Описание\nOriginal: Sousou no Frieren.") shouldBe "Sousou no Frieren"
        parseOriginalTitle("Оригинальное название: Kusuriya no Hitorigoto") shouldBe "Kusuriya no Hitorigoto"
        parseOriginalTitle("Mushoku Tensei: Jobless Reincarnation\nОписание на русском.") shouldBe
            "Mushoku Tensei: Jobless Reincarnation"
    }
}
