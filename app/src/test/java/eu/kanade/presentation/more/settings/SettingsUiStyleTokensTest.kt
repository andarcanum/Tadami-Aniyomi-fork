package eu.kanade.presentation.more.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SettingsUiStyleTokensTest {

    @Test
    fun `aurora dialog container uses dense surface alpha`() {
        resolveAuroraDialogContainerColor(Color(0xFF102030)) shouldBe Color(0xFF102030).copy(
            alpha = AURORA_SETTINGS_DIALOG_ALPHA,
        )
    }

    @Test
    fun `aurora selection background uses accent based highlight`() {
        resolveAuroraSelectionBackgroundColor(Color(0xFF33AAFF)) shouldBe Color(0xFF33AAFF).copy(
            alpha = AURORA_SETTINGS_SELECTION_BACKGROUND_ALPHA,
        )
    }

    @Test
    fun `aurora selection border uses stronger accent alpha`() {
        resolveAuroraSelectionBorderColor(Color(0xFF33AAFF)) shouldBe Color(0xFF33AAFF).copy(
            alpha = AURORA_SETTINGS_SELECTION_BORDER_ALPHA,
        )
    }

    @Test
    fun `aurora card border is hidden to match more screen cards`() {
        resolveAuroraCardBorderColor(Color(0xFF33AAFF)) shouldBe Color.Transparent
    }

    @Test
    fun `aurora card horizontal inset matches more screen spacing`() {
        AURORA_SETTINGS_CARD_HORIZONTAL_INSET shouldBe 16.dp
    }
}
