package eu.kanade.tachiyomi.ui.player

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HwdecOptionTest {

    @Test
    fun `hardware decoding resolves to mediacodec-copy, not auto`() {
        // `auto` never enables MediaCodec on Android (mpv-android#1088) and silently
        // falls back to software decoding, so the option must name the copy mode explicitly.
        buildHwdecOption(tryHWDecoding = true) shouldBe "mediacodec-copy"
    }

    @Test
    fun `hardware decoding disabled resolves to software decoding`() {
        buildHwdecOption(tryHWDecoding = false) shouldBe "no"
        buildHwdecOption(tryHWDecoding = false, preferZeroCopy = true) shouldBe "no"
    }

    @Test
    fun `zero copy preference resolves to mediacodec when hardware decoding is enabled`() {
        buildHwdecOption(tryHWDecoding = true, preferZeroCopy = true) shouldBe "mediacodec"
        buildHwdecOption(tryHWDecoding = true, preferZeroCopy = false) shouldBe "mediacodec-copy"
    }

    @Test
    fun `known hwdec values map to their decoder`() {
        getDecoderFromValue("mediacodec-copy") shouldBe Decoder.HW
        getDecoderFromValue("mediacodec") shouldBe Decoder.HWPlus
        getDecoderFromValue("no") shouldBe Decoder.SW
        getDecoderFromValue("auto") shouldBe Decoder.Auto
        getDecoderFromValue("auto-copy") shouldBe Decoder.AutoCopy
    }

    @Test
    fun `unknown hwdec-current value falls back to Auto instead of crashing`() {
        getDecoderFromValue("videotoolbox") shouldBe Decoder.Auto
        getDecoderFromValue("") shouldBe Decoder.Auto
    }
}
