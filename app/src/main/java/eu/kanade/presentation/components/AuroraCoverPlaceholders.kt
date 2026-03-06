package eu.kanade.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import eu.kanade.presentation.theme.LocalIsAuroraTheme
import eu.kanade.presentation.util.rememberResourceBitmapPainter
import eu.kanade.tachiyomi.R
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.entries.novel.model.NovelCover

enum class AuroraCoverPlaceholderVariant {
    Portrait,
    Wide,
}

@DrawableRes
fun auroraCoverPlaceholderResId(variant: AuroraCoverPlaceholderVariant): Int {
    return when (variant) {
        AuroraCoverPlaceholderVariant.Portrait -> R.drawable.aurora_cover_placeholder_portrait
        AuroraCoverPlaceholderVariant.Wide -> R.drawable.aurora_cover_placeholder_wide
    }
}

@Composable
fun rememberAuroraCoverPlaceholderPainter(
    variant: AuroraCoverPlaceholderVariant = AuroraCoverPlaceholderVariant.Portrait,
): Painter {
    return rememberResourceBitmapPainter(id = auroraCoverPlaceholderResId(variant))
}

@Composable
fun rememberThemeAwareCoverErrorPainter(
    variant: AuroraCoverPlaceholderVariant = AuroraCoverPlaceholderVariant.Portrait,
): Painter {
    return if (LocalIsAuroraTheme.current) {
        rememberAuroraCoverPlaceholderPainter(variant = variant)
    } else {
        rememberResourceBitmapPainter(id = R.drawable.cover_error)
    }
}

fun resolveAuroraCoverModel(data: Any?): Any? {
    return when (data) {
        null -> null
        is String -> data.takeIf { it.isNotBlank() }
        is AnimeCover -> data.takeIf { !it.url.isNullOrBlank() }
        is MangaCover -> data.takeIf { !it.url.isNullOrBlank() }
        is NovelCover -> data.takeIf { !it.url.isNullOrBlank() }
        else -> data
    }
}
