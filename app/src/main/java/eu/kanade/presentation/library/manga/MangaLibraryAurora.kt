package eu.kanade.presentation.library.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.entries.manga.model.asMangaCover
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun AniviewMangaCard(
    item: LibraryManga,
    onClick: (Long) -> Unit,
) {
    val colors = AuroraTheme.colors
    val context = LocalContext.current

    // Calculate progress (read chapters / total chapters)
    val progress = if (item.totalChapters > 0) {
        (item.totalChapters - item.unreadCount).toFloat() / item.totalChapters.toFloat()
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(item.manga.id) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f) // Portrait aspect ratio
                .clip(RoundedCornerShape(12.dp))
                .background(colors.cardBackground),
        ) {
            AsyncImage(
                model = remember(item.manga.id, item.manga.thumbnailUrl, item.manga.coverLastModified) {
                    ImageRequest.Builder(context)
                        .data(item.manga.asMangaCover())
                        .placeholderMemoryCacheKey(item.manga.thumbnailUrl)
                        .build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Unread chapters badge (top-left)
            if (item.unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(colors.accent, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = item.unreadCount.toString(),
                        color = colors.textOnAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Progress bar embedded at bottom (Aniview style)
            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = colors.progressCyan, // Cyan progress bar
                    trackColor = Color.Black.copy(alpha = 0.3f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                )
            }
        }

        // Title below image
        Text(
            text = item.manga.title,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )

        // Chapter count (metadata)
        val readCount = item.totalChapters - item.unreadCount
        Text(
            text = "$readCount/${item.totalChapters} ${stringResource(MR.strings.chapters)}",
            color = colors.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

// Legacy card name alias for compatibility
@Composable
fun AuroraMangaCard(
    item: LibraryManga,
    onClick: (Long) -> Unit,
) = AniviewMangaCard(item, onClick)
