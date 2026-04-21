package tachiyomi.presentation.core.util

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AppHapticsTest {

    @Test
    fun `tap performs feedback in full mode`() {
        val recordingHaptics = RecordingHapticFeedback()

        createAppHaptics(
            hapticFeedback = recordingHaptics,
            hapticFeedbackMode = HapticFeedbackMode.FULL,
        ).tap()

        recordingHaptics.calls shouldContainExactly listOf(HapticFeedbackType.VirtualKey)
    }

    @Test
    fun `tap is a no-op in partial mode`() {
        val recordingHaptics = RecordingHapticFeedback()

        createAppHaptics(
            hapticFeedback = recordingHaptics,
            hapticFeedbackMode = HapticFeedbackMode.PARTIAL,
        ).tap()

        recordingHaptics.calls shouldBe emptyList()
    }

    @Test
    fun `tap is a no-op in e ink mode`() {
        val recordingHaptics = RecordingHapticFeedback()

        createAppHaptics(
            hapticFeedback = recordingHaptics,
            hapticFeedbackMode = HapticFeedbackMode.FULL,
            isEInkMode = true,
        ).tap()

        recordingHaptics.calls shouldBe emptyList()
    }

    private class RecordingHapticFeedback : HapticFeedback {
        val calls = mutableListOf<HapticFeedbackType>()

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            calls += hapticFeedbackType
        }
    }
}
