package eu.kanade.presentation.more

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import org.junit.Rule
import org.junit.Test

class NewUpdateScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun changelog_is_collapsed_by_default_and_expands_on_click() {
        composeTestRule.setContent {
            TachiyomiPreviewTheme {
                NewUpdateScreen(
                    versionName = "v1.2.3",
                    releaseDate = "2026-03-29",
                    changelogInfo = """
                        ## Features
                        - Cleaner settings styling
                        - Better update dialog flow
                    """.trimIndent(),
                    ignoreThisVersion = false,
                    onToggleIgnoreVersion = {},
                    onOpenInBrowser = {},
                    onRejectUpdate = {},
                    onAcceptUpdate = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Show changelog").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Cleaner settings styling").assertCountEquals(0)
        composeTestRule.onNodeWithText("Don’t show this version again").assertIsDisplayed()
        composeTestRule.onNodeWithText("Download").assertIsDisplayed()

        composeTestRule.onNodeWithText("Show changelog").performClick()

        composeTestRule.onNodeWithText("Hide changelog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cleaner settings styling").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open on GitHub").assertIsDisplayed()
    }
}
