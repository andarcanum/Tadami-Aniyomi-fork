package eu.kanade.presentation.entries.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import coil3.compose.AsyncImage
import eu.kanade.presentation.util.rememberResourceBitmapPainter
import eu.kanade.tachiyomi.R
import tachiyomi.domain.entries.novel.model.NovelCover

enum class ItemCover(val ratio: Float) {
    Square(1f / 1f),
    Book(2f / 3f),
    Thumb(16f / 9f),
    ;

    @Composable
    operator fun invoke(
        data: Any?,
        modifier: Modifier = Modifier,
        contentDescription: String = "",
        shape: Shape = MaterialTheme.shapes.extraSmall,
        onClick: (() -> Unit)? = null,
    ) {
        val model = resolveCoverModel(data)
        val imageModifier = modifier
            .aspectRatio(ratio)
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )

        if (isLoadableCoverData(model)) {
            AsyncImage(
                model = model,
                placeholder = ColorPainter(CoverPlaceholderColor),
                error = rememberResourceBitmapPainter(id = R.drawable.cover_error),
                contentDescription = contentDescription,
                modifier = imageModifier,
                contentScale = ContentScale.Crop,
            )
        } else {
            Image(
                painter = rememberResourceBitmapPainter(id = R.drawable.cover_error),
                contentDescription = contentDescription,
                modifier = imageModifier,
                contentScale = ContentScale.Crop,
            )
        }
    }
}

internal fun resolveCoverModel(data: Any?): Any? {
    return when (data) {
        is NovelCover -> data.takeIf { !it.url.isNullOrBlank() }
        else -> data
    }
}

internal fun isLoadableCoverData(data: Any?): Boolean {
    return when (data) {
        null -> false
        is String -> data.isNotBlank()
        else -> true
    }
}

private val CoverPlaceholderColor = Color(0x1F888888)
