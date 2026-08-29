package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.novel.NovelQuoteShareFormatter
import tachiyomi.domain.book.novel.model.NovelHighlightWithChapter
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Vintage "old book" quotes screen: parchment surface, serif typography and ornamental
 * dividers between the saved quotes of the whole novel. Every quote carries its own
 * provenance line (novel title + chapter).
 */
@Composable
fun NovelQuotesScreen(
    novelTitle: String,
    quotes: List<NovelHighlightWithChapter>,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onShareAll: (String) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val parchment = if (dark) Color(0xFF2B2419) else Color(0xFFF1E4C6)
    val ink = if (dark) Color(0xFFE8DCC0) else Color(0xFF3B2F23)
    val faded = if (dark) Color(0xFFB8A98A) else Color(0xFF7A6A50)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(parchment),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = null,
                        tint = ink,
                    )
                }
                Text(
                    text = stringResource(AYMR.strings.novel_quotes_screen_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Serif,
                    color = ink,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = {
                    val document = NovelQuoteShareFormatter.formatDocument(
                        novelTitle = novelTitle,
                        quotes = quotes.map {
                            Triple(it.highlight.normalizedText, it.chapterName.orEmpty(), it.highlight.note)
                        },
                    )
                    onShareAll(document)
                }) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = stringResource(AYMR.strings.novel_quotes_share_all),
                        tint = ink,
                    )
                }
            }
            Text(
                text = novelTitle,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Serif,
                color = faded,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                textAlign = TextAlign.Center,
            )
            if (quotes.isEmpty()) {
                Text(
                    text = stringResource(AYMR.strings.novel_highlight_list_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Serif,
                    color = faded,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(quotes, key = { it.highlight.id }) { item ->
                        QuoteCard(
                            quote = item.highlight.normalizedText,
                            chapterName = item.chapterName.orEmpty(),
                            note = item.highlight.note.takeIf { it.isNotBlank() },
                            ink = ink,
                            faded = faded,
                            onCopy = { onCopy(item.highlight.normalizedText) },
                            onShare = { onShare(item.highlight.normalizedText) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuoteCard(
    quote: String,
    chapterName: String,
    note: String?,
    ink: Color,
    faded: Color,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "«$quote»",
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            color = ink,
        )
        Text(
            text = "— $chapterName",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Serif,
            color = faded,
        )
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Serif,
                color = faded,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onCopy) {
                Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = null, tint = faded)
            }
            IconButton(onClick = onShare) {
                Icon(imageVector = Icons.Outlined.Share, contentDescription = null, tint = faded)
            }
        }
        Text(
            text = "❦",
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Serif,
            color = faded,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            textAlign = TextAlign.Center,
        )
    }
}
