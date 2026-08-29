package eu.kanade.tachiyomi.util.storage

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DiskUtilTest {

    @Test
    fun `volume id is extracted from tree document ids`() {
        DiskUtil.volumeIdFromTreeDocumentId("primary:downloads") shouldBe "primary"
        DiskUtil.volumeIdFromTreeDocumentId("6236-3231:Android/data") shouldBe "6236-3231"
    }

    @Test
    fun `volume id is extracted when the tree points at the volume root`() {
        DiskUtil.volumeIdFromTreeDocumentId("primary:") shouldBe "primary"
        DiskUtil.volumeIdFromTreeDocumentId("6236-3231:") shouldBe "6236-3231"
    }

    @Test
    fun `volume id is null for malformed document ids`() {
        DiskUtil.volumeIdFromTreeDocumentId(":downloads") shouldBe null
        DiskUtil.volumeIdFromTreeDocumentId("") shouldBe null
        DiskUtil.volumeIdFromTreeDocumentId("no-delimiter") shouldBe null
    }
}
