package eu.kanade.tachiyomi.ui.reader.novel

import androidx.compose.ui.graphics.Color
import io.kotest.matchers.shouldBe
import org.junit.Test

class NovelReaderScreenSerializationTest {

    @Test
    fun `reader screen does not retain compose color fields`() {
        NovelReaderScreen::class.java.declaredFields
            .any { it.type == Color::class.java }
            .shouldBe(false)
    }
}
