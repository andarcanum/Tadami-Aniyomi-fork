package eu.kanade.presentation.reader.novel

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.icerock.moko.resources.StringResource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import android.graphics.Color as AndroidColor

class NovelReaderCustomBackgroundActionsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun card_exposes_three_actions_and_selects_card() {
        val renameCount = AtomicInteger(0)
        val replaceCount = AtomicInteger(0)
        val deleteCount = AtomicInteger(0)
        val selectCount = AtomicInteger(0)
        var renameLabel = ""
        var replaceLabel = ""
        var deleteLabel = ""

        val imageFile = createTestImageFile()
        val customItem = NovelReaderCustomBackgroundItem(
            id = "custom-1",
            displayName = "Moonlit Paper",
            fileName = imageFile.name,
            absolutePath = imageFile.absolutePath,
            isDarkHint = false,
            createdAt = 1L,
            updatedAt = 1L,
        )

        composeTestRule.setContent {
            MaterialTheme {
                renameLabel = stringResourceValue(AYMR.strings.editor_action_rename)
                replaceLabel = stringResourceValue(AYMR.strings.novel_reader_background_action_replace)
                deleteLabel = stringResourceValue(AYMR.strings.editor_action_delete)
                NovelReaderCustomBackgroundCard(
                    customItem = customItem,
                    selected = true,
                    onSelect = { selectCount.incrementAndGet() },
                    onRename = { renameCount.incrementAndGet() },
                    onReplace = { replaceCount.incrementAndGet() },
                    onDelete = { deleteCount.incrementAndGet() },
                )
            }
        }

        composeTestRule.onNodeWithText("Moonlit Paper").assertIsDisplayed()
        composeTestRule.onNodeWithText(imageFile.absolutePath).assertIsDisplayed()
        composeTestRule.onNodeWithText(renameLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(replaceLabel).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(deleteLabel).assertIsDisplayed()

        composeTestRule.onNodeWithText(renameLabel).performClick()
        composeTestRule.onNodeWithText(replaceLabel).performClick()
        composeTestRule.onNodeWithContentDescription(deleteLabel).performClick()
        composeTestRule.onNodeWithText("Moonlit Paper").performClick()

        assertEquals(1, renameCount.get())
        assertEquals(1, replaceCount.get())
        assertEquals(1, deleteCount.get())
        assertEquals(1, selectCount.get())
    }

    private fun createTestImageFile(): File {
        val file = File(composeTestRule.activity.cacheDir, "reader_background_test.png")
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.MAGENTA)
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        return file
    }

    @Composable
    private fun stringResourceValue(resource: StringResource): String {
        return stringResource(resource)
    }
}
