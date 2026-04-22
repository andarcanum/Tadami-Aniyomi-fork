package eu.kanade.presentation.entries.manga.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.updatePadding
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Size
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.entries.EditCoverAction
import eu.kanade.presentation.entries.components.aurora.AuroraPosterActionPanel
import eu.kanade.presentation.entries.components.aurora.AuroraPosterDialog
import eu.kanade.presentation.entries.components.aurora.AuroraZoomablePoster
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun MangaCoverDialog(
    manga: Manga,
    isCustomCover: Boolean,
    snackbarHostState: SnackbarHostState,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    onEditClick: ((EditCoverAction) -> Unit)?,
    onDismissRequest: () -> Unit,
) {
    AuroraPosterDialog(
        snackbarHostState = snackbarHostState,
        onDismissRequest = onDismissRequest,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                AuroraPosterActionPanel { contentColor ->
                    IconButton(onClick = onSaveClick) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            tint = contentColor,
                            contentDescription = stringResource(MR.strings.action_download),
                        )
                    }
                    if (onEditClick != null) {
                        Box {
                            var expanded by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = {
                                    if (isCustomCover) {
                                        expanded = true
                                    } else {
                                        onEditClick(EditCoverAction.EDIT)
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    tint = contentColor,
                                    contentDescription = stringResource(MR.strings.action_edit_cover),
                                )
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                offset = DpOffset(8.dp, 0.dp),
                            ) {
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(MR.strings.action_edit)) },
                                    onClick = {
                                        onEditClick(EditCoverAction.EDIT)
                                        expanded = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(MR.strings.action_delete)) },
                                    onClick = {
                                        onEditClick(EditCoverAction.DELETE)
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onShareClick) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            tint = contentColor,
                            contentDescription = stringResource(MR.strings.action_share),
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            tint = contentColor,
                            contentDescription = stringResource(MR.strings.action_close),
                        )
                    }
                }
            }
        },
    ) { contentPadding ->
            val statusBarPaddingPx = with(LocalDensity.current) { contentPadding.calculateTopPadding().roundToPx() }
            val bottomPaddingPx = with(LocalDensity.current) { contentPadding.calculateBottomPadding().roundToPx() }

            AuroraZoomablePoster(
                onDismissRequest = onDismissRequest,
                modifier = Modifier.padding(contentPadding),
            ) {
                AndroidView(
                    factory = {
                        ReaderPageImageView(it).apply {
                            onViewClicked = onDismissRequest
                            clipToPadding = false
                            clipChildren = false
                        }
                    },
                    update = { view ->
                        val request = ImageRequest.Builder(view.context)
                            .data(manga)
                            .size(Size.ORIGINAL)
                            .memoryCachePolicy(CachePolicy.DISABLED)
                            .target { image ->
                                val drawable = image.asDrawable(view.context.resources)

                                // Copy bitmap in case it came from memory cache
                                // Because SSIV needs to thoroughly read the image
                                val copy = (drawable as? BitmapDrawable)?.let {
                                    BitmapDrawable(
                                        view.context.resources,
                                        it.bitmap.copy(Bitmap.Config.HARDWARE, false),
                                    )
                                } ?: drawable
                                view.setImage(copy, ReaderPageImageView.Config(zoomDuration = 500))
                            }
                            .build()
                        view.context.imageLoader.enqueue(request)

                        view.updatePadding(top = statusBarPaddingPx, bottom = bottomPaddingPx)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
}
