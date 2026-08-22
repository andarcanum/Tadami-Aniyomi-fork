package eu.kanade.tachiyomi.ui.library.novel.quotes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.library.novel.quotes.NovelQuotesLibraryContent
import eu.kanade.presentation.reader.novel.NovelHighlightEditorSheet
import eu.kanade.presentation.util.Screen
import tachiyomi.domain.book.novel.model.NovelHighlightWithChapter
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

class NovelQuotesLibraryScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { NovelQuotesLibraryScreenModel() }
        val state by screenModel.state.collectAsStateWithLifecycle()

        var editing by remember { mutableStateOf<NovelHighlightWithChapter?>(null) }
        var deleting by remember { mutableStateOf<NovelHighlightWithChapter?>(null) }

        val onCopy: (String) -> Unit = { text ->
            val clipboard =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("quote", text))
        }

        NovelQuotesLibraryContent(
            state = state,
            onBack = navigator::pop,
            onSearchQueryChange = screenModel::search,
            onToggleSort = screenModel::toggleSort,
            onSelectBook = screenModel::selectBook,
            onEditQuote = { editing = it },
            onDeleteQuote = { deleting = it },
            onCopyQuote = onCopy,
        )

        editing?.let { item ->
            NovelHighlightEditorSheet(
                highlight = item.highlight,
                onDismiss = { editing = null },
                onSave = { note, colorArgb ->
                    screenModel.updateQuote(item.highlight.id, note, colorArgb)
                    editing = null
                },
                onDelete = {
                    deleting = item
                    editing = null
                },
                onCopy = onCopy,
                onShare = { text -> shareText(context, text) },
            )
        }

        deleting?.let { item ->
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text(text = stringResource(AYMR.strings.novel_quotes_delete_title)) },
                text = { Text(text = stringResource(AYMR.strings.novel_quotes_delete_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        screenModel.deleteQuote(item.highlight.id)
                        deleting = null
                    }) {
                        Text(text = stringResource(MR.strings.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleting = null }) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
    }

    private fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
}
