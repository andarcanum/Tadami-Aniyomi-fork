package eu.kanade.presentation.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.waitForIdle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import tachiyomi.presentation.core.components.AdaptiveSheetScrimTestTag
import tachiyomi.presentation.core.components.AdaptiveSheetSurfaceTestTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AdaptiveSheetTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun root_sheet_dismisses_once() {
        val dismissCount = AtomicInteger(0)
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            AdaptiveSheet(
                onDismissRequest = { dismissCount.incrementAndGet() },
            ) {
                Text("Root sheet content", Modifier.testTag("root_sheet_content"))
            }
        }

        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(AdaptiveSheetSurfaceTestTag).assertExists()
        composeTestRule.onNodeWithText("Root sheet content").assertIsDisplayed()

        composeTestRule.onNodeWithTag(AdaptiveSheetScrimTestTag).performClick()
        composeTestRule.onNodeWithTag(AdaptiveSheetScrimTestTag).performClick()

        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()

        assertEquals(1, dismissCount.get())
    }

    @Test
    fun swipe_disabled_sheet_does_not_drag_dismiss() {
        val dismissCount = AtomicInteger(0)
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            AdaptiveSheet(
                onDismissRequest = { dismissCount.incrementAndGet() },
                enableSwipeDismiss = false,
            ) {
                Text("Swipe disabled content", Modifier.testTag("swipe_disabled_content"))
            }
        }

        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(AdaptiveSheetSurfaceTestTag).assertExists()
        composeTestRule.onNodeWithText("Swipe disabled content").assertIsDisplayed()

        composeTestRule.onNodeWithTag(AdaptiveSheetSurfaceTestTag).performTouchInput {
            swipeDown()
        }
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()

        assertEquals(0, dismissCount.get())

        composeTestRule.onNodeWithTag(AdaptiveSheetScrimTestTag).performClick()
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()

        assertEquals(1, dismissCount.get())
    }

    @Test
    fun nested_navigator_back_pops_before_dismiss() {
        val dismissCount = AtomicInteger(0)
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            NavigatorAdaptiveSheet(
                screen = RootNavigatorScreen,
                onDismissRequest = { dismissCount.incrementAndGet() },
            )
        }

        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Root navigator content").assertExists()
        composeTestRule.onNodeWithText("Push child").performClick()
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Child navigator content").assertIsDisplayed()

        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Child navigator content").assertDoesNotExist()
        composeTestRule.onNodeWithText("Root navigator content").assertIsDisplayed()
        assertEquals(0, dismissCount.get())

        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()

        assertEquals(1, dismissCount.get())
    }
}

private object RootNavigatorScreen : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        Column {
            Text("Root navigator content")
            Button(onClick = { navigator.push(ChildNavigatorScreen) }) {
                Text("Push child")
            }
        }
    }
}

private object ChildNavigatorScreen : Screen() {
    @Composable
    override fun Content() {
        Text("Child navigator content")
    }
}
