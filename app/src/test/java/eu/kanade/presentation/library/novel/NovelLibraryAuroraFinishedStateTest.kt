package eu.kanade.presentation.library.novel

import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelLibraryAuroraFinishedStateTest {

    @Test
    fun `finished indicator treats completed publishing finished and cancelled novels as finished`() {
        resolveNovelLibraryCornerIndicatorIsFinished(SManga.COMPLETED.toLong()) shouldBe true
        resolveNovelLibraryCornerIndicatorIsFinished(SManga.PUBLISHING_FINISHED.toLong()) shouldBe true
        resolveNovelLibraryCornerIndicatorIsFinished(SManga.CANCELLED.toLong()) shouldBe true
        resolveNovelLibraryCornerIndicatorIsFinished(0L) shouldBe false
    }
}
